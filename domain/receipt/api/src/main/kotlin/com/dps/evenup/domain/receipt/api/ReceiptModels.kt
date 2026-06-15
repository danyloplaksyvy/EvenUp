package com.dps.evenup.domain.receipt.api

data class Receipt(
    val merchantName: String,
    val currencyCode: CurrencyCode,
    val items: List<ReceiptItem>,
    val fees: List<ReceiptFee>,
    val total: MoneyMinor,
    val transactionDateLabel: String? = null,
)

data class ReceiptItem(
    val id: ReceiptItemId,
    val name: String,
    val quantity: Quantity,
    val unitPrice: MoneyMinor,
    val totalPrice: MoneyMinor,
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
    Other,
}
