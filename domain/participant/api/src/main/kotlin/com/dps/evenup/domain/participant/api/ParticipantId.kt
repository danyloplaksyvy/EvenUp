package com.dps.evenup.domain.participant.api

@JvmInline
value class ParticipantId(val value: String) {
    init {
        require(value.isNotBlank()) { "Participant id must not be blank." }
    }
}
