package com.dps.evenup.feature.expenseflow.impl.assignitems

import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.ExpenseDraftId
import com.dps.evenup.domain.expense.api.FeeAllocation
import com.dps.evenup.domain.expense.api.ItemAssignment
import com.dps.evenup.domain.expense.api.ItemAssignmentMode
import com.dps.evenup.domain.expense.api.ItemAssignmentValidationResult
import com.dps.evenup.domain.expense.api.ItemParticipantShare
import com.dps.evenup.domain.expense.api.ValidateItemAssignmentsUseCase
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.CurrencyCode
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Quantity
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptItem
import com.dps.evenup.domain.receipt.api.ReceiptItemId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssignItemsPresenterTest {
    @Test
    fun `direct item tap adds selected people to item`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        assertNull(state.selectedParticipantId)

        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p1"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemTapped("item-1"))
        assertEquals(listOf("p1"), state.items.single().assignees.map { assignee -> assignee.participantId })

        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p2"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemTapped("item-1"))
        assertEquals(listOf("p1", "p2"), state.items.single().assignees.map { assignee -> assignee.participantId })

        state = presenter.reduce(state, AssignItemsUiEvent.ItemTapped("item-1"))
        assertEquals(listOf("p1"), state.items.single().assignees.map { assignee -> assignee.participantId })

        presenter.saveDraft(state)

        val savedAssignment = requireNotNull(repository.draft).itemAssignments.single()
        assertEquals(ItemAssignmentMode.Full, savedAssignment.mode)
        assertEquals("p1", savedAssignment.shares.single().participantId.value)
        assertEquals(1_000L, savedAssignment.shares.single().amount.value)
    }

    @Test
    fun `direct item tap removes selected person when they are the only assignee`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p1"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemTapped("item-1"))
        assertEquals(listOf("p1"), state.items.single().assignees.map { assignee -> assignee.participantId })

        state = presenter.reduce(state, AssignItemsUiEvent.ItemTapped("item-1"))

        assertEquals(emptyList<String>(), state.items.single().assignees.map { assignee -> assignee.participantId })
        assertEquals(AssignItemsItemState.Unassigned, state.items.single().assignmentState)
        assertEquals(false, state.canContinue)
    }

    @Test
    fun `direct tap on custom split opens split sheet instead of replacing assignment`() = runBlocking {
        val repository = FakeExpenseDraftRepository(
            draft(
                assignments = listOf(
                    ItemAssignment(
                        receiptItemId = ReceiptItemId("item-1"),
                        mode = ItemAssignmentMode.CustomAmount,
                        shares = listOf(
                            ItemParticipantShare(ParticipantId("p1"), MoneyMinor(500)),
                            ItemParticipantShare(ParticipantId("p2"), MoneyMinor(500)),
                        ),
                    ),
                ),
            ),
        )
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p1"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemTapped("item-1"))

        assertEquals("item-1", state.splitSheet?.itemId)
        assertEquals(AssignItemsSplitMode.CustomAmount, state.splitSheet?.mode)
        assertEquals(listOf("p1", "p2"), state.items.single().assignees.map { assignee -> assignee.participantId })
    }

    private fun draft(assignments: List<ItemAssignment> = emptyList()): ExpenseDraft = ExpenseDraft(
        id = ExpenseDraftId("draft-1"),
        receipt = Receipt(
            merchantName = "Cafe",
            currencyCode = CurrencyCode("EUR"),
            items = listOf(
                ReceiptItem(
                    id = ReceiptItemId("item-1"),
                    name = "Pizza",
                    quantity = Quantity(2),
                    unitPrice = MoneyMinor(500),
                    totalPrice = MoneyMinor(1_000),
                ),
            ),
            fees = emptyList(),
            total = MoneyMinor(1_000),
        ),
        participants = listOf(
            Participant(ParticipantId("p1"), "John", 0),
            Participant(ParticipantId("p2"), "Amy", 1),
        ),
        payerId = ParticipantId("p1"),
        itemAssignments = assignments,
        feeAllocations = emptyList<FeeAllocation>(),
    )

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

    private object AlwaysValidAssignments : ValidateItemAssignmentsUseCase {
        override fun validate(
            receiptItems: List<ReceiptItem>,
            participants: List<Participant>,
            assignments: List<ItemAssignment>,
        ): ItemAssignmentValidationResult = ItemAssignmentValidationResult.Valid
    }
}
