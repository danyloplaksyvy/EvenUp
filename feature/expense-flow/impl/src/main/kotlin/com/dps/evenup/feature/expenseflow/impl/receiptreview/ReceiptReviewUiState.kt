package com.dps.evenup.feature.expenseflow.impl.receiptreview

import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.ReceiptItemParseMetadata
import com.dps.evenup.domain.receipt.api.ReceiptParseCorrection
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val receiptTotalConfirmedByUser: Boolean = false,
    val currencyConfirmedByUser: Boolean = false,
    val issueNavigatorVisible: Boolean = false,
    val editDraft: ReceiptReviewEditDraft? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val firstBlockingSection: ReceiptReviewSection? = null,
    val firstBlockingItemId: String? = null,
    val validationRequestId: Int = 0,
    val submitError: String? = null,
) {
    val itemCountLabel: String = "${items.size} items"
    val additiveFees: List<ReceiptReviewFeeUiState> = fees.filterNot { fee -> fee.isDiscount }
    val discounts: List<ReceiptReviewFeeUiState> = fees.filter { fee -> fee.isDiscount }
    val feeCountLabel: String = when (additiveFees.size) {
        0 -> "No fees"
        1 -> "1 fee"
        else -> "${additiveFees.size} fees"
    }
    val discountCountLabel: String = when (discounts.size) {
        0 -> "No discounts"
        1 -> "1 discount"
        else -> "${discounts.size} discounts"
    }
    val derivedSubtotalAmount: String = formatMoneyInput(itemSubtotalMinor)
    val derivedFeesAmount: String = formatMoneyInput(feeTotalMinor)
    val derivedDiscountsAmount: String = formatMoneyInput(discountTotalMinor)
    val calculatedTotalAmount: String = formatMoneyInput(calculatedTotalMinor)
    val derivedSubtotalLabel: String = formatCurrency(derivedSubtotalAmount, currencyCode)
    val feesTotalLabel: String = formatCurrency(derivedFeesAmount, currencyCode)
    val discountsTotalLabel: String = "-${formatCurrency(derivedDiscountsAmount, currencyCode)}"
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
    val issues: List<ReceiptReviewIssueUiState> = buildIssues()
    val blockingIssues: List<ReceiptReviewIssueUiState> = issues.filter { issue -> issue.severity == ReceiptReviewIssueSeverity.Blocking }
    val firstIssue: ReceiptReviewIssueUiState? = blockingIssues.firstOrNull() ?: issues.firstOrNull()
    val dateDisplayLabel: String = dateLabel.toReceiptDisplayDate()
    val summaryStatusLabel: String = when {
        reconciliation.type == ReceiptReviewReconciliationType.Mismatch -> "Total differs"
        unresolvedReviewItemCount > 0 -> "Items need review"
        hasInvalidRequiredFields -> "Needs review"
        receiptTotalConfirmedByUser -> "Confirmed"
        else -> "Ready"
    }
    val statusLabel: String = when {
        blockingIssues.size > 1 -> "${blockingIssues.size} issues need review"
        blockingIssues.size == 1 -> blockingIssues.single().title
        issues.size > 1 -> "${issues.size} receipt notes to review"
        issues.size == 1 -> issues.single().title
        receiptTotalConfirmedByUser -> "Receipt total confirmed"
        else -> "AI found ${items.size} ${if (items.size == 1) "item" else "items"} · Ready"
    }
    val statusAccessibilityLabel: String = firstIssue?.accessibilityLabel ?: statusLabel
    val hasActiveWarningStatus: Boolean = issues.isNotEmpty() || fieldErrors.isNotEmpty() || submitError != null
    val hasWarningStatus: Boolean = hasActiveWarningStatus
    val canContinue: Boolean = !isSaving && blockingIssues.isEmpty()
    val continueBlockedMessage: String? = blockingIssues.firstOrNull()?.let { issue ->
        "Fix: ${issue.title}"
    }

    val itemSubtotalMinor: Long
        get() = items.sumOf { item -> parseMoneyMinorValue(item.amount) ?: 0L }

    val adjustmentTotalMinor: Long
        get() = feeTotalMinor - discountTotalMinor

    val feeTotalMinor: Long
        get() = additiveFees.sumOf { fee -> fee.amountMinor ?: 0L }

    val discountTotalMinor: Long
        get() = discounts.sumOf { fee -> fee.amountMinor ?: 0L }

    val calculatedTotalMinor: Long
        get() = itemSubtotalMinor + feeTotalMinor - discountTotalMinor

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
            !fee.hasValidAmount
        }

    private val hasInvalidRequiredFields: Boolean
        get() = merchantName.isBlank() || hasFutureDate ||
            items.isEmpty() || items.any { item ->
            item.name.isBlank() || item.quantity.toIntOrNull()?.let { it > 0 } != true ||
                parseMoneyMinorValue(item.amount)?.let { it > 0 } != true
        } || fees.any { fee ->
            !fee.hasValidAmount
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
                    quantity = item.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    parseMetadata = item.parseMetadata,
                )
            },
            parseCorrections = parseCorrections,
            scannedTotalMinor = scannedReceiptTotalMinor,
            calculatedTotalMinor = calculatedTotalMinor,
        )
    }

    private fun firstSuggestedCorrectionItemId(): String? {
        return items.firstOrNull { item -> item.suggestedCorrection != null }?.id
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
                val direction = if (delta > 0) "higher than" else "lower than"
                ReceiptReviewReconciliationUiState(
                    message = "Total differs by ${formatCurrency(formatMoneyInput(kotlin.math.abs(delta)), currencyCode)}",
                    isIssue = true,
                    type = ReceiptReviewReconciliationType.Mismatch,
                    detail = "Receipt total is ${formatCurrency(formatMoneyInput(total), currencyCode)} · Calculated total is ${formatCurrency(formatMoneyInput(calculatedTotalMinor), currencyCode)}",
                    reason = "Calculated total is ${formatCurrency(formatMoneyInput(kotlin.math.abs(delta)), currencyCode)} $direction the receipt total.",
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
                message = if (receiptTotalConfirmedByUser) "Receipt total confirmed" else "Matches scanned receipt",
                isIssue = false,
                type = ReceiptReviewReconciliationType.Matched,
                scannedReceiptTotalLabel = formatCurrency(formatMoneyInput(total), currencyCode),
            )
        }
    }

    private fun buildIssues(): List<ReceiptReviewIssueUiState> = buildList {
        if (merchantName.isBlank()) {
            add(
                ReceiptReviewIssueUiState(
                    id = "merchant",
                    kind = ReceiptReviewIssueKind.MissingMerchant,
                    severity = ReceiptReviewIssueSeverity.Blocking,
                    target = ReceiptReviewIssueTarget.Details(ReceiptReviewEditTarget.Merchant),
                    title = "Merchant is required",
                    message = "Add the merchant name before continuing.",
                    actionLabel = "Edit merchant",
                    accessibilityLabel = "Merchant is required. Edit merchant.",
                ),
            )
        }

        val parsedDate = dateLabel.toLocalDateOrNull()
        when {
            dateLabel.isBlank() -> add(
                ReceiptReviewIssueUiState(
                    id = "missing_date",
                    kind = ReceiptReviewIssueKind.MissingDate,
                    severity = ReceiptReviewIssueSeverity.Warning,
                    target = ReceiptReviewIssueTarget.Details(ReceiptReviewEditTarget.Date),
                    title = "Receipt date missing",
                    message = "Add a date if it is visible on the receipt.",
                    actionLabel = "Edit date",
                    accessibilityLabel = "Receipt date is missing. Edit date.",
                ),
            )
            parsedDate == null -> add(
                ReceiptReviewIssueUiState(
                    id = "invalid_date",
                    kind = ReceiptReviewIssueKind.InvalidDate,
                    severity = ReceiptReviewIssueSeverity.Blocking,
                    target = ReceiptReviewIssueTarget.Details(ReceiptReviewEditTarget.Date),
                    title = "Date needs review",
                    message = "Use a valid receipt date.",
                    actionLabel = "Edit date",
                    accessibilityLabel = "Date needs review. Edit date.",
                ),
            )
            parsedDate.isAfter(LocalDate.now()) -> add(
                ReceiptReviewIssueUiState(
                    id = "future_date",
                    kind = ReceiptReviewIssueKind.FutureDate,
                    severity = ReceiptReviewIssueSeverity.Blocking,
                    target = ReceiptReviewIssueTarget.Details(ReceiptReviewEditTarget.Date),
                    title = "Date cannot be in the future",
                    message = "Choose a receipt date that is today or earlier.",
                    actionLabel = "Edit date",
                    accessibilityLabel = "Date cannot be in the future. Edit date.",
                ),
            )
        }

        if (currencyCode.trim().length != 3) {
            add(
                ReceiptReviewIssueUiState(
                    id = "currency_invalid",
                    kind = ReceiptReviewIssueKind.InvalidCurrency,
                    severity = ReceiptReviewIssueSeverity.Blocking,
                    target = ReceiptReviewIssueTarget.Details(ReceiptReviewEditTarget.Currency),
                    title = "Currency needs review",
                    message = "Choose the receipt currency before continuing.",
                    actionLabel = "Edit currency",
                    accessibilityLabel = "Currency needs review. Edit currency.",
                ),
            )
        } else if (!currencyConfirmedByUser && reviewWarnings.any { warning -> warning.contains("currency", ignoreCase = true) }) {
            add(
                ReceiptReviewIssueUiState(
                    id = "currency_uncertain",
                    kind = ReceiptReviewIssueKind.UncertainCurrency,
                    severity = ReceiptReviewIssueSeverity.Warning,
                    target = ReceiptReviewIssueTarget.Details(ReceiptReviewEditTarget.Currency),
                    title = "Check receipt currency",
                    message = "The scan was not fully certain about the currency.",
                    actionLabel = "Review currency",
                    accessibilityLabel = "Check receipt currency. Review currency.",
                ),
            )
        }

        when (reconciliation.type) {
            ReceiptReviewReconciliationType.Mismatch -> add(
                ReceiptReviewIssueUiState(
                    id = "total_mismatch",
                    kind = ReceiptReviewIssueKind.TotalMismatch,
                    severity = ReceiptReviewIssueSeverity.Blocking,
                    target = firstSuggestedCorrectionItemId()?.let { itemId ->
                        ReceiptReviewIssueTarget.Item(itemId)
                    } ?: ReceiptReviewIssueTarget.Summary(ReceiptReviewEditTarget.TotalCheck),
                    title = if (suspectedCorrectionCount > 0) {
                        "$suspectedCorrectionCount likely item ${if (suspectedCorrectionCount == 1) "error" else "errors"} found"
                    } else {
                        "Total differs by ${reconciliation.differenceLabel.orEmpty()}"
                    },
                    message = reconciliation.detail ?: reconciliation.message,
                    actionLabel = if (suspectedCorrectionCount > 0) "Review item amounts" else "Review total",
                    accessibilityLabel = "${reconciliation.message}. ${if (suspectedCorrectionCount > 0) "Review item amounts." else "Review total."}",
                ),
            )
            ReceiptReviewReconciliationType.MissingScannedTotal -> add(
                ReceiptReviewIssueUiState(
                    id = "total_missing",
                    kind = ReceiptReviewIssueKind.InvalidTotal,
                    severity = ReceiptReviewIssueSeverity.Blocking,
                    target = ReceiptReviewIssueTarget.Summary(ReceiptReviewEditTarget.ReceiptTotal),
                    title = "Receipt total not detected",
                    message = "Confirm the receipt total before continuing.",
                    actionLabel = "Enter total",
                    accessibilityLabel = "Receipt total not detected. Enter total.",
                ),
            )
            else -> Unit
        }

        items.forEach { item ->
            if (item.needsReview) {
                val note = item.reviewNote ?: "Check price from receipt"
                add(
                    ReceiptReviewIssueUiState(
                        id = "item_${item.id}",
                        kind = ReceiptReviewIssueKind.ItemAmountReview,
                        severity = ReceiptReviewIssueSeverity.Blocking,
                        target = ReceiptReviewIssueTarget.Item(item.id),
                        title = "${item.name.ifBlank { "Item" }} needs review",
                        message = note,
                        actionLabel = "Review item",
                        accessibilityLabel = "Needs review: $note. Edit ${item.name.ifBlank { "item" }}.",
                    ),
                )
            }
        }

        items.forEach { item ->
            val quantity = item.quantity.toIntOrNull()
            val amount = parseMoneyMinorValue(item.amount)
            when {
                item.name.isBlank() -> add(
                    ReceiptReviewIssueUiState(
                        id = "item_name_${item.id}",
                        kind = ReceiptReviewIssueKind.InvalidItem,
                        severity = ReceiptReviewIssueSeverity.Blocking,
                        target = ReceiptReviewIssueTarget.Item(item.id),
                        title = "Item name missing",
                        message = "Add a name for this item.",
                        actionLabel = "Edit item",
                        accessibilityLabel = "Item name missing. Edit item.",
                    ),
                )
                quantity == null || quantity <= 0 -> add(
                    ReceiptReviewIssueUiState(
                        id = "item_quantity_${item.id}",
                        kind = ReceiptReviewIssueKind.InvalidItem,
                        severity = ReceiptReviewIssueSeverity.Blocking,
                        target = ReceiptReviewIssueTarget.Item(item.id),
                        title = "Item quantity needs review",
                        message = "Use a quantity from 1 to 999.",
                        actionLabel = "Edit item",
                        accessibilityLabel = "Item quantity needs review. Edit item.",
                    ),
                )
                amount == null || amount <= 0 -> add(
                    ReceiptReviewIssueUiState(
                        id = "item_amount_${item.id}",
                        kind = ReceiptReviewIssueKind.InvalidItem,
                        severity = ReceiptReviewIssueSeverity.Blocking,
                        target = ReceiptReviewIssueTarget.Item(item.id),
                        title = "Item amount needs review",
                        message = "Enter a positive item total.",
                        actionLabel = "Edit item",
                        accessibilityLabel = "Item amount needs review. Edit item.",
                    ),
                )
            }
        }
    }
}

