package com.dps.evenup.feature.expenseflow.impl.reviewexpense

import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.expense.api.ExpenseRepository
import com.dps.evenup.data.expenseinput.api.AiExpenseSessionRepository
import com.dps.evenup.data.sharing.api.SavedShareLink
import com.dps.evenup.domain.expense.api.CalculateExpenseSummaryUseCase
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.ExpenseDraftId
import com.dps.evenup.domain.expense.api.ExpenseId
import com.dps.evenup.domain.expense.api.ExpenseSummary
import com.dps.evenup.domain.expense.api.FinalExpenseValidationError
import com.dps.evenup.domain.expense.api.FinalExpenseValidationResult
import com.dps.evenup.domain.expense.api.FinalizedExpensePayload
import com.dps.evenup.domain.expense.api.ParticipantExpenseSummary
import com.dps.evenup.domain.expense.api.SettlementRow
import com.dps.evenup.domain.expense.api.ValidateExpenseBeforeSaveUseCase
import com.dps.evenup.domain.expenseinput.api.AiExpenseSession
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.CurrencyCode
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.sharing.api.GenerateGuestPasscodeUseCase
import com.dps.evenup.domain.sharing.api.ShareLink
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewExpensePresenterTest {
    @Test
    fun `load renders USD currency across summary settlement payer and details`() = runBlocking {
        val state = presenter(currencyCode = "USD").load()

        assertEquals("$100.00", state.totalLabel)
        assertEquals("Expense total, 100 dollars", state.totalContentDescription)
        assertEquals(listOf("$40.00", "$45.00"), state.settlementRows.map { row -> row.amountLabel })
        assertEquals("Kehn paid $100.00", state.payerSummary.paidLabel)
        assertEquals("Kehn's share is $15.00", state.payerSummary.shareLabel)
        assertEquals("Kehn receives $85.00", state.payerSummary.resultLabel)
        assertEquals(
            listOf("Kehn paid" to "$100.00", "Kehn's share" to "$15.00", "Kehn receives" to "$85.00"),
            state.payerSummary.rows.map { row -> row.label to row.valueLabel },
        )
        assertEquals("Totals balanced", state.balanceStatusLabel)
        assertEquals("Open calculation details. Totals balanced.", state.calculationDetailsContentDescription)

        val payerDetail = state.detailRows.first { detail -> detail.participantName == "Kehn" }
        assertEquals("$12.00", payerDetail.itemSubtotalLabel)
        assertEquals("$3.00", payerDetail.feesLabel)
        assertEquals("$15.00", payerDetail.totalShareLabel)
        assertEquals("$100.00", payerDetail.amountPaidLabel)
        assertEquals("Receives $85.00", payerDetail.resultLabel)
    }

    @Test
    fun `load exposes compact paid-by label and participant count`() = runBlocking {
        val state = presenter(currencyCode = "USD").load()

        assertEquals("Kehn paid", state.paidByLabel)
        assertEquals("4 people", state.participantCountLabel)
        assertEquals("Kehn paid, 4 people", state.paidByContentDescription)
    }

    @Test
    fun `load renders GBP and EUR currency symbols from receipt currency`() = runBlocking {
        val gbpState = presenter(currencyCode = "GBP").load()
        val eurState = presenter(currencyCode = "EUR").load()

        assertEquals("£100.00", gbpState.totalLabel)
        assertEquals("£40.00", gbpState.settlementRows.first().amountLabel)
        assertEquals("Kehn receives £85.00", gbpState.payerSummary.resultLabel)

        assertEquals("€100.00", eurState.totalLabel)
        assertEquals("€45.00", eurState.settlementRows.last().amountLabel)
        assertEquals("Pays €40.00", eurState.detailRows.first { detail -> detail.participantName == "Storak" }.resultLabel)
    }

    @Test
    fun `detail result labels map receiving paying and settled balances`() = runBlocking {
        val state = presenter(currencyCode = "USD").load()

        assertEquals("Receives $85.00", state.detailRows.first { it.participantName == "Kehn" }.resultLabel)
        assertEquals("Pays $40.00", state.detailRows.first { it.participantName == "Storak" }.resultLabel)
        assertEquals("Pays $45.00", state.detailRows.first { it.participantName == "Billy" }.resultLabel)
        assertEquals("Settled", state.detailRows.first { it.participantName == "Danya" }.resultLabel)
    }

    @Test
    fun `payer summary supports receives pays and settled variants`() = runBlocking {
        val receivesState = presenter(currencyCode = "USD").load()
        val paysState = presenter(
            currencyCode = "USD",
            summary = reviewExpenseSummary(
                payerShare = MoneyMinor(10_500),
                payerNetBalance = MoneyMinor(-500),
            ),
        ).load()
        val settledState = presenter(
            currencyCode = "USD",
            summary = reviewExpenseSummary(
                payerShare = MoneyMinor(10_000),
                payerNetBalance = MoneyMinor.Zero,
            ),
        ).load()

        assertEquals("Kehn receives" to "$85.00", receivesState.payerSummary.rows.last().let { it.label to it.valueLabel })
        assertEquals("Kehn pays" to "$5.00", paysState.payerSummary.rows.last().let { it.label to it.valueLabel })
        assertEquals("Kehn is settled" to "Settled", settledState.payerSummary.rows.last().let { it.label to it.valueLabel })
    }

    @Test
    fun `calculation details events open and dismiss bottom sheet state`() = runBlocking {
        val presenter = presenter(currencyCode = "USD")
        val loadedState = presenter.load()

        val openState = presenter.reduce(loadedState, ReviewExpenseUiEvent.CalculationDetailsClick)
        val dismissedState = presenter.reduce(openState, ReviewExpenseUiEvent.CalculationDetailsDismissed)

        assertTrue(openState.detailsSheetVisible)
        assertFalse(dismissedState.detailsSheetVisible)
    }

    @Test
    fun `final consistency validation blocks shares that do not match receipt total`() = runBlocking {
        val invalidSummary = reviewExpenseSummary().copy(
            participantSummaries = reviewExpenseSummary().participantSummaries.mapIndexed { index, participantSummary ->
                if (index == 0) {
                    participantSummary.copy(personShare = MoneyMinor(1_600))
                } else {
                    participantSummary
                }
            },
        )

        val state = presenter(currencyCode = "USD", summary = invalidSummary).load()

        assertFalse(state.canSave)
        assertEquals("Shares do not add up to the receipt total.", state.validationError)
        assertNull(state.balanceStatusLabel)
    }

    @Test
    fun `final consistency validation blocks paid amounts that do not match receipt total`() = runBlocking {
        val invalidSummary = reviewExpenseSummary().copy(
            participantSummaries = reviewExpenseSummary().participantSummaries.mapIndexed { index, participantSummary ->
                if (index == 0) {
                    participantSummary.copy(amountPaid = MoneyMinor(9_000))
                } else {
                    participantSummary
                }
            },
        )

        val state = presenter(currencyCode = "USD", summary = invalidSummary).load()

        assertFalse(state.canSave)
        assertEquals("Paid amounts do not match the receipt total.", state.validationError)
        assertNull(state.balanceStatusLabel)
    }

    @Test
    fun `final consistency validation blocks settlement transfers that do not match balances`() = runBlocking {
        val invalidSummary = reviewExpenseSummary().copy(
            settlementRows = reviewExpenseSummary().settlementRows.take(1),
        )

        val state = presenter(currencyCode = "USD", summary = invalidSummary).load()

        assertFalse(state.canSave)
        assertEquals("Settlement transfers do not match participant balances.", state.validationError)
        assertNull(state.balanceStatusLabel)
    }

    @Test
    fun `totals balanced is only shown when consistency and domain validation pass`() = runBlocking {
        val validState = presenter(currencyCode = "USD").load()
        val invalidDomainState = presenter(
            currencyCode = "USD",
            validationErrors = setOf(FinalExpenseValidationError.InvalidFeeAllocations),
        ).load()

        assertEquals("Totals balanced", validState.balanceStatusLabel)
        assertEquals("Totals balanced", validState.balanceStatusContentDescription)
        assertNull(invalidDomainState.balanceStatusLabel)
        assertEquals("Review fee allocations before saving.", invalidDomainState.validationError)
    }

    @Test
    fun `accessibility labels describe totals settlement payer summary and detail rows`() = runBlocking {
        val state = presenter(currencyCode = "USD").load()

        assertEquals("Expense total, 100 dollars", state.totalContentDescription)
        assertEquals("Storak pays Kehn 40 dollars", state.settlementRows.first().contentDescription)
        assertEquals(
            "Payer summary. Kehn paid 100 dollars. Kehn's share is 15 dollars. Kehn receives 85 dollars.",
            state.payerSummary.contentDescription,
        )
        assertEquals("Kehn receives 85 dollars", state.payerSummary.rows.last().contentDescription)
        val storakDetail = state.detailRows.first { detail -> detail.participantName == "Storak" }
        assertEquals(
            "Storak, items 35 dollars, fees 5 dollars, total share 40 dollars, paid 0 dollars, result pays 40 dollars.",
            storakDetail.contentDescription,
        )
        assertFalse(storakDetail.contentDescription.contains("-"))
    }

    @Test
    fun `long participant names remain presentation ready without altering money labels`() = runBlocking {
        val draft = reviewExpenseDraft(currencyCode = "USD").copy(
            receipt = reviewExpenseDraft(currencyCode = "USD").receipt.copy(
                merchantName = "A very long merchant name that should not push money labels off screen",
            ),
            participants = listOf(
                Participant(
                    id = ParticipantId("payer"),
                    name = "Kehn With An Exceptionally Long Display Name",
                    creationOrder = 0,
                ),
                Participant(
                    id = ParticipantId("storak"),
                    name = "Storak With Another Exceptionally Long Display Name",
                    creationOrder = 1,
                ),
                Participant(id = ParticipantId("billy"), name = "Billy", creationOrder = 2),
                Participant(id = ParticipantId("danya"), name = "Danya", creationOrder = 3),
            ),
        )

        val state = presenter(draft = draft, summary = reviewExpenseSummary()).load()

        assertEquals("Kehn With An Exceptionally Long Display Name paid", state.paidByLabel)
        assertEquals("$40.00", state.settlementRows.first().amountLabel)
        assertEquals(
            "Storak With Another Exceptionally Long Display Name",
            state.settlementRows.first().fromParticipantName,
        )
    }

    @Test
    fun `settlement rows contain only payment actions and payer summary is separate`() = runBlocking {
        val state = presenter(currencyCode = "USD").load()

        assertEquals(2, state.settlementRows.size)
        assertEquals(listOf("Storak", "Billy"), state.settlementRows.map { row -> row.fromParticipantName })
        assertFalse(state.settlementRows.any { row -> row.fromParticipantName == "Kehn" && row.amountLabel == "$15.00" })
        assertEquals("Kehn's share" to "$15.00", state.payerSummary.rows[1].let { row -> row.label to row.valueLabel })
    }

    @Test
    fun `payer summary supports structured paid share and result rows`() = runBlocking {
        val state = presenter(currencyCode = "USD").load()

        assertEquals(
            PayerSummaryUiState(
                paidLabel = "Kehn paid $100.00",
                shareLabel = "Kehn's share is $15.00",
                resultLabel = "Kehn receives $85.00",
                rows = listOf(
                    PayerSummaryRowUiState(
                        label = "Kehn paid",
                        valueLabel = "$100.00",
                        contentDescription = "Kehn paid 100 dollars",
                    ),
                    PayerSummaryRowUiState(
                        label = "Kehn's share",
                        valueLabel = "$15.00",
                        contentDescription = "Kehn's share is 15 dollars",
                    ),
                    PayerSummaryRowUiState(
                        label = "Kehn receives",
                        valueLabel = "$85.00",
                        contentDescription = "Kehn receives 85 dollars",
                        emphasized = true,
                    ),
                ),
                contentDescription = "Payer summary. Kehn paid 100 dollars. Kehn's share is 15 dollars. Kehn receives 85 dollars.",
            ),
            state.payerSummary,
        )
        assertTrue(state.canSave)
    }

    @Test
    fun `no settlement needed remains valid with clear empty settlement state`() = runBlocking {
        val state = presenter(currencyCode = "USD", summary = reviewSettledExpenseSummary()).load()

        assertTrue(state.settlementRows.isEmpty())
        assertTrue(state.canSave)
        assertEquals("Totals balanced", state.balanceStatusLabel)
        assertEquals("Kehn is settled" to "Settled", state.payerSummary.rows.last().let { it.label to it.valueLabel })
    }

    @Test
    fun `save generates guest passcode and returns it with share link`() = runBlocking {
        val expenseRepository = FakeExpenseRepository()
        val draftRepository = FakeExpenseDraftRepository(reviewExpenseDraft(currencyCode = "USD"))
        val sessionRepository = FakeAiExpenseSessionRepository(AiExpenseSession("session-1", description = "Dinner"))
        val presenter = presenter(
            draftRepository = draftRepository,
            expenseRepository = expenseRepository,
            generateGuestPasscode = FakeGenerateGuestPasscodeUseCase("KTRQ"),
            aiSessionRepository = sessionRepository,
        )

        val result = presenter.saveDraft()

        assertEquals(SaveReviewExpenseResult.Saved("https://example.test/e/share-1", "KTRQ"), result)
        assertEquals("KTRQ", expenseRepository.savedPayload?.guestPasscode)
        assertNull(draftRepository.draft)
        assertNull(sessionRepository.current.value)
    }

    @Test
    fun `invalid save preserves draft and AI session`() = runBlocking {
        val draftRepository = FakeExpenseDraftRepository(reviewExpenseDraft(currencyCode = "USD"))
        val sessionRepository = FakeAiExpenseSessionRepository(AiExpenseSession("session-1", description = "Dinner"))
        val presenter = presenter(
            draftRepository = draftRepository,
            aiSessionRepository = sessionRepository,
            validationErrors = setOf(FinalExpenseValidationError.InvalidReceipt),
        )

        val result = presenter.saveDraft()

        assertTrue(result is SaveReviewExpenseResult.Invalid)
        assertTrue(draftRepository.draft != null)
        assertTrue(sessionRepository.current.value != null)
    }

    @Test
    fun `worker save failure preserves draft and AI session`() = runBlocking {
        val draftRepository = FakeExpenseDraftRepository(reviewExpenseDraft(currencyCode = "USD"))
        val sessionRepository = FakeAiExpenseSessionRepository(AiExpenseSession("session-1", description = "Dinner"))
        val presenter = presenter(
            draftRepository = draftRepository,
            aiSessionRepository = sessionRepository,
            expenseRepository = FakeExpenseRepository(shouldFail = true),
        )

        val result = runCatching { presenter.saveDraft() }

        assertTrue(result.isFailure)
        assertTrue(draftRepository.draft != null)
        assertTrue(sessionRepository.current.value != null)
    }

    private fun presenter(
        currencyCode: String = "USD",
        draft: ExpenseDraft = reviewExpenseDraft(currencyCode = currencyCode),
        summary: ExpenseSummary = reviewExpenseSummary(),
        validationErrors: Set<FinalExpenseValidationError> = emptySet(),
        draftRepository: FakeExpenseDraftRepository = FakeExpenseDraftRepository(draft),
        expenseRepository: FakeExpenseRepository = FakeExpenseRepository(),
        generateGuestPasscode: GenerateGuestPasscodeUseCase = FakeGenerateGuestPasscodeUseCase("KTRQ"),
        aiSessionRepository: AiExpenseSessionRepository? = null,
    ): ReviewExpensePresenter {
        return ReviewExpensePresenter(
            draftRepository = draftRepository,
            expenseRepository = expenseRepository,
            calculateSummary = FakeCalculateExpenseSummaryUseCase(summary),
            validateExpenseBeforeSave = FakeValidateExpenseBeforeSaveUseCase(summary, validationErrors),
            generateGuestPasscode = generateGuestPasscode,
            aiSessionRepository = aiSessionRepository,
        )
    }

    private class FakeAiExpenseSessionRepository(initial: AiExpenseSession?) : AiExpenseSessionRepository {
        val current = MutableStateFlow(initial)
        override val session: Flow<AiExpenseSession?> = current

        override suspend fun getSession(): AiExpenseSession? = current.value

        override suspend fun saveSession(session: AiExpenseSession) {
            current.value = session
        }

        override suspend fun clearSession() {
            current.value = null
        }
    }

    private class FakeExpenseDraftRepository(
        var draft: ExpenseDraft?,
    ) : ExpenseDraftRepository {
        override suspend fun getDraft(): ExpenseDraft? = draft

        override suspend fun saveDraft(draft: ExpenseDraft) {
            this.draft = draft
        }

        override suspend fun clearDraft() {
            draft = null
        }
    }

    private class FakeExpenseRepository(
        private val shouldFail: Boolean = false,
    ) : ExpenseRepository {
        var savedPayload: FinalizedExpensePayload? = null

        override suspend fun saveFinalizedExpense(payload: FinalizedExpensePayload): SavedShareLink {
            if (shouldFail) error("Worker unavailable")
            savedPayload = payload
            return SavedShareLink(
                expenseId = ExpenseId("expense-1"),
                shareLink = ShareLink(shareId = "share-1", publicUrl = "https://example.test/e/share-1"),
            )
        }
    }

    private class FakeGenerateGuestPasscodeUseCase(
        private val passcode: String,
    ) : GenerateGuestPasscodeUseCase {
        override fun generate(): String = passcode
    }

    private class FakeCalculateExpenseSummaryUseCase(
        private val summary: ExpenseSummary,
    ) : CalculateExpenseSummaryUseCase {
        override fun calculate(
            receipt: Receipt,
            participants: List<Participant>,
            payerId: ParticipantId,
            itemAssignments: List<com.dps.evenup.domain.expense.api.ItemAssignment>,
            feeAllocations: List<com.dps.evenup.domain.expense.api.FeeAllocation>,
        ): ExpenseSummary = summary
    }

    private class FakeValidateExpenseBeforeSaveUseCase(
        private val summary: ExpenseSummary,
        private val validationErrors: Set<FinalExpenseValidationError>,
    ) : ValidateExpenseBeforeSaveUseCase {
        override fun validateAndBuildPayload(draft: ExpenseDraft): FinalExpenseValidationResult {
            if (validationErrors.isNotEmpty()) {
                return FinalExpenseValidationResult.Invalid(validationErrors)
            }
            return FinalExpenseValidationResult.Valid(
                FinalizedExpensePayload(
                    draftId = draft.id,
                    receipt = draft.receipt,
                    participants = draft.participants,
                    payerId = draft.payerId,
                    itemAssignments = draft.itemAssignments,
                    feeAllocations = draft.feeAllocations,
                    summary = summary,
                ),
            )
        }
    }
}

