package com.dps.evenup.domain.expenseinput.impl

import com.dps.evenup.domain.expenseinput.api.ParticipantNameMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultParticipantNameMatcherTest {
    private val matcher = DefaultParticipantNameMatcher()

    @Test
    fun `exact matching folds case and collapses whitespace`() {
        val result = matcher.match("  joHN   SMITH ", listOf("John Smith"))

        assertEquals(ParticipantNameMatch.Exact("John Smith"), result)
    }

    @Test
    fun `small edit distance and prefixes require confirmation`() {
        assertEquals(
            ParticipantNameMatch.Possible(listOf("Jon")),
            matcher.match("John", listOf("Jon", "Alice")),
        )
        assertEquals(
            ParticipantNameMatch.Possible(listOf("Johnny")),
            matcher.match("John", listOf("Johnny", "Alice")),
        )
    }

    @Test
    fun `unrelated names remain separate`() {
        assertTrue(matcher.match("John", listOf("Alice", "Robert")) is ParticipantNameMatch.NewName)
    }
}
