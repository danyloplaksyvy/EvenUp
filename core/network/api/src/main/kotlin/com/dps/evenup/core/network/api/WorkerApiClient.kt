package com.dps.evenup.core.network.api

interface WorkerApiClient {
    suspend fun get(path: String): WorkerApiResult

    suspend fun postJson(
        path: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): WorkerApiResult

    suspend fun requestJson(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): WorkerApiResult = when (method.uppercase()) {
        "GET" -> if (headers.isEmpty()) get(path) else WorkerApiResult.Failure(WorkerNetworkError.Unknown)
        "POST" -> postJson(path, body.orEmpty(), headers)
        else -> WorkerApiResult.Failure(WorkerNetworkError.Unknown)
    }

    suspend fun requestBytes(
        method: String,
        path: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
    ): WorkerApiResult = WorkerApiResult.Failure(WorkerNetworkError.Unknown)
}

interface AuthenticatedWorkerApiClient {
    suspend fun get(path: String): WorkerApiResult

    suspend fun sendJson(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): WorkerApiResult

    suspend fun sendBytes(
        method: String,
        path: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
    ): WorkerApiResult
}

sealed interface WorkerApiResult {
    data class Success(val response: WorkerApiResponse) : WorkerApiResult

    data class Failure(val error: WorkerNetworkError) : WorkerApiResult
}

data class WorkerApiResponse(
    val statusCode: Int,
    val body: String,
) {
    val isSuccessful: Boolean = statusCode in 200..299
}

sealed interface WorkerNetworkError {
    data object InvalidBaseUrl : WorkerNetworkError

    data object InvalidPath : WorkerNetworkError

    data object ConnectionFailed : WorkerNetworkError

    data object Timeout : WorkerNetworkError

    data class HttpFailure(
        val statusCode: Int,
        val body: String,
    ) : WorkerNetworkError

    data object Unknown : WorkerNetworkError
}
