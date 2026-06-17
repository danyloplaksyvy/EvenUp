package com.dps.evenup.domain.expense.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PercentageBasisPointsTest {
    @Test
    fun `accepts valid basis points`() {
        assertEquals(2_500, PercentageBasisPoints(2_500).value)
    }

    @Test
    fun `rejects negative basis points`() {
        assertThrows(IllegalArgumentException::class.java) {
            PercentageBasisPoints(-1)
        }
    }

    @Test
    fun `rejects more than one hundred percent`() {
        assertThrows(IllegalArgumentException::class.java) {
            PercentageBasisPoints(10_001)
        }
    }
}
