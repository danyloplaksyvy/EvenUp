package com.dps.evenup.feature.expenseflow.impl.manualentry

import com.dps.evenup.domain.receipt.api.FeeType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Currency
import java.util.Locale

data class ManualReceiptEntryUiState(
    val merchantName: String = "",
    val dateLabel: String = LocalDate.now().toString(),
    val currencyCode: String = resolveDefaultManualCurrencyCode(),
    val items: List<ManualReceiptItemUiState> = emptyList(),
    val fees: List<ManualReceiptFeeUiState> = emptyList(),
    val editDraft: ManualReceiptEditDraft? = null,
    val isSaving: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val firstBlockingSection: ManualReceiptEntrySection? = null,
    val firstBlockingItemId: String? = null,
    val validationRequestId: Int = 0,
    val submitError: String? = null,
) {
    val displayMerchantLabel: String = merchantName.trim().ifBlank { MANUAL_RECEIPT_FALLBACK_LABEL }
    val currencyChoices: List<String> = manualCurrencyChoices(currencyCode)
    val itemCountLabel: String = when (items.size) {
        0 -> "No items"
        1 -> "1 item"
        else -> "${items.size} items"
    }
    val feeCountLabel: String = when (fees.size) {
        0 -> "No fees"
        1 -> "1 fee"
        else -> "${fees.size} fees"
    }
    val itemSubtotalMinor: Long = items.sumOf { item -> parseManualMoneyMinorValue(item.amount) ?: 0L }
    val feeTotalMinor: Long = fees.sumOf { fee -> fee.amountMinor ?: 0L }
    val calculatedTotalMinor: Long = itemSubtotalMinor + feeTotalMinor
    val subtotalLabel: String = formatManualCurrency(formatManualMoneyInput(itemSubtotalMinor), currencyCode)
    val feesTotalLabel: String = formatManualCurrency(formatManualMoneyInput(feeTotalMinor), currencyCode)
    val calculatedTotalLabel: String = formatManualCurrency(formatManualMoneyInput(calculatedTotalMinor), currencyCode)
    val displayTotalLabel: String = calculatedTotalLabel
    val canContinue: Boolean = !isSaving && continueBlockedMessage == null
    val continueBlockedMessage: String?
        get() = when {
            items.isEmpty() -> "Add at least one item to continue."
            items.any { item -> item.name.isBlank() } -> "Name each item before continuing."
            items.any { item -> item.quantity.toIntOrNull()?.let { it in MIN_MANUAL_QUANTITY..MAX_MANUAL_QUANTITY } != true } -> {
                "Use a valid quantity for every item."
            }
            items.any { item -> parseManualMoneyMinorValue(item.amount)?.let { it > 0L } != true } -> {
                "Enter a positive amount for every item."
            }
            dateLabel.toManualLocalDateOrNull() == null -> "Choose a valid receipt date."
            dateLabel.toManualLocalDateOrNull()?.isAfter(LocalDate.now()) == true -> "Date cannot be in the future."
            !currencyCode.isManualCurrencyCode() -> "Choose a valid currency."
            fees.any { fee -> fee.type == FeeType.Discount || !fee.hasValidAmount } -> "Review fee amounts."
            else -> null
        }

}

data class ManualReceiptItemUiState(
    val id: String,
    val name: String = "",
    val quantity: String = "1",
    val unitPriceAmount: String = "",
    val amount: String = "",
) {
    fun totalLabel(currencyCode: String): String = formatManualCurrency(amount, currencyCode)

    fun quantityDetail(currencyCode: String): String {
        val quantityValue = quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val amountMinor = parseManualMoneyMinorValue(amount)
        return if (quantityValue > 1 && amountMinor != null) {
            val unitAmount = BigDecimal(amountMinor).divide(BigDecimal(quantityValue), 2, RoundingMode.HALF_UP)
            "${quantityValue}x - ${formatManualCurrency(formatManualMoneyInput(unitAmount), currencyCode)} each"
        } else {
            "${quantityValue}x"
        }
    }
}

data class ManualReceiptFeeUiState(
    val id: String,
    val type: FeeType = FeeType.Tax,
    val label: String = "",
    val amount: String = "",
) {
    val displayLabel: String = manualFeeDisplayLabel(type, label)
    val amountMinor: Long? = parseManualMoneyMinorValue(amount)
    val hasValidAmount: Boolean = displayLabel.isNotBlank() && amountMinor?.let { value -> value > 0L } == true

    fun amountLabel(currencyCode: String): String = formatManualCurrency(amount, currencyCode)
}

enum class ManualReceiptEntrySection {
    Details,
    Items,
    Fees,
    Summary,
}

sealed interface ManualReceiptEditTarget {
    data object Merchant : ManualReceiptEditTarget
    data object Date : ManualReceiptEditTarget
    data object Currency : ManualReceiptEditTarget
    data class Item(val itemId: String) : ManualReceiptEditTarget
    data class Fee(val feeId: String) : ManualReceiptEditTarget
}

