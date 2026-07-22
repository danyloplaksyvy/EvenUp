package com.dps.evenup.domain.expense.impl

import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.ExpenseBaseAllocationMode
import com.dps.evenup.domain.expense.api.FinalExpenseValidationError
import com.dps.evenup.domain.expense.api.FinalExpenseValidationResult
import com.dps.evenup.domain.expense.api.FinalizedExpensePayload
import com.dps.evenup.domain.expense.api.ValidateExpenseBeforeSaveUseCase
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.ExpensePricingMode
import com.dps.evenup.domain.receipt.api.ValidateReceiptUseCase
import com.dps.evenup.domain.participant.api.ValidateParticipantsUseCase

class DefaultValidateExpenseBeforeSaveUseCase(
    private val validateReceipt: ValidateReceiptUseCase,
    private val validateParticipants: ValidateParticipantsUseCase,
    private val validateItemAssignments: DefaultValidateItemAssignmentsUseCase,
    private val allocateFees: DefaultAllocateFeesUseCase,
    private val calculateSummary: DefaultCalculateExpenseSummaryUseCase,
) : ValidateExpenseBeforeSaveUseCase {
    override fun validateAndBuildPayload(draft: ExpenseDraft): FinalExpenseValidationResult {
        val errors = buildSet {
            if (!validateReceipt.validate(draft.receipt).isValid) add(FinalExpenseValidationError.InvalidReceipt)
            if (!validateParticipants.validate(draft.participants, draft.payerId).isValid) {
                add(FinalExpenseValidationError.InvalidParticipants)
            }
            if (draft.receipt.pricingMode == ExpensePricingMode.Itemized) {
                if (!validateItemAssignments.validate(
                        receiptItems = draft.receipt.items,
                        participants = draft.participants,
                        assignments = draft.itemAssignments,
                    ).isValid
                ) {
                    add(FinalExpenseValidationError.InvalidItemAssignments)
                }
            } else if (!draft.hasValidBaseAllocation()) {
                add(FinalExpenseValidationError.InvalidBaseAllocation)
            }

            draft.feeAllocations.forEach { allocation ->
                val fee = draft.receipt.fees.firstOrNull { it.id == allocation.feeId }
                if (fee == null || !allocateFees.validateCustom(fee, draft.participants, allocation.shares).isValid) {
                    add(FinalExpenseValidationError.InvalidFeeAllocations)
                }
            }
        }

        if (errors.isNotEmpty()) return FinalExpenseValidationResult.Invalid(errors)

        val summary = calculateSummary.calculate(
            receipt = draft.receipt,
            participants = draft.participants,
            payerId = draft.payerId,
            itemAssignments = draft.itemAssignments,
            feeAllocations = draft.feeAllocations,
            baseAllocation = draft.baseAllocation,
        )

        val summaryErrors = buildSet {
            if (summary.participantShareTotal != draft.receipt.total) {
                add(FinalExpenseValidationError.ParticipantSharesDoNotEqualReceiptTotal)
            }
            if (summary.netBalanceTotal != MoneyMinor.Zero) {
                add(FinalExpenseValidationError.NetBalancesDoNotSumToZero)
            }
        }

        if (summaryErrors.isNotEmpty()) return FinalExpenseValidationResult.Invalid(summaryErrors)

        return FinalExpenseValidationResult.Valid(
            FinalizedExpensePayload(
                draftId = draft.id,
                receipt = draft.receipt,
                participants = draft.participants,
                payerId = draft.payerId,
                itemAssignments = draft.itemAssignments,
                feeAllocations = draft.feeAllocations,
                summary = summary,
                baseAllocation = draft.baseAllocation,
            ),
        )
    }

    private fun ExpenseDraft.hasValidBaseAllocation(): Boolean {
        val allocation = baseAllocation ?: return false
        val participantIds = participants.map { participant -> participant.id }.toSet()
        if (allocation.mode != ExpenseBaseAllocationMode.Equal) return false
        if (allocation.shares.size != participants.size) return false
        if (allocation.shares.map { share -> share.participantId }.toSet() != participantIds) return false
        if (allocation.shares.any { share -> share.amount.value < 0L }) return false
        val feeTotal = receipt.fees.sumOf { fee -> fee.amount.value }
        val expectedBase = receipt.total.value - feeTotal
        val shareAmounts = allocation.shares.map { share -> share.amount.value }
        return expectedBase >= 0L &&
            shareAmounts.sum() == expectedBase &&
            shareAmounts.maxOrNull()!! - shareAmounts.minOrNull()!! <= 1L
    }
}
