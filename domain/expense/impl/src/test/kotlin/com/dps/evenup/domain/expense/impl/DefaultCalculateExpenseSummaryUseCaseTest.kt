package com.dps.evenup.domain.expense.impl

import com.dps.evenup.domain.expense.api.FeeAllocation
import com.dps.evenup.domain.expense.api.FeeAllocationMode
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
import com.dps.evenup.domain.receipt.api.Quantity
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptFee
import com.dps.evenup.domain.receipt.api.ReceiptItem
import com.dps.evenup.domain.receipt.api.ReceiptItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultCalculateExpenseSummaryUseCaseTest {
    private val useCase = DefaultCalculateExpenseSummaryUseCase()

    @Test
    fun `summary totals and one payer settlement are correct`() {
        val summary = useCase.calculate(
            receipt = receipt(),
            participants = participants(),
            payerId = ParticipantId("anna"),
            itemAssignments = itemAssignments(),
            feeAllocations = feeAllocations(),
        )

        assertEquals(MoneyMinor(12_050), summary.participantShareTotal)
        assertEquals(MoneyMinor.Zero, summary.netBalanceTotal)
        assertEquals(2, summary.settlementRows.size)
        assertEquals(MoneyMinor(4_000), summary.settlementRows[0].amount)
        assertEquals(ParticipantId("anna"), summary.settlementRows[0].toParticipantId)
        assertEquals(MoneyMinor(4_550), summary.settlementRows[1].amount)
    }

    @Test
    fun `discount credits reduce participant shares proportionally to assigned items`() {
        val summary = useCase.calculate(
            receipt = receipt().copy(
                fees = receipt().fees + ReceiptFee(FeeId("discount"), FeeType.Discount, "Promo", MoneyMinor(-1_000)),
                total = MoneyMinor(11_050),
            ),
            participants = participants(),
            payerId = ParticipantId("anna"),
            itemAssignments = itemAssignments(),
            feeAllocations = feeAllocations(),
        )

        val anna = summary.participantSummaries.first { participant -> participant.participantId == ParticipantId("anna") }
        val ben = summary.participantSummaries.first { participant -> participant.participantId == ParticipantId("ben") }
        val chris = summary.participantSummaries.first { participant -> participant.participantId == ParticipantId("chris") }

        assertEquals(MoneyMinor(300), anna.discountCreditTotal)
        assertEquals(MoneyMinor(300), ben.discountCreditTotal)
        assertEquals(MoneyMinor(400), chris.discountCreditTotal)
        assertEquals(MoneyMinor(11_050), summary.participantShareTotal)
        assertEquals(MoneyMinor.Zero, summary.netBalanceTotal)
    }

    @Test
    fun `discount credits fall back to equal split when assigned item subtotals are unavailable`() {
        val summary = useCase.calculate(
            receipt = receipt().copy(
                fees = listOf(ReceiptFee(FeeId("discount"), FeeType.Discount, "Promo", MoneyMinor(-100))),
                total = MoneyMinor(9_900),
            ),
            participants = participants(),
            payerId = ParticipantId("anna"),
            itemAssignments = emptyList(),
            feeAllocations = emptyList(),
        )

        assertEquals(listOf(34L, 33L, 33L), summary.participantSummaries.map { it.discountCreditTotal.value })
    }

    private fun participants(): List<Participant> = listOf(
        Participant(ParticipantId("anna"), "Anna", 0),
        Participant(ParticipantId("ben"), "Ben", 1),
        Participant(ParticipantId("chris"), "Chris", 2),
    )

    private fun receipt(): Receipt = Receipt(
        merchantName = "Example",
        currencyCode = CurrencyCode("USD"),
        items = listOf(
            ReceiptItem(ReceiptItemId("item-1"), "Meal", Quantity(1), MoneyMinor(10_000), MoneyMinor(10_000)),
        ),
        fees = listOf(ReceiptFee(FeeId("fee-1"), FeeType.Tax, "Tax", MoneyMinor(2_050))),
        total = MoneyMinor(12_050),
    )

    private fun itemAssignments(): List<ItemAssignment> = listOf(
        ItemAssignment(
            receiptItemId = ReceiptItemId("item-1"),
            mode = ItemAssignmentMode.CustomAmount,
            shares = listOf(
                ItemParticipantShare(ParticipantId("anna"), MoneyMinor(3_000)),
                ItemParticipantShare(ParticipantId("ben"), MoneyMinor(3_000)),
                ItemParticipantShare(ParticipantId("chris"), MoneyMinor(4_000)),
            ),
        ),
    )

    private fun feeAllocations(): List<FeeAllocation> = listOf(
        FeeAllocation(
            feeId = FeeId("fee-1"),
            mode = FeeAllocationMode.Custom,
            shares = listOf(
                FeeParticipantShare(ParticipantId("anna"), MoneyMinor(500)),
                FeeParticipantShare(ParticipantId("ben"), MoneyMinor(1_000)),
                FeeParticipantShare(ParticipantId("chris"), MoneyMinor(550)),
            ),
        ),
    )
}
