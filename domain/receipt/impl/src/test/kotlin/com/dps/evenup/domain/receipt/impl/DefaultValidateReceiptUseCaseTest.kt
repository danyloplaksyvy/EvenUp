package com.dps.evenup.domain.receipt.impl

import com.dps.evenup.domain.receipt.api.CurrencyCode
import com.dps.evenup.domain.receipt.api.FeeId
import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Quantity
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptFee
import com.dps.evenup.domain.receipt.api.ReceiptItem
import com.dps.evenup.domain.receipt.api.ReceiptItemId
import com.dps.evenup.domain.receipt.api.ReceiptValidationError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultValidateReceiptUseCaseTest {
    private val useCase = DefaultValidateReceiptUseCase()

    @Test
    fun `valid receipt passes`() {
        assertTrue(useCase.validate(validReceipt()).isValid)
    }

    @Test
    fun `invalid receipt fields return specific errors`() {
        val result = useCase.validate(
            validReceipt().copy(
                merchantName = " ",
                items = listOf(validItem().copy(name = " ", totalPrice = MoneyMinor(0))),
                total = MoneyMinor(1),
            ),
        )

        assertFalse(result.isValid)
        assertTrue(ReceiptValidationError.BlankMerchantName in result.errors)
        assertTrue(ReceiptValidationError.BlankItemName in result.errors)
        assertTrue(ReceiptValidationError.NonPositiveItemAmount in result.errors)
        assertTrue(ReceiptValidationError.TotalMismatch in result.errors)
    }

    private fun validReceipt(): Receipt = Receipt(
        merchantName = "Bella Roma",
        currencyCode = CurrencyCode("EUR"),
        items = listOf(validItem()),
        fees = listOf(
            ReceiptFee(FeeId("tax"), FeeType.Tax, "Tax", MoneyMinor(200)),
        ),
        total = MoneyMinor(1_200),
    )

    private fun validItem(): ReceiptItem = ReceiptItem(
        id = ReceiptItemId("item-1"),
        name = "Pasta",
        quantity = Quantity(1),
        unitPrice = MoneyMinor(1_000),
        totalPrice = MoneyMinor(1_000),
    )
}
