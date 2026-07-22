package com.dps.evenup.data.expenseinput.impl

import com.dps.evenup.core.datastore.api.StringDataStore
import com.dps.evenup.domain.expenseinput.api.AiExpensePhase
import com.dps.evenup.domain.expenseinput.api.AiExpenseSession
import com.dps.evenup.domain.expenseinput.api.AiExtractedParticipant
import com.dps.evenup.domain.expenseinput.api.AiExtraction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataStoreAiExpenseRepositoriesTest {
    @Test
    fun `restores complete session and converts interrupted processing to retryable failure`() = runBlocking {
        val store = FakeStringDataStore()
        val first = DataStoreAiExpenseSessionRepository(store)
        first.saveSession(
            AiExpenseSession(
                sessionId = "session-1",
                description = "Dana paid for dinner",
                extraction = AiExtraction(participants = listOf(AiExtractedParticipant("dana", "Dana"))),
                phase = AiExpensePhase.Processing,
                hasManualEdits = true,
            ),
        )

        val restoredRepository = DataStoreAiExpenseSessionRepository(store)
        val restored = restoredRepository.getSession()!!
        assertEquals(AiExpensePhase.Failure, restored.phase)
        assertEquals("INTERRUPTED", restored.failureCode)
        assertEquals("Dana paid for dinner", restored.description)
        assertEquals("Dana", restored.extraction!!.participants.single().name)
        assertEquals(true, restored.hasManualEdits)
        assertEquals(restored, restoredRepository.session.first())
    }

    @Test
    fun `clear removes local raw session data`() = runBlocking {
        val store = FakeStringDataStore()
        val repository = DataStoreAiExpenseSessionRepository(store)
        repository.saveSession(AiExpenseSession("session-1", description = "private"))

        repository.clearSession()

        assertNull(repository.getSession())
        assertNull(repository.session.first())
        assertEquals(emptyMap<String, String>(), store.values)
    }

    @Test
    fun `preferences normalize personal name and currency`() = runBlocking {
        val repository = DataStoreAiExpensePreferencesRepository(FakeStringDataStore())
        repository.setPersonalName("  Dana  ")
        repository.setDefaultCurrency(" eur ")

        assertEquals("Dana", repository.getPersonalName())
        assertEquals("EUR", repository.getDefaultCurrency())
        repository.setPersonalName(" ")
        assertNull(repository.getPersonalName())
    }

    private class FakeStringDataStore : StringDataStore {
        val values = mutableMapOf<String, String>()
        override suspend fun read(key: String): String? = values[key]
        override suspend fun write(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }
}