data class ReceiptReviewIssueUiState(
    val id: String,
    val kind: ReceiptReviewIssueKind,
    val severity: ReceiptReviewIssueSeverity,
    val target: ReceiptReviewIssueTarget,
    val title: String,
    val message: String,
    val actionLabel: String,
    val accessibilityLabel: String,
)

enum class ReceiptReviewIssueKind {
    TotalMismatch,
    MissingDate,
    InvalidDate,
    FutureDate,
    MissingMerchant,
    UncertainCurrency,
    InvalidCurrency,
    InvalidTotal,
    ItemAmountReview,
    InvalidItem,
}

enum class ReceiptReviewIssueSeverity {
    Blocking,
    Warning,
}

sealed interface ReceiptReviewIssueTarget {
    data class Details(val editTarget: ReceiptReviewEditTarget) : ReceiptReviewIssueTarget
    data class Item(val itemId: String) : ReceiptReviewIssueTarget
    data class Summary(val editTarget: ReceiptReviewEditTarget) : ReceiptReviewIssueTarget
    data object Adjustments : ReceiptReviewIssueTarget
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
    val isDiscount: Boolean = type == FeeType.Discount
    val amountMinor: Long? = parseMoneyMinorValue(amount)
    val signedAmountMinor: Long? = amountMinor?.let { value -> if (isDiscount) -value else value }
    val hasValidAmount: Boolean = displayLabel.isNotBlank() && amountMinor?.let { value -> value > 0L } == true

