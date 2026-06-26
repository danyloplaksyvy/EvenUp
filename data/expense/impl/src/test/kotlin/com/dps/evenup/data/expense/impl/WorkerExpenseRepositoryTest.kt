package com.dps.evenup.data.expense.impl

import com.dps.evenup.core.network.api.WorkerApiClient
import com.dps.evenup.core.network.api.WorkerApiResponse
import com.dps.evenup.core.network.api.WorkerApiResult
import com.dps.evenup.data.expense.api.ExpenseDataException
import com.dps.evenup.data.sharing.impl.DefaultShareLinkResponseMapper
import com.dps.evenup.domain.expense.api.ExpenseDraftId
import com.dps.evenup.domain.expense.api.ExpenseSummary
import com.dps.evenup.domain.expense.api.FeeAllocation
import com.dps.evenup.domain.expense.api.FeeAllocationMode
import com.dps.evenup.domain.expense.api.FeeParticipantShare
import com.dps.evenup.domain.expense.api.FinalizedExpensePayload
import com.dps.evenup.domain.expense.api.ItemAssignment
import com.dps.evenup.domain.expense.api.ItemAssignmentMode
import com.dps.evenup.domain.expense.api.ItemParticipantShare
import com.dps.evenup.domain.expense.api.ParticipantExpenseSummary
import com.dps.evenup.domain.expense.api.SettlementRow
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.CurrencyCode
import com.dps.evenup.domain.receipt.api.FeeId
import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Quantity
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptFee
import com.dps.evenup.domain.receipt.api.ReceiptItem
import com.dps.evenup.domain.receipt.api.ReceiptItemId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerExpenseRepositoryTest {
    @Test
    fun `save finalized expense posts to worker and maps share link response`() = runBlocking {
        val worker = FakeWorkerApiClient(
            responseBody = """
                {
                  "expenseId": "expense_123",
                  "shareId": "A8xQ2Lm9",
                  "shareUrl": "https://evenup.example/e/A8xQ2Lm9"
                }
            """.trimIndent(),
        )
        val repository = WorkerExpenseRepository(worker, DefaultShareLinkResponseMapper())

        val result = repository.saveFinalizedExpense(finalizedPayload())

        assertEquals("expense_123", result.expenseId.value)
        assertEquals("A8xQ2Lm9", result.shareLink.shareId)
        assertEquals("https://evenup.example/e/A8xQ2Lm9", result.shareLink.publicUrl)
        assertEquals("/v1/expenses", worker.lastPath)
        assertTrue(worker.lastBody.contains(""""schemaVersion":1"""))
        assertTrue(worker.lastBody.contains(""""payerParticipantId":"p1""""))
        assertTrue(worker.lastBody.contains(""""subtotalMinor":1000"""))
    }

    @Test
    fun `save finalized expense includes guest passcode when present`() = runBlocking {
        val worker = FakeWorkerApiClient(
            responseBody = """
                {
                  "expenseId": "expense_123",
                  "shareId": "A8xQ2Lm9",
                  "shareUrl": "https://evenup.example/e/A8xQ2Lm9"
                }
            """.trimIndent(),
        )
        val repository = WorkerExpenseRepository(worker, DefaultShareLinkResponseMapper())

        repository.saveFinalizedExpense(finalizedPayload().copy(guestPasscode = "KTRQ"))

        assertTrue(worker.lastBody.contains(""""guestAccess":{"passcode":"KTRQ"}"""))
    }

    @Test
    fun `invalid share link response produces controlled error`() {
        val repository = WorkerExpenseRepository(
            FakeWorkerApiClient("""{"expenseId":"","shareId":"","shareUrl":""}"""),
            DefaultShareLinkResponseMapper(),
        )

        assertThrows(ExpenseDataException::class.java) {
            runBlocking {
                repository.saveFinalizedExpense(finalizedPayload())
            }
        }
    }

    private fun finalizedPayload(): FinalizedExpensePayload = FinalizedExpensePayload(
        draftId = ExpenseDraftId("draft-1"),
        receipt = Receipt(
            merchantName = "Cafe",
            currencyCode = CurrencyCode("USD"),
            items = listOf(
                ReceiptItem(ReceiptItemId("item-1"), "Meal", Quantity(1), MoneyMinor(1_000), MoneyMinor(1_000)),
            ),
            fees = listOf(ReceiptFee(FeeId("fee-1"), FeeType.Tax, "Tax", MoneyMinor(200))),
            subtotal = MoneyMinor(1_000),
            total = MoneyMinor(1_200),
        ),
        participants = listOf(
            Participant(ParticipantId("p1"), "Anna", 0),
            Participant(ParticipantId("p2"), "Ben", 1),
        ),
        payerId = ParticipantId("p1"),
        itemAssignments = listOf(
            ItemAssignment(
                receiptItemId = ReceiptItemId("item-1"),
                mode = ItemAssignmentMode.SharedEqual,
                shares = listOf(
                    ItemParticipantShare(ParticipantId("p1"), MoneyMinor(500)),
                    ItemParticipantShare(ParticipantId("p2"), MoneyMinor(500)),
                ),
            ),
        ),
        feeAllocations = listOf(
            FeeAllocation(
                feeId = FeeId("fee-1"),
                mode = FeeAllocationMode.Equal,
                shares = listOf(
                    FeeParticipantShare(ParticipantId("p1"), MoneyMinor(100)),
                    FeeParticipantShare(ParticipantId("p2"), MoneyMinor(100)),
                ),
            ),
        ),
        summary = ExpenseSummary(
            receiptTotal = MoneyMinor(1_200),
            participantSummaries = listOf(
                ParticipantExpenseSummary(
                    participantId = ParticipantId("p1"),
                    assignedItemTotal = MoneyMinor(500),
                    allocatedFeeTotal = MoneyMinor(100),
                    personShare = MoneyMinor(600),
                    amountPaid = MoneyMinor(1_200),
                    netBalance = MoneyMinor(600),
                ),
                ParticipantExpenseSummary(
                    participantId = ParticipantId("p2"),
                    assignedItemTotal = MoneyMinor(500),
                    allocatedFeeTotal = MoneyMinor(100),
                    personShare = MoneyMinor(600),
                    amountPaid = MoneyMinor.Zero,
                    netBalance = MoneyMinor(-600),
                ),
            ),
            settlementRows = listOf(
                SettlementRow(
                    fromParticipantId = ParticipantId("p2"),
                    toParticipantId = ParticipantId("p1"),
                    amount = MoneyMinor(600),
                ),
            ),
        ),
    )

    private class FakeWorkerApiClient(
        private val responseBody: String,
    ) : WorkerApiClient {
        var lastPath: String = ""
        var lastBody: String = ""

        override suspend fun get(path: String): WorkerApiResult = error("Not used.")

        override suspend fun postJson(
            path: String,
            body: String,
            headers: Map<String, String>,
        ): WorkerApiResult {
            lastPath = path
            lastBody = body
            return WorkerApiResult.Success(WorkerApiResponse(201, responseBody))
        }
    }
}