sealed interface ManualReceiptEditDraft {
    data class Merchant(val value: String) : ManualReceiptEditDraft
    data class Date(val value: String) : ManualReceiptEditDraft
    data class Currency(val value: String) : ManualReceiptEditDraft

    data class Item(
        val itemId: String?,
        val name: String,
        val quantity: String,
        val unitPrice: String,
        val lineTotal: String,
        val lastEditedMoneyField: ManualReceiptMoneyField,
        val isNew: Boolean,
        val initialName: String = name,
        val initialQuantity: String = quantity,
        val initialUnitPrice: String = unitPrice,
        val initialLineTotal: String = lineTotal,
    ) : ManualReceiptEditDraft {
        val showsPriceEach: Boolean
            get() = quantity.toIntOrNull()?.let { it > 1 } == true

        val hasChanges: Boolean
            get() = name != initialName ||
                quantity != initialQuantity ||
                unitPrice != initialUnitPrice ||
                lineTotal != initialLineTotal

        val primaryActionLabel: String
            get() = if (isNew) "Add item" else "Save changes"

        fun averagePriceNote(currencyCode: String): String? {
            val quantityValue = quantity.toIntOrNull()?.takeIf { it > 1 } ?: return null
            val itemTotal = parseManualMoneyMinorValue(lineTotal) ?: return null
            if (itemTotal % quantityValue == 0L) return null
            val averagePriceEach = formatManualMoneyInput(
                BigDecimal(itemTotal).divide(BigDecimal(quantityValue), 2, RoundingMode.HALF_UP),
            )
            return "Average price each is ${formatManualCurrency(averagePriceEach, currencyCode)} because the item total does not split evenly."
        }
    }

    data class Fee(
        val feeId: String?,
        val type: FeeType,
        val label: String,
        val amount: String,
        val isNew: Boolean,
    ) : ManualReceiptEditDraft {
        val displayLabel: String = manualFeeDisplayLabel(type, label)
    }
}

enum class ManualReceiptMoneyField {
    PriceEach,
    ItemTotal,
}

internal const val MANUAL_RECEIPT_FALLBACK_LABEL = "Manual Receipt"
internal const val MIN_MANUAL_QUANTITY = 1
internal const val MAX_MANUAL_QUANTITY = 999
internal const val MAX_MANUAL_MONEY_MINOR = 9_999_999L

internal fun resolveDefaultManualCurrencyCode(locale: Locale = Locale.getDefault()): String {
    return runCatching { Currency.getInstance(locale).currencyCode }
        .getOrNull()
        ?.uppercase(Locale.US)
        ?.takeIf { value -> value.isManualCurrencyCode() }
        ?: "USD"
}

internal fun manualCurrencyChoices(selectedCurrencyCode: String): List<String> {
    return (listOf("EUR", "USD", "GBP", selectedCurrencyCode.uppercase(Locale.US)))
        .filter { value -> value.isManualCurrencyCode() }
        .distinct()
}

internal fun formatManualCurrency(amount: String, currencyCode: String): String {
    val value = parseManualMoneyMinorValue(amount)?.let(::formatManualMoneyInput)
        ?: amount.trim().ifBlank { "0.00" }
    return "${manualCurrencySymbol(currencyCode)}$value"
}

internal fun manualCurrencySymbol(currencyCode: String): String = when (currencyCode.uppercase(Locale.US)) {
    "EUR" -> "€"
    "USD" -> "$"
    "GBP" -> "£"
    else -> "${currencyCode.uppercase(Locale.US)} "
}

internal fun parseManualMoneyMinorValue(value: String): Long? {
    val normalized = value.trim()
        .removePrefix("$")
        .removePrefix("€")
        .removePrefix("£")
        .replace(',', '.')
    if (normalized.isBlank()) return null
    return try {
        val amountMinor = BigDecimal(normalized)
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
        amountMinor.takeIf { it in 0..MAX_MANUAL_MONEY_MINOR }
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }
}

internal fun formatManualMoneyInput(value: Long): String {
    return BigDecimal(value).movePointLeft(2).setScale(2).toPlainString()
}

internal fun formatManualMoneyInput(value: BigDecimal): String {
    return value.movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()
}

internal fun manualFeeDisplayLabel(type: FeeType, label: String = ""): String {
    return when (type) {
        FeeType.Tax -> "Tax"
        FeeType.Tip -> "Tip"
        FeeType.ServiceFee -> "Service charge"
        FeeType.Other -> label.trim().ifBlank { "Other" }
        FeeType.Discount -> label.trim().ifBlank { "Discount" }
    }
}

internal fun String.toManualLocalDateOrNull(): LocalDate? {
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

internal fun String.toManualReceiptDisplayDate(): String {
    val date = toManualLocalDateOrNull() ?: return trim()
    return date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))
}

internal fun String.isManualCurrencyCode(): Boolean {
    return length == 3 && all { char -> char in 'A'..'Z' }
}
