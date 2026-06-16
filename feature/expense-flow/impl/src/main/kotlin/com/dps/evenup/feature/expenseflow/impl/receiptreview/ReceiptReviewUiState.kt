package com.dps.evenup.feature.expenseflow.impl.receiptreview

import com.dps.evenup.domain.receipt.api.FeeType

data class ReceiptReviewUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val missingDraft: Boolean = false,
    val merchantName: String = "",
    val dateLabel: String = "",
    val currencyCode: String = "USD",
    val items: List<ReceiptReviewItemUiState> = emptyList(),
    val fees: List<ReceiptReviewFeeUiState> = emptyList(),
    val totalAmount: String = "",
    val fieldErrors: Map<String, String> = emptyMap(),
    val submitError: String? = null,
) {
    val itemCountLabel: String = "${items.size} items"
    val summaryTotalLabel: String = formatCurrency(totalAmount, currencyCode)
    val statusLabel: String = if (fieldErrors.isEmpty() && submitError == null) "Looks good" else "Needs review"
}

data class ReceiptReviewItemUiState(
    val id: String,
    val name: String = "",
    val quantity: String = "1",
    val amount: String = "",
)

data class ReceiptReviewFeeUiState(
    val id: String,
    val type: FeeType = FeeType.Other,
    val label: String = "",
    val amount: String = "",
)

internal fun formatCurrency(amount: String, currencyCode: String): String {
    val value = amount.trim().ifBlank { "0.00" }
    val prefix = when (currencyCode.uppercase()) {
        "EUR" -> "€"
        "USD" -> "\$"
        "GBP" -> "£"
        else -> "${currencyCode.uppercase()} "
    }
    return "$prefix$value"
}
