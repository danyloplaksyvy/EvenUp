package com.dps.evenup.feature.expenseflow.impl.receiptscan

data class ReceiptScanUiState(
    val isParsing: Boolean = false,
    val errorMessage: String? = null,
) {
    val captureEnabled: Boolean = !isParsing
}
