package com.dps.evenup.feature.expenseflow.impl.feesallocation

import com.dps.evenup.domain.receipt.api.MoneyMinor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeesAllocationRoutingTest {
    @Test
    fun `no fees skips fees allocation`() {
        assertFalse(shouldShowFeesAllocation(feesAllocationDraft(fees = emptyList())))
    }

    @Test
    fun `all zero fees skips fees allocation`() {
        assertFalse(shouldShowFeesAllocation(feesAllocationDraft(fees = listOf(fee(amount = 0)))))
    }

    @Test
    fun `positive fee shows fees allocation`() {
        assertTrue(shouldShowFeesAllocation(feesAllocationDraft(fees = listOf(fee(amount = 1)))))
    }

    private fun fee(amount: Long) = feesAllocationFee(amount = MoneyMinor(amount))
}
