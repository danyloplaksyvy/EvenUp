package com.dps.evenup.feature.expenseflow.impl.reviewexpense

import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.expense.api.ExpenseRepository
import com.dps.evenup.domain.expense.api.CalculateExpenseSummaryUseCase
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.ExpenseSummary
import com.dps.evenup.domain.expense.api.FinalExpenseValidationError
import com.dps.evenup.domain.expense.api.FinalExpenseValidationResult
import com.dps.evenup.domain.expense.api.ParticipantExpenseSummary
import com.dps.evenup.domain.expense.api.ValidateExpenseBeforeSaveUseCase
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.receipt.api.CurrencyCode
import com.dps.evenup.domain.receipt.api.MoneyMinor
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

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

    fun reduce(
        state: ReviewExpenseUiState,
        event: ReviewExpenseUiEvent,
    ): ReviewExpenseUiState {
        return when (event) {
            ReviewExpenseUiEvent.CalculationDetailsClick -> state.copy(detailsSheetVisible = true)
            ReviewExpenseUiEvent.CalculationDetailsDismissed -> state.copy(detailsSheetVisible = false)
            ReviewExpenseUiEvent.BackClick,
            ReviewExpenseUiEvent.SaveClick,
            ReviewExpenseUiEvent.SaveRetryClick,
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
        val currencyCode = draft.receipt.currencyCode
        val payerParticipantSummary = summary.participantSummaries
            .firstOrNull { participantSummary -> participantSummary.participantId == draft.payerId }
        val domainValidationError = when (val result = validateExpenseBeforeSave.validateAndBuildPayload(draft)) {
            is FinalExpenseValidationResult.Valid -> null
            is FinalExpenseValidationResult.Invalid -> result.errors.toValidationMessage()
        }
        val consistencyValidationError = summary.toConsistencyValidationMessage()
        val validationError = consistencyValidationError ?: domainValidationError
        val payerShare = payerParticipantSummary?.personShare ?: MoneyMinor.Zero
        val payerNetBalance = payerParticipantSummary?.netBalance ?: MoneyMinor.Zero
        val balanceStatusLabel = if (validationError == null) "Totals balanced" else null

        return ReviewExpenseUiState(
            isLoading = isLoading,
            merchantName = draft.receipt.merchantName,
            totalLabel = formatMoney(summary.receiptTotal, currencyCode),
            totalContentDescription = "Expense total, ${spokenMoney(summary.receiptTotal, currencyCode)}",
            payerName = payer.name,
            payerColorIndex = participantColorIndexes.getValue(payer.id),
            paidByLabel = "${payer.name} paid",
            participantCountLabel = draft.participants.size.toParticipantCountLabel(),
            paidByContentDescription = "${payer.name} paid, ${draft.participants.size.toParticipantCountLabel()}",
            settlementRows = summary.settlementRows.mapNotNull { row ->
                val from = participantsById[row.fromParticipantId] ?: return@mapNotNull null
                val to = participantsById[row.toParticipantId] ?: return@mapNotNull null
                val actionLabel = "Pays ${to.name}"
                SettlementRowUiState(
                    fromParticipantName = from.name,
                    fromParticipantColorIndex = participantColorIndexes.getValue(from.id),
                    toParticipantName = to.name,
                    amountLabel = formatMoney(row.amount, currencyCode),
                    actionLabel = actionLabel,
                    contentDescription = "${from.name} pays ${to.name} ${spokenMoney(row.amount, currencyCode)}",
                )
            },
            payerSummary = PayerSummaryUiState(
                paidLabel = "${payer.name} paid ${formatMoney(summary.receiptTotal, currencyCode)}",
                shareLabel = "${payer.name}'s share is ${formatMoney(payerShare, currencyCode)}",
                resultLabel = payerParticipantSummary?.netBalance.toPayerResultLabel(
                    payerName = payer.name,
                    currencyCode = currencyCode,
                ),
                rows = payerSummaryRows(
                    payerName = payer.name,
                    receiptTotal = summary.receiptTotal,
                    payerShare = payerShare,
                    payerNetBalance = payerNetBalance,
                    currencyCode = currencyCode,
                ),
                contentDescription = payerSummaryContentDescription(
                    payerName = payer.name,
                    receiptTotal = summary.receiptTotal,
                    payerShare = payerShare,
                    payerNetBalance = payerNetBalance,
                    currencyCode = currencyCode,
                ),
            ),
            detailRows = summary.participantSummaries.mapNotNull { participantSummary ->
                val participant = participantsById[participantSummary.participantId] ?: return@mapNotNull null
                val itemSubtotalLabel = formatMoney(participantSummary.assignedItemTotal, currencyCode)
                val feesLabel = formatMoney(participantSummary.allocatedFeeTotal, currencyCode)
                val totalShareLabel = formatMoney(participantSummary.personShare, currencyCode)
                val amountPaidLabel = formatMoney(participantSummary.amountPaid, currencyCode)
                val resultLabel = participantSummary.netBalance.toResultLabel(currencyCode)
                ParticipantCalculationDetailUiState(
                    participantId = participant.id.value,
                    participantName = participant.name,
                    participantColorIndex = participantColorIndexes.getValue(participant.id),
                    itemSubtotalLabel = itemSubtotalLabel,
                    feesLabel = feesLabel,
                    totalShareLabel = totalShareLabel,
                    amountPaidLabel = amountPaidLabel,
                    resultLabel = resultLabel,
                    contentDescription = participantSummary.toContentDescription(participant, currencyCode),
                )
            },
            balanceStatusLabel = balanceStatusLabel,
            balanceStatusContentDescription = balanceStatusLabel,
            calculationDetailsContentDescription = buildString {
                append("Open calculation details.")
                if (balanceStatusLabel != null) {
                    append(" $balanceStatusLabel.")
                }
            },
            canSave = validationError == null,
            validationError = validationError,
        )
    }

    private fun ExpenseSummary.toConsistencyValidationMessage(): String? {
        val shareTotal = participantSummaries.sumOf { participantSummary -> participantSummary.personShare.value }
        if (shareTotal != receiptTotal.value) {
            return "Shares do not add up to the receipt total."
        }

        val paidTotal = participantSummaries.sumOf { participantSummary -> participantSummary.amountPaid.value }
        if (paidTotal != receiptTotal.value) {
            return "Paid amounts do not match the receipt total."
        }

        val settlementTotal = settlementRows.sumOf { row -> row.amount.value }
        val positiveBalanceTotal = participantSummaries
            .filter { participantSummary -> participantSummary.netBalance.value > 0L }
            .sumOf { participantSummary -> participantSummary.netBalance.value }
        val negativeBalanceTotal = participantSummaries
            .filter { participantSummary -> participantSummary.netBalance.value < 0L }
            .sumOf { participantSummary -> -participantSummary.netBalance.value }
        if (settlementTotal != positiveBalanceTotal || settlementTotal != negativeBalanceTotal) {
            return "Settlement transfers do not match participant balances."
        }

        return null
    }

    private fun Set<FinalExpenseValidationError>.toValidationMessage(): String {
        return when {
            FinalExpenseValidationError.InvalidReceipt in this -> "Review the receipt before saving."
            FinalExpenseValidationError.InvalidParticipants in this -> "Review participants and payer before saving."
            FinalExpenseValidationError.InvalidItemAssignments in this -> "Assign every item before saving."
            FinalExpenseValidationError.InvalidFeeAllocations in this -> "Review fee allocations before saving."
            FinalExpenseValidationError.ParticipantSharesDoNotEqualReceiptTotal in this -> {
                "Shares do not add up to the receipt total."
            }
            FinalExpenseValidationError.NetBalancesDoNotSumToZero in this -> {
                "Settlement transfers do not match participant balances."
            }
            else -> "This expense is not ready to save."
        }
    }

    private fun MoneyMinor?.toPayerResultLabel(
        payerName: String,
        currencyCode: CurrencyCode,
    ): String {
        return when {
            this == null || value == 0L -> "$payerName is settled"
            value > 0L -> "$payerName receives ${formatMoney(this, currencyCode)}"
            else -> "$payerName pays ${formatMoney(MoneyMinor(-value), currencyCode)}"
        }
    }

    private fun payerSummaryRows(
        payerName: String,
        receiptTotal: MoneyMinor,
        payerShare: MoneyMinor,
        payerNetBalance: MoneyMinor,
        currencyCode: CurrencyCode,
    ): List<PayerSummaryRowUiState> {
        return listOf(
            PayerSummaryRowUiState(
                label = "$payerName paid",
                valueLabel = formatMoney(receiptTotal, currencyCode),
                contentDescription = "$payerName paid ${spokenMoney(receiptTotal, currencyCode)}",
            ),
            PayerSummaryRowUiState(
                label = "$payerName's share",
                valueLabel = formatMoney(payerShare, currencyCode),
                contentDescription = "$payerName's share is ${spokenMoney(payerShare, currencyCode)}",
            ),
            payerNetBalance.toPayerResultSummaryRow(
                payerName = payerName,
                currencyCode = currencyCode,
            ),
        )
    }

    private fun MoneyMinor.toPayerResultSummaryRow(
        payerName: String,
        currencyCode: CurrencyCode,
    ): PayerSummaryRowUiState {
        return when {
            value > 0L -> PayerSummaryRowUiState(
                label = "$payerName receives",
                valueLabel = formatMoney(this, currencyCode),
                contentDescription = "$payerName receives ${spokenMoney(this, currencyCode)}",
                emphasized = true,
            )
            value < 0L -> {
                val absoluteValue = MoneyMinor(-value)
                PayerSummaryRowUiState(
                    label = "$payerName pays",
                    valueLabel = formatMoney(absoluteValue, currencyCode),
                    contentDescription = "$payerName pays ${spokenMoney(absoluteValue, currencyCode)}",
                    emphasized = true,
                )
            }
            else -> PayerSummaryRowUiState(
                label = "$payerName is settled",
                valueLabel = "Settled",
                contentDescription = "$payerName is settled",
                emphasized = true,
            )
        }
    }

    private fun payerSummaryContentDescription(
        payerName: String,
        receiptTotal: MoneyMinor,
        payerShare: MoneyMinor,
        payerNetBalance: MoneyMinor,
        currencyCode: CurrencyCode,
    ): String {
        val resultDescription = when {
            payerNetBalance.value > 0L -> "$payerName receives ${spokenMoney(payerNetBalance, currencyCode)}"
            payerNetBalance.value < 0L -> {
                "$payerName pays ${spokenMoney(MoneyMinor(-payerNetBalance.value), currencyCode)}"
            }
            else -> "$payerName is settled"
        }
        return "Payer summary. $payerName paid ${spokenMoney(receiptTotal, currencyCode)}. " +
            "$payerName's share is ${spokenMoney(payerShare, currencyCode)}. $resultDescription."
    }

    private fun MoneyMinor.toResultLabel(currencyCode: CurrencyCode): String {
        return when {
            value > 0L -> "Receives ${formatMoney(this, currencyCode)}"
            value < 0L -> "Pays ${formatMoney(MoneyMinor(-value), currencyCode)}"
            else -> "Settled"
        }
    }

    private fun ParticipantExpenseSummary.toContentDescription(
        participant: Participant,
        currencyCode: CurrencyCode,
    ): String {
        val resultDescription = when {
            netBalance.value > 0L -> "receives ${spokenMoney(netBalance, currencyCode)}"
            netBalance.value < 0L -> "pays ${spokenMoney(MoneyMinor(-netBalance.value), currencyCode)}"
            else -> "settled"
        }
        return "${participant.name}, items ${spokenMoney(assignedItemTotal, currencyCode)}, " +
            "fees ${spokenMoney(allocatedFeeTotal, currencyCode)}, " +
            "total share ${spokenMoney(personShare, currencyCode)}, " +
            "paid ${spokenMoney(amountPaid, currencyCode)}, result $resultDescription."
    }

    private fun Int.toParticipantCountLabel(): String {
        return "$this ${if (this == 1) "person" else "people"}"
    }

    private fun formatMoney(
        value: MoneyMinor,
        currencyCode: CurrencyCode,
    ): String {
        return currencySymbol(currencyCode) +
            BigDecimal(value.value).movePointLeft(2).setScale(2).toPlainString()
    }

    private fun currencySymbol(currencyCode: CurrencyCode): String {
        return runCatching {
            Currency.getInstance(currencyCode.value).getSymbol(Locale.US)
        }.getOrDefault("${currencyCode.value} ")
    }

    private fun spokenMoney(
        value: MoneyMinor,
        currencyCode: CurrencyCode,
    ): String {
        val absoluteValue = kotlin.math.abs(value.value)
        val major = absoluteValue / 100L
        val minor = absoluteValue % 100L
        return when (currencyCode.value.uppercase(Locale.US)) {
            "USD" -> spokenMajorMinor(
                major = major,
                minor = minor,
                singularMajor = "dollar",
                pluralMajor = "dollars",
                singularMinor = "cent",
                pluralMinor = "cents",
            )
            "EUR" -> spokenMajorMinor(
                major = major,
                minor = minor,
                singularMajor = "euro",
                pluralMajor = "euros",
                singularMinor = "cent",
                pluralMinor = "cents",
            )
            "GBP" -> spokenMajorMinor(
                major = major,
                minor = minor,
                singularMajor = "pound",
                pluralMajor = "pounds",
                singularMinor = "penny",
                pluralMinor = "pence",
            )
            else -> "${currencyCode.value} ${BigDecimal(absoluteValue).movePointLeft(2).setScale(2).toPlainString()}"
        }
    }

    private fun spokenMajorMinor(
        major: Long,
        minor: Long,
        singularMajor: String,
        pluralMajor: String,
        singularMinor: String,
        pluralMinor: String,
    ): String {
        val majorUnit = if (major == 1L) singularMajor else pluralMajor
        val minorUnit = if (minor == 1L) singularMinor else pluralMinor
        return when {
            minor == 0L -> "$major $majorUnit"
            major == 0L -> "$minor $minorUnit"
            else -> "$major $majorUnit and $minor $minorUnit"
        }
    }
}

sealed interface SaveReviewExpenseResult {
    data class Saved(val shareUrl: String) : SaveReviewExpenseResult

    data object MissingDraft : SaveReviewExpenseResult

    data class Invalid(val message: String) : SaveReviewExpenseResult
}
