package com.dps.evenup.domain.expense.api

import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.receipt.api.ReceiptFee

interface AllocateFeesUseCase {
    fun allocateEqual(
        fee: ReceiptFee,
        participants: List<Participant>,
    ): FeeAllocation

    fun allocateProportional(
        fee: ReceiptFee,
        participants: List<Participant>,
        itemAssignments: List<ItemAssignment>,
    ): FeeAllocation

    fun validateCustom(
        fee: ReceiptFee,
        participants: List<Participant>,
        shares: List<FeeParticipantShare>,
    ): FeeAllocationValidationResult
}

data class FeeAllocationValidationResult(
    val errors: Set<FeeAllocationValidationError>,
) {
    val isValid: Boolean = errors.isEmpty()

    companion object {
        val Valid = FeeAllocationValidationResult(emptySet())
    }
}

enum class FeeAllocationValidationError {
    UnknownParticipant,
    EmptyShares,
    NegativeShareAmount,
    AmountTotalMismatch,
}
