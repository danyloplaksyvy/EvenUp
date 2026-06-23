package com.dps.evenup.data.receipt.impl

import com.dps.evenup.core.network.api.WorkerApiClient
import com.dps.evenup.core.network.api.WorkerApiResponse
import com.dps.evenup.core.network.api.WorkerApiResult
import com.dps.evenup.core.network.api.WorkerNetworkError
import com.dps.evenup.data.receipt.api.ReceiptDataException
import com.dps.evenup.data.receipt.api.ReceiptImageParseRequest
import com.dps.evenup.domain.receipt.api.FeeType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkerReceiptRepositoryTest {
    @Test
    fun `backend JSON maps to domain receipt`() = runBlocking {
        val repository = WorkerReceiptRepository(FakeWorkerApiClient(receiptJson()))

        val receipt = repository.parseReceiptImage(
            ReceiptImageParseRequest(
                imageBase64 = "abc",
                mimeType = "image/jpeg",
                localeHint = "en",
                currencyHint = "EUR",
            ),
        )

        assertEquals("Bella Roma", receipt.merchantName)
        assertEquals("EUR", receipt.currencyCode.value)
        assertEquals("item_1", receipt.items.single().id.value)
        assertEquals(2, receipt.items.single().quantity.value)
        assertEquals(3600L, receipt.items.single().totalPrice.value)
        assertEquals(3600L, receipt.subtotal?.value)
        assertEquals(0.62, receipt.items.single().parseMetadata.confidence)
        assertEquals(listOf(3500L, 3600L), receipt.items.single().parseMetadata.candidates.map { it.value })
        assertEquals(true, receipt.items.single().parseMetadata.needsReview)
        assertEquals("items[0].totalPriceMinor", receipt.parseMetadata.corrections.single().field)
        assertEquals(3500L, receipt.parseMetadata.corrections.single().to.value)
        assertEquals("Check subtotal", receipt.parseMetadata.reviewWarnings.single())
        assertEquals(FeeType.Tax, receipt.fees.first().type)
        assertEquals(4200L, receipt.total.value)
    }

    @Test
    fun `unknown fee type maps safely to other`() = runBlocking {
        val repository = WorkerReceiptRepository(FakeWorkerApiClient(receiptJson(feeType = "LOCAL_FEE")))

        val receipt = repository.parseReceiptImage(ReceiptImageParseRequest("abc", "image/jpeg"))

        assertEquals(FeeType.Other, receipt.fees.single().type)
    }

    @Test
    fun `discount fee type maps to discount and preserves negative amount`() = runBlocking {
        val repository = WorkerReceiptRepository(
            FakeWorkerApiClient(receiptJson(feeType = "DISCOUNT", feeLabel = "Promo", feeAmountMinor = -250, totalMinor = 3350)),
        )

        val receipt = repository.parseReceiptImage(ReceiptImageParseRequest("abc", "image/jpeg"))

        assertEquals(FeeType.Discount, receipt.fees.single().type)
        assertEquals(-250L, receipt.fees.single().amount.value)
    }

    @Test
    fun `old backend response removes included tax duplicated from receipt total`() = runBlocking {
        val repository = WorkerReceiptRepository(FakeWorkerApiClient(crownePlazaDuplicateTaxReceiptJson()))

        val receipt = repository.parseReceiptImage(ReceiptImageParseRequest("abc", "image/jpeg"))

        assertEquals(listOf(FeeType.Discount), receipt.fees.map { fee -> fee.type })
        assertEquals(listOf(-2_655L), receipt.fees.map { fee -> fee.amount.value })
        assertEquals(8_850L, receipt.subtotal?.value)
        assertEquals(6_195L, receipt.total.value)
        assertEquals("fees[0].amountMinor", receipt.parseMetadata.corrections.single().field)
        assertEquals(6_195L, receipt.parseMetadata.corrections.single().from.value)
        assertEquals(0L, receipt.parseMetadata.corrections.single().to.value)
    }

    @Test
    fun `request id is kept out of worker request body`() = runBlocking {
        val apiClient = FakeWorkerApiClient(receiptJson())
        val repository = WorkerReceiptRepository(apiClient)

        repository.parseReceiptImage(
            ReceiptImageParseRequest(
                imageBase64 = "abc",
                mimeType = "image/jpeg",
                requestId = "local-request-id",
            ),
        )

        assertFalse(apiClient.lastPostBody.orEmpty().contains("local-request-id"))
        assertFalse(apiClient.lastPostBody.orEmpty().contains("requestId"))
        assertEquals("local-request-id", apiClient.lastPostHeaders["X-EvenUp-Request-Id"])
    }

    @Test
    fun `retryable parse failures retry once and then return successful receipt`() = runBlocking {
        val retryableFailures = listOf(
            WorkerNetworkError.Timeout,
            WorkerNetworkError.ConnectionFailed,
            WorkerNetworkError.Unknown,
            WorkerNetworkError.HttpFailure(429, """{"error":true}"""),
            WorkerNetworkError.HttpFailure(500, """{"error":true}"""),
            WorkerNetworkError.HttpFailure(503, """{"error":true}"""),
        )

        retryableFailures.forEach { retryableFailure ->
            val apiClient = FakeWorkerApiClient(
                WorkerApiResult.Failure(retryableFailure),
                WorkerApiResult.Success(WorkerApiResponse(200, receiptJson())),
            )
            val repository = WorkerReceiptRepository(apiClient)

            val receipt = repository.parseReceiptImage(
                ReceiptImageParseRequest(
                    imageBase64 = "abc",
                    mimeType = "image/jpeg",
                    requestId = "retry-request-id",
                ),
            )

            assertEquals("Bella Roma", receipt.merchantName)
            assertEquals(2, apiClient.postCount)
            assertEquals(listOf("retry-request-id", "retry-request-id"), apiClient.postHeaders.map { it["X-EvenUp-Request-Id"] })
            assertEquals(1, apiClient.postBodies.distinct().size)
        }
    }

    @Test
    fun `client parse failure is not retried`() {
        val apiClient = FakeWorkerApiClient(
            WorkerApiResult.Failure(WorkerNetworkError.HttpFailure(400, """{"error":true}""")),
            WorkerApiResult.Success(WorkerApiResponse(200, receiptJson())),
        )
        val repository = WorkerReceiptRepository(apiClient)

        assertThrows(ReceiptDataException::class.java) {
            runBlocking {
                repository.parseReceiptImage(ReceiptImageParseRequest("abc", "image/jpeg"))
            }
        }
        assertEquals(1, apiClient.postCount)
    }

    @Test
    fun `final retryable failure is returned after second attempt`() {
        val apiClient = FakeWorkerApiClient(
            WorkerApiResult.Failure(WorkerNetworkError.Timeout),
            WorkerApiResult.Failure(WorkerNetworkError.Timeout),
        )
        val repository = WorkerReceiptRepository(apiClient)

        assertThrows(ReceiptDataException::class.java) {
            runBlocking {
                repository.parseReceiptImage(ReceiptImageParseRequest("abc", "image/jpeg"))
            }
        }
        assertEquals(2, apiClient.postCount)
    }

    @Test
    fun `old backend response reconciles single visually similar subtotal mismatch`() = runBlocking {
        val repository = WorkerReceiptRepository(FakeWorkerApiClient(tabernaReceiptJson()))

        val receipt = repository.parseReceiptImage(ReceiptImageParseRequest("abc", "image/jpeg"))

        val rissol = receipt.items.first { item -> item.name == "Rissol" }
        assertEquals(560L, rissol.totalPrice.value)
        assertEquals(280L, rissol.unitPrice.value)
        assertEquals(listOf(560L, 580L), rissol.parseMetadata.candidates.map { it.value })
        assertEquals("items[4].totalPriceMinor", receipt.parseMetadata.corrections.single().field)
        assertEquals(580L, receipt.parseMetadata.corrections.single().from.value)
        assertEquals(560L, receipt.parseMetadata.corrections.single().to.value)
    }

    @Test
    fun `old backend response reconciles quantity unit price parsed as line total`() = runBlocking {
        val repository = WorkerReceiptRepository(FakeWorkerApiClient(crownePlazaReceiptJson()))

        val receipt = repository.parseReceiptImage(ReceiptImageParseRequest("abc", "image/jpeg"))

        val solomillo = receipt.items.first { item -> item.name == "Solomillo a la Sal" }
        assertEquals(4_400L, solomillo.totalPrice.value)
        assertEquals(2_200L, solomillo.unitPrice.value)
        assertEquals(true, solomillo.parseMetadata.needsReview)
        assertEquals(listOf(4_400L, 2_200L), solomillo.parseMetadata.candidates.map { it.value })
        assertEquals(8_850L, receipt.subtotal?.value)
        assertEquals("items[2].totalPriceMinor", receipt.parseMetadata.corrections.single().field)
        assertEquals(2_200L, receipt.parseMetadata.corrections.single().from.value)
        assertEquals(4_400L, receipt.parseMetadata.corrections.single().to.value)
        assertEquals(true, receipt.parseMetadata.corrections.single().reason.contains("quantity line total", ignoreCase = true))
    }

    @Test
    fun `invalid values produce controlled errors`() {
        val repository = WorkerReceiptRepository(FakeWorkerApiClient(receiptJson(quantity = 1.5)))

        assertThrows(ReceiptDataException::class.java) {
            runBlocking {
                repository.parseReceiptImage(ReceiptImageParseRequest("abc", "image/jpeg"))
            }
        }
    }

    private fun receiptJson(
        feeType: String = "TAX",
        feeLabel: String = "Tax",
        feeAmountMinor: Long = 600,
        totalMinor: Long = 4200,
        quantity: Double = 2.0,
    ): String = """
        {
          "merchantName": "Bella Roma",
          "transactionDate": "2026-06-15",
          "currency": "EUR",
          "items": [
            {
              "name": "Pasta",
              "quantity": $quantity,
              "unitPriceMinor": 1800,
              "totalPriceMinor": 3600,
              "confidence": 0.62,
              "candidatesMinor": [3500, 3600],
              "needsReview": true
            }
          ],
          "fees": [
            {
              "type": "$feeType",
              "label": "$feeLabel",
              "amountMinor": $feeAmountMinor
            }
          ],
          "subtotalMinor": 3600,
          "totalMinor": $totalMinor,
          "confidence": 0.92,
          "corrections": [
            {
              "field": "items[0].totalPriceMinor",
              "itemName": "Pasta",
              "fromMinor": 3600,
              "toMinor": 3500,
              "reason": "Corrected to match subtotal."
            }
          ],
          "reviewWarnings": ["Check subtotal"]
        }
    """.trimIndent()

    private fun tabernaReceiptJson(): String = """
        {
          "merchantName": "Taberna do Mercado",
          "transactionDate": "2016-09-06",
          "currency": "GBP",
          "items": [
            {"name": "Super Bock", "quantity": 2, "unitPriceMinor": 250, "totalPriceMinor": 500},
            {"name": "Sparkling Water", "quantity": 1, "unitPriceMinor": 300, "totalPriceMinor": 300},
            {"name": "Cappucino", "quantity": 1, "unitPriceMinor": 260, "totalPriceMinor": 260},
            {"name": "Espresso", "quantity": 1, "unitPriceMinor": 200, "totalPriceMinor": 200},
            {"name": "Rissol", "quantity": 2, "unitPriceMinor": 290, "totalPriceMinor": 580},
            {"name": "Serra Da Estrela", "quantity": 1, "unitPriceMinor": 890, "totalPriceMinor": 890},
            {"name": "Chourico Vinho Tinto", "quantity": 1, "unitPriceMinor": 790, "totalPriceMinor": 790},
            {"name": "Octopus Peppers", "quantity": 1, "unitPriceMinor": 900, "totalPriceMinor": 900},
            {"name": "Dorset Char", "quantity": 1, "unitPriceMinor": 600, "totalPriceMinor": 600},
            {"name": "Bifana", "quantity": 1, "unitPriceMinor": 800, "totalPriceMinor": 800}
          ],
          "fees": [
            {
              "type": "SERVICE_FEE",
              "label": "Service Charge",
              "amountMinor": 725
            }
          ],
          "subtotalMinor": 5800,
          "totalMinor": 6525,
          "confidence": 0.84
        }
    """.trimIndent()

    private fun crownePlazaReceiptJson(): String = """
        {
          "merchantName": "Crowne Plaza Barcelona - Fira Center",
          "transactionDate": "2019-03-10",
          "currency": "EUR",
          "items": [
            {"name": "Burrata", "quantity": 1, "unitPriceMinor": 1600, "totalPriceMinor": 1600},
            {"name": "Pan de Coca", "quantity": 1, "unitPriceMinor": 350, "totalPriceMinor": 350},
            {"name": "Solomillo a la Sal", "quantity": 2, "unitPriceMinor": 2200, "totalPriceMinor": 2200},
            {"name": "100% Chocolate fondant", "quantity": 1, "unitPriceMinor": 850, "totalPriceMinor": 850},
            {"name": "Agua 1/2", "quantity": 1, "unitPriceMinor": 450, "totalPriceMinor": 450},
            {"name": "Copa Aphrodisiaque T.", "quantity": 2, "unitPriceMinor": 600, "totalPriceMinor": 1200}
          ],
          "fees": [
            {
              "type": "DISCOUNT",
              "label": "Portal Reserva 30%",
              "amountMinor": -2655
            }
          ],
          "subtotalMinor": 6650,
          "totalMinor": 6195,
          "confidence": 0.84
        }
    """.trimIndent()

    private fun crownePlazaDuplicateTaxReceiptJson(): String = """
        {
          "merchantName": "Crowne Plaza Barcelona - Fira Center",
          "transactionDate": "2019-03-10",
          "currency": "EUR",
          "items": [
            {"name": "Burrata", "quantity": 1, "unitPriceMinor": 1600, "totalPriceMinor": 1600},
            {"name": "Pan de Coca", "quantity": 1, "unitPriceMinor": 350, "totalPriceMinor": 350},
            {"name": "Solomillo a la Sal", "quantity": 2, "unitPriceMinor": 2200, "totalPriceMinor": 4400},
            {"name": "100% Chocolate fondant", "quantity": 1, "unitPriceMinor": 850, "totalPriceMinor": 850},
            {"name": "Agua 1/2", "quantity": 1, "unitPriceMinor": 450, "totalPriceMinor": 450},
            {"name": "Copa Aphrodisiaque T.", "quantity": 2, "unitPriceMinor": 600, "totalPriceMinor": 1200}
          ],
          "fees": [
            {
              "type": "TAX",
              "label": "Tax",
              "amountMinor": 6195
            },
            {
              "type": "DISCOUNT",
              "label": "Portal Reserva 30%",
              "amountMinor": -2655
            }
          ],
          "subtotalMinor": 8850,
          "totalMinor": 6195,
          "confidence": 0.84
        }
    """.trimIndent()

    private class FakeWorkerApiClient private constructor(
        private val responses: MutableList<WorkerApiResult>,
    ) : WorkerApiClient {
        constructor(responseBody: String) : this(mutableListOf(WorkerApiResult.Success(WorkerApiResponse(200, responseBody))))

        constructor(vararg responses: WorkerApiResult) : this(responses.toMutableList())

        var lastPostBody: String? = null
            private set
        var lastPostHeaders: Map<String, String> = emptyMap()
            private set
        val postBodies = mutableListOf<String>()
        val postHeaders = mutableListOf<Map<String, String>>()
        val postCount: Int
            get() = postBodies.size

        override suspend fun get(path: String): WorkerApiResult = error("Not used.")

        override suspend fun postJson(
            path: String,
            body: String,
            headers: Map<String, String>,
        ): WorkerApiResult {
            lastPostBody = body
            lastPostHeaders = headers
            postBodies += body
            postHeaders += headers
            return responses.removeAt(0)
        }
    }
}
