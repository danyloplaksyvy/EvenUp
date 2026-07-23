package com.dps.evenup.core.network.impl

import com.dps.evenup.core.network.api.AuthenticatedWorkerApiClient
import com.dps.evenup.core.network.api.WorkerApiClient
import com.dps.evenup.core.network.api.WorkerApiResult

/**
 * Keeps existing repository contracts stable while routing protected writes to
 * authenticated v2 endpoints.
 */
class AuthenticatedV2WorkerApiClient(
    private val authenticatedClient: AuthenticatedWorkerApiClient,
) : WorkerApiClient {
    override suspend fun get(path: String): WorkerApiResult =
        authenticatedClient.get(path.toV2Path())

    override suspend fun postJson(
        path: String,
        body: String,
        headers: Map<String, String>,
    ): WorkerApiResult = authenticatedClient.sendJson(
        method = "POST",
        path = path.toV2Path(),
        body = body,
        headers = headers,
    )

    override suspend fun requestJson(
        method: String,
        path: String,
        body: String?,
        headers: Map<String, String>,
    ): WorkerApiResult = authenticatedClient.sendJson(method, path.toV2Path(), body, headers)
}

private fun String.toV2Path(): String = when (this) {
    "/v1/expenses/interpret" -> "/v2/ai/jobs"
    "/v1/receipts/parse" -> "/v2/receipts/parse"
    "/v1/expenses" -> "/v2/expenses"
    else -> this
}
