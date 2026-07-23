package com.dps.evenup.feature.expenseflow.impl.newexpense

sealed interface NewExpenseUiEvent {
    data class DescriptionChanged(val value: String) : NewExpenseUiEvent
    data class AnswerChanged(val value: String) : NewExpenseUiEvent
    data object SubmitDescription : NewExpenseUiEvent
    data object SubmitAnswer : NewExpenseUiEvent
    data object CancelProcessing : NewExpenseUiEvent
    data object DescriptionMicClick : NewExpenseUiEvent
    data object AnswerMicClick : NewExpenseUiEvent
    data object StopRecording : NewExpenseUiEvent
    data object ReviewAllDetailsClick : NewExpenseUiEvent
    data object DefaultsClick : NewExpenseUiEvent
    data class DefaultsNameChanged(val value: String) : NewExpenseUiEvent
    data class DefaultsCurrencyChanged(val value: String) : NewExpenseUiEvent
    data object DefaultsSave : NewExpenseUiEvent
    data object DefaultsDismiss : NewExpenseUiEvent
    data object ScanReceiptClick : NewExpenseUiEvent
    data object EnterManuallyClick : NewExpenseUiEvent
    data object DiscardConfirmed : NewExpenseUiEvent
    data object DialogDismissed : NewExpenseUiEvent
    data object ReplaceConfirmed : NewExpenseUiEvent
    data object CloseClick : NewExpenseUiEvent
    data object ProfileClick : NewExpenseUiEvent
}