private fun reviewExpenseDraft(currencyCode: String): ExpenseDraft {
    return ExpenseDraft(
        id = ExpenseDraftId("draft-1"),
        receipt = Receipt(
            merchantName = "Taberna",
            currencyCode = CurrencyCode(currencyCode),
            items = emptyList(),
            fees = emptyList(),
            total = MoneyMinor(10_000),
        ),
        participants = reviewExpenseParticipants(),
        payerId = ParticipantId("payer"),
        itemAssignments = emptyList(),
        feeAllocations = emptyList(),
    )
}

private fun reviewExpenseParticipants(): List<Participant> {
    return listOf(
        Participant(id = ParticipantId("payer"), name = "Kehn", creationOrder = 0),
        Participant(id = ParticipantId("storak"), name = "Storak", creationOrder = 1),
        Participant(id = ParticipantId("billy"), name = "Billy", creationOrder = 2),
        Participant(id = ParticipantId("danya"), name = "Danya", creationOrder = 3),
    )
}

private fun reviewExpenseSummary(
    payerShare: MoneyMinor = MoneyMinor(1_500),
    payerNetBalance: MoneyMinor = MoneyMinor(8_500),
): ExpenseSummary {
    return ExpenseSummary(
        receiptTotal = MoneyMinor(10_000),
        participantSummaries = listOf(
            ParticipantExpenseSummary(
                participantId = ParticipantId("payer"),
                assignedItemTotal = MoneyMinor(1_200),
                allocatedFeeTotal = MoneyMinor(300),
                personShare = payerShare,
                amountPaid = MoneyMinor(10_000),
                netBalance = payerNetBalance,
            ),
            ParticipantExpenseSummary(
                participantId = ParticipantId("storak"),
                assignedItemTotal = MoneyMinor(3_500),
                allocatedFeeTotal = MoneyMinor(500),
                personShare = MoneyMinor(4_000),
                amountPaid = MoneyMinor.Zero,
                netBalance = MoneyMinor(-4_000),
            ),
            ParticipantExpenseSummary(
                participantId = ParticipantId("billy"),
                assignedItemTotal = MoneyMinor(4_000),
                allocatedFeeTotal = MoneyMinor(500),
                personShare = MoneyMinor(4_500),
                amountPaid = MoneyMinor.Zero,
                netBalance = MoneyMinor(-4_500),
            ),
            ParticipantExpenseSummary(
                participantId = ParticipantId("danya"),
                assignedItemTotal = MoneyMinor.Zero,
                allocatedFeeTotal = MoneyMinor.Zero,
                personShare = MoneyMinor.Zero,
                amountPaid = MoneyMinor.Zero,
                netBalance = MoneyMinor.Zero,
            ),
        ),
        settlementRows = listOf(
            SettlementRow(
                fromParticipantId = ParticipantId("storak"),
                toParticipantId = ParticipantId("payer"),
                amount = MoneyMinor(4_000),
            ),
            SettlementRow(
                fromParticipantId = ParticipantId("billy"),
                toParticipantId = ParticipantId("payer"),
                amount = MoneyMinor(4_500),
            ),
        ),
    )
}

