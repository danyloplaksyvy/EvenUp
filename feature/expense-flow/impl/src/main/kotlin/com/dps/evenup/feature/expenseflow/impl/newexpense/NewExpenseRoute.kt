package com.dps.evenup.feature.expenseflow.impl.newexpense

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun NewExpenseRoute(
    onScanReceipt: () -> Unit,
    onEnterManually: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    NewExpenseScreen(
        uiState = NewExpenseUiState(),
        onEvent = { event ->
            when (event) {
                NewExpenseUiEvent.ScanReceiptClick -> onScanReceipt()
                NewExpenseUiEvent.EnterManuallyClick -> onEnterManually()
                NewExpenseUiEvent.CloseClick -> onClose?.invoke()
            }
        },
        modifier = modifier,
        closeEnabled = onClose != null,
    )
}
