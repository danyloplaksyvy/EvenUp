package com.dps.evenup.domain.receipt.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuantityTest {
    @Test
    fun `accepts positive quantity`() {
        assertEquals(2, Quantity(2).value)
    }

    @Test
    fun `rejects zero quantity`() {
        assertThrows(IllegalArgumentException::class.java) {
            Quantity(0)
        }
    }

    @Test
    fun `rejects negative quantity`() {
        assertThrows(IllegalArgumentException::class.java) {
            Quantity(-1)
        }
    }
}
