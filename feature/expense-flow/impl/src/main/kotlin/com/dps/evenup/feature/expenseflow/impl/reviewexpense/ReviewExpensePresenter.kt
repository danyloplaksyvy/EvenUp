package com.dps.evenup.feature.expenseflow.impl.reviewexpense

import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.expense.api.ExpenseRepository
import com.dps.evenup.domain.expense.api.CalculateExpenseSummaryUseCase
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.FinalExpenseValidationError
import com.dps.evenup.domain.expense.api.FinalExpenseValidationResult
import com.dps.evenup.domain.expense.api.ValidateExpenseBeforeSaveUseCase
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.MoneyMinor
import java.math.BigDecimal

class ReviewExpensePresenter(
    private val draftRepository: ExpenseDraftRepository,
    private val expenseRepository: ExpenseRepository,
    private val calculateSummary: CalculateExpenseSummaryUseCase,
    private val validateExpenseBeforeSave: ValidateExpenseBeforeSaveUseCase,
) {
    suspend fun load(): ReviewExpenseUiState {
        val draft = draftRepository.getDraft() ?: return ReviewExpenseUiState(
            isLoading = false,
            missingDraft = true,
            submitError = "No expense draft was found.",
        )
        return buildState(draft, isLoading = false)
    }

    suspend fun reduce(
        state: ReviewExpenseUiState,
        event: ReviewExpenseUiEvent,
    ): ReviewExpenseUiState {
        return when (event) {
            ReviewExpenseUiEvent.CalculationDetailsToggled -> state.copy(detailsExpanded = !state.detailsExpanded)
            ReviewExpenseUiEvent.BackClick,
            ReviewExpenseUiEvent.SaveClick,
            -> state
        }
    }

    suspend fun saveDraft(): SaveReviewExpenseResult {
        val draft = draftRepository.getDraft() ?: return SaveReviewExpenseResult.MissingDraft
        return when (val result = validateExpenseBeforeSave.validateAndBuildPayload(draft)) {
            is FinalExpenseValidationResult.Valid -> {
                val savedShareLink = expenseRepository.saveFinalizedExpense(result.payload)
                SaveReviewExpenseResult.Saved(savedShareLink.shareLink.publicUrl)
            }
            is FinalExpenseValidationResult.Invalid -> SaveReviewExpenseResult.Invalid(result.errors.toValidationMessage())
        }
    }

    private fun buildState(
        draft: ExpenseDraft,
        isLoading: Boolean,
    ): ReviewExpenseUiState {
        val summary = calculateSummary.calculate(
            receipt = draft.receipt,
            participants = draft.participants,
            payerId = draft.payerId,
            itemAssignments = draft.itemAssignments,
            feeAllocations = draft.feeAllocations,
        )
        val participantColorIndexes = draft.participants.mapIndexed { index, participant ->
            participant.id to index
        }.toMap()
        val participantsById = draft.participants.associateBy { participant -> participant.id }
        val payer = participantsById.getValue(draft.payerId)
        val payerShare = summary.participantSummaries
            .firstOrNull { participantSummary -> participantSummary.participantId == draft.payerId }
            ?.personShare
            ?: MoneyMinor.Zero
        val validationError = when (val result = validateExpenseBeforeSave.validateAndBuildPayload(draft)) {
            is FinalExpenseValidationResult.Valid -> null
            is FinalExpenseValidationResult.Invalid -> result.errors.toValidationMessage()
        }

        return ReviewExpenseUiState(
            isLoading = isLoading,
            merchantName = draft.receipt.merchantName,
            totalLabel = formatMoney(summary.receiptTotal),
            payerName = payer.name,
            payerColorIndex = participantColorIndexes.getValue(payer.id),
            settlementRows = summary.settlementRows.mapNotNull { row ->
                val from = participantsById[row.fromParticipantId] ?: return@mapNotNull null
                val to = participantsById[row.toParticipantId] ?: return@mapNotNull null
                SettlementRowUiState(
                    fromParticipantName = from.name,
                    fromParticipantColorIndex = participantColorIndexes.getValue(from.id),
                    toParticipantName = to.name,
                    amountLabel = formatMoney(row.amount),
                )
            },
            detailRows = summary.participantSummaries.mapNotNull { participantSummary ->
                val participant = participantsById[participantSummary.participantId] ?: return@mapNotNull null
                ParticipantCalculationDetailUiState(
                    participantId = participant.id.value,
                    participantName = participant.name,
                    participantColorIndex = participantColorIndexes.getValue(participant.id),
                    itemSubtotalLabel = formatMoney(participantSummary.assignedItemTotal),
                    feesLabel = formatMoney(participantSummary.allocatedFeeTotal),
                    totalShareLabel = formatMoney(participantSummary.personShare),
                    amountPaidLabel = formatMoney(participantSummary.amountPaid),
                    netBalanceLabel = formatMoney(participantSummary.netBalance),
                )
            },
            payerShareLabel = formatMoney(payerShare),
            canSave = validationError == null,
            validationError = validationError,
        )
    }

    private fun Set<FinalExpenseValidationError>.toValidationMessage(): String {
        return when {
            FinalExpenseValidationError.InvalidReceipt in this -> "Review the receipt before saving."
            FinalExpenseValidationError.InvalidParticipants in this -> "Review participants and payer before saving."
            FinalExpenseValidationError.InvalidItemAssignments in this -> "Assign every item before saving."
            FinalExpenseValidationError.InvalidFeeAllocations in this -> "Review fee allocations before saving."
            FinalExpenseValidationError.ParticipantSharesDoNotEqualReceiptTotal in this -> {
                "Participant shares must equal the receipt total."
            }
            FinalExpenseValidationError.NetBalancesDoNotSumToZero in this -> "Net balances must add up to zero."
            else -> "This expense is not ready to save."
        }
    }

    private fun formatMoney(value: MoneyMinor): String {
        return "€${BigDecimal(value.value).movePointLeft(2).setScale(2).toPlainString()}"
    }
}

sealed interface SaveReviewExpenseResult {
    data class Saved(val shareUrl: String) : SaveReviewExpenseResult

    data object MissingDraft : SaveReviewExpenseResult

    data class Invalid(val message: String) : SaveReviewExpenseResult
}