    fun amountLabel(currencyCode: String): String {
        val label = formatCurrency(amount, currencyCode)
        return if (isDiscount) "-$label" else label
    }
}

data class ReceiptReviewReconciliationUiState(
    val message: String,
    val isIssue: Boolean,
    val type: ReceiptReviewReconciliationType,
    val detail: String? = null,
    val reason: String? = null,
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
        val initialName: String = name,
        val initialQuantity: String = quantity,
        val initialUnitPrice: String = unitPrice,
        val initialLineTotal: String = lineTotal,
    ) : ReceiptReviewEditDraft {
        val showsPriceEach: Boolean
            get() = quantity.toIntOrNull()?.let { it > 1 } == true

        val hasReviewIssue: Boolean
            get() = reviewNote != null || suggestedCorrection != null

        val hasChanges: Boolean
            get() = name != initialName ||
                quantity != initialQuantity ||
                unitPrice != initialUnitPrice ||
                lineTotal != initialLineTotal

        val primaryActionLabel: String
            get() = when {
                isNew -> "Add item"
                hasReviewIssue && !hasChanges -> "Confirm amount"
                else -> "Save changes"
            }

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
        "USD" -> "$"
        "GBP" -> "£"
        else -> "${currencyCode.uppercase()} "
    }
    return "$prefix$value"
}

internal fun parseMoneyMinorValue(value: String): Long? {
    val normalized = value.trim()
        .removePrefix("€")
        .removePrefix("$")
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
        FeeType.Discount -> label.trim().ifBlank { "Discount" }
        FeeType.Other -> label.trim().ifBlank { "Other" }
    }
}

internal fun String.toLocalDateOrNull(): LocalDate? {
    if (isBlank()) return null
    val value = trim()
    return listOf<(String) -> LocalDate>(
        { text -> LocalDate.parse(text) },
        { text -> LocalDateTime.parse(text).toLocalDate() },
        { text -> OffsetDateTime.parse(text).toLocalDate() },
        { text -> ZonedDateTime.parse(text).toLocalDate() },
    ).firstNotNullOfOrNull { parser ->
        try {
            parser(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

internal fun String.toReceiptDisplayDate(): String {
    val date = toLocalDateOrNull() ?: return trim()
    return date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))
}

internal const val MAX_RECEIPT_REVIEW_MONEY_MINOR = 9_999_999L
