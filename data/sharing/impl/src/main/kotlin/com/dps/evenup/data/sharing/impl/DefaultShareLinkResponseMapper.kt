package com.dps.evenup.data.sharing.impl

import com.dps.evenup.data.sharing.api.SavedShareLink
import com.dps.evenup.data.sharing.api.ShareLinkResponseMapper
import com.dps.evenup.domain.expense.api.ExpenseId
import com.dps.evenup.domain.sharing.api.ShareLink

class DefaultShareLinkResponseMapper : ShareLinkResponseMapper {
    override fun map(
        expenseId: String,
        shareId: String,
        shareUrl: String,
    ): SavedShareLink {
        require(expenseId.isNotBlank()) { "Expense id is required." }
        require(shareId.isNotBlank()) { "Share id is required." }
        require(shareUrl.isNotBlank()) { "Share URL is required." }

        return SavedShareLink(
            expenseId = ExpenseId(expenseId),
            shareLink = ShareLink(
                shareId = shareId,
                publicUrl = shareUrl,
            ),
        )
    }
}
