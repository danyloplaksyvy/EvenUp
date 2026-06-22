package com.dps.evenup.feature.expenseflow.impl.feesallocation

import com.dps.evenup.domain.expense.api.ExpenseDraft

internal fun shouldShowFeesAllocation(draft: ExpenseDraft?): Boolean {
    return draft?.receipt?.fees?.any { fee -> fee.amount.value > 0L } == true
}
