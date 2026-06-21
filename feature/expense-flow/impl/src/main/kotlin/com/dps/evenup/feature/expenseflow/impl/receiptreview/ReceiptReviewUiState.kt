package com.dps.evenup.feature.expenseflow.impl.receiptreview

import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.ReceiptItemParseMetadata
import com.dps.evenup.domain.receipt.api.ReceiptParseCorrection
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class ReceiptReviewUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val missingDraft: Boolean = false,
    val merchantName: String = "",
    val dateLabel: String = "",
    val currencyCode: String = "USD",
    val scannedReceiptTotalAmount: String = "",
    val items: List<ReceiptReviewItemUiState> = emptyList(),
    val fees: List<ReceiptReviewFeeUiState> = emptyList(),
    val parseCorrections: List<ReceiptParseCorrection> = emptyList(),
    val reviewWarnings: List<String> = emptyList(),
    val reviewWarningCount: Int = 0,
    val uncertainItemCount: Int = 0,
    val editDraft: ReceiptReviewEditDraft? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val firstBlockingSection: ReceiptReviewSection? = null,
    val firstBlockingItemId: String? = null,
    val validationRequestId: Int = 0,
    val submitError: String? = null,
) {
    val itemCountLabel: String = "${items.size} items"
    val adjustmentCountLabel: String = when (fees.size) {
        0 -> "No adjustments"
        1 -> "1 adjustment"
        else -> "${fees.size} adjustments"
    }
    val derivedSubtotalAmount: String = formatMoneyInput(itemSubtotalMinor)
    val derivedAdjustmentsAmount: String = formatMoneyInput(adjustmentTotalMinor)
    val calculatedTotalAmount: String = formatMoneyInput(calculatedTotalMinor)
    val derivedSubtotalLabel: String = formatCurrency(derivedSubtotalAmount, currencyCode)
    val adjustmentsTotalLabel: String = formatCurrency(derivedAdjustmentsAmount, currencyCode)
    val calculatedTotalLabel: String = formatCurrency(calculatedTotalAmount, currencyCode)
    val scannedReceiptTotalLabel: String? = scannedReceiptTotalAmount.takeIf { it.isNotBlank() }
        ?.let { amount -> formatCurrency(amount, currencyCode) }
    val summaryTotalLabel: String = calculatedTotalLabel
    val mismatchDiagnosis: ReceiptMismatchDiagnosis? = buildMismatchDiagnosis()
    val suspectedCorrectionCount: Int = mismatchDiagnosis?.suspectedCorrections?.size ?: 0
    val suggestedCorrectionActionLabel: String? = suspectedCorrectionCount.takeIf { count -> count > 0 }?.let { count ->
        "Review $count suggested ${if (count == 1) "correction" else "corrections"}"
    }
    val reconciliation: ReceiptReviewReconciliationUiState = buildReconciliation()
    val summaryStatusLabel: String = if (reconciliation.isIssue || hasInvalidRequiredFields) {
        "Needs review"
    } else {
        "Matches receipt"
    }
    val statusLabel: String = when {
        reconciliation.type == ReceiptReviewReconciliationType.Mismatch && suspectedCorrectionCount > 0 -> {
            "$suspectedCorrectionCount likely item ${if (suspectedCorrectionCount == 1) "error" else "errors"} found"
        }
        reconciliation.type == ReceiptReviewReconciliationType.Mismatch -> "Total differs · Review item amounts"
        unresolvedReviewItemCount > 0 -> "$unresolvedReviewItemCount ${if (unresolvedReviewItemCount == 1) "item needs" else "items need"} review"
        reconciliation.type == ReceiptReviewReconciliationType.MissingScannedTotal -> "Receipt total not detected"
        reviewWarningCount > 0 -> "Review needed"
        hasInvalidRequiredFields -> "Missing or uncertain fields"
        else -> "AI found ${items.size} ${if (items.size == 1) "item" else "items"} · Matches scanned receipt"
    }
    val hasWarningStatus: Boolean = reconciliation.isIssue || unresolvedReviewItemCount > 0 ||
        reviewWarningCount > 0 || hasInvalidRequiredFields || fieldErrors.isNotEmpty() || submitError != null

    val itemSubtotalMinor: Long
        get() = items.sumOf { item -> parseMoneyMinorValue(item.amount) ?: 0L }

    val adjustmentTotalMinor: Long
        get() = fees.sumOf { fee -> parseMoneyMinorValue(fee.amount) ?: 0L }

    val calculatedTotalMinor: Long
        get() = itemSubtotalMinor + adjustmentTotalMinor

    val scannedReceiptTotalMinor: Long?
        get() = parseMoneyMinorValue(scannedReceiptTotalAmount)

    val unresolvedReviewItemCount: Int
        get() = items.count { item -> item.needsReview }

    val hasBlockingIssues: Boolean
        get() = merchantName.isBlank() || hasFutureDate || scannedReceiptTotalMinor == null ||
            scannedReceiptTotalMinor != calculatedTotalMinor || unresolvedReviewItemCount > 0 ||
            items.isEmpty() || items.any { item ->
            item.name.isBlank() || item.quantity.toIntOrNull()?.let { it > 0 } != true ||
                parseMoneyMinorValue(item.amount)?.let { it > 0 } != true
        } || fees.any { fee ->
            fee.displayLabel.isBlank() || parseMoneyMinorValue(fee.amount)?.let { it > 0 } != true
        }

    private val hasInvalidRequiredFields: Boolean
        get() = merchantName.isBlank() || hasFutureDate ||
            items.isEmpty() || items.any { item ->
            item.name.isBlank() || item.quantity.toIntOrNull()?.let { it > 0 } != true ||
                parseMoneyMinorValue(item.amount)?.let { it > 0 } != true
        } || fees.any { fee ->
            fee.displayLabel.isBlank() || parseMoneyMinorValue(fee.amount)?.let { it > 0 } != true
        }

    private val hasFutureDate: Boolean
        get() = dateLabel.toLocalDateOrNull()?.isAfter(LocalDate.now()) == true

    private fun buildMismatchDiagnosis(): ReceiptMismatchDiagnosis? {
        return ReceiptMismatchDiagnoser.diagnose(
            items = items.mapNotNull { item ->
                ReceiptMismatchItem(
                    id = item.id,
                    name = item.name,
                    currentAmountMinor = parseMoneyMinorValue(item.amount) ?: return@mapNotNull null,
                    parseMetadata = item.parseMetadata,
                )
            },
            parseCorrections = parseCorrections,
            scannedTotalMinor = scannedReceiptTotalMinor,
            calculatedTotalMinor = calculatedTotalMinor,
        )
    }

    private fun buildReconciliation(): ReceiptReviewReconciliationUiState {
        val total = scannedReceiptTotalMinor
        return when {
            total == null -> ReceiptReviewReconciliationUiState(
                message = "Receipt total was not detected. Confirm before continuing.",
                isIssue = true,
                type = ReceiptReviewReconciliationType.MissingScannedTotal,
            )
            total != calculatedTotalMinor -> {
                val delta = total - calculatedTotalMinor
                ReceiptReviewReconciliationUiState(
                    message = "Receipt says ${formatCurrency(formatMoneyInput(total), currencyCode)} · Difference ${formatCurrency(formatMoneyInput(kotlin.math.abs(delta)), currencyCode)}",
                    isIssue = true,
                    type = ReceiptReviewReconciliationType.Mismatch,
                    scannedReceiptTotalLabel = formatCurrency(formatMoneyInput(total), currencyCode),
                    differenceLabel = formatCurrency(formatMoneyInput(kotlin.math.abs(delta)), currencyCode),
                    suggestedCorrectionActionLabel = suggestedCorrectionActionLabel,
                )
            }
            unresolvedReviewItemCount > 0 -> ReceiptReviewReconciliationUiState(
                message = "Review highlighted ${if (unresolvedReviewItemCount == 1) "item" else "items"} before continuing",
                isIssue = true,
                type = ReceiptReviewReconciliationType.ReviewItems,
            )
            else -> ReceiptReviewReconciliationUiState(
                message = "Matches scanned receipt",
                isIssue = false,
                type = ReceiptReviewReconciliationType.Matched,
                scannedReceiptTotalLabel = formatCurrency(formatMoneyInput(total), currencyCode),
            )
        }
    }
}

