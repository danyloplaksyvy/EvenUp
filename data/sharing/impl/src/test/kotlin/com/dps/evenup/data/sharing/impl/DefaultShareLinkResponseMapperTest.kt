package com.dps.evenup.data.sharing.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DefaultShareLinkResponseMapperTest {
    @Test
    fun `maps worker share link response to domain values`() {
        val result = DefaultShareLinkResponseMapper().map(
            expenseId = "expense_123",
            shareId = "A8xQ2Lm9",
            shareUrl = "https://evenup.example/e/A8xQ2Lm9",
        )

        assertEquals("expense_123", result.expenseId.value)
        assertEquals("A8xQ2Lm9", result.shareLink.shareId)
        assertEquals("https://evenup.example/e/A8xQ2Lm9", result.shareLink.publicUrl)
    }

    @Test
    fun `blank response fields fail before entering domain`() {
        assertThrows(IllegalArgumentException::class.java) {
            DefaultShareLinkResponseMapper().map(
                expenseId = "",
                shareId = "A8xQ2Lm9",
                shareUrl = "https://evenup.example/e/A8xQ2Lm9",
            )
        }
    }
}
