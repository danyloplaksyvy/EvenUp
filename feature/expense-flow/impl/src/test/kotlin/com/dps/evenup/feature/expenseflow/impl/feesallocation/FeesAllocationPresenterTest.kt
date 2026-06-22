package com.dps.evenup.feature.expenseflow.impl.feesallocation

import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.domain.expense.api.AllocateFeesUseCase
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.ExpenseDraftId
import com.dps.evenup.domain.expense.api.FeeAllocation
import com.dps.evenup.domain.expense.api.FeeAllocationMode
import com.dps.evenup.domain.expense.api.FeeAllocationValidationError
import com.dps.evenup.domain.expense.api.FeeAllocationValidationResult
import com.dps.evenup.domain.expense.api.FeeParticipantShare
import com.dps.evenup.domain.expense.api.ItemAssignment
import com.dps.evenup.domain.expense.api.ItemAssignmentMode
import com.dps.evenup.domain.expense.api.ItemParticipantShare
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.CurrencyCode
import com.dps.evenup.domain.receipt.api.FeeId
import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptFee
import com.dps.evenup.domain.receipt.api.ReceiptItemId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeesAllocationPresenterTest {
    @Test
    fun `load propagates receipt currency labels`() = runBlocking {
        val presenter = presenter(
            draft = feesAllocationDraft(
                currencyCode = "GBP",
                fees = listOf(feesAllocationFee(label = "Tax", amount = MoneyMinor(250))),
            ),
        )

        val state = presenter.load()

        assertEquals("GBP", state.currencyCode)
        assertEquals("£", state.currencySymbol)
        assertEquals("£2.50", state.totalFeesLabel)
        assertEquals("£2.50", state.feeRows.single().amountLabel)
        assertEquals("Choose how £2.50 in fees should be shared.", state.headerSubtitle)
    }

    @Test
    fun `fresh allocation defaults to proportional when item subtotals are available`() = runBlocking {
        val state = presenter(draft = feesAllocationDraft()).load()

        assertEquals(FeesAllocationModeUiState.Proportional, state.mode)
        assertEquals(listOf("$3.00", "$1.00"), state.participants.map { participant -> participant.allocatedFeesLabel })
    }

    @Test
    fun `fresh allocation falls back to equal when item subtotals are unavailable`() = runBlocking {
        val state = presenter(draft = feesAllocationDraft(itemAssignments = emptyList())).load()

        assertEquals(FeesAllocationModeUiState.Equal, state.mode)
        assertEquals("Using equal split because item subtotals are unavailable.", state.helperText)
        assertEquals(listOf("$2.00", "$2.00"), state.participants.map { participant -> participant.allocatedFeesLabel })
    }

    @Test
    fun `zero fees are hidden and not saved as allocations`() = runBlocking {
        val repository = FakeExpenseDraftRepository(
            feesAllocationDraft(
                fees = listOf(
                    feesAllocationFee(id = "tax", label = "Tax", amount = MoneyMinor(400)),
                    feesAllocationFee(id = "tip", label = "Tip", amount = MoneyMinor(0)),
                ),
            ),
        )
        val presenter = FeesAllocationPresenter(repository, TestAllocateFeesUseCase)

        val state = presenter.load()
        presenter.saveDraft(state)

        assertEquals(listOf("tax"), state.feeRows.map { fee -> fee.id })
        assertEquals(listOf("tax"), requireNotNull(repository.draft).feeAllocations.map { allocation -> allocation.feeId.value })
    }

    @Test
    fun `custom mode reports remaining fully allocated and over allocated per fee`() = runBlocking {
        val presenter = presenter(draft = feesAllocationDraft())
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))

        state = state.copy(
            feeCards = state.feeCards.map { card ->
                card.copy(participantRows = card.participantRows.map { row -> row.copy(customAmount = "") })
            },
        )
        state = presenter.reduce(
            state,
            FeesAllocationUiEvent.CustomAmountChanged(feeId = "fee-1", participantId = "p1", value = "1.00"),
        )
        assertEquals("$3.00 remaining", state.feeCards.single().statusLabel)
        assertFalse(state.canContinue)

        state = presenter.reduce(
            state,
            FeesAllocationUiEvent.CustomAmountChanged(feeId = "fee-1", participantId = "p2", value = "3.00"),
        )
        assertEquals("Fully allocated", state.feeCards.single().statusLabel)
        assertTrue(state.canContinue)

        state = presenter.reduce(
            state,
            FeesAllocationUiEvent.CustomAmountChanged(feeId = "fee-1", participantId = "p2", value = "3.50"),
        )
        assertEquals("Over by $0.50", state.feeCards.single().statusLabel)
        assertFalse(state.canContinue)
    }

    @Test
    fun `custom mode treats malformed values as invalid`() = runBlocking {
        val presenter = presenter(draft = feesAllocationDraft())
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))

        state = presenter.reduce(
            state,
            FeesAllocationUiEvent.CustomAmountChanged(feeId = "fee-1", participantId = "p1", value = "abc1"),
        )

        assertEquals("Enter valid amounts.", state.feeCards.single().statusLabel)
        assertTrue(state.feeCards.single().participantRows.first().customIsError)
        assertFalse(state.canContinue)
    }

    @Test
    fun `custom mode treats negative values as invalid`() = runBlocking {
        val presenter = presenter(draft = feesAllocationDraft())
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))

        state = presenter.reduce(
            state,
            FeesAllocationUiEvent.CustomAmountChanged(feeId = "fee-1", participantId = "p1", value = "\$-1.00"),
        )

        assertEquals("Enter valid amounts.", state.feeCards.single().statusLabel)
        assertTrue(state.feeCards.single().participantRows.first().customIsError)
        assertFalse(state.canContinue)
    }

    @Test
    fun `custom mode disables empty fields once fee is fully allocated`() = runBlocking {
        val presenter = presenter(draft = feesAllocationDraft())
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))
        state = state.copy(
            feeCards = state.feeCards.map { card ->
                card.copy(participantRows = card.participantRows.map { row -> row.copy(customAmount = "") })
            },
        )

        state = presenter.reduce(
            state,
            FeesAllocationUiEvent.CustomAmountChanged(feeId = "fee-1", participantId = "p1", value = "4.00"),
        )

        val rows = state.feeCards.single().participantRows
        assertTrue(rows.first { row -> row.participantId == "p1" }.customEnabled)
        assertFalse(rows.first { row -> row.participantId == "p2" }.customEnabled)
    }

    @Test
    fun `custom mode disables continue until every positive fee is exactly allocated`() = runBlocking {
        val presenter = presenter(
            draft = feesAllocationDraft(
                fees = listOf(
                    feesAllocationFee(id = "tax", label = "Tax", amount = MoneyMinor(400)),
                    feesAllocationFee(id = "service", label = "Service", amount = MoneyMinor(200)),
                ),
            ),
        )
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))
        state = state.copy(
            feeCards = state.feeCards.map { card ->
                card.copy(participantRows = card.participantRows.map { row -> row.copy(customAmount = "") })
            },
        )

        state = presenter.reduce(state, FeesAllocationUiEvent.CustomAmountChanged("tax", "p1", "4.00"))
        state = presenter.reduce(state, FeesAllocationUiEvent.CustomAmountChanged("service", "p1", "1.00"))

        assertEquals(listOf("Fully allocated", "$1.00 remaining"), state.feeCards.map { card -> card.statusLabel })
        assertFalse(state.canContinue)
    }

    @Test
    fun `existing saved mode is preserved on load`() = runBlocking {
        val draft = feesAllocationDraft(
            feeAllocations = listOf(
                FeeAllocation(
                    feeId = FeeId("fee-1"),
                    mode = FeeAllocationMode.Equal,
                    shares = listOf(
                        FeeParticipantShare(ParticipantId("p1"), MoneyMinor(200)),
                        FeeParticipantShare(ParticipantId("p2"), MoneyMinor(200)),
                    ),
                ),
            ),
        )

        val state = presenter(draft = draft).load()

        assertEquals(FeesAllocationModeUiState.Equal, state.mode)
    }

    @Test
    fun `custom mode exposes compact overview rows with fee breakdowns`() = runBlocking {
        val presenter = presenter(
            draft = feesAllocationDraft(
                fees = listOf(
                    feesAllocationFee(id = "tax", label = "Tax", amount = MoneyMinor(400)),
                    feesAllocationFee(id = "service", label = "Service", amount = MoneyMinor(200)),
                ),
            ),
        )
        var state = presenter.load()

        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))

        assertEquals("Set exact fee amounts manually.", state.helperText)
        assertEquals(listOf("$4.50", "$1.50"), state.customOverviewRows.map { row -> row.totalLabel })
        assertEquals("Tax $3.00 · Service $1.50", state.customOverviewRows.first().breakdownLabel)
    }

    @Test
    fun `opening a fee editor exposes only that fee rows and status`() = runBlocking {
        val presenter = presenter(draft = feesAllocationDraft())
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))

        state = presenter.reduce(state, FeesAllocationUiEvent.FeeEditorOpenClick("fee-1"))

        val editor = requireNotNull(state.selectedFeeEditor)
        assertEquals("fee-1", editor.feeId)
        assertEquals("Edit tax", editor.title)
        assertEquals("Tax total $4.00", editor.totalLabel)
        assertEquals("Fully allocated", editor.statusLabel)
        assertEquals(listOf("p1", "p2"), editor.rows.map { row -> row.participantId })
    }

    @Test
    fun `editing one fee does not mutate unrelated fee allocations`() = runBlocking {
        val presenter = presenter(
            draft = feesAllocationDraft(
                fees = listOf(
                    feesAllocationFee(id = "tax", label = "Tax", amount = MoneyMinor(400)),
                    feesAllocationFee(id = "service", label = "Service", amount = MoneyMinor(200)),
                ),
            ),
        )
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))
        val originalServiceRows = state.feeCards.first { card -> card.id == "service" }.participantRows.map { row -> row.customAmount }

        state = presenter.reduce(state, FeesAllocationUiEvent.CustomAmountChanged("tax", "p1", "2.00"))

        assertEquals(originalServiceRows, state.feeCards.first { card -> card.id == "service" }.participantRows.map { row -> row.customAmount })
    }

    @Test
    fun `assign all fees to one person sets selected participant to every fee total`() = runBlocking {
        val presenter = presenter(
            draft = feesAllocationDraft(
                fees = listOf(
                    feesAllocationFee(id = "tax", label = "Tax", amount = MoneyMinor(400)),
                    feesAllocationFee(id = "service", label = "Service", amount = MoneyMinor(200)),
                ),
            ),
        )
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))
        state = presenter.reduce(state, FeesAllocationUiEvent.AssignAllFeesClick)

        state = presenter.reduce(state, FeesAllocationUiEvent.ParticipantPicked("p2"))

        assertNull(state.participantPicker)
        assertEquals(listOf("0.00", "4.00"), state.feeCards.first { card -> card.id == "tax" }.participantRows.map { row -> row.customAmount })
        assertEquals(listOf("0.00", "2.00"), state.feeCards.first { card -> card.id == "service" }.participantRows.map { row -> row.customAmount })
        assertEquals(listOf("$0.00", "$6.00"), state.customOverviewRows.map { row -> row.totalLabel })
        assertTrue(state.canContinue)
    }

    @Test
    fun `assign this fee to one person changes only selected fee`() = runBlocking {
        val presenter = presenter(
            draft = feesAllocationDraft(
                fees = listOf(
                    feesAllocationFee(id = "tax", label = "Tax", amount = MoneyMinor(400)),
                    feesAllocationFee(id = "service", label = "Service", amount = MoneyMinor(200)),
                ),
            ),
        )
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))
        val originalServiceRows = state.feeCards.first { card -> card.id == "service" }.participantRows.map { row -> row.customAmount }

        state = presenter.reduce(state, FeesAllocationUiEvent.AssignThisFeeClick("tax"))
        state = presenter.reduce(state, FeesAllocationUiEvent.ParticipantPicked("p2"))

        assertEquals(listOf("0.00", "4.00"), state.feeCards.first { card -> card.id == "tax" }.participantRows.map { row -> row.customAmount })
        assertEquals(originalServiceRows, state.feeCards.first { card -> card.id == "service" }.participantRows.map { row -> row.customAmount })
    }

    @Test
    fun `manual edit after quick assignment adjusts covering participant`() = runBlocking {
        val presenter = presenter(draft = feesAllocationDraft())
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))
        state = presenter.reduce(state, FeesAllocationUiEvent.AssignThisFeeClick("fee-1"))
        state = presenter.reduce(state, FeesAllocationUiEvent.ParticipantPicked("p1"))

        state = presenter.reduce(state, FeesAllocationUiEvent.CustomAmountChanged("fee-1", "p2", "1.50"))

        val taxRows = state.feeCards.single().participantRows
        assertEquals(listOf("2.50", "1.50"), taxRows.map { row -> row.customAmount })
        assertEquals("Fully allocated", state.feeCards.single().statusLabel)
        assertTrue(state.canContinue)
    }

    @Test
    fun `manual edit beyond covering participant capacity shows over allocation`() = runBlocking {
        val presenter = presenter(draft = feesAllocationDraft())
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))
        state = presenter.reduce(state, FeesAllocationUiEvent.AssignThisFeeClick("fee-1"))
        state = presenter.reduce(state, FeesAllocationUiEvent.ParticipantPicked("p1"))

        state = presenter.reduce(state, FeesAllocationUiEvent.CustomAmountChanged("fee-1", "p2", "5.00"))

        val taxRows = state.feeCards.single().participantRows
        assertEquals(listOf("0.00", "5.00"), taxRows.map { row -> row.customAmount })
        assertEquals("Over by $1.00", state.feeCards.single().statusLabel)
        assertTrue(taxRows.first { row -> row.participantId == "p2" }.customIsError)
        assertEquals("Allocate all fees to continue.", state.invalidReason)
        assertFalse(state.canContinue)
    }

    @Test
    fun `reset to proportional confirmation appears only when custom differs`() = runBlocking {
        val presenter = presenter(draft = feesAllocationDraft())
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))

        assertFalse(state.canResetToProportional)
        state = presenter.reduce(state, FeesAllocationUiEvent.ResetToProportionalClick)
        assertFalse(state.showResetToProportionalConfirmation)

        state = presenter.reduce(state, FeesAllocationUiEvent.CustomAmountChanged("fee-1", "p2", "2.00"))
        assertTrue(state.canResetToProportional)
        state = presenter.reduce(state, FeesAllocationUiEvent.ResetToProportionalClick)
        assertTrue(state.showResetToProportionalConfirmation)
    }

    @Test
    fun `confirming reset switches to proportional and recomputes allocations`() = runBlocking {
        val presenter = presenter(draft = feesAllocationDraft())
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))
        state = presenter.reduce(state, FeesAllocationUiEvent.CustomAmountChanged("fee-1", "p2", "2.00"))
        state = presenter.reduce(state, FeesAllocationUiEvent.ResetToProportionalClick)

        state = presenter.reduce(state, FeesAllocationUiEvent.ResetToProportionalConfirmed)

        assertEquals(FeesAllocationModeUiState.Proportional, state.mode)
        assertEquals(listOf("$3.00", "$1.00"), state.participants.map { participant -> participant.allocatedFeesLabel })
        assertFalse(state.showResetToProportionalConfirmation)
    }

    @Test
    fun `assign all fees exposes undo snapshot and undo restores custom allocations`() = runBlocking {
        val presenter = presenter(
            draft = feesAllocationDraft(
                fees = listOf(
                    feesAllocationFee(id = "tax", label = "Tax", amount = MoneyMinor(400)),
                    feesAllocationFee(id = "service", label = "Service", amount = MoneyMinor(200)),
                ),
            ),
        )
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))
        val originalRows = state.customRowsByFee()

        state = presenter.reduce(state, FeesAllocationUiEvent.AssignAllFeesClick)
        state = presenter.reduce(state, FeesAllocationUiEvent.ParticipantPicked("p2"))

        assertEquals("Assigned all fees to Amy", state.feedback?.message)
        assertTrue(state.undoSnackbarId > 0L)
        assertTrue(state.undoSnapshot != null)
        assertEquals(listOf("$0.00", "$6.00"), state.customOverviewRows.map { row -> row.totalLabel })

        state = presenter.reduce(state, FeesAllocationUiEvent.UndoAutomaticChangeClick)

        assertEquals(originalRows, state.customRowsByFee())
        assertEquals(listOf("$4.50", "$1.50"), state.customOverviewRows.map { row -> row.totalLabel })
        assertNull(state.undoSnapshot)
        assertEquals("Fee changes restored", state.feedback?.message)
    }

    @Test
    fun `reset to proportional exposes undo snapshot and undo restores custom mode`() = runBlocking {
        val presenter = presenter(draft = feesAllocationDraft())
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))
        state = presenter.reduce(state, FeesAllocationUiEvent.CustomAmountChanged("fee-1", "p1", "2.00"))
        state = presenter.reduce(state, FeesAllocationUiEvent.CustomAmountChanged("fee-1", "p2", "2.00"))
        val customRows = state.customRowsByFee()
        state = presenter.reduce(state, FeesAllocationUiEvent.ResetToProportionalClick)

        state = presenter.reduce(state, FeesAllocationUiEvent.ResetToProportionalConfirmed)

        assertEquals(FeesAllocationModeUiState.Proportional, state.mode)
        assertEquals("Reset to proportional", state.feedback?.message)
        assertTrue(state.undoSnapshot != null)

        state = presenter.reduce(state, FeesAllocationUiEvent.UndoAutomaticChangeClick)

        assertEquals(FeesAllocationModeUiState.Custom, state.mode)
        assertEquals(customRows, state.customRowsByFee())
        assertEquals("Fee changes restored", state.feedback?.message)
        assertTrue(state.canContinue)
    }

    @Test
    fun `completing custom allocations exposes completion feedback`() = runBlocking {
        val presenter = presenter(draft = feesAllocationDraft())
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))

        state = presenter.reduce(state, FeesAllocationUiEvent.CustomAmountChanged("fee-1", "p1", "1.00"))
        assertNull(state.feedback)
        assertFalse(state.canContinue)

        state = presenter.reduce(state, FeesAllocationUiEvent.CustomAmountChanged("fee-1", "p2", "3.00"))

        assertTrue(state.canContinue)
        assertEquals("All fees allocated", state.feedback?.message)
    }

    @Test
    fun `undo dismissal clears automatic change snapshot`() = runBlocking {
        val presenter = presenter(draft = feesAllocationDraft())
        var state = presenter.load()
        state = presenter.reduce(state, FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom))
        state = presenter.reduce(state, FeesAllocationUiEvent.AssignAllFeesClick)
        state = presenter.reduce(state, FeesAllocationUiEvent.ParticipantPicked("p2"))

        state = presenter.reduce(state, FeesAllocationUiEvent.UndoAutomaticChangeDismissed)

        assertNull(state.undoSnapshot)
    }

    private fun FeesAllocationUiState.customRowsByFee(): Map<String, List<String>> {
        return feeCards.associate { card ->
            card.id to card.participantRows.map { row -> row.customAmount }
        }
    }

    private fun presenter(draft: ExpenseDraft): FeesAllocationPresenter {
        return FeesAllocationPresenter(FakeExpenseDraftRepository(draft), TestAllocateFeesUseCase)
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

    private object TestAllocateFeesUseCase : AllocateFeesUseCase {
        override fun allocateEqual(
            fee: ReceiptFee,
            participants: List<Participant>,
        ): FeeAllocation {
            val shares = splitEvenly(fee.amount.value, participants.sortedBy { participant -> participant.creationOrder })
            return FeeAllocation(fee.id, FeeAllocationMode.Equal, shares)
        }

        override fun allocateProportional(
            fee: ReceiptFee,
            participants: List<Participant>,
            itemAssignments: List<ItemAssignment>,
        ): FeeAllocation {
            val subtotals = participants.associate { participant ->
                participant.id to itemAssignments
                    .flatMap { assignment -> assignment.shares }
                    .filter { share -> share.participantId == participant.id }
                    .sumOf { share -> share.amount.value }
            }
            val totalSubtotal = subtotals.values.sum()
            val shares = if (totalSubtotal == 0L) {
                splitEvenly(fee.amount.value, participants.sortedBy { participant -> participant.creationOrder })
            } else {
                participants.sortedBy { participant -> participant.creationOrder }.map { participant ->
                    FeeParticipantShare(
                        participantId = participant.id,
                        amount = MoneyMinor((fee.amount.value * subtotals.getValue(participant.id)) / totalSubtotal),
                    )
                }
            }
            return FeeAllocation(fee.id, FeeAllocationMode.Proportional, shares)
        }

        override fun validateCustom(
            fee: ReceiptFee,
            participants: List<Participant>,
            shares: List<FeeParticipantShare>,
        ): FeeAllocationValidationResult {
            val hasUnknown = shares.any { share -> share.participantId !in participants.map { participant -> participant.id } }
            val hasNegative = shares.any { share -> share.amount.value < 0L }
            val hasMismatch = shares.sumOf { share -> share.amount.value } != fee.amount.value
            val errors = buildSet {
                if (shares.isEmpty()) add(FeeAllocationValidationError.EmptyShares)
                if (hasUnknown) add(FeeAllocationValidationError.UnknownParticipant)
                if (hasNegative) add(FeeAllocationValidationError.NegativeShareAmount)
                if (hasMismatch) add(FeeAllocationValidationError.AmountTotalMismatch)
            }
            return if (errors.isEmpty()) {
                FeeAllocationValidationResult.Valid
            } else {
                FeeAllocationValidationResult(errors)
            }
        }

        private fun splitEvenly(
            total: Long,
            participants: List<Participant>,
        ): List<FeeParticipantShare> {
            val base = total / participants.size
            var remainder = total % participants.size
            return participants.map { participant ->
                val extra = if (remainder > 0) {
                    remainder -= 1
                    1L
                } else {
                    0L
                }
                FeeParticipantShare(participant.id, MoneyMinor(base + extra))
            }
        }
    }
}

