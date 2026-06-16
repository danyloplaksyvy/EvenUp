package com.dps.evenup.feature.expenseflow.impl.manualentry

data class ManualReceiptEntryUiState(
    val merchantName: String = "",
    val dateLabel: String = "",
    val currencyCode: String = "USD",
    val items: List<ManualReceiptItemUiState> = listOf(ManualReceiptItemUiState(id = "1")),
    val taxAmount: String = "",
    val tipAmount: String = "",
    val totalAmount: String = "",
    val isSaving: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val submitError: String? = null,
) {
    val itemCountLabel: String = "${items.size} added"
}

data class ManualReceiptItemUiState(
    val id: String,
    val name: String = "",
    val amount: String = "",
)
