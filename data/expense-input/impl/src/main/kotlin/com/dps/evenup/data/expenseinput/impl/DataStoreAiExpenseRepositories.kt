package com.dps.evenup.data.expenseinput.impl

import com.dps.evenup.core.datastore.api.StringDataStore
import com.dps.evenup.data.expenseinput.api.AiExpensePreferencesRepository
import com.dps.evenup.data.expenseinput.api.AiExpenseSessionRepository
import com.dps.evenup.domain.expenseinput.api.AiExpensePhase
import com.dps.evenup.domain.expenseinput.api.AiExpenseSession
import java.util.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class DataStoreAiExpenseSessionRepository(
    private val stringDataStore: StringDataStore,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) : AiExpenseSessionRepository {
    private val mutableSession = MutableStateFlow<AiExpenseSession?>(null)
    override val session: Flow<AiExpenseSession?> = mutableSession

    override suspend fun getSession(): AiExpenseSession? {
        val stored = stringDataStore.read(SESSION_KEY) ?: return null.also { mutableSession.value = null }
        val session = try {
            json.decodeFromString<AiExpenseSessionDto>(stored).toDomain()
        } catch (error: SerializationException) {
            throw IllegalStateException("Stored AI expense session was invalid.", error)
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException("Stored AI expense session contained invalid values.", error)
        }
        val recovered = if (session.phase == AiExpensePhase.Processing) {
            session.copy(phase = AiExpensePhase.Failure, failureCode = "INTERRUPTED")
        } else {
            session
        }
        if (recovered != session) stringDataStore.write(SESSION_KEY, json.encodeToString(recovered.toDto()))
        mutableSession.value = recovered
        return recovered
    }

    override suspend fun saveSession(session: AiExpenseSession) {
        stringDataStore.write(SESSION_KEY, json.encodeToString(session.toDto()))
        mutableSession.value = session
    }

    override suspend fun clearSession() {
        stringDataStore.remove(SESSION_KEY)
        mutableSession.value = null
    }

    private companion object {
        const val SESSION_KEY = "ai_expense_session_json"
    }
}

class DataStoreAiExpensePreferencesRepository(
    private val stringDataStore: StringDataStore,
) : AiExpensePreferencesRepository {
    override suspend fun getPersonalName(): String? = stringDataStore.read(PERSONAL_NAME_KEY)?.trim()?.takeIf(String::isNotBlank)

    override suspend fun setPersonalName(name: String?) {
        val normalized = name?.trim().orEmpty()
        if (normalized.isBlank()) stringDataStore.remove(PERSONAL_NAME_KEY)
        else stringDataStore.write(PERSONAL_NAME_KEY, normalized)
    }

    override suspend fun getDefaultCurrency(): String? = stringDataStore.read(DEFAULT_CURRENCY_KEY)

    override suspend fun setDefaultCurrency(currency: String) {
        val normalized = currency.trim().uppercase()
        require(normalized != "XXX" && runCatching { Currency.getInstance(normalized).currencyCode == normalized }.getOrDefault(false))
        stringDataStore.write(DEFAULT_CURRENCY_KEY, normalized)
    }

    private companion object {
        const val PERSONAL_NAME_KEY = "ai_personal_name"
        const val DEFAULT_CURRENCY_KEY = "ai_default_currency"
    }
}
