package com.dps.evenup.feature.expenseflow.impl.expensesaved

data class ExpenseSavedUiState(
    val shareUrl: String,
    val isWorking: Boolean = false,
    val message: String? = null,
)
