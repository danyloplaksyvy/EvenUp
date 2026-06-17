package com.dps.evenup.domain.expense.api

import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Quantity
import com.dps.evenup.domain.receipt.api.ReceiptItemId

data class ItemAssignment(
    val receiptItemId: ReceiptItemId,
    val mode: ItemAssignmentMode,
    val shares: List<ItemParticipantShare>,
)

data class ItemParticipantShare(
    val participantId: ParticipantId,
    val amount: MoneyMinor,
    val quantity: Quantity? = null,
    val percentage: PercentageBasisPoints? = null,
)

enum class ItemAssignmentMode {
    Full,
    ByUnits,
    SharedEqual,
    CustomAmount,
    Percentage,
}
