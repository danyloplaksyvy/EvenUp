package com.dps.evenup.domain.receipt.api

interface ValidateReceiptUseCase {
    fun validate(receipt: Receipt): ReceiptValidationResult
}

data class ReceiptValidationResult(
    val errors: Set<ReceiptValidationError>,
) {
    val isValid: Boolean = errors.isEmpty()

    companion object {
        val Valid = ReceiptValidationResult(emptySet())
    }
}

enum class ReceiptValidationError {
    BlankMerchantName,
    NoItems,
    BlankItemName,
    NonPositiveItemAmount,
    NonPositiveFeeAmount,
    TotalMismatch,
    NegativeTotal,
}
