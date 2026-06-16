package com.dps.evenup.feature.expenseflow.impl.newexpense

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dps.evenup.feature.expenseflow.api.ExpenseFlowDestination

@Composable
fun NewExpenseRoute(
    onDestinationSelected: (ExpenseFlowDestination) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    NewExpenseScreen(
        uiState = NewExpenseUiState(),
        onEvent = { event ->
            when (event) {
                NewExpenseUiEvent.ScanReceiptClick -> onDestinationSelected(ExpenseFlowDestination.RECEIPT_SCAN)
                NewExpenseUiEvent.EnterManuallyClick -> onDestinationSelected(ExpenseFlowDestination.MANUAL_ENTRY)
                NewExpenseUiEvent.CloseClick -> onClose?.invoke()
            }
        },
        modifier = modifier,
        closeEnabled = onClose != null,
    )
}
