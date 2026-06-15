package com.dps.evenup.domain.expense.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExpenseIdsTest {
    @Test
    fun `accepts nonblank ids`() {
        assertEquals("expense-1", ExpenseId("expense-1").value)
        assertEquals("draft-1", ExpenseDraftId("draft-1").value)
    }

    @Test
    fun `rejects blank ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseId(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseDraftId(" ")
        }
    }
}
