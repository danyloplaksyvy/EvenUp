package com.dps.evenup.feature.expenseflow.impl.receiptscan

import android.net.Uri
import android.util.Base64
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
        val image = imageReader.readImage(uri = uri, source = source)
        val receipt = receiptRepository.parseReceiptImage(
            ReceiptImageParseRequest(
                imageBase64 = Base64.encodeToString(image.bytes, Base64.NO_WRAP),
                mimeType = image.mimeType,
                localeHint = DEFAULT_LOCALE_HINT,
                currencyHint = DEFAULT_CURRENCY_HINT,
            ),
        )
        val validation = validateReceipt.validate(receipt)
        if (validation.errors.hasFatalParseError()) {
            return ReceiptScanParseResult.Invalid(validation.errors.toErrorMessage())
        }

        draftRepository.saveDraft(receipt.toDraft())
        return ReceiptScanParseResult.Saved
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
        contains(ReceiptValidationError.TotalMismatch) -> "Receipt total did not match its items and fees."
        else -> "We could not read enough receipt details."
    }

    private companion object {
        const val DEFAULT_LOCALE_HINT = "en-US"
        const val DEFAULT_CURRENCY_HINT = "USD"
        const val PENDING_PAYER_ID = "pending-payer"
    }
}

sealed interface ReceiptScanParseResult {
    data object Saved : ReceiptScanParseResult

    data class Invalid(val message: String) : ReceiptScanParseResult
}
