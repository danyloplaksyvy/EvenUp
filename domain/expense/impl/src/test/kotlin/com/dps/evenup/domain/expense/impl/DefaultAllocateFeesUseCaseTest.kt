package com.dps.evenup.domain.expense.impl

import com.dps.evenup.domain.expense.api.FeeAllocationValidationError
import com.dps.evenup.domain.expense.api.FeeParticipantShare
import com.dps.evenup.domain.expense.api.ItemAssignment
import com.dps.evenup.domain.expense.api.ItemAssignmentMode
import com.dps.evenup.domain.expense.api.ItemParticipantShare
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.FeeId
import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.ReceiptFee
import com.dps.evenup.domain.receipt.api.ReceiptItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAllocateFeesUseCaseTest {
    private val useCase = DefaultAllocateFeesUseCase()

    @Test
    fun `equal allocation distributes remainder cents deterministically`() {
        val allocation = useCase.allocateEqual(fee(amount = 1_001), participants())

        assertEquals(MoneyMinor(334), allocation.shares[0].amount)
        assertEquals(MoneyMinor(334), allocation.shares[1].amount)
        assertEquals(MoneyMinor(333), allocation.shares[2].amount)
    }

    @Test
    fun `proportional allocation uses item subtotal by participant`() {
        val allocation = useCase.allocateProportional(
            fee = fee(amount = 300),
            participants = participants().take(2),
            itemAssignments = listOf(
                assignment("item-1", "p1", 1_000),
                assignment("item-2", "p2", 2_000),
            ),
        )

        assertEquals(MoneyMinor(100), allocation.shares[0].amount)
        assertEquals(MoneyMinor(200), allocation.shares[1].amount)
    }

    @Test
    fun `custom allocation validates exact totals`() {
        val result = useCase.validateCustom(
            fee = fee(amount = 300),
            participants = participants().take(2),
            shares = listOf(
                FeeParticipantShare(ParticipantId("p1"), MoneyMinor(100)),
                FeeParticipantShare(ParticipantId("p2"), MoneyMinor(150)),
            ),
        )

        assertTrue(FeeAllocationValidationError.AmountTotalMismatch in result.errors)
    }

    private fun fee(amount: Long): ReceiptFee = ReceiptFee(
        id = FeeId("fee-1"),
        type = FeeType.Tax,
        label = "Tax",
        amount = MoneyMinor(amount),
    )

    private fun participants(): List<Participant> = listOf(
        Participant(ParticipantId("p1"), "Anna", 0),
        Participant(ParticipantId("p2"), "Ben", 1),
        Participant(ParticipantId("p3"), "Chris", 2),
    )

    private fun assignment(itemId: String, participantId: String, amount: Long): ItemAssignment {
        return ItemAssignment(
            receiptItemId = ReceiptItemId(itemId),
            mode = ItemAssignmentMode.Full,
            shares = listOf(
                ItemParticipantShare(ParticipantId(participantId), MoneyMinor(amount)),
            ),
        )
    }
}
