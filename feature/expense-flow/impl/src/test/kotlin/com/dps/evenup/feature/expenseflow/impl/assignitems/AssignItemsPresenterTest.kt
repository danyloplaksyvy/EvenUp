package com.dps.evenup.feature.expenseflow.impl.assignitems

import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.ExpenseDraftId
import com.dps.evenup.domain.expense.api.FeeAllocation
import com.dps.evenup.domain.expense.api.ItemAssignment
import com.dps.evenup.domain.expense.api.ItemAssignmentMode
import com.dps.evenup.domain.expense.api.ItemAssignmentValidationResult
import com.dps.evenup.domain.expense.api.ItemParticipantShare
import com.dps.evenup.domain.expense.api.PercentageBasisPoints
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
import org.junit.Test

class AssignItemsPresenterTest {
    @Test
    fun `load auto selects first participant and uses receipt currency`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        val state = presenter.load()

        assertEquals(listOf("p1"), state.selectedParticipantIds)
        assertEquals(listOf(true, false), state.participants.map { participant -> participant.selected })
        assertEquals("Tap items to assign to John", state.helperText)
        assertEquals("$10.00", state.subtotalLabel)
        assertEquals("$5.00 each", state.items.single().unitPriceLabel)
        assertEquals("Unassigned", state.items.single().assignmentSummaryLabel)
        assertEquals("Assign", state.items.single().itemActionLabel)
    }

    @Test
    fun `direct item tap assigns full item to one selected participant`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ItemTapped("item-1"))

        assertEquals(listOf("p1"), state.items.single().assignees.map { assignee -> assignee.participantId })
        assertEquals("John", state.items.single().assignmentSummaryLabel)
        assertEquals("Edit", state.items.single().itemActionLabel)
        assertEquals("All items assigned", state.feedback?.message)

        presenter.saveDraft(state)

        val savedAssignment = requireNotNull(repository.draft).itemAssignments.single()
        assertEquals(ItemAssignmentMode.Full, savedAssignment.mode)
        assertEquals("p1", savedAssignment.shares.single().participantId.value)
        assertEquals(1_000L, savedAssignment.shares.single().amount.value)
    }

    @Test
    fun `direct item tap with selected people matching quantity assigns by units`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p2"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemTapped("item-1"))

        val item = state.items.single()
        assertEquals(AssignItemsSplitMode.Units, item.splitMode)
        assertEquals(listOf("p1", "p2"), item.assignees.map { assignee -> assignee.participantId })
        assertEquals(listOf(1, 1), item.shares.map { share -> share.quantity })
        assertEquals("John 1x · Amy 1x", item.assignmentSummaryLabel)
        assertEquals("Edit", item.itemActionLabel)
    }

    @Test
    fun `assigned item with three people keeps all assignees and uses compact label`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft(quantity = 3, includeThirdParticipant = true))
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p2"))
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p3"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemTapped("item-1"))

        val item = state.items.single()
        assertEquals(AssignItemsSplitMode.Units, item.splitMode)
        assertEquals(listOf("p1", "p2", "p3"), item.assignees.map { assignee -> assignee.participantId })
        assertEquals("3 people · Units", item.assignmentSummaryLabel)
        assertEquals("Edit", item.itemActionLabel)
    }

    @Test
    fun `direct item tap with multiple selected people and mismatched quantity assigns equal split`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft(quantity = 1))
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p2"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemTapped("item-1"))

        val item = state.items.single()
        assertEquals(AssignItemsSplitMode.SharedEqual, item.splitMode)
        assertEquals(listOf("p1", "p2"), item.assignees.map { assignee -> assignee.participantId })
        assertEquals("2 people · Equal", item.assignmentSummaryLabel)
        assertEquals("Edit", item.itemActionLabel)
    }

    @Test
    fun `selected participant chips do not change assigned or unassigned item row labels`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft(includeSecondItem = true))
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ItemTapped("item-1"))
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p2"))

        val assignedItem = state.items.first { item -> item.id == "item-1" }
        val unassignedItem = state.items.first { item -> item.id == "item-2" }
        assertEquals(listOf("p1"), assignedItem.assignees.map { assignee -> assignee.participantId })
        assertEquals("John", assignedItem.assignmentSummaryLabel)
        assertEquals("Edit", assignedItem.itemActionLabel)
        assertEquals(emptyList<String>(), unassignedItem.assignees.map { assignee -> assignee.participantId })
        assertEquals("Unassigned", unassignedItem.assignmentSummaryLabel)
        assertEquals("Assign", unassignedItem.itemActionLabel)
    }

    @Test
    fun `participant chips toggle multi selection and can deselect all manually`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p2"))
        assertEquals(listOf("p1", "p2"), state.selectedParticipantIds)
        assertEquals("Assigning to John and Amy", state.helperText)
        assertEquals("Amy selected", state.feedback?.message)

        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p1"))
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p2"))

        assertEquals(emptyList<String>(), state.selectedParticipantIds)
        assertEquals("Select people, then assign items", state.helperText)
        assertEquals(listOf(false, false), state.participants.map { participant -> participant.selected })
    }

    @Test
    fun `helper text supports more than two selected people`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft(includeThirdParticipant = true))
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p2"))
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p3"))

        assertEquals(listOf("p1", "p2", "p3"), state.selectedParticipantIds)
        assertEquals(listOf(true, true, true), state.participants.map { participant -> participant.selected })
        assertEquals("Assigning to 3 people", state.helperText)
    }

    @Test
    fun `direct tap replaces existing custom split for MVP`() = runBlocking {
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
        state = presenter.reduce(state, AssignItemsUiEvent.ItemTapped("item-1"))

        assertEquals(null, state.splitSheet)
        assertEquals(AssignItemsSplitMode.Units, state.items.single().splitMode)
        assertEquals(listOf("p1"), state.items.single().assignees.map { assignee -> assignee.participantId })
    }

    @Test
    fun `item action assigns unassigned item and opens editor for assigned item`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ItemActionClick("item-1"))

        assertEquals("John", state.items.single().assignmentSummaryLabel)
        assertEquals("Edit", state.items.single().itemActionLabel)
        assertEquals(null, state.splitSheet)

        state = presenter.reduce(state, AssignItemsUiEvent.ItemActionClick("item-1"))

        assertEquals("item-1", state.splitSheet?.itemId)
        assertEquals("John", state.items.single().assignmentSummaryLabel)
    }

    @Test
    fun `item action with no selected people guides without assigning`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p1"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemActionClick("item-1"))

        assertEquals(emptyList<String>(), state.selectedParticipantIds)
        assertEquals("Select people, then assign items", state.helperText)
        assertEquals("Select people, then assign items.", state.fieldErrors["assignment"])
        assertEquals(AssignItemsItemState.Unassigned, state.items.single().assignmentState)
        assertEquals(emptyList<String>(), state.items.single().assignees.map { assignee -> assignee.participantId })
    }

    @Test
    fun `clear assignments requires confirmation and keeps selected participant`() = runBlocking {
        val repository = FakeExpenseDraftRepository(
            draft(
                assignments = listOf(
                    ItemAssignment(
                        receiptItemId = ReceiptItemId("item-1"),
                        mode = ItemAssignmentMode.Full,
                        shares = listOf(ItemParticipantShare(ParticipantId("p1"), MoneyMinor(1_000))),
                    ),
                ),
            ),
        )
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        assertEquals(true, state.canClearAssignments)

        state = presenter.reduce(state, AssignItemsUiEvent.ClearAssignmentsClick)
        assertEquals(true, state.showClearAssignmentsConfirmation)

        state = presenter.reduce(state, AssignItemsUiEvent.ClearAssignmentsConfirmed)

        assertEquals(false, state.showClearAssignmentsConfirmation)
        assertEquals(listOf("p1"), state.selectedParticipantIds)
        assertEquals(AssignItemsItemState.Unassigned, state.items.single().assignmentState)
        assertEquals(false, state.canContinue)
        assertEquals("Assignments cleared", state.feedback?.message)
        assertEquals(true, state.clearUndoItems?.isNotEmpty())

        state = presenter.reduce(state, AssignItemsUiEvent.ClearAssignmentsUndoClick)

        assertEquals(null, state.clearUndoItems)
        assertEquals(AssignItemsItemState.Assigned, state.items.single().assignmentState)
        assertEquals(true, state.canContinue)
        assertEquals("Assignments restored", state.feedback?.message)
    }

    @Test
    fun `clear assignments click is ignored when no assignments exist`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()

        assertEquals(false, state.canClearAssignments)

        state = presenter.reduce(state, AssignItemsUiEvent.ClearAssignmentsClick)

        assertEquals(false, state.showClearAssignmentsConfirmation)
        assertEquals(AssignItemsItemState.Unassigned, state.items.single().assignmentState)
    }

    @Test
    fun `unit split cannot exceed item quantity from UI controls`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ItemSplitClick("item-1"))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitQuantityChanged("p1", 1))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitQuantityChanged("p1", 1))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitQuantityChanged("p1", 1))

        val sheet = requireNotNull(state.splitSheet)
        assertEquals(2, sheet.rows.first { row -> row.participantId == "p1" }.quantity)
        assertEquals("All units assigned", sheet.statusLabel)
        assertEquals(null, sheet.error)
    }

    @Test
    fun `split all equally confirms before replacing current assignments`() = runBlocking {
        val repository = FakeExpenseDraftRepository(
            draft(
                assignments = listOf(
                    ItemAssignment(
                        receiptItemId = ReceiptItemId("item-1"),
                        mode = ItemAssignmentMode.Full,
                        shares = listOf(ItemParticipantShare(ParticipantId("p1"), MoneyMinor(1_000))),
                    ),
                ),
            ),
        )
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ApplyEqualSplitClick)
        assertEquals(true, state.showEqualSplitConfirmation)

        state = presenter.reduce(state, AssignItemsUiEvent.ApplyEqualSplitConfirmed)

        assertEquals(false, state.showEqualSplitConfirmation)
        assertEquals(AssignItemsSplitMode.SharedEqual, state.items.single().splitMode)
        assertEquals(listOf("p1", "p2"), state.items.single().assignees.map { assignee -> assignee.participantId })
        assertEquals(listOf(500L, 500L), state.items.single().shares.map { share -> share.amountMinor })
    }

    @Test
    fun `assigned split modes expose edit action and concise summaries`() = runBlocking {
        val equalState = AssignItemsPresenter(
            FakeExpenseDraftRepository(draft(assignments = listOf(sharedEqualAssignment("p1", "p2")))),
            AlwaysValidAssignments,
        ).load()

        assertEquals("2 people · Equal", equalState.items.single().assignmentSummaryLabel)
        assertEquals("Edit", equalState.items.single().itemActionLabel)

        val customState = AssignItemsPresenter(
            FakeExpenseDraftRepository(
                draft(
                    assignments = listOf(
                        ItemAssignment(
                            receiptItemId = ReceiptItemId("item-1"),
                            mode = ItemAssignmentMode.CustomAmount,
                            shares = listOf(
                                ItemParticipantShare(ParticipantId("p1"), MoneyMinor(600)),
                                ItemParticipantShare(ParticipantId("p2"), MoneyMinor(400)),
                            ),
                        ),
                    ),
                ),
            ),
            AlwaysValidAssignments,
        ).load()

        assertEquals("2 people · Custom", customState.items.single().assignmentSummaryLabel)
        assertEquals("Edit", customState.items.single().itemActionLabel)

        val percentState = AssignItemsPresenter(
            FakeExpenseDraftRepository(draft(assignments = listOf(percentageAssignment("p1" to 7000, "p2" to 3000)))),
            AlwaysValidAssignments,
        ).load()

        assertEquals("2 people · Percent", percentState.items.single().assignmentSummaryLabel)
        assertEquals("Edit", percentState.items.single().itemActionLabel)
    }

    @Test
    fun `split sheet preselects selected people as unit split when quantity matches`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p2"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemSplitClick("item-1"))

        val sheet = requireNotNull(state.splitSheet)
        assertEquals(AssignItemsSplitMode.Units, sheet.mode)
        assertEquals(listOf(1, 1), sheet.rows.map { row -> row.quantity })
        assertEquals("All units assigned", sheet.statusLabel)
        assertEquals(true, sheet.canSave)
    }

    @Test
    fun `split sheet preselects selected people as equal split when quantity differs`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft(quantity = 1))
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p2"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemSplitClick("item-1"))

        val sheet = requireNotNull(state.splitSheet)
        assertEquals(AssignItemsSplitMode.SharedEqual, sheet.mode)
        assertEquals(listOf(true, true), sheet.rows.map { row -> row.included })
        assertEquals(listOf("$5.00", "$5.00"), sheet.rows.map { row -> row.amountLabel })
        assertEquals("2 people selected · $5.00 each", sheet.statusLabel)
    }

    @Test
    fun `percentage mode autofills selected people and exposes amount labels`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p2"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemSplitClick("item-1"))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitModeSelected(AssignItemsSplitMode.Percentage))

        val sheet = requireNotNull(state.splitSheet)
        assertEquals(AssignItemsSplitMode.Percentage, sheet.mode)
        assertEquals(listOf("50", "50"), sheet.rows.map { row -> row.percentage })
        assertEquals(listOf("$5.00", "$5.00"), sheet.rows.map { row -> row.amountLabel })
        assertEquals("0% remaining", sheet.statusLabel)
        assertEquals(true, sheet.canSave)
    }

    @Test
    fun `percentage mode for assigned item uses item assignees instead of selected chips`() = runBlocking {
        val repository = FakeExpenseDraftRepository(
            draft(
                assignments = listOf(
                    sharedEqualAssignment("p1", "p2"),
                ),
                includeThirdParticipant = true,
            ),
        )
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p3"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemSplitClick("item-1"))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitModeSelected(AssignItemsSplitMode.Percentage))

        val sheet = requireNotNull(state.splitSheet)
        assertEquals(AssignItemsSplitMode.Percentage, sheet.mode)
        assertEquals(listOf("p1", "p2", "p3"), sheet.rows.map { row -> row.participantId })
        assertEquals(listOf("50", "50", ""), sheet.rows.map { row -> row.percentage })
        assertEquals(listOf("$5.00", "$5.00", ""), sheet.rows.map { row -> row.amountLabel })
        assertEquals(listOf(true, true, false), sheet.rows.map { row -> row.included })
        assertEquals("0% remaining", sheet.statusLabel)
    }

    @Test
    fun `custom amount mode for assigned item uses item assignees instead of selected chips`() = runBlocking {
        val repository = FakeExpenseDraftRepository(
            draft(
                assignments = listOf(
                    sharedEqualAssignment("p1", "p2"),
                ),
                includeThirdParticipant = true,
            ),
        )
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p3"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemSplitClick("item-1"))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitModeSelected(AssignItemsSplitMode.CustomAmount))

        val sheet = requireNotNull(state.splitSheet)
        assertEquals(AssignItemsSplitMode.CustomAmount, sheet.mode)
        assertEquals(listOf("p1", "p2", "p3"), sheet.rows.map { row -> row.participantId })
        assertEquals(listOf("5.00", "5.00", ""), sheet.rows.map { row -> row.amount })
        assertEquals(listOf(true, true, false), sheet.rows.map { row -> row.included })
        assertEquals("$0.00 remaining", sheet.statusLabel)
    }

    @Test
    fun `shared equal mode for assigned item uses item assignees instead of selected chips`() = runBlocking {
        val repository = FakeExpenseDraftRepository(
            draft(
                assignments = listOf(
                    byUnitsAssignment("p1", "p2"),
                ),
                includeThirdParticipant = true,
            ),
        )
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p3"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemSplitClick("item-1"))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitModeSelected(AssignItemsSplitMode.SharedEqual))

        val sheet = requireNotNull(state.splitSheet)
        assertEquals(AssignItemsSplitMode.SharedEqual, sheet.mode)
        assertEquals(listOf("p1", "p2", "p3"), sheet.rows.map { row -> row.participantId })
        assertEquals(listOf(true, true, false), sheet.rows.map { row -> row.included })
        assertEquals(listOf("$5.00", "$5.00", ""), sheet.rows.map { row -> row.amountLabel })
        assertEquals("2 people selected · $5.00 each", sheet.statusLabel)
    }

    @Test
    fun `existing percentage assignment keeps saved percentages when switching back to percent`() = runBlocking {
        val repository = FakeExpenseDraftRepository(
            draft(
                assignments = listOf(
                    percentageAssignment(
                        "p1" to 7000,
                        "p2" to 3000,
                    ),
                ),
                includeThirdParticipant = true,
            ),
        )
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p3"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemSplitClick("item-1"))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitModeSelected(AssignItemsSplitMode.CustomAmount))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitModeSelected(AssignItemsSplitMode.Percentage))

        val sheet = requireNotNull(state.splitSheet)
        assertEquals(AssignItemsSplitMode.Percentage, sheet.mode)
        assertEquals(listOf("70", "30", ""), sheet.rows.map { row -> row.percentage })
        assertEquals(listOf("$7.00", "$3.00", ""), sheet.rows.map { row -> row.amountLabel })
        assertEquals(listOf(true, true, false), sheet.rows.map { row -> row.included })
        assertEquals("0% remaining", sheet.statusLabel)
    }

    @Test
    fun `amount mode autofills one remaining selected participant`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p2"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemSplitClick("item-1"))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitModeSelected(AssignItemsSplitMode.CustomAmount))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitCustomAmountChanged("p1", "2.00"))

        val sheet = requireNotNull(state.splitSheet)
        assertEquals(listOf("2.00", "8.00"), sheet.rows.map { row -> row.amount })
        assertEquals("$0.00 remaining", sheet.statusLabel)
        assertEquals(true, sheet.canSave)
    }

    @Test
    fun `amount mode updates generated remainder but preserves edited values`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p2"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemSplitClick("item-1"))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitModeSelected(AssignItemsSplitMode.CustomAmount))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitCustomAmountChanged("p1", "2.00"))

        var sheet = requireNotNull(state.splitSheet)
        assertEquals(listOf("2.00", "8.00"), sheet.rows.map { row -> row.amount })
        assertEquals(listOf(false, true), sheet.rows.map { row -> row.amountGenerated })

        state = presenter.reduce(state, AssignItemsUiEvent.SplitCustomAmountChanged("p1", "3.00"))
        sheet = requireNotNull(state.splitSheet)
        assertEquals(listOf("3.00", "7.00"), sheet.rows.map { row -> row.amount })

        state = presenter.reduce(state, AssignItemsUiEvent.SplitCustomAmountChanged("p2", "6.00"))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitCustomAmountChanged("p1", "4.00"))
        sheet = requireNotNull(state.splitSheet)

        assertEquals(listOf("4.00", "6.00"), sheet.rows.map { row -> row.amount })
        assertEquals(listOf(true, true), sheet.rows.map { row -> row.amountEdited })
        assertEquals(listOf(false, false), sheet.rows.map { row -> row.amountGenerated })
        assertEquals(true, sheet.canSave)
    }

    @Test
    fun `percentage mode updates generated remainder but preserves edited values`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ParticipantSelected("p2"))
        state = presenter.reduce(state, AssignItemsUiEvent.ItemSplitClick("item-1"))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitModeSelected(AssignItemsSplitMode.Percentage))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitPercentageChanged("p1", "40"))

        var sheet = requireNotNull(state.splitSheet)
        assertEquals(listOf("40", "60"), sheet.rows.map { row -> row.percentage })
        assertEquals(listOf(false, true), sheet.rows.map { row -> row.percentageGenerated })

        state = presenter.reduce(state, AssignItemsUiEvent.SplitPercentageChanged("p2", "55"))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitPercentageChanged("p1", "45"))
        sheet = requireNotNull(state.splitSheet)

        assertEquals(listOf("45", "55"), sheet.rows.map { row -> row.percentage })
        assertEquals(listOf(true, true), sheet.rows.map { row -> row.percentageEdited })
        assertEquals(listOf(false, false), sheet.rows.map { row -> row.percentageGenerated })
        assertEquals(true, sheet.canSave)
    }

    @Test
    fun `assignment feedback scrolls to next unassigned item`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft(includeSecondItem = true))
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ItemTapped("item-1"))

        assertEquals("Pizza assigned to John", state.feedback?.message)
        assertEquals("item-2", state.scrollToItemId)
        assertEquals(false, state.canContinue)
    }

    @Test
    fun `split save emits all assigned feedback when it completes assignment`() = runBlocking {
        val repository = FakeExpenseDraftRepository(draft())
        val presenter = AssignItemsPresenter(repository, AlwaysValidAssignments)

        var state = presenter.load()
        state = presenter.reduce(state, AssignItemsUiEvent.ItemSplitClick("item-1"))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitQuantityChanged("p1", 2))
        state = presenter.reduce(state, AssignItemsUiEvent.SplitSaveClick)

        assertEquals("All items assigned", state.feedback?.message)
        assertEquals(true, state.canContinue)
    }

    private fun draft(
        assignments: List<ItemAssignment> = emptyList(),
        quantity: Int = 2,
        includeSecondItem: Boolean = false,
        includeThirdParticipant: Boolean = false,
    ): ExpenseDraft = ExpenseDraft(
        id = ExpenseDraftId("draft-1"),
        receipt = run {
            val items = listOf(
                ReceiptItem(
                    id = ReceiptItemId("item-1"),
                    name = "Pizza",
                    quantity = Quantity(quantity),
                    unitPrice = MoneyMinor(1_000L / quantity),
                    totalPrice = MoneyMinor(1_000),
                ),
            ) + if (includeSecondItem) {
                listOf(
                    ReceiptItem(
                        id = ReceiptItemId("item-2"),
                        name = "Water",
                        quantity = Quantity(1),
                        unitPrice = MoneyMinor(300),
                        totalPrice = MoneyMinor(300),
                    ),
                )
            } else {
                emptyList()
            }
            Receipt(
                merchantName = "Cafe",
                currencyCode = CurrencyCode("USD"),
                items = items,
                fees = emptyList(),
                total = MoneyMinor(items.sumOf { item -> item.totalPrice.value }),
            )
        },
        participants = listOf(
            Participant(ParticipantId("p1"), "John", 0),
            Participant(ParticipantId("p2"), "Amy", 1),
        ) + if (includeThirdParticipant) {
            listOf(Participant(ParticipantId("p3"), "Max", 2))
        } else {
            emptyList()
        },
        payerId = ParticipantId("p1"),
        itemAssignments = assignments,
        feeAllocations = emptyList<FeeAllocation>(),
    )

    private fun sharedEqualAssignment(
        firstParticipantId: String,
        secondParticipantId: String,
    ): ItemAssignment = ItemAssignment(
        receiptItemId = ReceiptItemId("item-1"),
        mode = ItemAssignmentMode.SharedEqual,
        shares = listOf(
            ItemParticipantShare(ParticipantId(firstParticipantId), MoneyMinor(500)),
            ItemParticipantShare(ParticipantId(secondParticipantId), MoneyMinor(500)),
        ),
    )

    private fun byUnitsAssignment(
        firstParticipantId: String,
        secondParticipantId: String,
    ): ItemAssignment = ItemAssignment(
        receiptItemId = ReceiptItemId("item-1"),
        mode = ItemAssignmentMode.ByUnits,
        shares = listOf(
            ItemParticipantShare(ParticipantId(firstParticipantId), MoneyMinor(500), quantity = Quantity(1)),
            ItemParticipantShare(ParticipantId(secondParticipantId), MoneyMinor(500), quantity = Quantity(1)),
        ),
    )

    private fun percentageAssignment(
        vararg shares: Pair<String, Int>,
    ): ItemAssignment = ItemAssignment(
        receiptItemId = ReceiptItemId("item-1"),
        mode = ItemAssignmentMode.Percentage,
        shares = shares.map { (participantId, percentageBasisPoints) ->
            ItemParticipantShare(
                participantId = ParticipantId(participantId),
                amount = MoneyMinor(1_000L * percentageBasisPoints / 10_000),
                percentage = PercentageBasisPoints(percentageBasisPoints),
            )
        },
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
