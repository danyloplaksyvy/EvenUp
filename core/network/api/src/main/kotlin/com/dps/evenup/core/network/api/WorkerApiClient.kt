package com.dps.evenup.core.network.api

interface WorkerApiClient {
    suspend fun get(path: String): WorkerApiResult

    suspend fun postJson(
        path: String,
        body: String,
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
