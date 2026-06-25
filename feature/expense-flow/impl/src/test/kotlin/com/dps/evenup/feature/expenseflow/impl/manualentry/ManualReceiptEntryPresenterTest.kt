package com.dps.evenup.feature.expenseflow.impl.manualentry

import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptValidationResult
import com.dps.evenup.domain.receipt.api.ValidateReceiptUseCase
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualReceiptEntryPresenterTest {
    @Test
    fun `state defaults date to today and resolves locale currency with usd fallback`() {
        val state = ManualReceiptEntryUiState()

        assertEquals(LocalDate.now().toString(), state.dateLabel)
        assertEquals("\$0.00", state.displayTotalLabel)
        assertEquals("EUR", resolveDefaultManualCurrencyCode(Locale.GERMANY))
        assertEquals("USD", resolveDefaultManualCurrencyCode(Locale.US))
        assertEquals("USD", resolveDefaultManualCurrencyCode(Locale.Builder().setLanguage("en").build()))
    }

    @Test
    fun `add item opens draft and commits only after explicit save`() {
        val presenter = presenter()
        var state = ManualReceiptEntryUiState(currencyCode = "USD")

        state = presenter.reduce(state, ManualReceiptEntryUiEvent.AddItemClick)

        assertTrue(state.editDraft is ManualReceiptEditDraft.Item)
        assertTrue(state.items.isEmpty())

        state = presenter.reduce(state, ManualReceiptEntryUiEvent.ItemNameChanged("Pasta"))
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.ItemLineTotalChanged("10.00"))

        assertTrue(state.items.isEmpty())

        state = presenter.reduce(state, ManualReceiptEntryUiEvent.EditCommitClick)

        assertEquals(listOf("Pasta"), state.items.map { item -> item.name })
        assertEquals("10.00", state.items.single().amount)
        assertNull(state.editDraft)
    }

    @Test
    fun `item draft mirrors receipt review quantity and money behavior`() {
        val presenter = presenter()
        var state = ManualReceiptEntryUiState(currencyCode = "EUR")

        state = presenter.reduce(state, ManualReceiptEntryUiEvent.AddItemClick)
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.ItemNameChanged("Cake"))
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.ItemQuantityChanged("3"))
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.ItemUnitPriceChanged("2.50"))
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.EditCommitClick)

        val item = state.items.single()
        assertEquals("3", item.quantity)
        assertEquals("2.50", item.unitPriceAmount)
        assertEquals("7.50", item.amount)
        assertEquals("€7.50", state.calculatedTotalLabel)

        state = presenter.reduce(state, ManualReceiptEntryUiEvent.EditTargetSelected(ManualReceiptEditTarget.Item(item.id)))
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.ItemLineTotalChanged("10.00"))

        val draft = state.editDraft as ManualReceiptEditDraft.Item
        assertEquals("3.33", draft.unitPrice)
        assertEquals("10.00", draft.lineTotal)
        assertEquals(ManualReceiptMoneyField.ItemTotal, draft.lastEditedMoneyField)
        assertEquals(
            "Average price each is €3.33 because the item total does not split evenly.",
            draft.averagePriceNote("EUR"),
        )
    }

    @Test
    fun `add edit delete fee recalculates derived totals and zero existing fee is absent`() {
        val presenter = presenter()
        var state = ManualReceiptEntryUiState(currencyCode = "USD").withItem(presenter)

        state = presenter.reduce(state, ManualReceiptEntryUiEvent.AddFeeClick)
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.FeeTypeChanged(FeeType.Tip))
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.FeeAmountChanged("2.00"))
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.EditCommitClick)

        assertEquals("12.00", state.calculatedTotalMinor.let(::formatManualMoneyInput))
        assertEquals("\$2.00", state.feesTotalLabel)
        assertEquals(FeeType.Tip, state.fees.single().type)

        val feeId = state.fees.single().id
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.EditTargetSelected(ManualReceiptEditTarget.Fee(feeId)))
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.FeeAmountChanged("3.50"))
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.EditCommitClick)

        assertEquals("\$13.50", state.calculatedTotalLabel)

        state = presenter.reduce(state, ManualReceiptEntryUiEvent.EditTargetSelected(ManualReceiptEditTarget.Fee(feeId)))
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.FeeAmountChanged("0.00"))
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.EditCommitClick)

        assertTrue(state.fees.isEmpty())
        assertEquals("\$10.00", state.calculatedTotalLabel)
    }

    @Test
    fun `blank new fee save is a no op`() {
        val presenter = presenter()
        var state = ManualReceiptEntryUiState(currencyCode = "USD")

        state = presenter.reduce(state, ManualReceiptEntryUiEvent.AddFeeClick)
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.EditCommitClick)

        assertTrue(state.fees.isEmpty())
        assertNull(state.editDraft)
    }

    @Test
    fun `visible validation requires committed item valid date and valid currency`() {
        val presenter = presenter()
        var state = ManualReceiptEntryUiState(currencyCode = "USD")

        state = presenter.validateVisibleState(state)

        assertEquals("Add at least one item.", state.fieldErrors["items"])
        assertEquals(ManualReceiptEntrySection.Items, state.firstBlockingSection)
        assertFalse(state.canContinue)

        state = ManualReceiptEntryUiState(
            currencyCode = "US",
            dateLabel = LocalDate.now().plusDays(1).toString(),
        ).withItem(presenter)

        state = presenter.validateVisibleState(state)

        assertEquals("Date cannot be in the future.", state.fieldErrors["date"])
        assertEquals("Use a 3-letter code.", state.fieldErrors["currency"])
        assertEquals(ManualReceiptEntrySection.Details, state.firstBlockingSection)
    }

    @Test
    fun `save uses manual receipt fallback merchant and calculated total`() = runBlocking {
        val repository = FakeExpenseDraftRepository()
        val presenter = presenter(repository = repository)
        var state = ManualReceiptEntryUiState(currencyCode = "USD").withItem(presenter)

        state = presenter.reduce(state, ManualReceiptEntryUiEvent.AddFeeClick)
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.FeeTypeChanged(FeeType.ServiceFee))
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.FeeAmountChanged("1.25"))
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.EditCommitClick)

        val result = presenter.saveDraft(state)

        assertEquals(SaveManualReceiptDraftResult.Saved, result)
        val receipt = requireNotNull(repository.draft).receipt
        assertEquals("Manual Receipt", receipt.merchantName)
        assertEquals(MoneyMinor(1_000), receipt.subtotal)
        assertEquals(MoneyMinor(1_125), receipt.total)
        assertEquals(FeeType.ServiceFee, receipt.fees.single().type)
        assertEquals(MoneyMinor(125), receipt.fees.single().amount)
    }

    private fun ManualReceiptEntryUiState.withItem(
        presenter: ManualReceiptEntryPresenter,
    ): ManualReceiptEntryUiState {
        var state = this
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.AddItemClick)
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.ItemNameChanged("Pasta"))
        state = presenter.reduce(state, ManualReceiptEntryUiEvent.ItemLineTotalChanged("10.00"))
        return presenter.reduce(state, ManualReceiptEntryUiEvent.EditCommitClick)
    }

    private fun presenter(
        repository: FakeExpenseDraftRepository = FakeExpenseDraftRepository(),
    ): ManualReceiptEntryPresenter = ManualReceiptEntryPresenter(
        draftRepository = repository,
        validateReceipt = AlwaysValidReceipt,
    )

    private class FakeExpenseDraftRepository : ExpenseDraftRepository {
        var draft: ExpenseDraft? = null

        override suspend fun getDraft(): ExpenseDraft? = draft

        override suspend fun saveDraft(draft: ExpenseDraft) {
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
