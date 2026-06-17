package com.dps.evenup.data.participant.impl

import com.dps.evenup.core.datastore.api.StringDataStore
import com.dps.evenup.domain.participant.api.SavedParticipantName
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreSavedParticipantRepositoryTest {
    @Test
    fun `added names appear in future repository instances`() = runBlocking {
        val store = FakeStringDataStore()
        DataStoreSavedParticipantRepository(store).addSavedParticipantName(SavedParticipantName("Anna"))

        val names = DataStoreSavedParticipantRepository(store).getSavedParticipantNames()

        assertEquals(listOf(SavedParticipantName("Anna")), names)
    }

    @Test
    fun `duplicate names are avoided case-insensitively`() = runBlocking {
        val repository = DataStoreSavedParticipantRepository(FakeStringDataStore())

        repository.addSavedParticipantName(SavedParticipantName("Anna"))
        repository.addSavedParticipantName(SavedParticipantName(" anna "))

        assertEquals(listOf(SavedParticipantName("Anna")), repository.getSavedParticipantNames())
    }

    @Test
    fun `names can be deleted`() = runBlocking {
        val repository = DataStoreSavedParticipantRepository(FakeStringDataStore())
        repository.addSavedParticipantName(SavedParticipantName("Anna"))
        repository.addSavedParticipantName(SavedParticipantName("Ben"))

        repository.deleteSavedParticipantName(SavedParticipantName("anna"))

        assertEquals(listOf(SavedParticipantName("Ben")), repository.getSavedParticipantNames())
    }

    private class FakeStringDataStore : StringDataStore {
        private val values = mutableMapOf<String, String>()

        override suspend fun read(key: String): String? = values[key]

        override suspend fun write(
            key: String,
            value: String,
        ) {
            values[key] = value
        }

        override suspend fun remove(key: String) {
            values.remove(key)
        }
    }
}
