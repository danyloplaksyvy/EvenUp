package com.dps.evenup.feature.expenseflow.impl.receiptscan

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.dps.evenup.core.camera.api.ReceiptImageReader
import com.dps.evenup.core.camera.api.ReceiptImageSource
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.receipt.api.ReceiptImageParseRequest
import com.dps.evenup.data.receipt.api.ReceiptRepository
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.ExpenseDraftId
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptValidationError
import com.dps.evenup.domain.receipt.api.ValidateReceiptUseCase
import java.util.UUID

class ReceiptScanPresenter(
    private val draftRepository: ExpenseDraftRepository,
    private val receiptRepository: ReceiptRepository,
    private val imageReader: ReceiptImageReader,
    private val validateReceipt: ValidateReceiptUseCase,
) {
    suspend fun parseImage(
        uri: Uri,
        source: ReceiptImageSource,
    ): ReceiptScanParseResult {
        val requestId = "receipt-scan-${UUID.randomUUID()}"
        val totalStart = System.nanoTime()

        try {
            val image = timedStage(
                requestId = requestId,
                stage = "image_read",
                metadata = listOf("source=$source"),
            ) {
                imageReader.readImage(uri = uri, source = source)
            }

            val imageBase64 = timedStage(
                requestId = requestId,
                stage = "base64_encode",
                metadata = listOf(
                    "imageBytes=${image.bytes.size}",
                    "mimeType=${image.mimeType}",
                    "source=${image.source}",
                ),
            ) {
                Base64.encodeToString(image.bytes, Base64.NO_WRAP)
            }

            val receipt = timedStage(
                requestId = requestId,
                stage = "repository_parse",
                metadata = listOf(
                    "imageBytes=${image.bytes.size}",
                    "base64Length=${imageBase64.length}",
                    "mimeType=${image.mimeType}",
                ),
            ) {
                receiptRepository.parseReceiptImage(
                    ReceiptImageParseRequest(
                        imageBase64 = imageBase64,
                        mimeType = image.mimeType,
                        localeHint = DEFAULT_LOCALE_HINT,
                        currencyHint = DEFAULT_CURRENCY_HINT,
                        requestId = requestId,
                    ),
                )
            }

            val validation = timedStage(
                requestId = requestId,
                stage = "receipt_validation",
                metadata = listOf(
                    "itemCount=${receipt.items.size}",
                    "feeCount=${receipt.fees.size}",
                    "warningCount=${receipt.parseMetadata.reviewWarnings.size}",
                ),
            ) {
                validateReceipt.validate(receipt)
            }
            if (validation.errors.hasFatalParseError()) {
                logTiming(
                    requestId = requestId,
                    stage = "scan_total",
                    durationMs = elapsedMillis(totalStart),
                    metadata = listOf("result=invalid"),
                )
                return ReceiptScanParseResult.Invalid(validation.errors.toErrorMessage())
            }

            timedStage(
                requestId = requestId,
                stage = "draft_save",
                metadata = listOf(
                    "itemCount=${receipt.items.size}",
                    "feeCount=${receipt.fees.size}",
                    "warningCount=${receipt.parseMetadata.reviewWarnings.size}",
                ),
            ) {
                draftRepository.saveDraft(receipt.toDraft())
            }

            logTiming(
                requestId = requestId,
                stage = "scan_total",
                durationMs = elapsedMillis(totalStart),
                metadata = listOf("result=saved"),
            )
            return ReceiptScanParseResult.Saved
        } catch (error: RuntimeException) {
            logTiming(
                requestId = requestId,
                stage = "scan_total",
                durationMs = elapsedMillis(totalStart),
                metadata = listOf(
                    "result=failed",
                    "errorType=${error::class.java.simpleName}",
                ),
                warning = true,
            )
            throw error
        }
    }

    private suspend inline fun <T> timedStage(
        requestId: String,
        stage: String,
        metadata: List<String> = emptyList(),
        block: suspend () -> T,
    ): T {
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            logTiming(
                requestId = requestId,
                stage = stage,
                durationMs = elapsedMillis(start),
                metadata = metadata,
            )
        }
    }

    private fun elapsedMillis(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    private fun logTiming(
        requestId: String,
        stage: String,
        durationMs: Long,
        metadata: List<String> = emptyList(),
        warning: Boolean = false,
    ) {
        val message = buildString {
            append("receipt_scan_timing requestId=")
            append(requestId)
            append(" stage=")
            append(stage)
            append(" durationMs=")
            append(durationMs)
            metadata.forEach { value ->
                append(' ')
                append(value)
            }
        }

        runCatching {
            if (warning) {
                Log.w(TAG, message)
            } else {
                Log.i(TAG, message)
            }
        }
    }

    private fun Receipt.toDraft(): ExpenseDraft = ExpenseDraft(
        id = ExpenseDraftId("draft-${UUID.randomUUID()}"),
        receipt = this,
        participants = emptyList(),
        payerId = ParticipantId(PENDING_PAYER_ID),
        itemAssignments = emptyList(),
        feeAllocations = emptyList(),
    )

    private fun Set<ReceiptValidationError>.hasFatalParseError(): Boolean {
        return any { error -> error != ReceiptValidationError.TotalMismatch }
    }

    private fun Set<ReceiptValidationError>.toErrorMessage(): String = when {
        contains(ReceiptValidationError.NoItems) -> "No receipt items were found."
        contains(ReceiptValidationError.BlankMerchantName) -> "Merchant name was missing."
        contains(ReceiptValidationError.BlankItemName) -> "One or more receipt items were missing names."
        contains(ReceiptValidationError.NonPositiveItemAmount) -> "One or more item amounts were invalid."
        contains(ReceiptValidationError.NonPositiveFeeAmount) -> "One or more fee amounts were invalid."
        contains(ReceiptValidationError.NegativeTotal) -> "Receipt total was invalid."
        contains(ReceiptValidationError.FutureDate) -> "Receipt date was in the future."
        contains(ReceiptValidationError.TotalMismatch) -> "Receipt total did not match its items and fees."
        else -> "We could not read enough receipt details."
    }

    private companion object {
        const val TAG = "ReceiptScan"
        const val DEFAULT_LOCALE_HINT = "en-US"
        const val DEFAULT_CURRENCY_HINT = "USD"
        const val PENDING_PAYER_ID = "pending-payer"
    }
}

sealed interface ReceiptScanParseResult {
    data object Saved : ReceiptScanParseResult

    data class Invalid(val message: String) : ReceiptScanParseResult
}
