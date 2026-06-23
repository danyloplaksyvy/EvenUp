package com.dps.evenup.domain.receipt.api

data class Receipt(
    val merchantName: String,
    val currencyCode: CurrencyCode,
    val items: List<ReceiptItem>,
    val fees: List<ReceiptFee>,
    val total: MoneyMinor,
    val transactionDateLabel: String? = null,
    val subtotal: MoneyMinor? = null,
    val parseMetadata: ReceiptParseMetadata = ReceiptParseMetadata(),
)

data class ReceiptItem(
    val id: ReceiptItemId,
    val name: String,
    val quantity: Quantity,
    val unitPrice: MoneyMinor,
    val totalPrice: MoneyMinor,
    val parseMetadata: ReceiptItemParseMetadata = ReceiptItemParseMetadata(),
)

data class ReceiptFee(
    val id: FeeId,
    val type: FeeType,
    val label: String,
    val amount: MoneyMinor,
)

enum class FeeType {
    Tax,
    Tip,
    ServiceFee,
    Discount,
    Other,
}

data class ReceiptParseMetadata(
    val corrections: List<ReceiptParseCorrection> = emptyList(),
    val reviewWarnings: List<String> = emptyList(),
)

data class ReceiptItemParseMetadata(
    val confidence: Double? = null,
    val candidates: List<MoneyMinor> = emptyList(),
    val needsReview: Boolean = false,
)

data class ReceiptParseCorrection(
    val field: String,
    val itemName: String?,
    val from: MoneyMinor,
    val to: MoneyMinor,
    val reason: String,
)
