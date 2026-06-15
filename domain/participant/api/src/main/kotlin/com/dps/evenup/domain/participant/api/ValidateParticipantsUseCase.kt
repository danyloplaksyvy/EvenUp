package com.dps.evenup.domain.participant.api

interface ValidateParticipantsUseCase {
    fun validate(
        participants: List<Participant>,
        payerId: ParticipantId,
    ): ParticipantValidationResult
}

data class ParticipantValidationResult(
    val errors: Set<ParticipantValidationError>,
) {
    val isValid: Boolean = errors.isEmpty()

    companion object {
        val Valid = ParticipantValidationResult(emptySet())
    }
}

enum class ParticipantValidationError {
    TooFewParticipants,
    BlankParticipantName,
    DuplicateParticipantId,
    PayerNotFound,
}
