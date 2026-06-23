package com.dps.evenup.domain.receipt.impl

import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.NormalizeReceiptUseCase
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptFee
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

class DefaultNormalizeReceiptUseCase : NormalizeReceiptUseCase {
    override fun normalize(receipt: Receipt): Receipt {
        return receipt.copy(
            fees = receipt.normalizedFees(),
            transactionDateLabel = receipt.transactionDateLabel?.toReceiptDateLabel(),
        )
    }

    private fun Receipt.normalizedFees(): List<ReceiptFee> {
        return fees.filterIndexed { index, fee ->
            !shouldRemoveIncludedTaxFee(fee = fee, feeIndex = index)
        }
    }

    private fun Receipt.shouldRemoveIncludedTaxFee(
        fee: ReceiptFee,
        feeIndex: Int,
    ): Boolean {
        if (!fee.isTaxLike() || fee.amount.value <= 0L) return false
        if (fee.label.isIncludedTaxLabel()) return true
        if (fee.amount.value == total.value || fee.amount.value == subtotal?.value) return true

        val feesWithoutCurrent = fees.filterIndexed { index, _ -> index != feeIndex }
        return reconcilesWithFees(feesWithoutCurrent)
    }

    private fun Receipt.reconcilesWithFees(fees: List<ReceiptFee>): Boolean {
        val itemTotal = items.sumOf { item -> item.totalPrice.value }
        val feeTotal = fees.sumOf { fee -> fee.amount.value }
        val subtotalValue = subtotal?.value
        return if (subtotalValue != null) {
            itemTotal == subtotalValue && subtotalValue + feeTotal == total.value
        } else {
            itemTotal + feeTotal == total.value
        }
    }

    private fun ReceiptFee.isTaxLike(): Boolean {
        val normalized = label.normalizedForTaxMatching()
        return type == FeeType.Tax || normalized.split(" ").any { word -> word in TAX_LABEL_WORDS }
    }

    private fun String.isIncludedTaxLabel(): Boolean {
        val normalized = normalizedForTaxMatching()
        return INCLUDED_TAX_PHRASES.any { phrase -> normalized.contains(phrase) }
    }

    private fun String.normalizedForTaxMatching(): String {
        return lowercase()
            .replace(".", " ")
            .replace("-", " ")
            .replace("_", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.toReceiptDateLabel(): String {
        val value = trim()
        if (value.isBlank()) return value
        return parseIsoDate(value)?.toString() ?: value
    }

    private fun parseIsoDate(value: String): LocalDate? {
        return parsers.firstNotNullOfOrNull { parser ->
            try {
                parser(value)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    private companion object {
        val INCLUDED_TAX_PHRASES = listOf(
            "di cui iva",
            "iva inclusa",
            "vat included",
            "incl vat",
            "includes tax",
            "tax included",
            "mwst enthalten",
            "tva incluse",
            "iva incluido",
        )

        val TAX_LABEL_WORDS = listOf(
            "tax",
            "vat",
            "iva",
            "gst",
            "mwst",
            "tva",
        )

        val parsers: List<(String) -> LocalDate> = listOf(
            { value -> LocalDate.parse(value) },
            { value -> LocalDateTime.parse(value).toLocalDate() },
            { value -> OffsetDateTime.parse(value).toLocalDate() },
            { value -> ZonedDateTime.parse(value).toLocalDate() },
        )
    }
}
