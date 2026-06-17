package com.dps.evenup.domain.participant.impl

import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.participant.api.ParticipantValidationError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultValidateParticipantsUseCaseTest {
    private val useCase = DefaultValidateParticipantsUseCase()

    @Test
    fun `valid participants pass`() {
        assertTrue(useCase.validate(validParticipants(), ParticipantId("p1")).isValid)
    }

    @Test
    fun `at least two participants are required`() {
        val result = useCase.validate(listOf(participant("p1", "Anna", 0)), ParticipantId("p1"))

        assertFalse(result.isValid)
        assertTrue(ParticipantValidationError.TooFewParticipants in result.errors)
    }

    @Test
    fun `payer must exist in participants`() {
        val result = useCase.validate(validParticipants(), ParticipantId("missing"))

        assertFalse(result.isValid)
        assertTrue(ParticipantValidationError.PayerNotFound in result.errors)
    }

    private fun validParticipants(): List<Participant> = listOf(
        participant("p1", "Anna", 0),
        participant("p2", "Ben", 1),
    )

    private fun participant(id: String, name: String, order: Int): Participant {
        return Participant(ParticipantId(id), name, order)
    }
}
