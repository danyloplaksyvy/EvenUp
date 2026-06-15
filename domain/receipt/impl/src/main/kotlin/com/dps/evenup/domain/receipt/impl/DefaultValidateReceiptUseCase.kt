package com.dps.evenup.domain.receipt.impl

import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptValidationError
import com.dps.evenup.domain.receipt.api.ReceiptValidationResult
import com.dps.evenup.domain.receipt.api.ValidateReceiptUseCase

class DefaultValidateReceiptUseCase : ValidateReceiptUseCase {
    override fun validate(receipt: Receipt): ReceiptValidationResult {
        val errors = buildSet {
            if (receipt.merchantName.isBlank()) add(ReceiptValidationError.BlankMerchantName)
            if (receipt.items.isEmpty()) add(ReceiptValidationError.NoItems)
            if (receipt.total.value < 0) add(ReceiptValidationError.NegativeTotal)

            receipt.items.forEach { item ->
                if (item.name.isBlank()) add(ReceiptValidationError.BlankItemName)
                if (item.unitPrice.value <= 0 || item.totalPrice.value <= 0) {
                    add(ReceiptValidationError.NonPositiveItemAmount)
                }
            }

            receipt.fees.forEach { fee ->
                if (fee.amount.value <= 0) add(ReceiptValidationError.NonPositiveFeeAmount)
            }

            val itemTotal = receipt.items.sumOf { it.totalPrice.value }
            val feeTotal = receipt.fees.sumOf { it.amount.value }
            if (itemTotal + feeTotal != receipt.total.value) {
                add(ReceiptValidationError.TotalMismatch)
            }
        }

        return if (errors.isEmpty()) ReceiptValidationResult.Valid else ReceiptValidationResult(errors)
    }
}
