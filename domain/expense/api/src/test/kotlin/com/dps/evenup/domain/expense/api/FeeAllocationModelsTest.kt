package com.dps.evenup.domain.expense.api

import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.FeeId
import com.dps.evenup.domain.receipt.api.MoneyMinor
import org.junit.Assert.assertEquals
import org.junit.Test

class FeeAllocationModelsTest {
    @Test
    fun `fee allocation supports proportional mode`() {
        val allocation = FeeAllocation(
            feeId = FeeId("fee-1"),
            mode = FeeAllocationMode.Proportional,
            shares = listOf(
                FeeParticipantShare(
                    participantId = ParticipantId("participant-1"),
                    amount = MoneyMinor(275),
                ),
            ),
        )

        assertEquals(FeeAllocationMode.Proportional, allocation.mode)
        assertEquals(MoneyMinor(275), allocation.shares.single().amount)
    }
}