data class ReceiptReviewItemUiState(
    val id: String,
    val name: String = "",
    val quantity: String = "1",
    val unitPriceAmount: String = "",
    val amount: String = "",
    val needsReview: Boolean = false,
    val reviewNote: String? = null,
    val reviewConfirmed: Boolean = false,
    val parseMetadata: ReceiptItemParseMetadata = ReceiptItemParseMetadata(),
    val originalName: String = name,
    val correctionFields: List<String> = emptyList(),
    val suggestedCorrection: SuspectedItemCorrection? = null,
) {
    fun totalLabel(currencyCode: String): String = formatCurrency(amount, currencyCode)

    fun suggestedCorrectionNote(currencyCode: String): String? {
        val correction = suggestedCorrection ?: return reviewNote
        return "Receipt likely says ${formatCurrency(formatMoneyInput(correction.suggestedAmountMinor), currencyCode)} · Difference ${formatCurrency(formatMoneyInput(correction.differenceMinor), currencyCode)}"
    }

    fun quantityDetail(currencyCode: String): String {
        val quantityValue = quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val amountMinor = parseMoneyMinorValue(amount)
        return if (quantityValue > 1 && amountMinor != null) {
            val unitAmount = BigDecimal(amountMinor).divide(BigDecimal(quantityValue), 2, RoundingMode.HALF_UP)
            "${quantityValue}x · ${formatCurrency(formatMoneyInput(unitAmount), currencyCode)} each"
        } else {
            "${quantityValue}x"
        }
    }
}

