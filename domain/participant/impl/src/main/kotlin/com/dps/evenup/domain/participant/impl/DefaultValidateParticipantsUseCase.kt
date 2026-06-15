package com.dps.evenup.domain.participant.impl

import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.participant.api.ParticipantValidationError
import com.dps.evenup.domain.participant.api.ParticipantValidationResult
import com.dps.evenup.domain.participant.api.ValidateParticipantsUseCase

class DefaultValidateParticipantsUseCase : ValidateParticipantsUseCase {
    override fun validate(
        participants: List<Participant>,
        payerId: ParticipantId,
    ): ParticipantValidationResult {
        val errors = buildSet {
            if (participants.size < MIN_PARTICIPANTS) add(ParticipantValidationError.TooFewParticipants)
            if (participants.any { it.name.isBlank() }) add(ParticipantValidationError.BlankParticipantName)
            if (participants.map { it.id }.distinct().size != participants.size) {
                add(ParticipantValidationError.DuplicateParticipantId)
            }
            if (participants.none { it.id == payerId }) add(ParticipantValidationError.PayerNotFound)
        }

        return if (errors.isEmpty()) ParticipantValidationResult.Valid else ParticipantValidationResult(errors)
    }

    private companion object {
        const val MIN_PARTICIPANTS = 2
    }
}
