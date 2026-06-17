package com.dps.evenup.data.receipt.impl

import com.dps.evenup.core.network.api.WorkerApiClient
import com.dps.evenup.core.network.api.WorkerApiResponse
import com.dps.evenup.core.network.api.WorkerApiResult
import com.dps.evenup.data.receipt.api.ReceiptDataException
import com.dps.evenup.data.receipt.api.ReceiptImageParseRequest
import com.dps.evenup.domain.receipt.api.FeeType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
              "label": "Tax",
              "amountMinor": 600
            }
          ],
          "subtotalMinor": 3600,
          "totalMinor": 4200,
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

    private class FakeWorkerApiClient(
        private val responseBody: String,
    ) : WorkerApiClient {
        override suspend fun get(path: String): WorkerApiResult = error("Not used.")

        override suspend fun postJson(
            path: String,
            body: String,
        ): WorkerApiResult = WorkerApiResult.Success(WorkerApiResponse(200, responseBody))
    }
}
