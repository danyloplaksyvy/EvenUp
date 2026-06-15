package com.dps.evenup.domain.expense.impl

import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.MoneyMinor
import org.junit.Assert.assertEquals
import org.junit.Test

class DeterministicSplitRoundingTest {
    private val participants = listOf(
        ParticipantId("payer"),
        ParticipantId("created-1"),
        ParticipantId("created-2"),
    )

    @Test
    fun `splits 1000 into 334 333 333`() {
        val result = DeterministicSplitRounding.splitEvenly(MoneyMinor(1_000), participants)

        assertEquals(MoneyMinor(334), result.getValue(participants[0]))
        assertEquals(MoneyMinor(333), result.getValue(participants[1]))
        assertEquals(MoneyMinor(333), result.getValue(participants[2]))
    }

    @Test
    fun `splits 1001 into 334 334 333`() {
        val result = DeterministicSplitRounding.splitEvenly(MoneyMinor(1_001), participants)

        assertEquals(MoneyMinor(334), result.getValue(participants[0]))
        assertEquals(MoneyMinor(334), result.getValue(participants[1]))
        assertEquals(MoneyMinor(333), result.getValue(participants[2]))
    }

    @Test
    fun `distributes remainder in stable recipient order`() {
        val reordered = listOf(participants[2], participants[0], participants[1])

        val result = DeterministicSplitRounding.splitEvenly(MoneyMinor(1_001), reordered)

        assertEquals(MoneyMinor(334), result.getValue(participants[2]))
        assertEquals(MoneyMinor(334), result.getValue(participants[0]))
        assertEquals(MoneyMinor(333), result.getValue(participants[1]))
    }
}
