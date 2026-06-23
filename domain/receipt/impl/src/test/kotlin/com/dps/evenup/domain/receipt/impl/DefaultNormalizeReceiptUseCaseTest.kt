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
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultNormalizeReceiptUseCaseTest {
    private val useCase = DefaultNormalizeReceiptUseCase()

    @Test
    fun `included VAT phrases are removed from additive fees`() {
        val phrases = listOf(
            "DI CUI IVA",
            "IVA inclusa",
            "VAT included",
            "incl. VAT",
            "includes tax",
            "tax included",
            "MwSt enthalten",
            "TVA incluse",
            "IVA incluido",
        )

        phrases.forEach { phrase ->
            val receipt = useCase.normalize(
                receipt(
                    fees = listOf(ReceiptFee(FeeId("fee-1"), FeeType.Other, phrase, MoneyMinor(123))),
                ),
            )

            assertEquals("Failed phrase $phrase", emptyList<ReceiptFee>(), receipt.fees)
        }
    }

    @Test
    fun `true additive fees remain additive`() {
        val additiveFees = listOf(
            ReceiptFee(FeeId("tip"), FeeType.Tip, "Tip", MoneyMinor(200)),
            ReceiptFee(FeeId("service"), FeeType.ServiceFee, "Service charge", MoneyMinor(300)),
            ReceiptFee(FeeId("tax"), FeeType.Tax, "Tax", MoneyMinor(400)),
            ReceiptFee(FeeId("delivery"), FeeType.Other, "Delivery fee", MoneyMinor(500)),
        )

        val receipt = useCase.normalize(receipt(fees = additiveFees))

        assertEquals(additiveFees, receipt.fees)
    }

    @Test
    fun `duplicate tax matching receipt total is removed`() {
        val duplicateTax = ReceiptFee(FeeId("tax"), FeeType.Tax, "Tax", MoneyMinor(6_195))
        val discount = ReceiptFee(FeeId("discount"), FeeType.Discount, "Portal Reserva 30%", MoneyMinor(-2_655))

        val receipt = useCase.normalize(
            receipt(
                itemTotal = 8_850,
                fees = listOf(duplicateTax, discount),
                subtotal = MoneyMinor(8_850),
                total = MoneyMinor(6_195),
            ),
        )

        assertEquals(listOf(discount), receipt.fees)
    }

    @Test
    fun `included IVA is removed when other adjustments already reconcile total`() {
        val includedIva = ReceiptFee(FeeId("tax"), FeeType.Tax, "IVA 10%", MoneyMinor(563))
        val discount = ReceiptFee(FeeId("discount"), FeeType.Discount, "Portal Reserva 30%", MoneyMinor(-2_655))

        val receipt = useCase.normalize(
            receipt(
                itemTotal = 8_850,
                fees = listOf(includedIva, discount),
                subtotal = MoneyMinor(8_850),
                total = MoneyMinor(6_195),
            ),
        )

        assertEquals(listOf(discount), receipt.fees)
    }

    @Test
    fun `true additive tax remains when needed to reconcile total`() {
        val additiveTax = ReceiptFee(FeeId("tax"), FeeType.Tax, "Tax", MoneyMinor(100))

        val receipt = useCase.normalize(
            receipt(
                itemTotal = 1_000,
                fees = listOf(additiveTax),
                subtotal = MoneyMinor(1_000),
                total = MoneyMinor(1_100),
            ),
        )

        assertEquals(listOf(additiveTax), receipt.fees)
    }

    @Test
    fun `raw iso datetime normalizes to date label`() {
        val receipt = useCase.normalize(receipt(transactionDateLabel = "2018-11-03T16:39:00"))

        assertEquals("2018-11-03", receipt.transactionDateLabel)
    }

    private fun receipt(
        fees: List<ReceiptFee> = emptyList(),
        transactionDateLabel: String? = null,
        itemTotal: Long = 1_000,
        subtotal: MoneyMinor? = null,
        total: MoneyMinor = MoneyMinor(itemTotal + fees.sumOf { fee -> fee.amount.value }),
    ): Receipt = Receipt(
        merchantName = "Cafe",
        currencyCode = CurrencyCode("EUR"),
        items = listOf(
            ReceiptItem(
                id = ReceiptItemId("item-1"),
                name = "Coffee",
                quantity = Quantity(1),
                unitPrice = MoneyMinor(itemTotal),
                totalPrice = MoneyMinor(itemTotal),
            ),
        ),
        fees = fees,
        total = total,
        transactionDateLabel = transactionDateLabel,
        subtotal = subtotal,
    )
}
