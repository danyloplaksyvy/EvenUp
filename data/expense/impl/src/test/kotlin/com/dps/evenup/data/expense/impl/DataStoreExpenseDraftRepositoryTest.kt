package com.dps.evenup.data.expense.impl

import com.dps.evenup.core.datastore.api.StringDataStore
import com.dps.evenup.core.network.api.WorkerApiClient
import com.dps.evenup.core.network.api.WorkerApiResponse
import com.dps.evenup.core.network.api.WorkerApiResult
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.sharing.impl.DefaultShareLinkResponseMapper
import com.dps.evenup.domain.expense.api.ExpenseDraft
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
import com.dps.evenup.domain.receipt.api.ReceiptItemParseMetadata
import com.dps.evenup.domain.receipt.api.ReceiptParseCorrection
import com.dps.evenup.domain.receipt.api.ReceiptParseMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreExpenseDraftRepositoryTest {
    @Test
    fun `draft survives repository recreation through stored JSON`() = runBlocking {
        val store = FakeStringDataStore()
        DataStoreExpenseDraftRepository(store).saveDraft(expenseDraft())

        val restoredDraft = DataStoreExpenseDraftRepository(store).getDraft()

        assertEquals(expenseDraft(), restoredDraft)
    }

    @Test
    fun `draft clears after explicit reset`() = runBlocking {
        val repository = DataStoreExpenseDraftRepository(FakeStringDataStore())
        repository.saveDraft(expenseDraft())

        repository.clearDraft()

        assertNull(repository.getDraft())
    }

    @Test
    fun `old draft JSON without subtotal or parse metadata still restores`() = runBlocking {
        val store = FakeStringDataStore()
        store.write(
            "expense_draft_json",
            """
                {
                  "id": "draft-1",
                  "receipt": {
                    "merchantName": "Cafe",
                    "currency": "USD",
                    "transactionDateLabel": "2026-06-16",
                    "items": [
                      {
                        "id": "item-1",
                        "name": "Meal",
                        "quantity": 1,
                        "unitPriceMinor": 1000,
                        "totalPriceMinor": 1000
                      }
                    ],
                    "fees": [
                      {
                        "id": "fee-1",
                        "type": "Tax",
                        "label": "Tax",
                        "amountMinor": 200
                      }
                    ],
                    "totalMinor": 1200
                  },
                  "participants": [],
                  "payerId": "pending-payer",
                  "itemAssignments": [],
                  "feeAllocations": []
                }
            """.trimIndent(),
        )

        val restoredDraft = DataStoreExpenseDraftRepository(store).getDraft()

        assertNull(restoredDraft?.receipt?.subtotal)
        assertTrue(restoredDraft?.receipt?.parseMetadata?.corrections.orEmpty().isEmpty())
        assertTrue(restoredDraft?.receipt?.items?.single()?.parseMetadata?.candidates.orEmpty().isEmpty())
    }

    @Test
    fun `successful save clears draft`() = runBlocking {
        val draftRepository = FakeExpenseDraftRepository()
        draftRepository.saveDraft(expenseDraft())
        val repository = WorkerExpenseRepository(
            workerApiClient = FakeWorkerApiClient(success = true),
            shareLinkResponseMapper = DefaultShareLinkResponseMapper(),
            draftRepository = draftRepository,
        )

        repository.saveFinalizedExpense(finalizedPayload())

        assertTrue(draftRepository.wasCleared)
    }

    @Test
    fun `failed save does not clear draft`() {
        val draftRepository = FakeExpenseDraftRepository()
        val repository = WorkerExpenseRepository(
            workerApiClient = FakeWorkerApiClient(success = false),
            shareLinkResponseMapper = DefaultShareLinkResponseMapper(),
            draftRepository = draftRepository,
        )

        runCatching {
            runBlocking {
                repository.saveFinalizedExpense(finalizedPayload())
            }
        }

        assertTrue(!draftRepository.wasCleared)
    }

    private fun finalizedPayload(): FinalizedExpensePayload = FinalizedExpensePayload(
        draftId = expenseDraft().id,
        receipt = receipt(),
        participants = participants(),
        payerId = ParticipantId("p1"),
        itemAssignments = itemAssignments(),
        feeAllocations = feeAllocations(),
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
                SettlementRow(ParticipantId("p2"), ParticipantId("p1"), MoneyMinor(600)),
            ),
        ),
    )

    private fun expenseDraft(): ExpenseDraft = ExpenseDraft(
        id = ExpenseDraftId("draft-1"),
        receipt = receipt(),
        participants = participants(),
        payerId = ParticipantId("p1"),
        itemAssignments = itemAssignments(),
        feeAllocations = feeAllocations(),
    )

    private fun receipt(): Receipt = Receipt(
        merchantName = "Cafe",
        currencyCode = CurrencyCode("USD"),
        transactionDateLabel = "2026-06-16",
        items = listOf(
            ReceiptItem(ReceiptItemId("item-1"), "Meal", Quantity(1), MoneyMinor(1_000), MoneyMinor(1_000)),
            ReceiptItem(
                id = ReceiptItemId("item-2"),
                name = "Dessert",
                quantity = Quantity(1),
                unitPrice = MoneyMinor(300),
                totalPrice = MoneyMinor(300),
                parseMetadata = ReceiptItemParseMetadata(
                    confidence = 0.72,
                    candidates = listOf(MoneyMinor(300), MoneyMinor(380)),
                    needsReview = true,
                ),
            ),
        ),
        fees = listOf(ReceiptFee(FeeId("fee-1"), FeeType.Tax, "Tax", MoneyMinor(200))),
        subtotal = MoneyMinor(1_300),
        total = MoneyMinor(1_500),
        parseMetadata = ReceiptParseMetadata(
            corrections = listOf(
                ReceiptParseCorrection(
                    field = "items[1].totalPriceMinor",
                    itemName = "Dessert",
                    from = MoneyMinor(380),
                    to = MoneyMinor(300),
                    reason = "Corrected to match subtotal.",
                ),
            ),
            reviewWarnings = listOf("Check Dessert"),
        ),
    )

    private fun participants(): List<Participant> = listOf(
        Participant(ParticipantId("p1"), "Anna", 0),
        Participant(ParticipantId("p2"), "Ben", 1, isSavedLocalName = true),
    )

    private fun itemAssignments(): List<ItemAssignment> = listOf(
        ItemAssignment(
            receiptItemId = ReceiptItemId("item-1"),
            mode = ItemAssignmentMode.SharedEqual,
            shares = listOf(
                ItemParticipantShare(ParticipantId("p1"), MoneyMinor(500)),
                ItemParticipantShare(ParticipantId("p2"), MoneyMinor(500)),
            ),
        ),
    )

    private fun feeAllocations(): List<FeeAllocation> = listOf(
        FeeAllocation(
            feeId = FeeId("fee-1"),
            mode = FeeAllocationMode.Equal,
            shares = listOf(
                FeeParticipantShare(ParticipantId("p1"), MoneyMinor(100)),
                FeeParticipantShare(ParticipantId("p2"), MoneyMinor(100)),
            ),
        ),
    )

    private class FakeStringDataStore : StringDataStore {
        private val values = mutableMapOf<String, String>()

        override suspend fun read(key: String): String? = values[key]

        override suspend fun write(
            key: String,
            value: String,
        ) {
            values[key] = value
        }

        override suspend fun remove(key: String) {
            values.remove(key)
        }
    }

    private class FakeExpenseDraftRepository : ExpenseDraftRepository {
        var wasCleared = false
        private var draft: ExpenseDraft? = null

        override suspend fun getDraft(): ExpenseDraft? = draft

        override suspend fun saveDraft(draft: ExpenseDraft) {
            this.draft = draft
        }

        override suspend fun clearDraft() {
            wasCleared = true
            draft = null
        }
    }

    private class FakeWorkerApiClient(
        private val success: Boolean,
    ) : WorkerApiClient {
        override suspend fun get(path: String): WorkerApiResult = error("Not used.")

        override suspend fun postJson(
            path: String,
            body: String,
            headers: Map<String, String>,
        ): WorkerApiResult {
            if (!success) {
                return WorkerApiResult.Failure(com.dps.evenup.core.network.api.WorkerNetworkError.ConnectionFailed)
            }

            return WorkerApiResult.Success(
                WorkerApiResponse(
                    statusCode = 201,
                    body = """
                        {
                          "expenseId": "expense_123",
                          "shareId": "A8xQ2Lm9",
                          "shareUrl": "https://evenup.example/e/A8xQ2Lm9"
                        }
                    """.trimIndent(),
                ),
            )
        }
    }
}
