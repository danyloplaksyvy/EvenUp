package com.dps.evenup.feature.expenseflow.impl.expensesaved

sealed interface ExpenseSavedUiEvent {
    data object ShareClick : ExpenseSavedUiEvent

    data object CopyClick : ExpenseSavedUiEvent

    data object AddAnotherClick : ExpenseSavedUiEvent
}
