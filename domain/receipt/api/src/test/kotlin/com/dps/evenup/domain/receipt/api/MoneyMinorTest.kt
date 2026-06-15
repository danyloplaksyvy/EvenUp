package com.dps.evenup.domain.receipt.api

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyMinorTest {
    @Test
    fun `supports signed minor units for balances`() {
        assertEquals(MoneyMinor(-250), MoneyMinor(500) - MoneyMinor(750))
    }

    @Test
    fun `supports exact integer addition`() {
        assertEquals(MoneyMinor(1250), MoneyMinor(500) + MoneyMinor(750))
    }
}
