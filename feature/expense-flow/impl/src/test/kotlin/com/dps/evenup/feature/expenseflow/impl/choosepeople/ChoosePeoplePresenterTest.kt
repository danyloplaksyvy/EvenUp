package com.dps.evenup.feature.expenseflow.impl.choosepeople

import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.participant.api.SavedParticipantRepository
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.ExpenseDraftId
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.participant.api.ParticipantValidationResult
import com.dps.evenup.domain.participant.api.SavedParticipantName
import com.dps.evenup.domain.participant.api.ValidateParticipantsUseCase
import com.dps.evenup.domain.receipt.api.CurrencyCode
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Quantity
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptItem
import com.dps.evenup.domain.receipt.api.ReceiptItemId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChoosePeoplePresenterTest {
    @Test
    fun `first added participant becomes payer by default`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()

        state = presenter.reduce(state, ChoosePeopleUiEvent.ParticipantNameInputChanged("Kehn"))
        state = presenter.reduce(state, ChoosePeopleUiEvent.AddParticipantClick)

        assertEquals(1, state.participants.size)
        assertEquals(state.participants.single().id, state.payerId)
        assertEquals("Payer", state.selectedParticipants.single().payerActionLabel)
        assertEquals("Add 1 more person to continue.", state.helperText)
        assertFalse(state.canContinue)
    }

    @Test
    fun `payer can be changed inside selected participant list`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()
        state = addTyped(state, presenter, "Kehn")
        state = addTyped(state, presenter, "Storak")
        val storakId = state.participants.first { participant -> participant.name == "Storak" }.id

        state = presenter.reduce(state, ChoosePeopleUiEvent.PayerSelected(storakId))

        assertEquals(storakId, state.payerId)
        assertEquals(listOf("Set payer", "Payer"), state.selectedParticipants.map { participant -> participant.payerActionLabel })
        assertEquals("Tap a person to change who paid.", state.helperText)
        assertTrue(state.canContinue)
    }

    @Test
    fun `removing payer assigns first remaining selected participant`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()
        state = addTyped(state, presenter, "Kehn")
        state = addTyped(state, presenter, "Storak")
        state = addTyped(state, presenter, "Billy")
        val payerId = state.payerId

        state = presenter.reduce(state, ChoosePeopleUiEvent.RemoveParticipantClick(requireNotNull(payerId)))

        assertEquals(listOf("Storak", "Billy"), state.participants.map { participant -> participant.name })
        assertEquals(state.participants.first().id, state.payerId)
        assertTrue(state.canContinue)
    }

    @Test
    fun `removing last participant clears payer`() = runBlocking {
        val presenter = presenter()
        var state = presenter.load()
        state = addTyped(state, presenter, "Kehn")

        state = presenter.reduce(state, ChoosePeopleUiEvent.RemoveParticipantClick(state.participants.single().id))

        assertTrue(state.participants.isEmpty())
        assertNull(state.payerId)
        assertEquals("Add at least 2 people to continue.", state.helperText)
        assertFalse(state.canContinue)
    }

    @Test
    fun `search filters suggestions and typed add only appears for new names`() = runBlocking {
        val savedRepository = FakeSavedParticipantRepository("Kehn", "Storak", "Billy")
        val presenter = presenter(savedRepository = savedRepository)
        var state = presenter.load()

        state = presenter.reduce(state, ChoosePeopleUiEvent.ParticipantNameInputChanged("Sto"))

        assertEquals(listOf("Storak"), state.savedSuggestions.map { suggestion -> suggestion.name })
        assertEquals("Add “Sto”", state.typedAddLabel)

        state = presenter.reduce(state, ChoosePeopleUiEvent.ParticipantNameInputChanged("Storak"))

        assertEquals(listOf("Storak"), state.savedSuggestions.map { suggestion -> suggestion.name })
        assertNull(state.typedAddLabel)

        state = presenter.reduce(state, ChoosePeopleUiEvent.ParticipantNameInputChanged("Alex"))

        assertTrue(state.savedSuggestions.isEmpty())
        assertEquals("Add “Alex”", state.typedAddLabel)
    }

    @Test
    fun `selected saved participant disappears from suggestions and returns after removal`() = runBlocking {
        val savedRepository = FakeSavedParticipantRepository("Kehn", "Storak")
        val presenter = presenter(savedRepository = savedRepository)
        var state = presenter.load()

        state = presenter.reduce(state, ChoosePeopleUiEvent.AddSavedParticipantClick("Kehn"))

        assertEquals(listOf("Storak"), state.savedSuggestions.map { suggestion -> suggestion.name })

        state = presenter.reduce(state, ChoosePeopleUiEvent.RemoveParticipantClick(state.participants.single().id))

        assertEquals(listOf("Kehn", "Storak"), state.savedSuggestions.map { suggestion -> suggestion.name })
    }

    @Test
    fun `continue requires payer to belong to selected participants`() {
        val state = ChoosePeopleUiState(
            isLoading = false,
            participants = listOf(
                ChoosePeopleParticipantUiState("p1", "Kehn", 0),
                ChoosePeopleParticipantUiState("p2", "Storak", 1),
            ),
            payerId = "missing",
        )

        assertFalse(state.canContinue)
        assertEquals("Choose who paid to continue.", state.helperText)
    }

    private suspend fun addTyped(
        state: ChoosePeopleUiState,
        presenter: ChoosePeoplePresenter,
        name: String,
    ): ChoosePeopleUiState {
        return presenter.reduce(
            presenter.reduce(state, ChoosePeopleUiEvent.ParticipantNameInputChanged(name)),
            ChoosePeopleUiEvent.AddParticipantClick,
        )
    }

    private fun presenter(
        draftRepository: FakeExpenseDraftRepository = FakeExpenseDraftRepository(draft()),
        savedRepository: FakeSavedParticipantRepository = FakeSavedParticipantRepository(),
    ): ChoosePeoplePresenter = ChoosePeoplePresenter(
        draftRepository = draftRepository,
        savedParticipantRepository = savedRepository,
        validateParticipants = AlwaysValidParticipants,
    )

    private fun draft(): ExpenseDraft = ExpenseDraft(
        id = ExpenseDraftId("draft-1"),
        receipt = Receipt(
            merchantName = "Cafe",
            currencyCode = CurrencyCode("USD"),
            items = listOf(
                ReceiptItem(
                    id = ReceiptItemId("item-1"),
                    name = "Coffee",
                    quantity = Quantity(1),
                    unitPrice = MoneyMinor(500),
                    totalPrice = MoneyMinor(500),
                ),
            ),
            fees = emptyList(),
            total = MoneyMinor(500),
        ),
        participants = emptyList(),
        payerId = ParticipantId("pending-payer"),
        itemAssignments = emptyList(),
        feeAllocations = emptyList(),
    )

    private class FakeExpenseDraftRepository(
        private var draft: ExpenseDraft?,
    ) : ExpenseDraftRepository {
        override suspend fun getDraft(): ExpenseDraft? = draft

        override suspend fun saveDraft(draft: ExpenseDraft) {
            this.draft = draft
        }

        override suspend fun clearDraft() {
            draft = null
        }
    }

    private class FakeSavedParticipantRepository(
        vararg names: String,
    ) : SavedParticipantRepository {
        private val savedNames = names.toMutableList()

        override suspend fun getSavedParticipantNames(): List<SavedParticipantName> {
            return savedNames.map(::SavedParticipantName)
        }

        override suspend fun addSavedParticipantName(name: SavedParticipantName) {
            if (savedNames.none { savedName -> savedName.equals(name.value, ignoreCase = true) }) {
                savedNames += name.value
            }
        }

        override suspend fun deleteSavedParticipantName(name: SavedParticipantName) {
            savedNames.removeAll { savedName -> savedName.equals(name.value, ignoreCase = true) }
        }
    }

    private object AlwaysValidParticipants : ValidateParticipantsUseCase {
        override fun validate(
            participants: List<Participant>,
            payerId: ParticipantId,
        ): ParticipantValidationResult = ParticipantValidationResult.Valid
    }
}
