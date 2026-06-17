package com.dps.evenup.domain.sharing.api

import com.dps.evenup.domain.expense.api.ExpenseId

interface CreateShareLinkUseCase {
    suspend fun createShareLink(expenseId: ExpenseId): ShareLink
}

data class ShareLink(
    val shareId: String,
    val publicUrl: String,
)
