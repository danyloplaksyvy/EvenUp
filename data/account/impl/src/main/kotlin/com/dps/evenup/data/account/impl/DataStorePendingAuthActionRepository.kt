package com.dps.evenup.data.account.impl

import com.dps.evenup.core.datastore.api.StringDataStore
import com.dps.evenup.data.account.api.PendingAuthActionRepository
import com.dps.evenup.domain.account.api.PendingActionState
import com.dps.evenup.domain.account.api.PendingAuthAction
import com.dps.evenup.domain.account.api.PendingAuthActionType
import com.dps.evenup.domain.account.api.PendingAuthOrigin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DataStorePendingAuthActionRepository(
    private val store: StringDataStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PendingAuthActionRepository {
    private val mutex = Mutex()
    private val mutableAction = MutableStateFlow<PendingAuthAction?>(null)
    private var claimedInThisProcess: String? = null

    override fun observe(): Flow<PendingAuthAction?> = mutableAction

    override suspend fun get(): PendingAuthAction? = mutex.withLock {
        var action = readStored()
        if (action?.state == PendingActionState.Executing && claimedInThisProcess != action.id) {
            action = action.copy(state = PendingActionState.RetryRequired)
            write(action)
        }
        if (action != null && action.isExpired(nowMillis())) {
            store.remove(KEY)
            mutableAction.value = null
            null
        } else {
            mutableAction.value = action
            action
        }
    }

    override suspend fun replace(action: PendingAuthAction) = mutex.withLock {
        write(action.copy(state = PendingActionState.Pending))
    }

    override suspend fun claim(actionId: String): PendingAuthAction? = mutex.withLock {
        val current = readStored() ?: return@withLock null
        if (current.id != actionId || current.state != PendingActionState.Pending || current.isExpired(nowMillis())) {
            return@withLock null
        }
        current.copy(state = PendingActionState.Executing).also {
            claimedInThisProcess = it.id
            write(it)
        }
    }

    override suspend fun update(action: PendingAuthAction) = mutex.withLock {
        val current = readStored()
        if (current?.id == action.id) write(action)
    }

    override suspend fun clear() = mutex.withLock {
        store.remove(KEY)
        claimedInThisProcess = null
        mutableAction.value = null
    }

    private suspend fun readStored(): PendingAuthAction? =
        store.read(KEY)?.let { encoded ->
            runCatching { json.decodeFromString<PendingAuthActionDto>(encoded).toDomain() }.getOrNull()
        }

    private suspend fun write(action: PendingAuthAction) {
        store.write(KEY, json.encodeToString(PendingAuthActionDto.from(action)))
        mutableAction.value = action
    }

    private companion object {
        const val KEY = "pending_auth_action_v1"
        const val EXPIRY_MILLIS = 30 * 60 * 1000L

        fun PendingAuthAction.isExpired(now: Long): Boolean = now - createdAtEpochMillis >= EXPIRY_MILLIS
    }
}

@Serializable
private data class PendingAuthActionDto(
    val id: String,
    val type: String,
    val origin: String,
    val reference: String?,
    val createdAtEpochMillis: Long,
    val state: String,
) {
    fun toDomain(): PendingAuthAction = PendingAuthAction(
        id = id,
        type = PendingAuthActionType.valueOf(type),
        origin = PendingAuthOrigin.valueOf(origin),
        reference = reference,
        createdAtEpochMillis = createdAtEpochMillis,
        state = PendingActionState.valueOf(state),
    )

    companion object {
        fun from(action: PendingAuthAction): PendingAuthActionDto = PendingAuthActionDto(
            id = action.id,
            type = action.type.name,
            origin = action.origin.name,
            reference = action.reference,
            createdAtEpochMillis = action.createdAtEpochMillis,
            state = action.state.name,
        )
    }
}
