package com.dps.evenup.data.expenseinput.api

import com.dps.evenup.domain.expenseinput.api.AiClarificationTurn
import com.dps.evenup.domain.expenseinput.api.AiExpenseSession
import com.dps.evenup.domain.expenseinput.api.AiExtraction
import com.dps.evenup.domain.expenseinput.api.ClarificationKind
import kotlinx.coroutines.flow.Flow

interface AiExpenseInterpreter {
    suspend fun interpret(command: InterpretAiExpenseCommand): InterpretAiExpenseResult
}

data class InterpretAiExpenseCommand(
    val sessionId: String,
    val requestId: String,
    val description: String,
    val locale: String,
    val defaultCurrency: String,
    val personalName: String? = null,
    val activeClarification: ClarificationKind? = null,
    val clarificationAnswer: String? = null,
    val clarificationHistory: List<AiClarificationTurn> = emptyList(),
    val priorExtraction: AiExtraction? = null,
)

sealed interface InterpretAiExpenseResult {
    data class Success(
        val requestId: String,
        val extraction: AiExtraction,
    ) : InterpretAiExpenseResult

    data class Failure(
        val reason: AiInterpretationFailureReason,
        val code: String? = null,
    ) : InterpretAiExpenseResult
}

enum class AiInterpretationFailureReason {
    Connection,
    Timeout,
    InvalidInput,
    UnsupportedLanguage,
    RateLimited,
    Unavailable,
    InvalidResponse,
}

interface AiExpenseSessionRepository {
    val session: Flow<AiExpenseSession?>

    suspend fun getSession(): AiExpenseSession?

    suspend fun saveSession(session: AiExpenseSession)

    suspend fun clearSession()
}

interface AiExpensePreferencesRepository {
    suspend fun getPersonalName(): String?

    suspend fun setPersonalName(name: String?)

    suspend fun getDefaultCurrency(): String?

    suspend fun setDefaultCurrency(currency: String)
}
