package com.dps.evenup.domain.receipt.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReceiptIdsTest {
    @Test
    fun `accepts nonblank receipt item id`() {
        assertEquals("item-1", ReceiptItemId("item-1").value)
        assertEquals("fee-1", FeeId("fee-1").value)
    }

    @Test
    fun `rejects blank receipt ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReceiptItemId(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FeeId(" ")
        }
    }
}
