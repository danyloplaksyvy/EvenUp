package com.dps.evenup.data.expenseinput.impl

import com.dps.evenup.core.network.api.WorkerApiClient
import com.dps.evenup.core.network.api.WorkerApiResult
import com.dps.evenup.core.network.api.WorkerNetworkError
import com.dps.evenup.data.expenseinput.api.AiExpenseInterpreter
import com.dps.evenup.data.expenseinput.api.AiInterpretationFailureReason
import com.dps.evenup.data.expenseinput.api.InterpretAiExpenseCommand
import com.dps.evenup.data.expenseinput.api.InterpretAiExpenseResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class WorkerAiExpenseInterpreter(
    private val workerApiClient: WorkerApiClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) : AiExpenseInterpreter {
    override suspend fun interpret(command: InterpretAiExpenseCommand): InterpretAiExpenseResult {
        if (command.description.isBlank() || command.description.length > MAX_DESCRIPTION_LENGTH ||
            (command.clarificationAnswer?.length ?: 0) > MAX_ANSWER_LENGTH ||
            command.clarificationHistory.size + (if (command.activeClarification == null) 0 else 1) > MAX_CLARIFICATION_TURNS
        ) {
            return InterpretAiExpenseResult.Failure(AiInterpretationFailureReason.InvalidInput, "INVALID_REQUEST")
        }
        val request = InterpretRequestDto(
            sessionId = command.sessionId,
            requestId = command.requestId,
            locale = command.locale,
            defaultCurrency = command.defaultCurrency,
            personalName = command.personalName,
            description = command.description,
            activeClarification = command.activeClarification?.let { kind ->
                ActiveClarificationDto(kind.name, command.clarificationAnswer.orEmpty())
            },
            clarificationHistory = command.clarificationHistory.map { it.toDto() },
            priorExtraction = command.priorExtraction?.toDto(),
        )
        return when (val result = workerApiClient.postJson("/v1/expenses/interpret", json.encodeToString(request))) {
            is WorkerApiResult.Success -> decodeSuccess(result.response.body, command.requestId)
            is WorkerApiResult.Failure -> result.error.toFailure()
        }
    }

    private fun decodeSuccess(body: String, expectedRequestId: String): InterpretAiExpenseResult {
        val response = try {
            json.decodeFromString<InterpretResponseDto>(body)
        } catch (_: SerializationException) {
            return InterpretAiExpenseResult.Failure(AiInterpretationFailureReason.InvalidResponse, "INVALID_RESPONSE")
        } catch (_: IllegalArgumentException) {
            return InterpretAiExpenseResult.Failure(AiInterpretationFailureReason.InvalidResponse, "INVALID_RESPONSE")
        }
        if (response.schemaVersion != 1 || response.requestId != expectedRequestId) {
            return InterpretAiExpenseResult.Failure(AiInterpretationFailureReason.InvalidResponse, "STALE_RESPONSE")
        }
        val extraction = try {
            response.extraction.toDomain()
        } catch (_: IllegalArgumentException) {
            return InterpretAiExpenseResult.Failure(AiInterpretationFailureReason.InvalidResponse, "INVALID_RESPONSE")
        }
        return InterpretAiExpenseResult.Success(response.requestId, extraction)
    }

    private fun WorkerNetworkError.toFailure(): InterpretAiExpenseResult.Failure = when (this) {
        WorkerNetworkError.ConnectionFailed, WorkerNetworkError.InvalidBaseUrl, WorkerNetworkError.InvalidPath ->
            InterpretAiExpenseResult.Failure(AiInterpretationFailureReason.Connection)
        WorkerNetworkError.Timeout -> InterpretAiExpenseResult.Failure(AiInterpretationFailureReason.Timeout)
        WorkerNetworkError.Unknown -> InterpretAiExpenseResult.Failure(AiInterpretationFailureReason.Unavailable)
        is WorkerNetworkError.HttpFailure -> {
            val code = runCatching { json.decodeFromString<ErrorEnvelopeDto>(body).error.code }.getOrNull()
            val reason = when {
                code == "UNSUPPORTED_LANGUAGE" -> AiInterpretationFailureReason.UnsupportedLanguage
                statusCode in listOf(400, 413) -> AiInterpretationFailureReason.InvalidInput
                statusCode == 422 -> AiInterpretationFailureReason.InvalidResponse
                statusCode == 429 -> AiInterpretationFailureReason.RateLimited
                statusCode == 504 -> AiInterpretationFailureReason.Timeout
                else -> AiInterpretationFailureReason.Unavailable
            }
            InterpretAiExpenseResult.Failure(reason, code)
        }
    }

    private companion object {
        const val MAX_DESCRIPTION_LENGTH = 4_000
        const val MAX_ANSWER_LENGTH = 1_000
        const val MAX_CLARIFICATION_TURNS = 10
    }
}
