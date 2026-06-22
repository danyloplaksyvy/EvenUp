package com.dps.evenup.feature.expenseflow.impl.reviewexpense

sealed interface ReviewExpenseUiEvent {
    data object CalculationDetailsClick : ReviewExpenseUiEvent

    data object CalculationDetailsDismissed : ReviewExpenseUiEvent

    data object SaveClick : ReviewExpenseUiEvent

    data object SaveRetryClick : ReviewExpenseUiEvent

    data object BackClick : ReviewExpenseUiEvent
}
