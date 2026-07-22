package com.dps.evenup.domain.expense.api

import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Receipt

interface CalculateExpenseSummaryUseCase {
    fun calculate(
        receipt: Receipt,
        participants: List<Participant>,
        payerId: ParticipantId,
        itemAssignments: List<ItemAssignment>,
        feeAllocations: List<FeeAllocation>,
    ): ExpenseSummary

    fun calculate(
        receipt: Receipt,
        participants: List<Participant>,
        payerId: ParticipantId,
        itemAssignments: List<ItemAssignment>,
        feeAllocations: List<FeeAllocation>,
        baseAllocation: ExpenseBaseAllocation?,
    ): ExpenseSummary = calculate(receipt, participants, payerId, itemAssignments, feeAllocations)
}

data class ExpenseSummary(
    val receiptTotal: MoneyMinor,
    val participantSummaries: List<ParticipantExpenseSummary>,
    val settlementRows: List<SettlementRow>,
) {
    val participantShareTotal: MoneyMinor = MoneyMinor(participantSummaries.sumOf { it.personShare.value })
    val netBalanceTotal: MoneyMinor = MoneyMinor(participantSummaries.sumOf { it.netBalance.value })
}

data class ParticipantExpenseSummary(
    val participantId: ParticipantId,
    val assignedItemTotal: MoneyMinor,
    val allocatedFeeTotal: MoneyMinor,
    val personShare: MoneyMinor,
    val amountPaid: MoneyMinor,
    val netBalance: MoneyMinor,
    val discountCreditTotal: MoneyMinor = MoneyMinor.Zero,
    val baseShare: MoneyMinor = MoneyMinor.Zero,
)

data class SettlementRow(
    val fromParticipantId: ParticipantId,
    val toParticipantId: ParticipantId,
    val amount: MoneyMinor,
)
