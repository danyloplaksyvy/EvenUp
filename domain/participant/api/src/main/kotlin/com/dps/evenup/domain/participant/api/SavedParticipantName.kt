package com.dps.evenup.domain.participant.api

@JvmInline
value class SavedParticipantName(val value: String) {
    init {
        require(value.isNotBlank()) { "Saved participant name must not be blank." }
    }

    override fun toString(): String = value
}
