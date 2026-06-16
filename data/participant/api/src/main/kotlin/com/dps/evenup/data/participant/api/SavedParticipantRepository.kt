package com.dps.evenup.data.participant.api

import com.dps.evenup.domain.participant.api.SavedParticipantName

interface SavedParticipantRepository {
    suspend fun getSavedParticipantNames(): List<SavedParticipantName>

    suspend fun addSavedParticipantName(name: SavedParticipantName)

    suspend fun deleteSavedParticipantName(name: SavedParticipantName)
}

class ParticipantDataException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
