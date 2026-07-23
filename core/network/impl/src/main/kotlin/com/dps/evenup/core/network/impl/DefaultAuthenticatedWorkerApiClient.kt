package com.dps.evenup.core.network.impl

import com.dps.evenup.core.auth.api.AppAttestationTokenProvider
import com.dps.evenup.core.auth.api.AuthTokenProvider
import com.dps.evenup.core.network.api.AuthenticatedWorkerApiClient
import com.dps.evenup.core.network.api.WorkerApiClient
import com.dps.evenup.core.network.api.WorkerApiResult
import com.dps.evenup.core.network.api.WorkerNetworkError
import java.util.UUID

class DefaultAuthenticatedWorkerApiClient(
    private val delegate: WorkerApiClient,
    private val authTokenProvider: AuthTokenProvider,
    private val appAttestationTokenProvider: AppAttestationTokenProvider,
) : AuthenticatedWorkerApiClient {
    override suspend fun get(path: String): WorkerApiResult =
        sendJson(method = "GET", path = path)

    override suspend fun sendJson(
        method: String,
        path: String,
        body: String?,
        headers: Map<String, String>,
    ): WorkerApiResult {
        val requestId = headers["X-Request-Id"] ?: "android-${UUID.randomUUID()}"
        val first = execute(
            method = method,
            path = path,
            body = body,
            requestId = requestId,
            forceRefresh = false,
            additionalHeaders = headers,
        )
        if (first.isUnauthorized()) {
            return execute(
                method = method,
                path = path,
                body = body,
                requestId = requestId,
                forceRefresh = true,
                additionalHeaders = headers,
            )
        }
        return first
    }

    override suspend fun sendBytes(
        method: String,
        path: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): WorkerApiResult {
        val requestId = headers["X-Request-Id"] ?: "android-${UUID.randomUUID()}"
        val first = executeBytes(method, path, body, contentType, requestId, false, headers)
        return if (first.isUnauthorized()) {
            executeBytes(method, path, body, contentType, requestId, true, headers)
        } else {
            first
        }
    }

    private suspend fun execute(
        method: String,
        path: String,
        body: String?,
        requestId: String,
        forceRefresh: Boolean,
        additionalHeaders: Map<String, String>,
    ): WorkerApiResult {
        val idToken = authTokenProvider.getIdToken(forceRefresh)
            ?: return WorkerApiResult.Failure(WorkerNetworkError.HttpFailure(401, ""))
        val appCheckToken = appAttestationTokenProvider.getToken(forceRefresh)
        val requestHeaders = buildMap {
            putAll(additionalHeaders)
            put("Authorization", "Bearer $idToken")
            put("X-Request-Id", requestId)
            if (!appCheckToken.isNullOrBlank()) put("X-Firebase-AppCheck", appCheckToken)
        }
        return delegate.requestJson(
            method = method,
            path = path,
            body = body,
            headers = requestHeaders,
        )
    }

    private suspend fun executeBytes(
        method: String,
        path: String,
        body: ByteArray,
        contentType: String,
        requestId: String,
        forceRefresh: Boolean,
        additionalHeaders: Map<String, String>,
    ): WorkerApiResult {
        val idToken = authTokenProvider.getIdToken(forceRefresh)
            ?: return WorkerApiResult.Failure(WorkerNetworkError.HttpFailure(401, ""))
        val appCheckToken = appAttestationTokenProvider.getToken(forceRefresh)
        val requestHeaders = buildMap {
            putAll(additionalHeaders)
            put("Authorization", "Bearer $idToken")
            put("X-Request-Id", requestId)
            if (!appCheckToken.isNullOrBlank()) put("X-Firebase-AppCheck", appCheckToken)
        }
        return delegate.requestBytes(method, path, body, contentType, requestHeaders)
    }
}

private fun WorkerApiResult.isUnauthorized(): Boolean {
    val failure = this as? WorkerApiResult.Failure ?: return false
    val httpFailure = failure.error as? WorkerNetworkError.HttpFailure ?: return false
    return httpFailure.statusCode == 401
}
