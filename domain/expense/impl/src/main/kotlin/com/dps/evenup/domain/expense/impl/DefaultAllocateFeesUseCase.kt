package com.dps.evenup.domain.expense.impl

import com.dps.evenup.domain.expense.api.AllocateFeesUseCase
import com.dps.evenup.domain.expense.api.FeeAllocation
import com.dps.evenup.domain.expense.api.FeeAllocationMode
import com.dps.evenup.domain.expense.api.FeeAllocationValidationError
import com.dps.evenup.domain.expense.api.FeeAllocationValidationResult
import com.dps.evenup.domain.expense.api.FeeParticipantShare
import com.dps.evenup.domain.expense.api.ItemAssignment
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.ReceiptFee

class DefaultAllocateFeesUseCase : AllocateFeesUseCase {
    override fun allocateEqual(
        fee: ReceiptFee,
        participants: List<Participant>,
    ): FeeAllocation {
        val orderedParticipants = participants.sortedBy { it.creationOrder }
        val amounts = DeterministicSplitRounding.splitEvenly(fee.amount, orderedParticipants.map { it.id })
        return FeeAllocation(
            feeId = fee.id,
            mode = FeeAllocationMode.Equal,
            shares = orderedParticipants.map { participant ->
                FeeParticipantShare(participant.id, amounts.getValue(participant.id))
            },
        )
    }

    override fun allocateProportional(
        fee: ReceiptFee,
        participants: List<Participant>,
        itemAssignments: List<ItemAssignment>,
    ): FeeAllocation {
        val subtotals = itemSubtotals(participants, itemAssignments)
        val totalSubtotal = subtotals.values.sumOf { it.value }
        val shares = if (totalSubtotal == 0L) {
            allocateEqual(fee, participants).shares
        } else {
            allocateByWeights(fee.amount, participants, subtotals, totalSubtotal)
        }

        return FeeAllocation(
            feeId = fee.id,
            mode = FeeAllocationMode.Proportional,
            shares = shares,
        )
    }

    override fun validateCustom(
        fee: ReceiptFee,
        participants: List<Participant>,
        shares: List<FeeParticipantShare>,
    ): FeeAllocationValidationResult {
        val participantIds = participants.map { it.id }.toSet()
        val errors = buildSet {
            if (shares.isEmpty()) add(FeeAllocationValidationError.EmptyShares)
            if (shares.any { it.participantId !in participantIds }) {
                add(FeeAllocationValidationError.UnknownParticipant)
            }
            if (shares.any { it.amount.value < 0 }) {
                add(FeeAllocationValidationError.NegativeShareAmount)
            }
            if (shares.sumOf { it.amount.value } != fee.amount.value) {
                add(FeeAllocationValidationError.AmountTotalMismatch)
            }
        }
        return if (errors.isEmpty()) FeeAllocationValidationResult.Valid else FeeAllocationValidationResult(errors)
    }

    private fun itemSubtotals(
        participants: List<Participant>,
        itemAssignments: List<ItemAssignment>,
    ): Map<ParticipantId, MoneyMinor> {
        val initial = participants.associate { it.id to MoneyMinor.Zero }.toMutableMap()
        itemAssignments.flatMap { it.shares }.forEach { share ->
            initial[share.participantId] = initial.getOrDefault(share.participantId, MoneyMinor.Zero) + share.amount
        }
        return initial
    }

    private fun allocateByWeights(
        total: MoneyMinor,
        participants: List<Participant>,
        subtotals: Map<ParticipantId, MoneyMinor>,
        totalSubtotal: Long,
    ): List<FeeParticipantShare> {
        val ordered = participants.sortedBy { it.creationOrder }
        val rawShares = ordered.map { participant ->
            participant.id to ((total.value * subtotals.getValue(participant.id).value) / totalSubtotal)
        }
        val allocated = rawShares.sumOf { it.second }
        var remainder = total.value - allocated

        return rawShares.map { (participantId, amount) ->
            val extra = if (remainder > 0 && subtotals.getValue(participantId).value > 0) {
                remainder -= 1
                1L
            } else {
                0L
            }
            FeeParticipantShare(participantId, MoneyMinor(amount + extra))
        }
    }
}
