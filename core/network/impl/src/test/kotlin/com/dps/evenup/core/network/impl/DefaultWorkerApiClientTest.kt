package com.dps.evenup.core.network.impl

import com.dps.evenup.core.network.api.WorkerApiConfig
import com.dps.evenup.core.network.api.WorkerApiResult
import com.dps.evenup.core.network.api.WorkerNetworkError
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultWorkerApiClientTest {
    @Test
    fun `post json returns successful response body`() = runBlocking {
        val connection = FakeHttpURLConnection(URL("https://example.test/v1/receipts/parse"), 200, """{"ok":true}""")
        val client = DefaultWorkerApiClient(WorkerApiConfig("https://example.test")) { connection }

        val result = client.postJson("/v1/receipts/parse", """{"imageBase64":"abc"}""")

        assertTrue(result is WorkerApiResult.Success)
        result as WorkerApiResult.Success
        assertEquals(200, result.response.statusCode)
        assertEquals("""{"ok":true}""", result.response.body)
        assertEquals("POST", connection.requestMethod)
        assertEquals("""{"imageBase64":"abc"}""", connection.output.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `http errors are mapped safely`() = runBlocking {
        val connection = FakeHttpURLConnection(URL("https://example.test/missing"), 404, """{"error":true}""")
        val client = DefaultWorkerApiClient(WorkerApiConfig("https://example.test")) { connection }

        val result = client.get("/missing")

        assertEquals(WorkerApiResult.Failure(WorkerNetworkError.HttpFailure(404, """{"error":true}""")), result)
    }

    @Test
    fun `timeouts are mapped safely`() = runBlocking {
        val connection = FakeHttpURLConnection(URL("https://example.test/health"), 200, "") {
            throw SocketTimeoutException("timeout")
        }
        val client = DefaultWorkerApiClient(WorkerApiConfig("https://example.test")) {
            connection
        }

        val result = client.get("/health")

        assertEquals(WorkerApiResult.Failure(WorkerNetworkError.Timeout), result)
    }

    private class FakeHttpURLConnection(
        url: URL,
        private val statusCode: Int,
        private val responseBody: String,
        private val inputStreamProvider: () -> ByteArrayInputStream = {
            ByteArrayInputStream(responseBody.toByteArray())
        },
    ) : HttpURLConnection(url) {
        val output = ByteArrayOutputStream()

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun connect() = Unit

        override fun getResponseCode(): Int = statusCode

        override fun getInputStream(): ByteArrayInputStream = inputStreamProvider()

        override fun getErrorStream(): ByteArrayInputStream = ByteArrayInputStream(responseBody.toByteArray())

        override fun getOutputStream(): ByteArrayOutputStream = output
    }
}
