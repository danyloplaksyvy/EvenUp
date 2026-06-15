package com.dps.evenup.domain.receipt.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CurrencyCodeTest {
    @Test
    fun `accepts uppercase three-letter code`() {
        assertEquals("USD", CurrencyCode("USD").value)
    }

    @Test
    fun `rejects lowercase code`() {
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyCode("usd")
        }
    }

    @Test
    fun `rejects wrong length code`() {
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyCode("US")
        }
    }
}
