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
import java.time.LocalDate
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
    fun `items plus fees matching total passes without parsed subtotal`() {
        val result = useCase.validate(
            validReceipt().copy(
                items = listOf(validItem().copy(totalPrice = MoneyMinor(1_500))),
                fees = listOf(ReceiptFee(FeeId("tip"), FeeType.Tip, "Tip", MoneyMinor(300))),
                total = MoneyMinor(1_800),
                subtotal = null,
            ),
        )

        assertTrue(result.isValid)
    }

    @Test
    fun `discount reduces total and validates as negative receipt component`() {
        val result = useCase.validate(
            validReceipt().copy(
                items = listOf(validItem().copy(totalPrice = MoneyMinor(1_500))),
                fees = listOf(
                    ReceiptFee(FeeId("tip"), FeeType.Tip, "Tip", MoneyMinor(300)),
                    ReceiptFee(FeeId("discount"), FeeType.Discount, "Discount", MoneyMinor(-200)),
                ),
                total = MoneyMinor(1_600),
                subtotal = null,
            ),
        )

        assertTrue(result.isValid)
    }

    @Test
    fun `discount must be negative and additive fees must be positive`() {
        val positiveDiscount = useCase.validate(
            validReceipt().copy(
                fees = listOf(ReceiptFee(FeeId("discount"), FeeType.Discount, "Discount", MoneyMinor(100))),
                total = MoneyMinor(1_100),
            ),
        )
        val negativeTax = useCase.validate(
            validReceipt().copy(
                fees = listOf(ReceiptFee(FeeId("tax"), FeeType.Tax, "Tax", MoneyMinor(-100))),
                total = MoneyMinor(900),
            ),
        )

        assertFalse(positiveDiscount.isValid)
        assertTrue(ReceiptValidationError.NonPositiveFeeAmount in positiveDiscount.errors)
        assertFalse(negativeTax.isValid)
        assertTrue(ReceiptValidationError.NonPositiveFeeAmount in negativeTax.errors)
    }

    @Test
    fun `items plus fees mismatch returns total mismatch without parsed subtotal`() {
        val result = useCase.validate(
            validReceipt().copy(
                items = listOf(validItem().copy(totalPrice = MoneyMinor(1_500))),
                fees = listOf(ReceiptFee(FeeId("tip"), FeeType.Tip, "Tip", MoneyMinor(300))),
                total = MoneyMinor(1_700),
                subtotal = null,
            ),
        )

        assertFalse(result.isValid)
        assertTrue(ReceiptValidationError.TotalMismatch in result.errors)
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

    @Test
    fun `subtotal validates item sum before fees`() {
        val result = useCase.validate(
            validReceipt().copy(
                subtotal = MoneyMinor(1_000),
                total = MoneyMinor(1_200),
            ),
        )

        assertTrue(result.isValid)
    }

    @Test
    fun `subtotal mismatch returns total mismatch error`() {
        val result = useCase.validate(
            validReceipt().copy(
                items = listOf(validItem().copy(totalPrice = MoneyMinor(1_020))),
                subtotal = MoneyMinor(1_000),
                total = MoneyMinor(1_200),
            ),
        )

        assertFalse(result.isValid)
        assertTrue(ReceiptValidationError.TotalMismatch in result.errors)
    }

    @Test
    fun `subtotal plus fees must equal total`() {
        val result = useCase.validate(
            validReceipt().copy(
                subtotal = MoneyMinor(1_000),
                total = MoneyMinor(1_210),
            ),
        )

        assertFalse(result.isValid)
        assertTrue(ReceiptValidationError.TotalMismatch in result.errors)
    }

    @Test
    fun `future receipt date is invalid`() {
        val result = useCase.validate(
            validReceipt().copy(
                transactionDateLabel = LocalDate.now().plusDays(1).toString(),
            ),
        )

        assertFalse(result.isValid)
        assertTrue(ReceiptValidationError.FutureDate in result.errors)
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
