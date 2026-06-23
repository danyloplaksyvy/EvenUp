package com.dps.evenup.domain.expense.impl

import com.dps.evenup.domain.expense.api.CalculateExpenseSummaryUseCase
import com.dps.evenup.domain.expense.api.ExpenseSummary
import com.dps.evenup.domain.expense.api.FeeAllocation
import com.dps.evenup.domain.expense.api.FeeParticipantShare
import com.dps.evenup.domain.expense.api.ItemAssignment
import com.dps.evenup.domain.expense.api.ItemParticipantShare
import com.dps.evenup.domain.expense.api.ParticipantExpenseSummary
import com.dps.evenup.domain.expense.api.SettlementRow
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Receipt

class DefaultCalculateExpenseSummaryUseCase : CalculateExpenseSummaryUseCase {
    override fun calculate(
        receipt: Receipt,
        participants: List<Participant>,
        payerId: ParticipantId,
        itemAssignments: List<ItemAssignment>,
        feeAllocations: List<FeeAllocation>,
    ): ExpenseSummary {
        val itemTotalsByParticipant = participants.associate { participant ->
            participant.id to itemAssignments
                .flatMap { it.shares }
                .filter { it.participantId == participant.id }
                .sumItemShares()
        }
        val discountCredits = receipt.discountCredits(
            participants = participants,
            payerId = payerId,
            itemTotalsByParticipant = itemTotalsByParticipant,
        )
        val summaries = participants.sortedBy { it.creationOrder }.map { participant ->
            val itemTotal = itemTotalsByParticipant.getValue(participant.id)
            val feeTotal = feeAllocations
                .flatMap { it.shares }
                .filter { it.participantId == participant.id }
                .sumFeeShares()
            val discountCredit = discountCredits.getOrDefault(participant.id, MoneyMinor.Zero)
            val personShare = itemTotal + feeTotal - discountCredit
            val amountPaid = if (participant.id == payerId) receipt.total else MoneyMinor.Zero

            ParticipantExpenseSummary(
                participantId = participant.id,
                assignedItemTotal = itemTotal,
                allocatedFeeTotal = feeTotal,
                personShare = personShare,
                amountPaid = amountPaid,
                netBalance = amountPaid - personShare,
                discountCreditTotal = discountCredit,
            )
        }

        val settlementRows = summaries
            .filter { it.netBalance.value < 0 }
            .map {
                SettlementRow(
                    fromParticipantId = it.participantId,
                    toParticipantId = payerId,
                    amount = MoneyMinor(-it.netBalance.value),
                )
            }

        return ExpenseSummary(
            receiptTotal = receipt.total,
            participantSummaries = summaries,
            settlementRows = settlementRows,
        )
    }

    private fun List<ItemParticipantShare>.sumItemShares(): MoneyMinor {
        return MoneyMinor(sumOf { it.amount.value })
    }

    private fun List<FeeParticipantShare>.sumFeeShares(): MoneyMinor {
        return MoneyMinor(sumOf { it.amount.value })
    }

    private fun Receipt.discountCredits(
        participants: List<Participant>,
        payerId: ParticipantId,
        itemTotalsByParticipant: Map<ParticipantId, MoneyMinor>,
    ): Map<ParticipantId, MoneyMinor> {
        val discountTotal = -fees
            .filter { fee -> fee.type == FeeType.Discount }
            .sumOf { fee -> fee.amount.value }
        if (discountTotal <= 0L || participants.isEmpty()) return emptyMap()

        val orderedParticipants = participants.stableDiscountOrder(payerId)
        val totalItemSubtotal = itemTotalsByParticipant.values.sumOf { subtotal -> subtotal.value }
        if (totalItemSubtotal <= 0L) {
            return DeterministicSplitRounding.splitEvenly(MoneyMinor(discountTotal), orderedParticipants.map { it.id })
        }

        val rawCredits = orderedParticipants.map { participant ->
            val subtotal = itemTotalsByParticipant.getValue(participant.id).value
            participant.id to ((discountTotal * subtotal) / totalItemSubtotal)
        }
        val allocated = rawCredits.sumOf { it.second }
        var remainder = discountTotal - allocated

        return rawCredits.associate { (participantId, amount) ->
            val hasSubtotal = itemTotalsByParticipant.getValue(participantId).value > 0L
            val extra = if (remainder > 0L && hasSubtotal) {
                remainder -= 1L
                1L
            } else {
                0L
            }
            participantId to MoneyMinor(amount + extra)
        }
    }

    private fun List<Participant>.stableDiscountOrder(payerId: ParticipantId): List<Participant> {
        return sortedWith(
            compareBy<Participant> { participant -> if (participant.id == payerId) 0 else 1 }
                .thenBy { participant -> participant.creationOrder },
        )
    }
}
