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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    private suspend fun request(
        path: String,
        method: String,
        body: String?,
        headers: Map<String, String> = emptyMap(),
    ): WorkerApiResult = withContext(ioDispatcher) {
        val url = buildUrl(path) ?: return@withContext WorkerApiResult.Failure(WorkerNetworkError.InvalidPath)
        val connection = try {
            openConnection(url)
        } catch (_: IllegalArgumentException) {
            return@withContext WorkerApiResult.Failure(WorkerNetworkError.InvalidBaseUrl)
        } catch (_: IOException) {
            return@withContext WorkerApiResult.Failure(WorkerNetworkError.ConnectionFailed)
        }

        try {
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
