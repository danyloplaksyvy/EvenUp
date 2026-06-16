package com.dps.evenup.feature.expenseflow.impl.newexpense

sealed interface NewExpenseUiEvent {
    data object ScanReceiptClick : NewExpenseUiEvent

    data object EnterManuallyClick : NewExpenseUiEvent

    data object CloseClick : NewExpenseUiEvent
}
