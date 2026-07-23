package com.dps.evenup.data.account.impl

import com.dps.evenup.core.datastore.api.StringDataStore
import com.dps.evenup.domain.account.api.PendingActionState
import com.dps.evenup.domain.account.api.PendingAuthAction
import com.dps.evenup.domain.account.api.PendingAuthActionType
import com.dps.evenup.domain.account.api.PendingAuthOrigin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingAuthActionRepositoryTest {
    @Test
    fun replacementAndClaimAreSingleWinner() = runBlocking {
        val repository = DataStorePendingAuthActionRepository(MemoryStore(), nowMillis = { 1_000 })
        repository.replace(action("first"))
        repository.replace(action("second"))

        assertNull(repository.claim("first"))
        assertEquals(PendingActionState.Executing, repository.claim("second")?.state)
        assertNull(repository.claim("second"))
    }

    @Test
    fun expiredActionIsRemoved() = runBlocking {
        val store = MemoryStore()
        val repository = DataStorePendingAuthActionRepository(
            store,
            nowMillis = { 30 * 60 * 1000L + 1 },
        )
        repository.replace(action("expired", createdAt = 0))

        assertNull(repository.get())
        assertNull(store.read("pending_auth_action_v1"))
    }

    @Test
    fun executingActionBecomesUserInitiatedRetryAfterProcessRecreation() = runBlocking {
        val store = MemoryStore()
        val firstProcess = DataStorePendingAuthActionRepository(store, nowMillis = { 1_000 })
        firstProcess.replace(action("ai"))
        assertEquals(PendingActionState.Executing, firstProcess.claim("ai")?.state)

        val recreatedProcess = DataStorePendingAuthActionRepository(store, nowMillis = { 1_001 })

        assertEquals(PendingActionState.RetryRequired, recreatedProcess.get()?.state)
        assertNull(recreatedProcess.claim("ai"))
    }

    private fun action(id: String, createdAt: Long = 1_000) = PendingAuthAction(
        id = id,
        type = PendingAuthActionType.SubmitAiDescription,
        origin = PendingAuthOrigin.NewExpense,
        reference = "session-1",
        createdAtEpochMillis = createdAt,
        state = PendingActionState.Pending,
    )
}

private class MemoryStore : StringDataStore {
    private val values = mutableMapOf<String, String>()
    override suspend fun read(key: String): String? = values[key]
    override suspend fun write(key: String, value: String) { values[key] = value }
    override suspend fun remove(key: String) { values.remove(key) }
}
