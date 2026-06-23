package com.dps.evenup.feature.expenseflow.impl.feesallocation

import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.receipt.api.FeeType

internal fun shouldShowFeesAllocation(draft: ExpenseDraft?): Boolean {
    return draft?.receipt?.fees?.any { fee -> fee.type != FeeType.Discount && fee.amount.value > 0L } == true
}
