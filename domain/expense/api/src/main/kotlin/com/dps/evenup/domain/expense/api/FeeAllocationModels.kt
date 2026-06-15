package com.dps.evenup.domain.expense.api

import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.FeeId
import com.dps.evenup.domain.receipt.api.MoneyMinor

data class FeeAllocation(
    val feeId: FeeId,
    val mode: FeeAllocationMode,
    val shares: List<FeeParticipantShare>,
)

data class FeeParticipantShare(
    val participantId: ParticipantId,
    val amount: MoneyMinor,
)

enum class FeeAllocationMode {
    Equal,
    Proportional,
    Custom,
}
