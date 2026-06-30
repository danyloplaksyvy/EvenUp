package com.dps.evenup.feature.expenseflow.impl.receiptreview

import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.ExpenseDraftId
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.CurrencyCode
import com.dps.evenup.domain.receipt.api.FeeId
import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.NormalizeReceiptUseCase
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
    fun `other fee requires name and commits type aware label`() = runBlocking {
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
    fun `editing and deleting fee updates derived receipt total`() = runBlocking {
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
    fun `parsed discount is shown separately and saved as negative amount`() = runBlocking {
        val repository = FakeExpenseDraftRepository(
            draft().copy(
                receipt = draft().receipt.copy(
                    fees = listOf(ReceiptFee(FeeId("discount"), FeeType.Discount, "Promo", MoneyMinor(-100))),
                    total = MoneyMinor(900),
                ),
            ),
        )
        val presenter = presenter(repository = repository)
        val state = presenter.load()

        assertEquals("0.00", state.derivedFeesAmount)
        assertEquals("1.00", state.derivedDiscountsAmount)
        assertEquals("-€1.00", state.discountsTotalLabel)
        assertEquals("9.00", state.calculatedTotalAmount)
        assertEquals("1 adjustment", state.adjustmentCountLabel)
        assertEquals("Promo", state.discounts.single().displayLabel)

        val result = presenter.saveDraft(state)

        assertEquals(SaveReceiptReviewResult.Saved, result)
        assertEquals(FeeType.Discount, repository.draft?.receipt?.fees?.single()?.type)
        assertEquals("Promo", repository.draft?.receipt?.fees?.single()?.label)
        assertEquals(MoneyMinor(-100), repository.draft?.receipt?.fees?.single()?.amount)
    }

    @Test
    fun `adjustment count includes fees and discounts`() = runBlocking {
        val presenter = presenter(
            draft = draft().copy(
                receipt = draft().receipt.copy(
                    fees = listOf(
                        ReceiptFee(FeeId("tip"), FeeType.Tip, "Tip", MoneyMinor(200)),
                        ReceiptFee(FeeId("discount"), FeeType.Discount, "Promo", MoneyMinor(-100)),
                    ),
                    total = MoneyMinor(1_100),
                ),
            ),
        )

        val state = presenter.load()

        assertEquals("2 adjustments", state.adjustmentCountLabel)
        assertEquals("2.00", state.derivedFeesAmount)
        assertEquals("1.00", state.derivedDiscountsAmount)
        assertEquals(listOf("Tip", "Promo"), state.fees.map { fee -> fee.displayLabel })
    }

    @Test
    fun `editing discount preserves custom label when saved`() = runBlocking {
        val repository = FakeExpenseDraftRepository(
            draft().copy(
                receipt = draft().receipt.copy(
                    fees = listOf(ReceiptFee(FeeId("discount"), FeeType.Discount, "Promo", MoneyMinor(-100))),
                    total = MoneyMinor(900),
                ),
            ),
        )
        val presenter = presenter(repository = repository)
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Fee("discount")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.FeeAmountChanged("1.50"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertEquals("Promo", state.fees.single().displayLabel)

        state = presenter.reduce(state, ReceiptReviewUiEvent.KeepCalculatedTotalClick)

        assertEquals(SaveReceiptReviewResult.Saved, presenter.saveDraft(state))
        assertEquals("Promo", repository.draft?.receipt?.fees?.single()?.label)
        assertEquals(MoneyMinor(-150), repository.draft?.receipt?.fees?.single()?.amount)
    }

    @Test
    fun `new adjustment can be created as discount`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.AddFeeClick)
        state = presenter.reduce(state, ReceiptReviewUiEvent.FeeTypeChanged(FeeType.Discount))
        state = presenter.reduce(state, ReceiptReviewUiEvent.FeeLabelChanged("Promo"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.FeeAmountChanged("2.00"))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        val adjustment = state.fees.single()
        assertEquals(FeeType.Discount, adjustment.type)
        assertEquals("Promo", adjustment.displayLabel)
        assertEquals("-€2.00", adjustment.amountLabel(state.currencyCode))
        assertEquals("1 adjustment", state.adjustmentCountLabel)
    }

    @Test
    fun `normalized duplicate tax does not block continue`() = runBlocking {
        val duplicateTax = ReceiptFee(FeeId("tax"), FeeType.Tax, "Tax", MoneyMinor(6_195))
        val discount = ReceiptFee(FeeId("discount"), FeeType.Discount, "Portal Reserva 30%", MoneyMinor(-2_655))
        val presenter = presenter(
            draft = draft().copy(
                receipt = draft().receipt.copy(
                    merchantName = "Crowne Plaza Barcelona - Fira Center",
                    items = listOf(
                        validItem("item-1", "Burrata", 1_600),
                        validItem("item-2", "Pan de Coca", 350),
                        validItem("item-3", "Solomillo a la Sal", 4_400, quantity = 2),
                        validItem("item-4", "100% Chocolate fondant", 850),
                        validItem("item-5", "Agua 1/2", 450),
                        validItem("item-6", "Copa Aphrodisiaque T.", 1_200, quantity = 2),
                    ),
                    fees = listOf(duplicateTax, discount),
                    subtotal = MoneyMinor(8_850),
                    total = MoneyMinor(6_195),
                ),
            ),
            normalizeReceipt = RemoveDuplicateTaxNormalizeReceipt,
        )

        val state = presenter.load()

        assertEquals(listOf("Portal Reserva 30%"), state.fees.map { fee -> fee.displayLabel })
        assertEquals("61.95", state.calculatedTotalAmount)
        assertEquals("AI found 6 items", state.statusLabel)
        assertTrue(state.canContinue)
        assertFalse(state.hasWarningStatus)
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
        assertEquals("", state.summaryStatusLabel)
        assertEquals("AI found 1 item", state.statusLabel)
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
        assertEquals("Total differs", state.summaryStatusLabel)
        assertEquals("Total differs by €1.00", state.statusLabel)
        assertEquals(ReceiptReviewReconciliationType.Mismatch, state.reconciliation.type)
        assertEquals("Total differs by €1.00", state.reconciliation.message)
        assertEquals("Review total", state.footerState.label)
        assertEquals(ReceiptReviewFooterAction.ReviewIssue("total_mismatch"), state.footerState.action)
        assertEquals(null, state.footerState.helperText)
        assertFalse(state.canContinue)
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
        assertTrue(validated.fieldErrors["summary"].orEmpty().contains("Total differs"))
        assertTrue(result is SaveReceiptReviewResult.Invalid)
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun `Rissol and Octopus mismatch highlights two suggested item corrections`() = runBlocking {
        val presenter = presenter(draft = rissolOctopusDraft())

        val state = presenter.load()

        assertEquals("2 likely item errors found", state.statusLabel)
        assertEquals("Review 2 suggested corrections", state.suggestedCorrectionActionLabel)
        assertEquals("Total differs by €1.10", state.reconciliation.message)
        assertEquals("Review item amounts", state.footerState.label)
        assertEquals(ReceiptReviewFooterAction.ReviewIssue("total_mismatch"), state.footerState.action)
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
        assertEquals("", state.summaryStatusLabel)
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
        assertTrue(validated.fieldErrors["summary"].orEmpty().contains("Total differs"))
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

        assertEquals("Rissol needs review", state.statusLabel)
        assertEquals("Amount was corrected to match subtotal", state.items.single().reviewNote)
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

        assertEquals("2 issues need review", state.statusLabel)
        assertEquals("Review highlighted items before continuing", state.reconciliation.message)
        assertEquals(listOf("Check price from receipt", "Low-confidence item name or amount"), state.items.map { it.reviewNote })
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
        assertTrue(validated.editDraft is ReceiptReviewEditDraft.Merchant)
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
        assertEquals("Amount was corrected to match subtotal", state.items.single().reviewNote)
        assertEquals("Dessert needs review", state.statusLabel)
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

    @Test
    fun `matched total with subtotal parser warning does not create active issue`() = runBlocking {
        val presenter = presenter(
            draft = draft().copy(
                receipt = draft().receipt.copy(
                    parseMetadata = ReceiptParseMetadata(
                        reviewWarnings = listOf("Item amounts should be checked against the printed subtotal."),
                    ),
                ),
            ),
        )
        val state = presenter.load()

        assertTrue(state.issues.isEmpty())
        assertFalse(state.hasWarningStatus)
        assertTrue(state.canContinue)

        val nextState = presenter.reduce(state, ReceiptReviewUiEvent.StatusClick)

        assertEquals(null, nextState.editDraft)
        assertFalse(nextState.issueNavigatorVisible)
        assertEquals(null, nextState.firstBlockingSection)
    }

    @Test
    fun `confirming final flagged item clears stale parser warnings and saved metadata`() = runBlocking {
        val repository = FakeExpenseDraftRepository(
            draft().copy(
                receipt = draft().receipt.copy(
                    items = listOf(
                        validItem(
                            id = "item-1",
                            name = "Dessert",
                            amountMinor = 1_000,
                            parseMetadata = ReceiptItemParseMetadata(
                                candidates = listOf(MoneyMinor(1_000), MoneyMinor(1_100)),
                                needsReview = true,
                            ),
                        ),
                    ),
                    parseMetadata = ReceiptParseMetadata(
                        reviewWarnings = listOf(
                            "Dessert amount may be wrong.",
                            "Check item amounts against the subtotal.",
                        ),
                    ),
                ),
            ),
        )
        val presenter = presenter(repository = repository)
        var state = presenter.load()

        assertFalse(state.canContinue)
        assertEquals("Dessert needs review", state.statusLabel)

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-1")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertEquals("", state.summaryStatusLabel)
        assertFalse(state.hasWarningStatus)
        assertTrue(state.canContinue)
        assertEquals(SaveReceiptReviewResult.Saved, presenter.saveDraft(state))
        assertTrue(repository.draft?.receipt?.parseMetadata?.reviewWarnings.orEmpty().isEmpty())
    }

    @Test
    fun `quantity line total correction appears reviewable and clears after confirmation`() = runBlocking {
        val presenter = presenter(
            draft = draft().copy(
                receipt = draft().receipt.copy(
                    merchantName = "Crowne Plaza Barcelona - Fira Center",
                    items = listOf(
                        validItem("item-1", "Burrata", 1_600),
                        validItem("item-2", "Pan de Coca", 350),
                        validItem(
                            id = "item-3",
                            name = "Solomillo a la Sal",
                            amountMinor = 4_400,
                            quantity = 2,
                            parseMetadata = ReceiptItemParseMetadata(
                                candidates = listOf(MoneyMinor(4_400), MoneyMinor(2_200)),
                                needsReview = true,
                            ),
                        ),
                        validItem("item-4", "100% Chocolate fondant", 850),
                        validItem("item-5", "Agua 1/2", 450),
                        validItem("item-6", "Copa Aphrodisiaque T.", 1_200, quantity = 2),
                    ),
                    fees = listOf(
                        ReceiptFee(FeeId("discount"), FeeType.Discount, "Portal Reserva 30%", MoneyMinor(-2_655)),
                    ),
                    total = MoneyMinor(6_195),
                    parseMetadata = ReceiptParseMetadata(
                        corrections = listOf(
                            ReceiptParseCorrection(
                                field = "items[2].totalPriceMinor",
                                itemName = "Solomillo a la Sal",
                                from = MoneyMinor(2_200),
                                to = MoneyMinor(4_400),
                                reason = "Corrected quantity line total to match expected item subtotal.",
                            ),
                        ),
                    ),
                ),
            ),
        )
        var state = presenter.load()

        assertEquals("Solomillo a la Sal needs review", state.statusLabel)
        assertEquals("Review highlighted item before continuing", state.reconciliation.message)
        assertFalse(state.canContinue)

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-3")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertTrue(state.canContinue)
        assertEquals("", state.summaryStatusLabel)
        assertFalse(state.hasWarningStatus)
    }

    @Test
    fun `multiple issues show issue navigator from status click`() = runBlocking {
        val presenter = presenter()
        val state = presenter.load().copy(
            merchantName = "",
            scannedReceiptTotalAmount = "11.00",
        )

        val nextState = presenter.reduce(state, ReceiptReviewUiEvent.StatusClick)

        assertTrue(nextState.issueNavigatorVisible)
        assertEquals(2, nextState.blockingIssues.size)
        assertEquals(listOf("Merchant is required", "Total differs by €1.00"), nextState.blockingIssues.map { it.title })
        assertEquals("Review issues", state.footerState.label)
        assertEquals(ReceiptReviewFooterAction.ReviewIssues, state.footerState.action)
    }

    @Test
    fun `selecting item issue opens item draft and requests row focus`() = runBlocking {
        val presenter = presenter(
            draft = draft().copy(
                receipt = draft().receipt.copy(
                    items = listOf(
                        validItem(
                            id = "item-1",
                            name = "Dessert",
                            amountMinor = 1_000,
                            parseMetadata = ReceiptItemParseMetadata(needsReview = true),
                        ),
                    ),
                ),
            ),
        )
        val state = presenter.load()

        val nextState = presenter.reduce(state.copy(issueNavigatorVisible = true), ReceiptReviewUiEvent.IssueSelected("item_item-1"))

        assertFalse(nextState.issueNavigatorVisible)
        assertEquals(ReceiptReviewSection.Items, nextState.firstBlockingSection)
        assertEquals("item-1", nextState.firstBlockingItemId)
        assertTrue(nextState.editDraft is ReceiptReviewEditDraft.Item)
    }

    @Test
    fun `item edit draft exposes confirm amount CTA when flagged value is unchanged`() = runBlocking {
        val presenter = presenter(
            draft = draft().copy(
                receipt = draft().receipt.copy(
                    items = listOf(
                        validItem(
                            id = "item-1",
                            name = "Dessert",
                            amountMinor = 1_000,
                            parseMetadata = ReceiptItemParseMetadata(needsReview = true),
                        ),
                    ),
                ),
            ),
        )
        var state = presenter.load()

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-1")))

        val draft = state.editDraft as ReceiptReviewEditDraft.Item
        assertEquals("Confirm amount", draft.primaryActionLabel)

        state = presenter.reduce(state, ReceiptReviewUiEvent.ItemLineTotalChanged("10.01"))

        assertEquals("Save changes", (state.editDraft as ReceiptReviewEditDraft.Item).primaryActionLabel)
    }

    @Test
    fun `keep calculated total confirms calculated total and clears mismatch`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load().copy(scannedReceiptTotalAmount = "11.00")

        assertTrue(state.reconciliation.isIssue)

        state = presenter.reduce(state, ReceiptReviewUiEvent.KeepCalculatedTotalClick)

        assertEquals("10.00", state.scannedReceiptTotalAmount)
        assertTrue(state.receiptTotalConfirmedByUser)
        assertFalse(state.reconciliation.isIssue)
        assertEquals("Receipt total confirmed", state.statusLabel)
    }

    @Test
    fun `use receipt total adds positive fee when receipt total is higher`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load().copy(scannedReceiptTotalAmount = "11.25")

        state = presenter.reduce(state, ReceiptReviewUiEvent.UseReceiptTotalClick)

        assertEquals("1.25", state.fees.single().amount)
        assertEquals("Receipt total fee", state.fees.single().displayLabel)
        assertEquals("11.25", state.calculatedTotalAmount)
        assertFalse(state.reconciliation.isIssue)
    }

    @Test
    fun `use receipt total keeps lower unsafe mismatch blocking`() = runBlocking {
        val presenter = presenter()
        val state = presenter.load().copy(scannedReceiptTotalAmount = "9.00")

        val nextState = presenter.reduce(state, ReceiptReviewUiEvent.UseReceiptTotalClick)

        assertEquals("Review item amounts or edit the receipt total to resolve the difference.", nextState.fieldErrors["summary"])
        assertEquals(ReceiptReviewSection.Summary, nextState.firstBlockingSection)
        assertTrue(nextState.reconciliation.isIssue)
    }

    @Test
    fun `date label displays readable date without raw iso timestamp`() = runBlocking {
        val presenter = presenter(draft = draft().copy(receipt = draft().receipt.copy(transactionDateLabel = "2018-11-03T16:39:00")))

        val state = presenter.load()

        assertEquals("2018-11-03T16:39:00", state.dateLabel)
        assertEquals("3 Nov 2018", state.dateDisplayLabel)
    }

    @Test
    fun `currency warning becomes actionable currency issue`() = runBlocking {
        val repository = FakeExpenseDraftRepository(
            draft = draft().copy(
                receipt = draft().receipt.copy(
                    parseMetadata = ReceiptParseMetadata(reviewWarnings = listOf("Currency was uncertain.")),
                ),
            ),
        )
        val presenter = presenter(repository = repository)

        var state = presenter.load()

        assertEquals(ReceiptReviewIssueKind.UncertainCurrency, state.issues.single().kind)
        assertEquals("Check receipt currency", state.issues.single().title)
        assertEquals(ReceiptReviewEditTarget.Currency, (state.issues.single().target as ReceiptReviewIssueTarget.Details).editTarget)

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Currency))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertTrue(state.currencyConfirmedByUser)
        assertTrue(state.issues.isEmpty())
        assertFalse(state.hasWarningStatus)
        assertEquals(SaveReceiptReviewResult.Saved, presenter.saveDraft(state))
        assertTrue(repository.draft?.receipt?.parseMetadata?.reviewWarnings.orEmpty().isEmpty())
    }

    @Test
    fun `continue state follows blocking issues`() = runBlocking {
        val presenter = presenter(
            draft = draft().copy(
                receipt = draft().receipt.copy(
                    items = listOf(
                        validItem(
                            id = "item-1",
                            name = "Dessert",
                            amountMinor = 1_000,
                            parseMetadata = ReceiptItemParseMetadata(needsReview = true),
                        ),
                    ),
                ),
            ),
        )
        var state = presenter.load()

        assertFalse(state.canContinue)
        assertEquals("Review item", state.footerState.label)
        assertEquals(ReceiptReviewFooterAction.ReviewIssue("item_item-1"), state.footerState.action)
        assertEquals(null, state.footerState.helperText)

        state = presenter.reduce(state, ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item("item-1")))
        state = presenter.reduce(state, ReceiptReviewUiEvent.EditCommitClick)

        assertTrue(state.canContinue)
        assertEquals("Continue", state.footerState.label)
        assertEquals(ReceiptReviewFooterAction.Continue, state.footerState.action)
        assertEquals(null, state.footerState.helperText)
    }

    @Test
    fun `valid and saving states map to compact footer actions`() = runBlocking {
        val presenter = presenter()
        val state = presenter.load()

        assertTrue(state.canContinue)
        assertEquals("Continue", state.footerState.label)
        assertEquals(ReceiptReviewFooterAction.Continue, state.footerState.action)

        val savingState = state.copy(isSaving = true)

        assertFalse(savingState.canContinue)
        assertEquals("Saving...", savingState.footerState.label)
        assertEquals(ReceiptReviewFooterAction.Disabled, savingState.footerState.action)
        assertFalse(savingState.footerState.enabled)
    }

    @Test
    fun `invalid fee maps to review fee footer action`() = runBlocking {
        val presenter = presenter(
            draft = draft().copy(
                receipt = draft().receipt.copy(
                    fees = listOf(ReceiptFee(FeeId("fee-1"), FeeType.Tip, "Tip", MoneyMinor(0))),
                ),
            ),
        )
        val state = presenter.load()

        assertFalse(state.canContinue)
        assertEquals("Review fee", state.footerState.label)
        assertEquals(ReceiptReviewFooterAction.ReviewIssue("fee_fee-1"), state.footerState.action)

        val nextState = presenter.reduce(state, ReceiptReviewUiEvent.IssueSelected("fee_fee-1"))

        assertEquals(ReceiptReviewSection.Adjustments, nextState.firstBlockingSection)
        assertTrue(nextState.editDraft is ReceiptReviewEditDraft.Fee)
    }

    private fun presenter(
        draft: ExpenseDraft = draft(),
        repository: FakeExpenseDraftRepository = FakeExpenseDraftRepository(draft),
        normalizeReceipt: NormalizeReceiptUseCase = NoOpNormalizeReceipt,
    ): ReceiptReviewPresenter = ReceiptReviewPresenter(
        draftRepository = repository,
        normalizeReceipt = normalizeReceipt,
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

    private object NoOpNormalizeReceipt : NormalizeReceiptUseCase {
        override fun normalize(receipt: Receipt): Receipt = receipt
    }

    private object RemoveDuplicateTaxNormalizeReceipt : NormalizeReceiptUseCase {
        override fun normalize(receipt: Receipt): Receipt {
            return receipt.copy(
                fees = receipt.fees.filterNot { fee ->
                    fee.type == FeeType.Tax && fee.amount == receipt.total
                },
            )
        }
    }
}
