package com.dps.evenup.domain.participant.api

data class Participant(
    val id: ParticipantId,
    val name: String,
    val creationOrder: Int,
    val isSavedLocalName: Boolean = false,
)
