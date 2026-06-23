package com.dps.evenup.feature.expenseflow.impl.receiptreview

import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.ReceiptItemParseMetadata
import com.dps.evenup.domain.receipt.api.ReceiptParseCorrection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptMismatchDiagnoserTest {
    @Test
    fun `derives calculated minus scanned difference direction`() {
        val diagnosis = diagnose(
            items = listOf(item("item-1", "Rissol", 580)),
            scannedTotalMinor = 560,
            calculatedTotalMinor = 580,
        )

        assertEquals(20L, diagnosis?.differenceMinor)
        assertEquals(560L, diagnosis?.suspectedCorrections?.single()?.suggestedAmountMinor)
    }

    @Test
    fun `generates exact correction from parse metadata candidates`() {
        val diagnosis = diagnose(
            items = listOf(
                item(
                    id = "item-1",
                    name = "Rissol",
                    amountMinor = 580,
                    parseMetadata = ReceiptItemParseMetadata(candidates = listOf(MoneyMinor(560))),
                ),
            ),
            scannedTotalMinor = 560,
            calculatedTotalMinor = 580,
        )

        val correction = diagnosis?.suspectedCorrections?.single()
        assertEquals(560L, correction?.suggestedAmountMinor)
        assertEquals(SuspectedCorrectionReason.MultipleAmountCandidates, correction?.reason)
        assertEquals(DiagnosisConfidence.High, correction?.confidence)
    }

    @Test
    fun `generates exact correction from visual digit substitution`() {
        val diagnosis = diagnose(
            items = listOf(item("item-1", "Rissol", 580)),
            scannedTotalMinor = 560,
            calculatedTotalMinor = 580,
        )

        val correction = diagnosis?.suspectedCorrections?.single()
        assertEquals(560L, correction?.suggestedAmountMinor)
        assertEquals(SuspectedCorrectionReason.VisualDigitMismatch, correction?.reason)
    }

    @Test
    fun `one item correction explains mismatch`() {
        val diagnosis = diagnose(
            items = listOf(
                item("item-1", "Rissol", 580),
                item("item-2", "Coffee", 300),
            ),
            scannedTotalMinor = 860,
            calculatedTotalMinor = 880,
        )

        assertEquals(listOf("item-1"), diagnosis?.suspectedCorrections?.map { it.itemId })
    }

    @Test
    fun `two item corrections explain mismatch`() {
        val diagnosis = diagnose(
            items = listOf(
                item("item-1", "Rissol", 580),
                item("item-2", "Octopus Peppers", 990),
                item("item-3", "Coffee", 5_065),
            ),
            scannedTotalMinor = 6_525,
            calculatedTotalMinor = 6_635,
        )

        assertEquals(
            listOf(560L, 900L),
            diagnosis?.suspectedCorrections?.map { it.suggestedAmountMinor }?.sorted(),
        )
        assertEquals(DiagnosisConfidence.High, diagnosis?.confidence)
    }

    @Test
    fun `does not return high confidence rows when candidates do not close mismatch`() {
        val diagnosis = diagnose(
            items = listOf(item("item-1", "Rissol", 580)),
            scannedTotalMinor = 570,
            calculatedTotalMinor = 580,
        )

        assertTrue(diagnosis?.suspectedCorrections.orEmpty().isEmpty())
        assertEquals(DiagnosisConfidence.Low, diagnosis?.confidence)
    }

    @Test
    fun `prefers metadata candidate over equivalent visual substitution`() {
        val diagnosis = diagnose(
            items = listOf(
                item(
                    id = "item-1",
                    name = "Rissol",
                    amountMinor = 580,
                    parseMetadata = ReceiptItemParseMetadata(candidates = listOf(MoneyMinor(560))),
                ),
            ),
            scannedTotalMinor = 560,
            calculatedTotalMinor = 580,
        )

        assertEquals(SuspectedCorrectionReason.MultipleAmountCandidates, diagnosis?.suspectedCorrections?.single()?.reason)
    }

    @Test
    fun `generates exact correction from quantity line total mismatch`() {
        val diagnosis = diagnose(
            items = listOf(
                item(
                    id = "item-1",
                    name = "Solomillo a la Sal",
                    amountMinor = 2_200,
                    quantity = 2,
                ),
            ),
            scannedTotalMinor = 6_195,
            calculatedTotalMinor = 3_995,
        )

        val correction = diagnosis?.suspectedCorrections?.single()
        assertEquals(4_400L, correction?.suggestedAmountMinor)
        assertEquals(SuspectedCorrectionReason.QuantityLineTotalMismatch, correction?.reason)
        assertEquals(DiagnosisConfidence.High, correction?.confidence)
    }

    private fun diagnose(
        items: List<ReceiptMismatchItem>,
        scannedTotalMinor: Long,
        calculatedTotalMinor: Long,
        corrections: List<ReceiptParseCorrection> = emptyList(),
    ): ReceiptMismatchDiagnosis? {
        return ReceiptMismatchDiagnoser.diagnose(
            items = items,
            parseCorrections = corrections,
            scannedTotalMinor = scannedTotalMinor,
            calculatedTotalMinor = calculatedTotalMinor,
        )
    }

    private fun item(
        id: String,
        name: String,
        amountMinor: Long,
        quantity: Int = 1,
        parseMetadata: ReceiptItemParseMetadata = ReceiptItemParseMetadata(),
    ): ReceiptMismatchItem {
        return ReceiptMismatchItem(
            id = id,
            name = name,
            currentAmountMinor = amountMinor,
            quantity = quantity,
            parseMetadata = parseMetadata,
        )
    }
}
