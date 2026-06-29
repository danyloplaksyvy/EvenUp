package com.dps.evenup.feature.expenseflow.impl.expensesaved

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeGeneratorTest {
    @Test
    fun `qr code is deterministic for access url`() {
        val url = "https://evenup.example/e/A8xQ2Lm9?code=KTRQ"

        val first = QrCodeGenerator.encode(url)
        val second = QrCodeGenerator.encode(url)

        assertNotNull(first)
        requireNotNull(first)
        requireNotNull(second)
        assertEquals(first.size, second.size)
        assertEquals(first.darkModuleCount, second.darkModuleCount)

        for (y in 0 until first.size) {
            for (x in 0 until first.size) {
                assertEquals(first[x, y], second[x, y])
            }
        }
    }

    @Test
    fun `qr code contains both dark and light modules`() {
        val matrix = requireNotNull(QrCodeGenerator.encode("https://evenup.example/e/A8xQ2Lm9?code=KTRQ"))

        assertTrue(matrix.size > 0)
        assertTrue(matrix.darkModuleCount > 0)
        assertTrue(matrix.darkModuleCount < matrix.size * matrix.size)
    }

    @Test
    fun `blank or oversized qr payload is not rendered`() {
        assertNull(QrCodeGenerator.encode(""))
        assertNull(QrCodeGenerator.encode("x".repeat(400)))
    }
}
