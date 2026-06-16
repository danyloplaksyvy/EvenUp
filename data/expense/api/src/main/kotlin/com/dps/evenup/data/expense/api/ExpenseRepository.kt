package com.dps.evenup.data.expense.api

import com.dps.evenup.data.sharing.api.SavedShareLink
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.FinalizedExpensePayload

interface ExpenseRepository {
    suspend fun saveFinalizedExpense(payload: FinalizedExpensePayload): SavedShareLink
}

interface ExpenseDraftRepository {
    suspend fun getDraft(): ExpenseDraft?

    suspend fun saveDraft(draft: ExpenseDraft)

    suspend fun clearDraft()
}

class ExpenseDataException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