private fun reviewSettledExpenseSummary(): ExpenseSummary {
    return ExpenseSummary(
        receiptTotal = MoneyMinor(10_000),
        participantSummaries = listOf(
            ParticipantExpenseSummary(
                participantId = ParticipantId("payer"),
                assignedItemTotal = MoneyMinor(10_000),
                allocatedFeeTotal = MoneyMinor.Zero,
                personShare = MoneyMinor(10_000),
                amountPaid = MoneyMinor(10_000),
                netBalance = MoneyMinor.Zero,
            ),
            ParticipantExpenseSummary(
                participantId = ParticipantId("storak"),
                assignedItemTotal = MoneyMinor.Zero,
                allocatedFeeTotal = MoneyMinor.Zero,
                personShare = MoneyMinor.Zero,
                amountPaid = MoneyMinor.Zero,
                netBalance = MoneyMinor.Zero,
            ),
            ParticipantExpenseSummary(
                participantId = ParticipantId("billy"),
                assignedItemTotal = MoneyMinor.Zero,
                allocatedFeeTotal = MoneyMinor.Zero,
                personShare = MoneyMinor.Zero,
                amountPaid = MoneyMinor.Zero,
                netBalance = MoneyMinor.Zero,
            ),
            ParticipantExpenseSummary(
                participantId = ParticipantId("danya"),
                assignedItemTotal = MoneyMinor.Zero,
                allocatedFeeTotal = MoneyMinor.Zero,
                personShare = MoneyMinor.Zero,
                amountPaid = MoneyMinor.Zero,
                netBalance = MoneyMinor.Zero,
            ),
        ),
        settlementRows = emptyList(),
    )
}
