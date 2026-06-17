package com.dps.evenup.feature.expenseflow.impl.receiptreview

import com.dps.evenup.domain.receipt.api.FeeType
import java.math.BigDecimal
import java.math.RoundingMode

data class ReceiptReviewUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val missingDraft: Boolean = false,
    val merchantName: String = "",
    val dateLabel: String = "",
    val currencyCode: String = "USD",
    val items: List<ReceiptReviewItemUiState> = emptyList(),
    val fees: List<ReceiptReviewFeeUiState> = emptyList(),
    val subtotalAmount: String? = null,
    val totalAmount: String = "",
    val reviewWarningCount: Int = 0,
    val uncertainItemCount: Int = 0,
    val editTarget: ReceiptReviewEditTarget? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val submitError: String? = null,
) {
    val itemCountLabel: String = "${items.size} items"
    val adjustmentCountLabel: String = when (fees.size) {
        0 -> "No adjustments"
        1 -> "1 adjustment"
        else -> "${fees.size} adjustments"
    }
    val summaryTotalLabel: String = formatCurrency(totalAmount, currencyCode)
    val derivedSubtotalAmount: String = formatMoneyInput(itemSubtotalMinor)
    val derivedAdjustmentsAmount: String = formatMoneyInput(adjustmentTotalMinor)
    val reconciledTotalAmount: String = formatMoneyInput(reconciledTotalMinor)
    val derivedSubtotalLabel: String = formatCurrency(derivedSubtotalAmount, currencyCode)
    val adjustmentsTotalLabel: String = formatCurrency(derivedAdjustmentsAmount, currencyCode)
    val reconciledTotalLabel: String = formatCurrency(reconciledTotalAmount, currencyCode)
    val receiptTotalLabel: String = formatCurrency(totalAmount, currencyCode)
    val reconciliation: ReceiptReviewReconciliationUiState = buildReconciliation()
    val statusLabel: String = when {
        reconciliation.isMismatch -> "Total does not match receipt"
        uncertainItemCount > 0 -> "Check $uncertainItemCount uncertain ${if (uncertainItemCount == 1) "item" else "items"}"
        reviewWarningCount > 0 -> "Review needed"
        hasInvalidRequiredFields -> "Missing or uncertain fields"
        else -> "AI found ${items.size} ${if (items.size == 1) "item" else "items"} · Total matches"
    }
    val hasWarningStatus: Boolean = reconciliation.isMismatch || uncertainItemCount > 0 || reviewWarningCount > 0 ||
        hasInvalidRequiredFields || fieldErrors.isNotEmpty() || submitError != null

    private val itemSubtotalMinor: Long
        get() = items.sumOf { item -> parseMoneyMinorValue(item.amount) ?: 0L }

    private val adjustmentTotalMinor: Long
        get() = fees.sumOf { fee -> parseMoneyMinorValue(fee.amount) ?: 0L }

    private val reconciledTotalMinor: Long
        get() = itemSubtotalMinor + adjustmentTotalMinor

    private val receiptTotalMinor: Long?
        get() = parseMoneyMinorValue(totalAmount)

    private val hasInvalidRequiredFields: Boolean
        get() = merchantName.isBlank() || items.isEmpty() || items.any { item ->
            item.name.isBlank() || item.quantity.toIntOrNull()?.let { it > 0 } != true ||
                parseMoneyMinorValue(item.amount)?.let { it > 0 } != true
        } || fees.any { fee ->
            fee.label.isBlank() || parseMoneyMinorValue(fee.amount)?.let { it > 0 } != true
        } || receiptTotalMinor == null

    private fun buildReconciliation(): ReceiptReviewReconciliationUiState {
        val total = receiptTotalMinor
        return when {
            total == null -> ReceiptReviewReconciliationUiState(
                message = "Receipt total needs review.",
                isMismatch = true,
            )
            total == reconciledTotalMinor -> ReceiptReviewReconciliationUiState(
                message = "Items and adjustments match the receipt total.",
                isMismatch = false,
            )
            else -> {
                val delta = total - reconciledTotalMinor
                val direction = if (delta > 0) "Receipt total is higher by" else "Receipt total is lower by"
                ReceiptReviewReconciliationUiState(
                    message = "$direction ${formatCurrency(formatMoneyInput(kotlin.math.abs(delta)), currencyCode)}.",
                    isMismatch = true,
                )
            }
        }
    }
}

data class ReceiptReviewItemUiState(
    val id: String,
    val name: String = "",
    val quantity: String = "1",
    val amount: String = "",
) {
    fun totalLabel(currencyCode: String): String = formatCurrency(amount, currencyCode)

    fun quantityDetail(currencyCode: String): String {
        val quantityValue = quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val amountMinor = parseMoneyMinorValue(amount)
        return if (quantityValue > 1 && amountMinor != null) {
            val unitAmount = BigDecimal(amountMinor)
                .divide(BigDecimal(quantityValue), 0, RoundingMode.DOWN)
                .longValueExact()
            "Qty $quantityValue · ${formatCurrency(formatMoneyInput(unitAmount), currencyCode)} each"
        } else {
            "Qty $quantityValue"
        }
    }
}

data class ReceiptReviewFeeUiState(
    val id: String,
    val type: FeeType = FeeType.Other,
    val label: String = "",
    val amount: String = "",
)

data class ReceiptReviewReconciliationUiState(
    val message: String,
    val isMismatch: Boolean,
)

sealed interface ReceiptReviewEditTarget {
    data object Merchant : ReceiptReviewEditTarget

    data object Date : ReceiptReviewEditTarget

    data object Currency : ReceiptReviewEditTarget

    data object ReceiptTotal : ReceiptReviewEditTarget

    data class Item(val itemId: String) : ReceiptReviewEditTarget

    data class Fee(val feeId: String) : ReceiptReviewEditTarget
}

internal fun formatCurrency(amount: String, currencyCode: String): String {
    val value = amount.trim().ifBlank { "0.00" }
    val prefix = when (currencyCode.uppercase()) {
        "EUR" -> "€"
        "USD" -> "\$"
        "GBP" -> "£"
        else -> "${currencyCode.uppercase()} "
    }
    return "$prefix$value"
}

internal fun parseMoneyMinorValue(value: String): Long? {
    val normalized = value.trim().removePrefix("€").removePrefix("\$").replace(",", "")
    if (normalized.isBlank()) return null
    return try {
        BigDecimal(normalized)
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }
}

internal fun formatMoneyInput(value: Long): String {
    return BigDecimal(value).movePointLeft(2).setScale(2).toPlainString()
}
