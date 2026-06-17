package com.dps.evenup.domain.participant.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ParticipantTest {
    @Test
    fun `participant preserves creation order`() {
        val participant = Participant(
            id = ParticipantId("participant-1"),
            name = "Anna",
            creationOrder = 2,
        )

        assertEquals(2, participant.creationOrder)
        assertFalse(participant.isSavedLocalName)
    }
}
