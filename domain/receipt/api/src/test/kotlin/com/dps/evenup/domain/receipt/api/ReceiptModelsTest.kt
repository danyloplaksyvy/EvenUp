package com.dps.evenup.domain.receipt.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptModelsTest {
    @Test
    fun `receipt model holds items fees and total`() {
        val item = ReceiptItem(
            id = ReceiptItemId("item-1"),
            name = "Pasta",
            quantity = Quantity(2),
            unitPrice = MoneyMinor(1_200),
            totalPrice = MoneyMinor(2_400),
        )
        val fee = ReceiptFee(
            id = FeeId("fee-1"),
            type = FeeType.Tax,
            label = "Tax",
            amount = MoneyMinor(240),
        )

        val receipt = Receipt(
            merchantName = "Bella Roma",
            currencyCode = CurrencyCode("EUR"),
            items = listOf(item),
            fees = listOf(fee),
            total = MoneyMinor(2_640),
            transactionDateLabel = "2026-06-15",
            subtotal = MoneyMinor(2_400),
        )

        assertEquals(MoneyMinor(2_640), receipt.total)
        assertEquals(MoneyMinor(2_400), receipt.subtotal)
        assertEquals(FeeType.Tax, receipt.fees.single().type)
    }
}
