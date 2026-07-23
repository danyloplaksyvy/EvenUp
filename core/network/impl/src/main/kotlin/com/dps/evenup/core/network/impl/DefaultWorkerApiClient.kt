package com.dps.evenup.core.network.impl

import com.dps.evenup.core.network.api.WorkerApiClient
import com.dps.evenup.core.network.api.WorkerApiConfig
import com.dps.evenup.core.network.api.WorkerApiResponse
import com.dps.evenup.core.network.api.WorkerApiResult
import com.dps.evenup.core.network.api.WorkerNetworkError
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class DefaultWorkerApiClient(
    private val config: WorkerApiConfig,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val openConnection: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) : WorkerApiClient {
    override suspend fun get(path: String): WorkerApiResult = request(
        path = path,
        method = "GET",
        body = null,
    )

    override suspend fun postJson(
        path: String,
        body: String,
        headers: Map<String, String>,
    ): WorkerApiResult = request(
        path = path,
        method = "POST",
        body = body,
        headers = headers,
    )

    override suspend fun requestJson(
        method: String,
        path: String,
        body: String?,
        headers: Map<String, String>,
    ): WorkerApiResult = request(
        path = path,
        method = method.uppercase(),
        body = body,
        headers = headers,
    )

    override suspend fun requestBytes(
        method: String,
        path: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): WorkerApiResult {
        val url = buildUrl(path) ?: return WorkerApiResult.Failure(WorkerNetworkError.InvalidPath)
        return suspendCancellableCoroutine { continuation ->
            val activeConnection = AtomicReference<HttpURLConnection?>(null)
            val requestJob = CoroutineScope(ioDispatcher).launch {
                ensureActive()
                val result = executeByteRequest(
                    url = url,
                    method = method.uppercase(),
                    body = body,
                    contentType = contentType,
                    headers = headers,
                    activeConnection = activeConnection,
                )
                if (continuation.isActive) continuation.resume(result)
            }
            continuation.invokeOnCancellation {
                activeConnection.get()?.disconnect()
                requestJob.cancel()
            }
        }
    }

    private suspend fun request(
        path: String,
        method: String,
        body: String?,
        headers: Map<String, String> = emptyMap(),
    ): WorkerApiResult {
        val url = buildUrl(path) ?: return WorkerApiResult.Failure(WorkerNetworkError.InvalidPath)
        return suspendCancellableCoroutine { continuation ->
            val activeConnection = AtomicReference<HttpURLConnection?>(null)
            val requestJob = CoroutineScope(ioDispatcher).launch {
                ensureActive()
                val result = executeRequest(url, method, body, headers, activeConnection)
                if (continuation.isActive) continuation.resume(result)
            }
            continuation.invokeOnCancellation {
                activeConnection.get()?.disconnect()
                requestJob.cancel()
            }
        }
    }

    private fun executeRequest(
        url: URL,
        method: String,
        body: String?,
        headers: Map<String, String>,
        activeConnection: AtomicReference<HttpURLConnection?>,
    ): WorkerApiResult {
        val connection = try {
            openConnection(url)
        } catch (_: IllegalArgumentException) {
            return WorkerApiResult.Failure(WorkerNetworkError.InvalidBaseUrl)
        } catch (_: IOException) {
            return WorkerApiResult.Failure(WorkerNetworkError.ConnectionFailed)
        }
        activeConnection.set(connection)

        return try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (name, value) ->
                connection.setRequestProperty(name, value)
            }

            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Content-Length", bytes.size.toString())
                connection.outputStream.use { output -> output.write(bytes) }
            }

            val statusCode = connection.responseCode
            val responseBody = readResponseBody(connection, statusCode)
            if (statusCode in 200..299) {
                WorkerApiResult.Success(WorkerApiResponse(statusCode, responseBody))
            } else {
                WorkerApiResult.Failure(WorkerNetworkError.HttpFailure(statusCode, responseBody))
            }
        } catch (_: SocketTimeoutException) {
            WorkerApiResult.Failure(WorkerNetworkError.Timeout)
        } catch (_: IOException) {
            WorkerApiResult.Failure(WorkerNetworkError.ConnectionFailed)
        } catch (_: RuntimeException) {
            WorkerApiResult.Failure(WorkerNetworkError.Unknown)
        } finally {
            connection.disconnect()
            activeConnection.compareAndSet(connection, null)
        }
    }

    private fun executeByteRequest(
        url: URL,
        method: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
        activeConnection: AtomicReference<HttpURLConnection?>,
    ): WorkerApiResult {
        val connection = try {
            openConnection(url)
        } catch (_: IllegalArgumentException) {
            return WorkerApiResult.Failure(WorkerNetworkError.InvalidBaseUrl)
        } catch (_: IOException) {
            return WorkerApiResult.Failure(WorkerNetworkError.ConnectionFailed)
        }
        activeConnection.set(connection)
        return try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", contentType)
            connection.setRequestProperty("Content-Length", body.size.toString())
            connection.outputStream.use { output -> output.write(body) }
            val statusCode = connection.responseCode
            val responseBody = readResponseBody(connection, statusCode)
            if (statusCode in 200..299) {
                WorkerApiResult.Success(WorkerApiResponse(statusCode, responseBody))
            } else {
                WorkerApiResult.Failure(WorkerNetworkError.HttpFailure(statusCode, responseBody))
            }
        } catch (_: SocketTimeoutException) {
            WorkerApiResult.Failure(WorkerNetworkError.Timeout)
        } catch (_: IOException) {
            WorkerApiResult.Failure(WorkerNetworkError.ConnectionFailed)
        } catch (_: RuntimeException) {
            WorkerApiResult.Failure(WorkerNetworkError.Unknown)
        } finally {
            connection.disconnect()
            activeConnection.compareAndSet(connection, null)
        }
    }

    private fun buildUrl(path: String): URL? {
        if (!path.startsWith("/")) return null
        return runCatching {
            URL(URL(config.baseUrl.trim().trimEnd('/')), path)
        }.getOrNull()
    }

    private fun readResponseBody(
        connection: HttpURLConnection,
        statusCode: Int,
    ): String {
        val stream = if (statusCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }
        return stream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
    }
}
