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
        val summaries = participants.sortedBy { it.creationOrder }.map { participant ->
            val itemTotal = itemAssignments
                .flatMap { it.shares }
                .filter { it.participantId == participant.id }
                .sumItemShares()
            val feeTotal = feeAllocations
                .flatMap { it.shares }
                .filter { it.participantId == participant.id }
                .sumFeeShares()
            val personShare = itemTotal + feeTotal
            val amountPaid = if (participant.id == payerId) receipt.total else MoneyMinor.Zero

            ParticipantExpenseSummary(
                participantId = participant.id,
                assignedItemTotal = itemTotal,
                allocatedFeeTotal = feeTotal,
                personShare = personShare,
                amountPaid = amountPaid,
                netBalance = amountPaid - personShare,
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
}
