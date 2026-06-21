package com.dps.evenup.feature.expenseflow.impl.receiptreview

import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.ExpenseDraftId
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
import com.dps.evenup.domain.receipt.api.ReceiptValidationResult
import com.dps.evenup.domain.receipt.api.ValidateReceiptUseCase
import java.time.LocalDate
import org.junit.Assert.assertFalse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptReviewPresenterTest {
    @Test
    fun `add item opens draft and dismiss does not mutate items`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.AddItemClick)

        assertEquals(1, state.items.size)
        assertTrue(state.editDraft is ReceiptReviewEditDraft.Item)

        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemNameChanged("Cake"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemLineTotalChanged("4.50"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditDismissed)

        assertEquals(listOf("Pasta"), state.items.map { item -> item.name })
        assertEquals(null, state.editDraft)
    }

    @Test
    fun `item draft commits only after validation and calculates line total from unit price`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.AddItemClick)
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemNameChanged("Cake"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemQuantityChanged("3"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemUnitPriceChanged("2.50"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        val item = state.items.last()
        assertEquals("Cake", item.name)
        assertEquals("3", item.quantity)
        assertEquals("7.50", item.amount)
        assertEquals("17.50", state.calculatedTotalAmount)
        assertEquals("10.00", state.scannedReceiptTotalAmount)
        assertEquals(null, state.editDraft)
    }

    @Test
    fun `quantity one item draft exposes only item total state`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-1")))

        val draft = state.editDraft as ReceiptReviewEditDraft.Item
        assertFalse(draft.showsPriceEach)
        assertEquals(null, draft.averagePriceNote("EUR"))
    }

    @Test
    fun `quantity two item draft exposes price each and item total state`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-1")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemQuantityChanged("2"))

        val draft = state.editDraft as ReceiptReviewEditDraft.Item
        assertTrue(draft.showsPriceEach)
    }

    @Test
    fun `editing item line total updates subtotal and derived receipt total`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-1")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemLineTotalChanged("12.34"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertEquals("12.34", state.derivedSubtotalAmount)
        assertEquals("12.34", state.calculatedTotalAmount)
        assertEquals("10.00", state.scannedReceiptTotalAmount)
    }

    @Test
    fun `editing unit price updates line total and derived receipt total`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-1")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemQuantityChanged("3"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemUnitPriceChanged("2.50"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertEquals("7.50", state.items.single().amount)
        assertEquals("7.50", state.calculatedTotalAmount)
        assertEquals("10.00", state.scannedReceiptTotalAmount)
    }

    @Test
    fun `editing item total derives price each without changing authoritative item total`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-1")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemQuantityChanged("3"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemLineTotalChanged("10.00"))

        val draft = state.editDraft as ReceiptReviewEditDraft.Item
        assertEquals("3.33", draft.unitPrice)
        assertEquals("10.00", draft.lineTotal)
        assertEquals(ReceiptReviewMoneyField.ItemTotal, draft.lastEditedMoneyField)
    }

    @Test
    fun `changing quantity follows last edited money field`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-1")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemQuantityChanged("2"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemUnitPriceChanged("2.50"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemQuantityChanged("3"))

        var draft = state.editDraft as ReceiptReviewEditDraft.Item
        assertEquals("2.50", draft.unitPrice)
        assertEquals("7.50", draft.lineTotal)

        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemLineTotalChanged("10.00"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemQuantityChanged("4"))

        draft = state.editDraft as ReceiptReviewEditDraft.Item
        assertEquals("2.50", draft.unitPrice)
        assertEquals("10.00", draft.lineTotal)
        assertEquals(ReceiptReviewMoneyField.ItemTotal, draft.lastEditedMoneyField)
    }

    @Test
    fun `non divisible line total preserves saved total price exactly and exposes helper note`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = presenter(repository = repository)
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-1")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemQuantityChanged("3"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemLineTotalChanged("10.00"))

        val draft = state.editDraft as ReceiptReviewEditDraft.Item
        assertEquals("3.33", draft.unitPrice)
        assertEquals(
            "Average price each is €3.33 because the item total does not split evenly.",
            draft.averagePriceNote("EUR"),
        )

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)
        val result = presenter.saveDraft(state)

        assertEquals(SaveReceiptReviewResult.Saved, result)
        assertEquals(MoneyMinor(1_000), repository.draft?.receipt?.items?.single()?.totalPrice)
        assertEquals(MoneyMinor(1_000), repository.draft?.receipt?.total)
    }

    @Test
    fun `other adjustment requires name and commits type aware label`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.AddFeeClick)
        state = presenter.reduce(state, ReceiptReviewUiEvent.FeeTypeChanged(FeeType.Other))
        state = presenter.reduce(state, ReceiptReviewUiEvent.FeeAmountChanged("1.25"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertTrue(state.fieldErrors.containsKey("fee_label_draft"))
        assertTrue(state.editDraft is ReceiptReviewEditDraft.Fee)

        state = presenter.reduce(state, ReceiptReviewUiEvent.FeeLabelChanged("Bag fee"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        val fee = state.fees.single()
        assertEquals(FeeType.Other, fee.type)
        assertEquals("Bag fee", fee.displayLabel)
        assertEquals("1.25", fee.amount)
    }

    @Test
    fun `editing and deleting adjustment updates derived receipt total`() = runBlocking {
        val presenter = presenter(
            draft = draft().copy(
                receipt = draft().receipt.copy(
                    fees = listOf(ReceiptFee(FeeId("fee-1"), FeeType.Tip, "Tip", MoneyMinor(200))),
                    total = MoneyMinor(1_200),
                ),
            ),
        )
        var state = presenter.load()

        assertEquals("12.00", state.scannedReceiptTotalAmount)
        assertEquals("12.00", state.calculatedTotalAmount)

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Fee("fee-1")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.FeeAmountChanged("3.50"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertEquals("12.00", state.scannedReceiptTotalAmount)
        assertEquals("13.50", state.calculatedTotalAmount)

        state = presenter.reduce(state, ReceiptReviewUiEvent.RemoveFeeClick("fee-1"))

        assertEquals("12.00", state.scannedReceiptTotalAmount)
        assertEquals("10.00", state.calculatedTotalAmount)
        assertTrue(state.fees.isEmpty())
    }

    @Test
    fun `deleting item updates derived receipt total`() = runBlocking {
        val presenter = presenter(
            draft = draft().copy(
                receipt = draft().receipt.copy(
                    items = listOf(
                        validItem("item-1", "Pasta", 1_000),
                        validItem("item-2", "Cake", 450),
                    ),
                    total = MoneyMinor(1_450),
                ),
            ),
        )
        var state = presenter.load()

        assertEquals("14.50", state.scannedReceiptTotalAmount)
        assertEquals("14.50", state.calculatedTotalAmount)

        state = presenter.reduce(state, ReceiptReviewUiEvent.RemoveItemClick("item-2"))

        assertEquals("14.50", state.scannedReceiptTotalAmount)
        assertEquals("10.00", state.calculatedTotalAmount)
        assertEquals(listOf("Pasta"), state.items.map { item -> item.name })
    }

    @Test
    fun `matched scanned total shows single total label and matched status`() = runBlocking {
        val presenter = presenter()
        val state = presenter.load()

        assertEquals("€10.00", state.summaryTotalLabel)
        assertEquals("Matches receipt", state.summaryStatusLabel)
        assertEquals("Matches scanned receipt", state.reconciliation.message)
        assertEquals(ReceiptReviewReconciliationType.Matched, state.reconciliation.type)
        assertFalse(state.reconciliation.isIssue)
    }

    @Test
    fun `mismatched scanned total keeps scanned value and exposes difference`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-1")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemLineTotalChanged("11.00"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertEquals("11.00", state.calculatedTotalAmount)
        assertEquals("10.00", state.scannedReceiptTotalAmount)
        assertEquals("€11.00", state.summaryTotalLabel)
        assertEquals("Needs review", state.summaryStatusLabel)
        assertEquals("Total differs · Review item amounts", state.statusLabel)
        assertEquals(ReceiptReviewReconciliationType.Mismatch, state.reconciliation.type)
        assertEquals("Receipt says €10.00 · Difference €1.00", state.reconciliation.message)
    }

    @Test
    fun `missing scanned total shows review warning and opens total check`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load().copy(scannedReceiptTotalAmount = "")

        assertEquals(ReceiptReviewReconciliationType.MissingScannedTotal, state.reconciliation.type)
        assertEquals("Receipt total was not detected. Confirm before continuing.", state.reconciliation.message)

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.TotalCheck))

        assertTrue(state.editDraft is ReceiptReviewEditDraft.TotalCheck)
    }

    @Test
    fun `editing receipt total updates reconciliation and saved receipt total`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = presenter(repository = repository)
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-1")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemLineTotalChanged("12.34"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertTrue(state.reconciliation.isIssue)

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.ReceiptTotal))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ReceiptTotalChanged("12.34"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertEquals("12.34", state.scannedReceiptTotalAmount)
        assertFalse(state.reconciliation.isIssue)
        assertEquals(SaveReceiptReviewResult.Saved, presenter.saveDraft(state))
        assertEquals(MoneyMinor(1_234), repository.draft?.receipt?.total)
    }

    @Test
    fun `mismatched totals block save and request summary section`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = presenter(repository = repository)
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-1")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemLineTotalChanged("12.34"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        val validated = presenter.validateVisibleState(state)
        val result = presenter.saveDraft(validated)

        assertEquals(ReceiptReviewSection.Summary, validated.firstBlockingSection)
        assertTrue(validated.fieldErrors["summary"].orEmpty().contains("Receipt says"))
        assertTrue(result is SaveReceiptReviewResult.Invalid)
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun `Rissol and Octopus mismatch highlights two suggested item corrections`() = runBlocking {
        val presenter = presenter(draft = rissolOctopusDraft())

        val state = presenter.load()

        assertEquals("2 likely item errors found", state.statusLabel)
        assertEquals("Review 2 suggested corrections", state.suggestedCorrectionActionLabel)
        assertEquals("Receipt says €65.25 · Difference €1.10", state.reconciliation.message)
        assertEquals(
            listOf(
                "Receipt likely says €5.60 · Difference €0.20",
                "Receipt likely says €9.00 · Difference €0.90",
                null,
            ),
            state.items.map { item -> item.suggestedCorrectionNote(state.currencyCode) },
        )
    }

    @Test
    fun `applying one suggested correction reduces mismatch and leaves remaining item highlighted`() = runBlocking {
        val presenter = presenter(draft = rissolOctopusDraft())
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-1")))
        val draft = state.editDraft as ReceiptReviewEditDraft.Item
        assertEquals(560L, draft.suggestedCorrection?.suggestedAmountMinor)

        state = presenter.reduce(state, ReceiptReviewUiEvent.UseSuggestedItemCorrectionClick)
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertEquals("66.15", state.calculatedTotalAmount)
        assertEquals("1 likely item error found", state.statusLabel)
        assertEquals(listOf(null, 900L, null), state.items.map { item -> item.suggestedCorrection?.suggestedAmountMinor })
    }

    @Test
    fun `applying all suggested corrections clears mismatch and highlights`() = runBlocking {
        val presenter = presenter(draft = rissolOctopusDraft())
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-1")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.UseSuggestedItemCorrectionClick)
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-2")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.UseSuggestedItemCorrectionClick)
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertEquals("65.25", state.calculatedTotalAmount)
        assertEquals("65.25", state.scannedReceiptTotalAmount)
        assertEquals("Matches receipt", state.summaryStatusLabel)
        assertFalse(state.hasWarningStatus)
        assertEquals(listOf(null, null, null), state.items.map { item -> item.suggestedCorrection })
    }

    @Test
    fun `suggested mismatch blocks save and requests first suspected item`() = runBlocking {
        val repository = FakeExpenseDraftRepository(rissolOctopusDraft())
        val presenter = presenter(repository = repository)
        val state = presenter.load()

        val validated = presenter.validateVisibleState(state)
        val result = presenter.saveDraft(validated)

        assertEquals(ReceiptReviewSection.Items, validated.firstBlockingSection)
        assertEquals("item-1", validated.firstBlockingItemId)
        assertTrue(validated.fieldErrors["summary"].orEmpty().contains("Receipt says"))
        assertTrue(result is SaveReceiptReviewResult.Invalid)
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun `one corrected item produces compact header and summary review copy`() = runBlocking {
        val presenter = presenter(
            draft = draft().copy(
                receipt = draft().receipt.copy(
                    items = listOf(
                        validItem("item-1", "Rissol", 1_000),
                    ),
                    parseMetadata = ReceiptParseMetadata(
                        corrections = listOf(
                            ReceiptParseCorrection(
                                field = "items[0].totalPriceMinor",
                                itemName = "Rissol",
                                from = MoneyMinor(1_100),
                                to = MoneyMinor(1_000),
                                reason = "Corrected locally to match printed subtotal.",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val state = presenter.load()

        assertEquals("1 item needs review", state.statusLabel)
        assertEquals("Corrected from €11.00 · Check amount", state.items.single().reviewNote)
        assertEquals("Review highlighted item before continuing", state.reconciliation.message)
        assertEquals(ReceiptReviewReconciliationType.ReviewItems, state.reconciliation.type)
    }

    @Test
    fun `multiple highlighted items produce plural compact header and summary copy`() = runBlocking {
        val presenter = presenter(
            draft = draft().copy(
                receipt = draft().receipt.copy(
                    items = listOf(
                        validItem(
                            id = "item-1",
                            name = "Rissol",
                            amountMinor = 500,
                            parseMetadata = ReceiptItemParseMetadata(needsReview = true),
                        ),
                        validItem(
                            id = "item-2",
                            name = "Bifana",
                            amountMinor = 500,
                            parseMetadata = ReceiptItemParseMetadata(confidence = 0.62),
                        ),
                    ),
                ),
            ),
        )
        val state = presenter.load()

        assertEquals("2 items need review", state.statusLabel)
        assertEquals("Review highlighted items before continuing", state.reconciliation.message)
        assertEquals(listOf("Check amount", "Low scan confidence · Check amount"), state.items.map { it.reviewNote })
    }

    @Test
    fun `review highlighted items action scrolls to items and closes sheet`() = runBlocking {
        val presenter = presenter()
        val loaded = presenter.load()
        val state = loaded.copy(
            editDraft = ReceiptReviewEditDraft.TotalCheck,
            items = loaded.items.map { item -> item.copy(needsReview = true) },
        )

        val nextState = presenter.reduce(state, ReceiptReviewUiEvent.ReviewHighlightedItemsClick)

        assertEquals(null, nextState.editDraft)
        assertEquals(ReceiptReviewSection.Items, nextState.firstBlockingSection)
        assertEquals(1, nextState.validationRequestId)
    }

    @Test
    fun `money input normalizes on commit and rejects over max value`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.AddItemClick)
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemNameChanged("Cake"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemLineTotalChanged("8"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertEquals("8.00", state.items.last().amount)

        state = presenter.reduce(state, ReceiptReviewUiEvent.AddFeeClick)
        state = presenter.reduce(state, ReceiptReviewUiEvent.FeeAmountChanged("100000.00"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertEquals("Enter a positive amount.", state.fieldErrors["fee_amount_draft"])
        assertTrue(state.editDraft is ReceiptReviewEditDraft.Fee)
    }

    @Test
    fun `future date draft stays open with field error`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Date))
        state = presenter.reduce(state, ReceiptReviewUiEvent.DateChanged(LocalDate.now().plusDays(1).toString()))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertEquals("Date cannot be in the future.", state.fieldErrors["date"])
        assertTrue(state.editDraft is ReceiptReviewEditDraft.Date)
    }

    @Test
    fun `invalid visible state returns field errors and first blocking section without saving`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = presenter(repository = repository)
        val state = presenter.load().copy(merchantName = "")

        val validated = presenter.validateVisibleState(state)
        val result = presenter.saveDraft(validated)

        assertEquals("Merchant is required.", validated.fieldErrors["merchant"])
        assertEquals(ReceiptReviewSection.Details, validated.firstBlockingSection)
        assertEquals(1, validated.validationRequestId)
        assertTrue(result is SaveReceiptReviewResult.Invalid)
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun `status click requests first issue focus`() = runBlocking {
        val presenter = presenter()
        val state = presenter.load().copy(merchantName = "")

        val validated = presenter.reduce(state, ReceiptReviewUiEvent.StatusClick)

        assertEquals(ReceiptReviewSection.Details, validated.firstBlockingSection)
        assertEquals(1, validated.validationRequestId)
        assertFalse(validated.fieldErrors.isEmpty())
    }

    @Test
    fun `item metadata maps to review note and confirmation clears unresolved state`() = runBlocking {
        val repository = FakeExpenseDraftRepository(
            draft().copy(
                receipt = draft().receipt.copy(
                    items = listOf(
                        validItem(
                            id = "item-1",
                            name = "Dessert",
                            amountMinor = 1_000,
                            parseMetadata = ReceiptItemParseMetadata(
                                confidence = 0.64,
                                candidates = listOf(MoneyMinor(1_000), MoneyMinor(1_100)),
                                needsReview = true,
                            ),
                        ),
                    ),
                    parseMetadata = ReceiptParseMetadata(
                        corrections = listOf(
                            ReceiptParseCorrection(
                                field = "items[0].totalPriceMinor",
                                itemName = "Dessert",
                                from = MoneyMinor(1_100),
                                to = MoneyMinor(1_000),
                                reason = "Corrected locally to match printed subtotal.",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val presenter = presenter(repository = repository)
        var state = presenter.load()

        assertTrue(state.items.single().needsReview)
        assertEquals("Corrected from €11.00 · Check amount", state.items.single().reviewNote)
        assertEquals("1 item needs review", state.statusLabel)
        assertTrue(state.reconciliation.isIssue)

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-1")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertFalse(state.items.single().needsReview)
        assertEquals(null, state.items.single().reviewNote)
        assertFalse(state.reconciliation.isIssue)
        assertEquals(SaveReceiptReviewResult.Saved, presenter.saveDraft(state))
        assertFalse(repository.draft?.receipt?.items?.single()?.parseMetadata?.needsReview ?: true)
        assertTrue(repository.draft?.receipt?.items?.single()?.parseMetadata?.candidates.orEmpty().isEmpty())
        assertTrue(repository.draft?.receipt?.parseMetadata?.corrections.orEmpty().isEmpty())
    }

    private fun presenter(
        draft: ExpenseDraft = draft(),
        repository: FakeExpenseDraftRepository = FakeExpenseDraftRepository(draft),
    ): ReceiptReviewPresenter = ReceiptReviewPresenter(
        draftRepository = repository,
        validateReceipt = AlwaysValidReceipt,
    )

    private fun draft(): ExpenseDraft = ExpenseDraft(
        id = ExpenseDraftId("draft-1"),
        receipt = Receipt(
            merchantName = "Bella Roma",
            currencyCode = CurrencyCode("EUR"),
            transactionDateLabel = LocalDate.now().toString(),
            items = listOf(
                validItem("item-1", "Pasta", 1_000),
            ),
            fees = emptyList(),
            total = MoneyMinor(1_000),
        ),
        participants = emptyList(),
        payerId = ParticipantId("payer"),
        itemAssignments = emptyList(),
        feeAllocations = emptyList(),
    )

    private fun rissolOctopusDraft(): ExpenseDraft {
        return draft().copy(
            receipt = draft().receipt.copy(
                items = listOf(
                    validItem("item-1", "Rissol", 580),
                    validItem("item-2", "Octopus Peppers", 990),
                    validItem("item-3", "Coffee", 5_065),
                ),
                total = MoneyMinor(6_525),
            ),
        )
    }

    private fun validItem(
        id: String,
        name: String,
        amountMinor: Long,
        quantity: Int = 1,
        parseMetadata: ReceiptItemParseMetadata = ReceiptItemParseMetadata(),
    ): ReceiptItem = ReceiptItem(
        id = ReceiptItemId(id),
        name = name,
        quantity = Quantity(quantity),
        unitPrice = MoneyMinor(amountMinor / quantity),
        totalPrice = MoneyMinor(amountMinor),
        parseMetadata = parseMetadata,
    )

    private class FakeExpenseDraftRepository(
        var draft: ExpenseDraft?,
    ) : ExpenseDraftRepository {
        var saveCount: Int = 0

        override suspend fun getDraft(): ExpenseDraft? = draft

        override suspend fun saveDraft(draft: ExpenseDraft) {
            saveCount += 1
            this.draft = draft
        }

        override suspend fun clearDraft() {
            draft = null
        }
    }

    private object AlwaysValidReceipt : ValidateReceiptUseCase {
        override fun validate(receipt: Receipt): ReceiptValidationResult = ReceiptValidationResult.Valid
    }
}
