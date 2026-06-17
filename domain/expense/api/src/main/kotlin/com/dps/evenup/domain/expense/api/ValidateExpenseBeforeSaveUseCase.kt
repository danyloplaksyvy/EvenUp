package com.dps.evenup.domain.expense.api

interface ValidateExpenseBeforeSaveUseCase {
    fun validateAndBuildPayload(draft: ExpenseDraft): FinalExpenseValidationResult
}

sealed interface FinalExpenseValidationResult {
    data class Valid(val payload: FinalizedExpensePayload) : FinalExpenseValidationResult
    data class Invalid(val errors: Set<FinalExpenseValidationError>) : FinalExpenseValidationResult
}

enum class FinalExpenseValidationError {
    InvalidReceipt,
    InvalidParticipants,
    InvalidItemAssignments,
    InvalidFeeAllocations,
    ParticipantSharesDoNotEqualReceiptTotal,
    NetBalancesDoNotSumToZero,
}
