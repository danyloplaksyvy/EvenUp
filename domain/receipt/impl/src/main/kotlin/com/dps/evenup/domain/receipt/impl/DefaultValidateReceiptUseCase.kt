package com.dps.evenup.domain.receipt.impl

import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.ExpensePricingMode
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptValidationError
import com.dps.evenup.domain.receipt.api.ReceiptValidationResult
import com.dps.evenup.domain.receipt.api.ValidateReceiptUseCase
import java.time.LocalDate
import java.time.format.DateTimeParseException

class DefaultValidateReceiptUseCase : ValidateReceiptUseCase {
    override fun validate(receipt: Receipt): ReceiptValidationResult {
        val errors = buildSet {
            if (receipt.merchantName.isBlank()) add(ReceiptValidationError.BlankMerchantName)
            if (receipt.pricingMode == ExpensePricingMode.Itemized && receipt.items.isEmpty()) {
                add(ReceiptValidationError.NoItems)
            }
            if (receipt.total.value < 0) add(ReceiptValidationError.NegativeTotal)
            if (receipt.transactionDateLabel.isFutureIsoDate()) add(ReceiptValidationError.FutureDate)

            receipt.items.forEach { item ->
                if (item.name.isBlank()) add(ReceiptValidationError.BlankItemName)
                if (item.unitPrice.value <= 0 || item.totalPrice.value <= 0) {
                    add(ReceiptValidationError.NonPositiveItemAmount)
                }
            }
            receipt.descriptiveItems.forEach { item ->
                if (item.name.isBlank()) add(ReceiptValidationError.BlankItemName)
            }

            receipt.fees.forEach { fee ->
                when (fee.type) {
                    FeeType.Discount -> if (fee.amount.value >= 0) add(ReceiptValidationError.NonPositiveFeeAmount)
                    else -> if (fee.amount.value <= 0) add(ReceiptValidationError.NonPositiveFeeAmount)
                }
            }

            val itemTotal = receipt.items.sumOf { it.totalPrice.value }
            val feeTotal = receipt.fees.sumOf { it.amount.value }
            val subtotal = receipt.subtotal
            if (receipt.pricingMode == ExpensePricingMode.TotalOnly) {
                if (receipt.items.isNotEmpty() || receipt.total.value <= 0L || receipt.total.value - feeTotal < 0L) {
                    add(ReceiptValidationError.TotalMismatch)
                }
            } else if (subtotal != null) {
                if (itemTotal != subtotal.value || subtotal.value + feeTotal != receipt.total.value) {
                    add(ReceiptValidationError.TotalMismatch)
                }
            } else {
                if (itemTotal + feeTotal != receipt.total.value) {
                    add(ReceiptValidationError.TotalMismatch)
                }
            }
        }

        return if (errors.isEmpty()) ReceiptValidationResult.Valid else ReceiptValidationResult(errors)
    }

    private fun String?.isFutureIsoDate(): Boolean {
        if (isNullOrBlank()) return false
        return try {
            LocalDate.parse(trim()).isAfter(LocalDate.now())
        } catch (_: DateTimeParseException) {
            false
        }
    }
}