data class ReceiptReviewFeeUiState(
    val id: String,
    val type: FeeType = FeeType.Other,
    val label: String = "",
    val amount: String = "",
) {
    val displayLabel: String = feeDisplayLabel(type, label)
}

data class ReceiptReviewReconciliationUiState(
    val message: String,
    val isIssue: Boolean,
    val type: ReceiptReviewReconciliationType,
    val scannedReceiptTotalLabel: String? = null,
    val differenceLabel: String? = null,
    val suggestedCorrectionActionLabel: String? = null,
)

enum class ReceiptReviewReconciliationType {
    Matched,
    Mismatch,
    MissingScannedTotal,
    ReviewItems,
}

enum class ReceiptReviewSection {
    Details,
    Items,
    Adjustments,
    Summary,
}

sealed interface ReceiptReviewEditTarget {
    data object Merchant : ReceiptReviewEditTarget

    data object Date : ReceiptReviewEditTarget

    data object Currency : ReceiptReviewEditTarget

    data object ReceiptTotal : ReceiptReviewEditTarget

    data object TotalCheck : ReceiptReviewEditTarget

    data class Item(val itemId: String) : ReceiptReviewEditTarget

    data class Fee(val feeId: String) : ReceiptReviewEditTarget
}

sealed interface ReceiptReviewEditDraft {
    data class Merchant(val value: String) : ReceiptReviewEditDraft

    data class Date(val value: String) : ReceiptReviewEditDraft

    data class Currency(val value: String) : ReceiptReviewEditDraft

    data class ReceiptTotal(val value: String) : ReceiptReviewEditDraft

    data object TotalCheck : ReceiptReviewEditDraft

    data class Item(
        val itemId: String?,
        val name: String,
        val quantity: String,
        val unitPrice: String,
        val lineTotal: String,
        val lastEditedMoneyField: ReceiptReviewMoneyField,
        val isNew: Boolean,
        val reviewNote: String? = null,
        val suggestedCorrection: SuspectedItemCorrection? = null,
    ) : ReceiptReviewEditDraft {
        val showsPriceEach: Boolean
            get() = quantity.toIntOrNull()?.let { it > 1 } == true

        fun averagePriceNote(currencyCode: String): String? {
            val quantityValue = quantity.toIntOrNull()?.takeIf { it > 1 } ?: return null
            val itemTotal = parseMoneyMinorValue(lineTotal) ?: return null
            if (itemTotal % quantityValue == 0L) return null
            val averagePriceEach = formatMoneyInput(
                BigDecimal(itemTotal).divide(BigDecimal(quantityValue), 2, RoundingMode.HALF_UP),
            )
            return "Average price each is ${formatCurrency(averagePriceEach, currencyCode)} because the item total does not split evenly."
        }
    }

    data class Fee(
        val feeId: String?,
        val type: FeeType,
        val label: String,
        val amount: String,
        val isNew: Boolean,
    ) : ReceiptReviewEditDraft {
        val displayLabel: String = feeDisplayLabel(type, label)
    }
}

enum class ReceiptReviewMoneyField {
    PriceEach,
    ItemTotal,
}

internal fun formatCurrency(amount: String, currencyCode: String): String {
    val value = parseMoneyMinorValue(amount)?.let(::formatMoneyInput) ?: amount.trim().ifBlank { "0.00" }
    val prefix = when (currencyCode.uppercase()) {
        "EUR" -> "€"
        "USD" -> "\$"
        "GBP" -> "£"
        else -> "${currencyCode.uppercase()} "
    }
    return "$prefix$value"
}

internal fun parseMoneyMinorValue(value: String): Long? {
    val normalized = value.trim()
        .removePrefix("€")
        .removePrefix("\$")
        .removePrefix("£")
        .replace(',', '.')
    if (normalized.isBlank()) return null
    return try {
        val amountMinor = BigDecimal(normalized)
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
        amountMinor.takeIf { it in 0..MAX_RECEIPT_REVIEW_MONEY_MINOR }
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }
}

internal fun formatMoneyInput(value: Long): String {
    return BigDecimal(value).movePointLeft(2).setScale(2).toPlainString()
}

internal fun formatMoneyInput(value: BigDecimal): String {
    return value.movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()
}

internal fun feeDisplayLabel(type: FeeType, label: String = ""): String {
    return when (type) {
        FeeType.Tax -> "Tax"
        FeeType.Tip -> "Tip"
        FeeType.ServiceFee -> "Service charge"
        FeeType.Other -> label.trim().ifBlank { "Other" }
    }
}

internal fun String.toLocalDateOrNull(): LocalDate? {
    if (isBlank()) return null
    return try {
        LocalDate.parse(trim())
    } catch (_: DateTimeParseException) {
        null
    }
}

internal const val MAX_RECEIPT_REVIEW_MONEY_MINOR = 9_999_999L
