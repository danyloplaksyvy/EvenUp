package com.dps.evenup.domain.expense.api

import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.MoneyMinor

data class ExpenseBaseAllocation(
    val mode: ExpenseBaseAllocationMode,
    val shares: List<ExpenseBaseParticipantShare>,
)

enum class ExpenseBaseAllocationMode {
    Equal,
}

data class ExpenseBaseParticipantShare(
    val participantId: ParticipantId,
    val amount: MoneyMinor,
)
