package com.dps.evenup.domain.participant.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ParticipantIdTest {
    @Test
    fun `accepts nonblank participant id`() {
        assertEquals("participant-1", ParticipantId("participant-1").value)
    }

    @Test
    fun `rejects blank participant id`() {
        assertThrows(IllegalArgumentException::class.java) {
            ParticipantId(" ")
        }
    }
}
