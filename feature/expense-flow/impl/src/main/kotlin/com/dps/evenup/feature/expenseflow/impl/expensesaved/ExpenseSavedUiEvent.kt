package com.dps.evenup.feature.expenseflow.impl.expensesaved

sealed interface ExpenseSavedUiEvent {
    data object ShareInviteClick : ExpenseSavedUiEvent

    data object CopyLinkClick : ExpenseSavedUiEvent

    data object CopyCodeClick : ExpenseSavedUiEvent

    data object QrOpenClick : ExpenseSavedUiEvent

    data object AddAnotherClick : ExpenseSavedUiEvent
}
