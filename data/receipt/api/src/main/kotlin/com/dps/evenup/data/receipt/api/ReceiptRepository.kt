package com.dps.evenup.data.receipt.api

import com.dps.evenup.domain.receipt.api.Receipt

interface ReceiptRepository {
    suspend fun parseReceiptImage(request: ReceiptImageParseRequest): Receipt
}

data class ReceiptImageParseRequest(
    val imageBase64: String,
    val mimeType: String,
    val localeHint: String? = null,
    val currencyHint: String? = null,
)

class ReceiptDataException(
    message: String,
    val reason: ReceiptDataFailureReason = ReceiptDataFailureReason.Unknown,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

enum class ReceiptDataFailureReason {
    Connection,
    Timeout,
    ParseRejected,
    Unknown,
}