internal fun feesAllocationDraft(
    currencyCode: String = "USD",
    fees: List<ReceiptFee> = listOf(feesAllocationFee()),
    itemAssignments: List<ItemAssignment> = listOf(
        feesAllocationAssignment("item-1", "p1", 300),
        feesAllocationAssignment("item-2", "p2", 100),
    ),
    feeAllocations: List<FeeAllocation> = emptyList(),
): ExpenseDraft {
    return ExpenseDraft(
        id = ExpenseDraftId("draft-1"),
        receipt = Receipt(
            merchantName = "Cafe",
            currencyCode = CurrencyCode(currencyCode),
            items = emptyList(),
            fees = fees,
            total = MoneyMinor(fees.sumOf { fee -> fee.amount.value }),
        ),
        participants = listOf(
            Participant(ParticipantId("p1"), "John", 0),
            Participant(ParticipantId("p2"), "Amy", 1),
        ),
        payerId = ParticipantId("p1"),
        itemAssignments = itemAssignments,
        feeAllocations = feeAllocations,
    )
}

internal fun feesAllocationFee(
    id: String = "fee-1",
    label: String = "Tax",
    amount: MoneyMinor = MoneyMinor(400),
): ReceiptFee {
    return ReceiptFee(
        id = FeeId(id),
        type = FeeType.Tax,
        label = label,
        amount = amount,
    )
}

private fun feesAllocationAssignment(
    itemId: String,
    participantId: String,
    amount: Long,
): ItemAssignment {
    return ItemAssignment(
        receiptItemId = ReceiptItemId(itemId),
        mode = ItemAssignmentMode.Full,
        shares = listOf(
            ItemParticipantShare(ParticipantId(participantId), MoneyMinor(amount)),
        ),
    )
}
