package com.dps.evenup.domain.expense.api

import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Quantity
import com.dps.evenup.domain.receipt.api.ReceiptItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class AssignmentModelsTest {
    @Test
    fun `item assignment supports by-units shares`() {
        val assignment = ItemAssignment(
            receiptItemId = ReceiptItemId("item-1"),
            mode = ItemAssignmentMode.ByUnits,
            shares = listOf(
                ItemParticipantShare(
                    participantId = ParticipantId("participant-1"),
                    quantity = Quantity(1),
                    amount = MoneyMinor(1_200),
                ),
            ),
        )

        assertEquals(ItemAssignmentMode.ByUnits, assignment.mode)
        assertEquals(Quantity(1), assignment.shares.single().quantity)
    }

    @Test
    fun `item assignment supports percentage shares`() {
        val assignment = ItemAssignment(
            receiptItemId = ReceiptItemId("item-1"),
            mode = ItemAssignmentMode.Percentage,
            shares = listOf(
                ItemParticipantShare(
                    participantId = ParticipantId("participant-1"),
                    amount = MoneyMinor(500),
                    percentage = PercentageBasisPoints(5_000),
                ),
            ),
        )

        assertEquals(PercentageBasisPoints(5_000), assignment.shares.single().percentage)
    }
}
