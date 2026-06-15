package com.dps.evenup.domain.expense.api

@JvmInline
value class ExpenseId(val value: String) {
    init {
        require(value.isNotBlank()) { "Expense id must not be blank." }
    }
}

@JvmInline
value class ExpenseDraftId(val value: String) {
    init {
        require(value.isNotBlank()) { "Expense draft id must not be blank." }
    }
}
