package com.dps.evenup.feature.expenseflow.impl.expensesaved

data class ExpenseSavedUiState(
    val shareUrl: String,
    val guestPasscode: String,
    val isWorking: Boolean = false,
    val message: String? = null,
) {
    val shareMessage: String = "EvenUp expense breakdown\n$shareUrl\nCode: $guestPasscode"
}
