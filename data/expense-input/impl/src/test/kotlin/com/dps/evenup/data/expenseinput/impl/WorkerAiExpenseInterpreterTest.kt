package com.dps.evenup.data.expenseinput.impl

import com.dps.evenup.core.network.api.WorkerApiClient
import com.dps.evenup.core.network.api.WorkerApiResponse
import com.dps.evenup.core.network.api.WorkerApiResult
import com.dps.evenup.data.expenseinput.api.AiInterpretationFailureReason
import com.dps.evenup.data.expenseinput.api.InterpretAiExpenseCommand
import com.dps.evenup.data.expenseinput.api.InterpretAiExpenseResult
import com.dps.evenup.domain.expenseinput.api.AiPricingMode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerAiExpenseInterpreterTest {
    @Test
    fun `maps full response and sends privacy-safe structured request`() = runBlocking {
        val client = FakeWorkerApiClient(successResponse(requestId = "request-1"))
        val result = WorkerAiExpenseInterpreter(client).interpret(command())

        assertTrue(result is InterpretAiExpenseResult.Success)
        result as InterpretAiExpenseResult.Success
        assertEquals(AiPricingMode.TotalOnly, result.extraction.pricingMode)
        assertEquals("Dana", result.extraction.participants.first().name)
        assertEquals("/v1/expenses/interpret", client.path)
        val body = Json.parseToJsonElement(client.body!!).jsonObject
        assertEquals("request-1", body.getValue("requestId").jsonPrimitive.content)
        assertEquals("A private dinner", body.getValue("description").jsonPrimitive.content)
        assertNull(body["audio"])
        assertNull(body["apiKey"])
    }

    @Test
    fun `rejects stale echoed request id`() = runBlocking {
        val result = WorkerAiExpenseInterpreter(FakeWorkerApiClient(successResponse("old-request"))).interpret(command())

        assertEquals(
            InterpretAiExpenseResult.Failure(AiInterpretationFailureReason.InvalidResponse, "STALE_RESPONSE"),
            result,
        )
    }

    @Test
    fun `enforces local limits before network`() = runBlocking {
        val client = FakeWorkerApiClient(successResponse("request-1"))
        val result = WorkerAiExpenseInterpreter(client).interpret(command().copy(description = "x".repeat(4_001)))

        assertEquals(AiInterpretationFailureReason.InvalidInput, (result as InterpretAiExpenseResult.Failure).reason)
        assertNull(client.path)
    }

    @Test
    fun `unknown response enum becomes a controlled invalid response`() = runBlocking {
        val result = WorkerAiExpenseInterpreter(
            FakeWorkerApiClient(successResponse("request-1", pricingMode = "Unexpected")),
        ).interpret(command())

        assertEquals(
            InterpretAiExpenseResult.Failure(AiInterpretationFailureReason.InvalidResponse, "INVALID_RESPONSE"),
            result,
        )
    }

    private fun command() = InterpretAiExpenseCommand(
        sessionId = "session-1",
        requestId = "request-1",
        description = "A private dinner",
        locale = "en-US",
        defaultCurrency = "USD",
    )

    private fun successResponse(requestId: String, pricingMode: String = "TotalOnly"): WorkerApiResult = WorkerApiResult.Success(
        WorkerApiResponse(
            200,
            """
            {
              "schemaVersion": 1,
              "requestId": "$requestId",
              "extraction": {
                "title": "Dinner",
                "currency": "USD",
                "totalMinor": 4800,
                "pricingMode": "$pricingMode",
                "participants": [
                  {"ref": "dana", "name": "Dana", "isSelf": false},
                  {"ref": "lee", "name": "Lee", "isSelf": false}
                ],
                "payerParticipantRef": "dana",
                "items": [],
                "fees": [],
                "splitEverythingEqually": true,
                "provenance": [],
                "warnings": []
              }
            }
            """.trimIndent(),
        ),
    )

    private class FakeWorkerApiClient(private val result: WorkerApiResult) : WorkerApiClient {
        var path: String? = null
        var body: String? = null

        override suspend fun get(path: String): WorkerApiResult = error("Not used")

        override suspend fun postJson(path: String, body: String, headers: Map<String, String>): WorkerApiResult {
            this.path = path
            this.body = body
            return result
        }
    }
}
