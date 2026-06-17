package com.dps.evenup.domain.expense.api

import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.Receipt

data class ExpenseDraft(
    val id: ExpenseDraftId,
    val receipt: Receipt,
    val participants: List<Participant>,
    val payerId: ParticipantId,
    val itemAssignments: List<ItemAssignment>,
    val feeAllocations: List<FeeAllocation>,
)

data class FinalizedExpensePayload(
    val draftId: ExpenseDraftId,
    val receipt: Receipt,
    val participants: List<Participant>,
    val payerId: ParticipantId,
    val itemAssignments: List<ItemAssignment>,
    val feeAllocations: List<FeeAllocation>,
    val summary: ExpenseSummary,
)
