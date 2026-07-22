package com.dps.evenup.domain.expense.impl

import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.ExpenseDraftId
import com.dps.evenup.domain.expense.api.ExpenseBaseAllocation
import com.dps.evenup.domain.expense.api.ExpenseBaseAllocationMode
import com.dps.evenup.domain.expense.api.ExpenseBaseParticipantShare
import com.dps.evenup.domain.expense.api.FinalExpenseValidationError
import com.dps.evenup.domain.expense.api.FinalExpenseValidationResult
import com.dps.evenup.domain.expense.api.FeeAllocation
import com.dps.evenup.domain.expense.api.FeeAllocationMode
import com.dps.evenup.domain.expense.api.FeeParticipantShare
import com.dps.evenup.domain.expense.api.ItemAssignment
import com.dps.evenup.domain.expense.api.ItemAssignmentMode
import com.dps.evenup.domain.expense.api.ItemParticipantShare
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.participant.api.ParticipantValidationResult
import com.dps.evenup.domain.participant.api.ValidateParticipantsUseCase
import com.dps.evenup.domain.receipt.api.CurrencyCode
import com.dps.evenup.domain.receipt.api.ExpensePricingMode
import com.dps.evenup.domain.receipt.api.FeeId
import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Quantity
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptFee
import com.dps.evenup.domain.receipt.api.ReceiptItem
import com.dps.evenup.domain.receipt.api.ReceiptItemId
import com.dps.evenup.domain.receipt.api.ReceiptValidationError
import com.dps.evenup.domain.receipt.api.ReceiptValidationResult
import com.dps.evenup.domain.receipt.api.ValidateReceiptUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultValidateExpenseBeforeSaveUseCaseTest {
    @Test
    fun `invalid draft cannot become finalized expense`() {
        val useCase = useCase(
            receiptResult = ReceiptValidationResult(setOf(ReceiptValidationError.NoItems)),
        )

        val result = useCase.validateAndBuildPayload(validDraft())

        assertTrue(result is FinalExpenseValidationResult.Invalid)
        result as FinalExpenseValidationResult.Invalid
        assertTrue(FinalExpenseValidationError.InvalidReceipt in result.errors)
    }

    @Test
    fun `valid draft produces finalized expense payload`() {
        val result = useCase().validateAndBuildPayload(validDraft())

        assertTrue(result is FinalExpenseValidationResult.Valid)
        result as FinalExpenseValidationResult.Valid
        assertEquals(ExpenseDraftId("draft-1"), result.payload.draftId)
        assertEquals(MoneyMinor(1_200), result.payload.summary.participantShareTotal)
        assertEquals(MoneyMinor.Zero, result.payload.summary.netBalanceTotal)
    }

    @Test
    fun `valid total-only draft produces payload with explicit base allocation`() {
        val draft = validDraft().copy(
            receipt = receipt().copy(
                items = emptyList(),
                fees = emptyList(),
                subtotal = MoneyMinor(1_001),
                total = MoneyMinor(1_001),
                pricingMode = ExpensePricingMode.TotalOnly,
            ),
            itemAssignments = emptyList(),
            feeAllocations = emptyList(),
            baseAllocation = ExpenseBaseAllocation(
                ExpenseBaseAllocationMode.Equal,
                listOf(
                    ExpenseBaseParticipantShare(ParticipantId("p1"), MoneyMinor(501)),
                    ExpenseBaseParticipantShare(ParticipantId("p2"), MoneyMinor(500)),
                ),
            ),
        )

        val result = useCase().validateAndBuildPayload(draft)

        assertTrue(result is FinalExpenseValidationResult.Valid)
        result as FinalExpenseValidationResult.Valid
        assertEquals(listOf(501L, 500L), result.payload.summary.participantSummaries.map { it.baseShare.value })
        assertEquals(MoneyMinor(1_001), result.payload.summary.participantShareTotal)
    }

    @Test
    fun `inconsistent total-only base allocation blocks save`() {
        val draft = validDraft().copy(
            receipt = receipt().copy(
                items = emptyList(),
                fees = emptyList(),
                total = MoneyMinor(1_001),
                pricingMode = ExpensePricingMode.TotalOnly,
            ),
            itemAssignments = emptyList(),
            feeAllocations = emptyList(),
            baseAllocation = ExpenseBaseAllocation(
                ExpenseBaseAllocationMode.Equal,
                listOf(
                    ExpenseBaseParticipantShare(ParticipantId("p1"), MoneyMinor(500)),
                    ExpenseBaseParticipantShare(ParticipantId("p2"), MoneyMinor(500)),
                ),
            ),
        )

        val result = useCase().validateAndBuildPayload(draft) as FinalExpenseValidationResult.Invalid

        assertTrue(FinalExpenseValidationError.InvalidBaseAllocation in result.errors)
    }

    @Test
    fun `duplicate or non-equal total-only shares block save`() {
        val baseDraft = validDraft().copy(
            receipt = receipt().copy(
                items = emptyList(),
                fees = emptyList(),
                subtotal = MoneyMinor(1_000),
                total = MoneyMinor(1_000),
                pricingMode = ExpensePricingMode.TotalOnly,
            ),
            itemAssignments = emptyList(),
            feeAllocations = emptyList(),
        )
        val duplicate = baseDraft.copy(
            baseAllocation = ExpenseBaseAllocation(
                ExpenseBaseAllocationMode.Equal,
                listOf(
                    ExpenseBaseParticipantShare(ParticipantId("p1"), MoneyMinor(500)),
                    ExpenseBaseParticipantShare(ParticipantId("p1"), MoneyMinor(500)),
                ),
            ),
        )
        val nonEqual = baseDraft.copy(
            baseAllocation = ExpenseBaseAllocation(
                ExpenseBaseAllocationMode.Equal,
                listOf(
                    ExpenseBaseParticipantShare(ParticipantId("p1"), MoneyMinor(600)),
                    ExpenseBaseParticipantShare(ParticipantId("p2"), MoneyMinor(400)),
                ),
            ),
        )

        listOf(duplicate, nonEqual).forEach { draft ->
            val result = useCase().validateAndBuildPayload(draft) as FinalExpenseValidationResult.Invalid
            assertTrue(FinalExpenseValidationError.InvalidBaseAllocation in result.errors)
        }
    }

    private fun useCase(
        receiptResult: ReceiptValidationResult = ReceiptValidationResult.Valid,
        participantResult: ParticipantValidationResult = ParticipantValidationResult.Valid,
    ): DefaultValidateExpenseBeforeSaveUseCase {
        return DefaultValidateExpenseBeforeSaveUseCase(
            validateReceipt = FakeValidateReceiptUseCase(receiptResult),
            validateParticipants = FakeValidateParticipantsUseCase(participantResult),
            validateItemAssignments = DefaultValidateItemAssignmentsUseCase(),
            allocateFees = DefaultAllocateFeesUseCase(),
            calculateSummary = DefaultCalculateExpenseSummaryUseCase(),
        )
    }

    private fun validDraft(): ExpenseDraft = ExpenseDraft(
        id = ExpenseDraftId("draft-1"),
        receipt = receipt(),
        participants = participants(),
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
    )

    private fun receipt(): Receipt = Receipt(
        merchantName = "Cafe",
        currencyCode = CurrencyCode("USD"),
        items = listOf(
            ReceiptItem(ReceiptItemId("item-1"), "Meal", Quantity(1), MoneyMinor(1_000), MoneyMinor(1_000)),
        ),
        fees = listOf(ReceiptFee(FeeId("fee-1"), FeeType.Tax, "Tax", MoneyMinor(200))),
        total = MoneyMinor(1_200),
    )

    private fun participants(): List<Participant> = listOf(
        Participant(ParticipantId("p1"), "Anna", 0),
        Participant(ParticipantId("p2"), "Ben", 1),
    )

    private class FakeValidateReceiptUseCase(
        private val result: ReceiptValidationResult,
    ) : ValidateReceiptUseCase {
        override fun validate(receipt: Receipt): ReceiptValidationResult = result
    }

    private class FakeValidateParticipantsUseCase(
        private val result: ParticipantValidationResult,
    ) : ValidateParticipantsUseCase {
        override fun validate(
            participants: List<Participant>,
            payerId: ParticipantId,
        ): ParticipantValidationResult = result
    }
}
