package com.dps.evenup.feature.expenseflow.impl.receiptreview

import com.dps.evenup.domain.receipt.api.ReceiptItemParseMetadata
import com.dps.evenup.domain.receipt.api.ReceiptParseCorrection
import kotlin.math.abs
import kotlin.sequences.SequenceScope

internal object ReceiptMismatchDiagnoser {
    fun diagnose(
        items: List<ReceiptMismatchItem>,
        parseCorrections: List<ReceiptParseCorrection>,
        scannedTotalMinor: Long?,
        calculatedTotalMinor: Long,
    ): ReceiptMismatchDiagnosis? {
        val scannedTotal = scannedTotalMinor ?: return null
        val difference = calculatedTotalMinor - scannedTotal
        if (difference == 0L) return null

        val desiredDelta = -difference
        val candidates = items.flatMapIndexed { index, item ->
            item.candidates(index = index, corrections = parseCorrections)
        }
            .filter { candidate -> candidate.deltaMinor != 0L }
            .filter { candidate -> candidate.deltaMinor.sign == desiredDelta.sign }
            .filter { candidate -> abs(candidate.deltaMinor) <= abs(desiredDelta) }
            .distinctBy { candidate ->
                listOf(candidate.itemId, candidate.suggestedAmountMinor, candidate.reason)
            }

        val exactMatch = findExactCombination(candidates, desiredDelta)
        val suspectedCorrections = exactMatch.orEmpty().map { candidate ->
            SuspectedItemCorrection(
                itemId = candidate.itemId,
                itemName = candidate.itemName,
                currentAmountMinor = candidate.currentAmountMinor,
                suggestedAmountMinor = candidate.suggestedAmountMinor,
                differenceMinor = abs(candidate.deltaMinor),
                reason = candidate.reason,
                confidence = DiagnosisConfidence.High,
            )
        }

        return ReceiptMismatchDiagnosis(
            scannedTotalMinor = scannedTotal,
            calculatedTotalMinor = calculatedTotalMinor,
            differenceMinor = difference,
            suspectedCorrections = suspectedCorrections,
            confidence = if (suspectedCorrections.isEmpty()) DiagnosisConfidence.Low else DiagnosisConfidence.High,
        )
    }

    private fun ReceiptMismatchItem.candidates(
        index: Int,
        corrections: List<ReceiptParseCorrection>,
    ): List<CorrectionCandidate> {
        val metadataCandidates = parseMetadata.candidates.map { candidate ->
            candidate.value to SuspectedCorrectionReason.MultipleAmountCandidates
        }
        val tiedCorrections = corrections
            .filter { correction -> correction.matchesItem(index = index, itemName = name) }
            .flatMap { correction ->
                listOf(
                    correction.from.value to SuspectedCorrectionReason.CorrectedToMatchSubtotal,
                    correction.to.value to SuspectedCorrectionReason.CorrectedToMatchSubtotal,
                )
            }
        val visualCandidates = visualDigitCandidates(currentAmountMinor).map { candidate ->
            candidate to SuspectedCorrectionReason.VisualDigitMismatch
        }

        return (metadataCandidates + tiedCorrections + visualCandidates)
            .filter { (amount, _) -> amount > 0 && amount != currentAmountMinor }
            .map { (amount, reason) ->
                CorrectionCandidate(
                    itemId = id,
                    itemName = name,
                    currentAmountMinor = currentAmountMinor,
                    suggestedAmountMinor = amount,
                    deltaMinor = amount - currentAmountMinor,
                    reason = reason,
                    metadataBacked = reason != SuspectedCorrectionReason.VisualDigitMismatch,
                )
            }
    }

    private fun findExactCombination(
        candidates: List<CorrectionCandidate>,
        desiredDelta: Long,
    ): List<CorrectionCandidate>? {
        val ranked = candidates.sortedWith(
            compareByDescending<CorrectionCandidate> { it.metadataBacked }
                .thenBy { abs(abs(desiredDelta) - abs(it.deltaMinor)) }
                .thenBy { abs(it.deltaMinor) },
        )
        for (size in 1..MAX_CORRECTION_SET_SIZE) {
            val match = combinations(ranked, size).firstOrNull { combination ->
                combination.map { candidate -> candidate.itemId }.distinct().size == combination.size &&
                    combination.sumOf { candidate -> candidate.deltaMinor } == desiredDelta
            }
            if (match != null) return match.sortedBy { candidate -> candidate.itemName.lowercase() }
        }
        return null
    }

    private fun combinations(
        candidates: List<CorrectionCandidate>,
        size: Int,
    ): Sequence<List<CorrectionCandidate>> = sequence {
        val capped = candidates.take(MAX_CANDIDATES)
        suspend fun SequenceScope<List<CorrectionCandidate>>.visit(
            start: Int,
            current: List<CorrectionCandidate>,
        ) {
            if (current.size == size) {
                yield(current)
                return
            }
            for (index in start until capped.size) {
                visit(index + 1, current + capped[index])
            }
        }
        visit(start = 0, current = emptyList())
    }

    private fun visualDigitCandidates(amountMinor: Long): List<Long> {
        val digits = amountMinor.toString().padStart(3, '0')
        return digits.flatMapIndexed { index, digit ->
            VISUAL_DIGIT_PAIRS[digit].orEmpty().map { replacement ->
                digits.replaceRange(index, index + 1, replacement.toString()).toLong()
            }
        }.distinct()
    }

    private fun ReceiptParseCorrection.matchesItem(
        index: Int,
        itemName: String,
    ): Boolean {
        val fieldIndex = ITEM_FIELD_INDEX.find(field)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return fieldIndex == index || itemName.isNotBlank() && itemName.equals(this.itemName, ignoreCase = true)
    }

    private val Long.sign: Int
        get() = compareTo(0)

    private const val MAX_CORRECTION_SET_SIZE = 3
    private const val MAX_CANDIDATES = 36
    private val ITEM_FIELD_INDEX = Regex("""items\[(\d+)]""")
    private val VISUAL_DIGIT_PAIRS = mapOf(
        '8' to listOf('6', '0', '3'),
        '6' to listOf('8', '5'),
        '9' to listOf('0'),
        '0' to listOf('9', '8'),
        '5' to listOf('6'),
        '3' to listOf('8'),
    )
}

internal data class ReceiptMismatchItem(
    val id: String,
    val name: String,
    val currentAmountMinor: Long,
    val parseMetadata: ReceiptItemParseMetadata,
)

data class ReceiptMismatchDiagnosis(
    val scannedTotalMinor: Long,
    val calculatedTotalMinor: Long,
    val differenceMinor: Long,
    val suspectedCorrections: List<SuspectedItemCorrection>,
    val confidence: DiagnosisConfidence,
)

data class SuspectedItemCorrection(
    val itemId: String,
    val itemName: String,
    val currentAmountMinor: Long,
    val suggestedAmountMinor: Long,
    val differenceMinor: Long,
    val reason: SuspectedCorrectionReason,
    val confidence: DiagnosisConfidence,
)

enum class DiagnosisConfidence {
    High,
    Medium,
    Low,
}

enum class SuspectedCorrectionReason {
    CandidateAmountMatchesTotal,
    VisualDigitMismatch,
    CorrectedToMatchSubtotal,
    LowScanConfidence,
    MultipleAmountCandidates,
}

private data class CorrectionCandidate(
    val itemId: String,
    val itemName: String,
    val currentAmountMinor: Long,
    val suggestedAmountMinor: Long,
    val deltaMinor: Long,
    val reason: SuspectedCorrectionReason,
    val metadataBacked: Boolean,
)
