package com.dps.evenup.feature.expenseflow.impl.receiptentry

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptEntryControlsTest {
    @Test
    fun `blank date defaults to today`() {
        val todayMillis = epochMillis("2026-06-23")

        val selectedMillis = resolveDatePickerInitialSelectedMillis("", todayMillis)

        assertEquals(todayMillis, selectedMillis)
    }

    @Test
    fun `existing date is preserved`() {
        val todayMillis = epochMillis("2026-06-23")
        val existingMillis = epochMillis("2019-03-10")

        val selectedMillis = resolveDatePickerInitialSelectedMillis("2019-03-10", todayMillis)

        assertEquals(existingMillis, selectedMillis)
    }

    @Test
    fun `future date falls back to latest selectable date`() {
        val todayMillis = epochMillis("2026-06-23")

        val selectedMillis = resolveDatePickerInitialSelectedMillis("2026-06-24", todayMillis)

        assertEquals(todayMillis, selectedMillis)
    }

    private fun epochMillis(value: String): Long = LocalDate.parse(value)
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()
}
