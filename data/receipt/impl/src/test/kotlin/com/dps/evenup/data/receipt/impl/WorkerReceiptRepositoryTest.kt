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
              "totalPriceMinor": 3600
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
          "confidence": 0.92
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
