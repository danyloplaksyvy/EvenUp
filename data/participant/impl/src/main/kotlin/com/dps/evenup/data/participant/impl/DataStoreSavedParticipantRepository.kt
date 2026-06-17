package com.dps.evenup.data.participant.impl

import com.dps.evenup.core.datastore.api.StringDataStore
import com.dps.evenup.data.participant.api.ParticipantDataException
import com.dps.evenup.data.participant.api.SavedParticipantRepository
import com.dps.evenup.domain.participant.api.SavedParticipantName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class DataStoreSavedParticipantRepository(
    private val stringDataStore: StringDataStore,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) : SavedParticipantRepository {
    override suspend fun getSavedParticipantNames(): List<SavedParticipantName> {
        return readStoredNames().map(::SavedParticipantName)
    }

    override suspend fun addSavedParticipantName(name: SavedParticipantName) {
        val normalizedName = name.value.trim()
        val existingNames = readStoredNames()
        if (existingNames.any { existing -> existing.equals(normalizedName, ignoreCase = true) }) {
            return
        }

        writeStoredNames(existingNames + normalizedName)
    }

    override suspend fun deleteSavedParticipantName(name: SavedParticipantName) {
        val normalizedName = name.value.trim()
        val remainingNames = readStoredNames()
            .filterNot { existing -> existing.equals(normalizedName, ignoreCase = true) }
        writeStoredNames(remainingNames)
    }

    private suspend fun readStoredNames(): List<String> {
        val storedValue = stringDataStore.read(SAVED_PARTICIPANTS_KEY) ?: return emptyList()
        val dto = try {
            json.decodeFromString<SavedParticipantNamesDto>(storedValue)
        } catch (error: SerializationException) {
            throw ParticipantDataException("Stored participant names were invalid.", error)
        }

        return dto.names
            .map { name -> name.trim() }
            .filter { name -> name.isNotBlank() }
            .distinctBy { name -> name.lowercase() }
    }

    private suspend fun writeStoredNames(names: List<String>) {
        stringDataStore.write(
            key = SAVED_PARTICIPANTS_KEY,
            value = json.encodeToString(SavedParticipantNamesDto(names)),
        )
    }

    private companion object {
        const val SAVED_PARTICIPANTS_KEY = "saved_participant_names_json"
    }
}

@Serializable
private data class SavedParticipantNamesDto(
    val names: List<String>,
)
