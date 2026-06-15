package com.dps.evenup.data.sharing.api

import com.dps.evenup.domain.expense.api.ExpenseId
import com.dps.evenup.domain.sharing.api.ShareLink

data class SavedShareLink(
    val expenseId: ExpenseId,
    val shareLink: ShareLink,
)

interface ShareLinkResponseMapper {
    fun map(
        expenseId: String,
        shareId: String,
        shareUrl: String,
    ): SavedShareLink
}
