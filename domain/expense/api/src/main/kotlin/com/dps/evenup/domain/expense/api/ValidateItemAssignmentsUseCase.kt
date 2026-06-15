package com.dps.evenup.domain.expense.api

import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.receipt.api.ReceiptItem

interface ValidateItemAssignmentsUseCase {
    fun validate(
        receiptItems: List<ReceiptItem>,
        participants: List<Participant>,
        assignments: List<ItemAssignment>,
    ): ItemAssignmentValidationResult
}

data class ItemAssignmentValidationResult(
    val errors: Set<ItemAssignmentValidationError>,
) {
    val isValid: Boolean = errors.isEmpty()

    companion object {
        val Valid = ItemAssignmentValidationResult(emptySet())
    }
}

enum class ItemAssignmentValidationError {
    MissingItemAssignment,
    UnknownReceiptItem,
    UnknownParticipant,
    EmptyShares,
    AmountTotalMismatch,
    QuantityTotalMismatch,
    PercentageTotalMismatch,
    NegativeShareAmount,
    InvalidModeShape,
}
