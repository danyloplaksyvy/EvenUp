package com.dps.evenup.data.receipt.impl

import android.util.Log
import com.dps.evenup.core.network.api.WorkerApiClient
import com.dps.evenup.core.network.api.WorkerApiResult
import com.dps.evenup.core.network.api.WorkerNetworkError
import com.dps.evenup.data.receipt.api.ReceiptDataException
import com.dps.evenup.data.receipt.api.ReceiptDataFailureReason
import com.dps.evenup.data.receipt.api.ReceiptImageParseRequest
import com.dps.evenup.data.receipt.api.ReceiptRepository
import com.dps.evenup.domain.receipt.api.CurrencyCode
import com.dps.evenup.domain.receipt.api.FeeId
import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Quantity
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptFee
import com.dps.evenup.domain.receipt.api.ReceiptItem
import com.dps.evenup.domain.receipt.api.ReceiptItemId
import com.dps.evenup.domain.receipt.api.ReceiptItemParseMetadata
import com.dps.evenup.domain.receipt.api.ReceiptParseCorrection
import com.dps.evenup.domain.receipt.api.ReceiptParseMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class WorkerReceiptRepository(
    private val workerApiClient: WorkerApiClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) : ReceiptRepository {
    override suspend fun parseReceiptImage(request: ReceiptImageParseRequest): Receipt {
        val requestId = request.requestId ?: UNKNOWN_REQUEST_ID
        val requestBody = timedStage(
            requestId = requestId,
            stage = "request_json",
            metadata = listOf(
                "imageBytes=${estimateBase64ByteLength(request.imageBase64)}",
                "base64Length=${request.imageBase64.length}",
                "mimeType=${request.mimeType}",
            ),
        ) {
            json.encodeToString(request.toDto())
        }
        val response = timedStage(
            requestId = requestId,
            stage = "network_post",
            metadata = listOf("requestBodyBytes=${requestBody.toByteArray(Charsets.UTF_8).size}"),
        ) {
            workerApiClient.postJson(
                path = "/v1/receipts/parse",
                body = requestBody,
                headers = mapOf(REQUEST_ID_HEADER to requestId),
            )
        }
        return when (response) {
            is WorkerApiResult.Failure -> {
                logTiming(
                    requestId = requestId,
                    stage = "network_post_result",
                    durationMs = 0L,
                    metadata = response.error.safeMetadata(),
                    warning = true,
                )
                throw ReceiptDataException(
                    message = "Receipt parse request failed.",
                    reason = response.error.toReceiptFailureReason(),
                )
            }
            is WorkerApiResult.Success -> {
                logTiming(
                    requestId = requestId,
                    stage = "network_post_result",
                    durationMs = 0L,
                    metadata = listOf(
                        "status=${response.response.statusCode}",
                        "responseBodyBytes=${response.response.body.toByteArray(Charsets.UTF_8).size}",
                    ),
                )
                timedStage(
                    requestId = requestId,
                    stage = "response_mapping",
                    metadata = listOf("status=${response.response.statusCode}"),
                ) {
                    mapReceipt(response.response.body).also { receipt ->
                        logTiming(
                            requestId = requestId,
                            stage = "response_mapping_result",
                            durationMs = 0L,
                            metadata = listOf(
                                "itemCount=${receipt.items.size}",
                                "feeCount=${receipt.fees.size}",
                                "warningCount=${receipt.parseMetadata.reviewWarnings.size}",
                            ),
                        )
                    }
                }
            }
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

    private fun mapReceipt(body: String): Receipt {
        val dto = try {
            json.decodeFromString<ReceiptParseResponseDto>(body)
        } catch (error: SerializationException) {
            throw ReceiptDataException("Receipt parse response was invalid.", cause = error)
        }

        return try {
            dto.toDomain()
        } catch (error: IllegalArgumentException) {
            throw ReceiptDataException("Receipt parse response contained invalid values.", cause = error)
        }
    }

    private fun ReceiptImageParseRequest.toDto(): ReceiptParseRequestDto = ReceiptParseRequestDto(
        imageBase64 = imageBase64,
        mimeType = mimeType,
        localeHint = localeHint,
        currencyHint = currencyHint,
    )

    private companion object {
        const val TAG = "WorkerReceiptRepository"
        const val UNKNOWN_REQUEST_ID = "unknown"
        const val REQUEST_ID_HEADER = "X-EvenUp-Request-Id"
    }
}

private fun estimateBase64ByteLength(value: String): Int {
    val normalized = value.filterNot { character -> character.isWhitespace() }
    val padding = when {
        normalized.endsWith("==") -> 2
        normalized.endsWith("=") -> 1
        else -> 0
    }
    return ((normalized.length * 3) / 4 - padding).coerceAtLeast(0)
}

private fun WorkerNetworkError.toReceiptFailureReason(): ReceiptDataFailureReason = when (this) {
    WorkerNetworkError.ConnectionFailed,
    WorkerNetworkError.InvalidBaseUrl,
    WorkerNetworkError.InvalidPath,
    -> ReceiptDataFailureReason.Connection
    WorkerNetworkError.Timeout -> ReceiptDataFailureReason.Timeout
    is WorkerNetworkError.HttpFailure -> ReceiptDataFailureReason.ParseRejected
    WorkerNetworkError.Unknown -> ReceiptDataFailureReason.Unknown
}

private fun WorkerNetworkError.safeMetadata(): List<String> = when (this) {
    WorkerNetworkError.ConnectionFailed -> listOf("error=ConnectionFailed")
    WorkerNetworkError.InvalidBaseUrl -> listOf("error=InvalidBaseUrl")
    WorkerNetworkError.InvalidPath -> listOf("error=InvalidPath")
    WorkerNetworkError.Timeout -> listOf("error=Timeout")
    is WorkerNetworkError.HttpFailure -> listOf(
        "error=HttpFailure",
        "status=$statusCode",
        "responseBodyBytes=${body.toByteArray(Charsets.UTF_8).size}",
    )
    WorkerNetworkError.Unknown -> listOf("error=Unknown")
}

private fun ReceiptParseResponseDto.toDomain(): Receipt {
    val receipt = Receipt(
        merchantName = merchantName.trim().ifEmpty { throw IllegalArgumentException("Merchant name is required.") },
        currencyCode = CurrencyCode(currency.uppercase()),
        items = items.mapIndexed { index, item -> item.toDomain(index) },
        fees = fees.mapIndexed { index, fee -> fee.toDomain(index) },
        total = MoneyMinor(totalMinor),
        transactionDateLabel = transactionDate,
        subtotal = subtotalMinor?.let(::MoneyMinor),
        parseMetadata = ReceiptParseMetadata(
            corrections = corrections.map { correction -> correction.toDomain() },
            reviewWarnings = reviewWarnings.map { warning -> warning.trim() }.filter { warning -> warning.isNotEmpty() },
        ),
    )
    return receipt.reconcileSubtotalIfNeeded()
}

private fun ReceiptItemDto.toDomain(index: Int): ReceiptItem {
    val quantityInt = quantity.toInt()
    require(quantity > 0.0 && quantity == quantityInt.toDouble()) { "Quantity must be a positive whole number." }
    require(name.isNotBlank()) { "Item name is required." }
    require(unitPriceMinor >= 0) { "Unit price must not be negative." }
    require(totalPriceMinor > 0) { "Item total must be positive." }

    return ReceiptItem(
        id = ReceiptItemId("item_${index + 1}"),
        name = name.trim(),
        quantity = Quantity(quantityInt),
        unitPrice = MoneyMinor(unitPriceMinor),
        totalPrice = MoneyMinor(totalPriceMinor),
        parseMetadata = ReceiptItemParseMetadata(
            confidence = confidence?.coerceIn(0.0, 1.0),
            candidates = candidatesMinor.distinct().map(::MoneyMinor),
            needsReview = needsReview,
        ),
    )
}

private fun ReceiptFeeDto.toDomain(index: Int): ReceiptFee {
    require(label.isNotBlank()) { "Fee label is required." }
    return ReceiptFee(
        id = FeeId("fee_${index + 1}"),
        type = type.toFeeType(),
        label = label.trim(),
        amount = MoneyMinor(amountMinor),
    )
}

private fun String.toFeeType(): FeeType = when (uppercase()) {
    "TAX" -> FeeType.Tax
    "TIP" -> FeeType.Tip
    "SERVICE_FEE" -> FeeType.ServiceFee
    else -> FeeType.Other
}

@Serializable
private data class ReceiptParseRequestDto(
    val imageBase64: String,
    val mimeType: String,
    val localeHint: String? = null,
    val currencyHint: String? = null,
)

@Serializable
private data class ReceiptParseResponseDto(
    val merchantName: String,
    val transactionDate: String? = null,
    val currency: String,
    val items: List<ReceiptItemDto>,
    val fees: List<ReceiptFeeDto> = emptyList(),
    val subtotalMinor: Long? = null,
    val totalMinor: Long,
    val confidence: Double,
    val corrections: List<ReceiptParseCorrectionDto> = emptyList(),
    val reviewWarnings: List<String> = emptyList(),
)

@Serializable
private data class ReceiptItemDto(
    val name: String,
    val quantity: Double,
    val unitPriceMinor: Long,
    val totalPriceMinor: Long,
    val confidence: Double? = null,
    val candidatesMinor: List<Long> = emptyList(),
    val needsReview: Boolean = false,
)

@Serializable
private data class ReceiptFeeDto(
    val type: String,
    val label: String,
    val amountMinor: Long,
)

@Serializable
private data class ReceiptParseCorrectionDto(
    val field: String,
    val itemName: String? = null,
    val fromMinor: Long,
    val toMinor: Long,
    val reason: String,
)

private fun ReceiptParseCorrectionDto.toDomain(): ReceiptParseCorrection = ReceiptParseCorrection(
    field = field,
    itemName = itemName,
    from = MoneyMinor(fromMinor),
    to = MoneyMinor(toMinor),
    reason = reason,
)

private fun Receipt.reconcileSubtotalIfNeeded(): Receipt {
    val subtotalValue = subtotal?.value ?: return this
    val itemSum = items.sumOf { item -> item.totalPrice.value }
    if (itemSum == subtotalValue) return this

    val candidateCorrection = candidateCorrections(subtotalValue).singleOrNull() ?: return this
    val correctedItems = items.mapIndexed { index, item ->
        if (index == candidateCorrection.index) {
            val correctedTotal = MoneyMinor(candidateCorrection.toMinor)
            item.copy(
                totalPrice = correctedTotal,
                unitPrice = correctedUnitPrice(item, correctedTotal),
                parseMetadata = item.parseMetadata.copy(
                    candidates = (listOf(correctedTotal, item.totalPrice) + item.parseMetadata.candidates).distinct(),
                    needsReview = true,
                ),
            )
        } else {
            item
        }
    }

    return copy(
        items = correctedItems,
        parseMetadata = parseMetadata.copy(
            corrections = parseMetadata.corrections + ReceiptParseCorrection(
                field = "items[${candidateCorrection.index}].totalPriceMinor",
                itemName = candidateCorrection.itemName,
                from = MoneyMinor(candidateCorrection.fromMinor),
                to = MoneyMinor(candidateCorrection.toMinor),
                reason = "Corrected locally to match printed subtotal; digit likely misread.",
            ),
        ),
    )
}

private fun Receipt.candidateCorrections(subtotalValue: Long): List<SubtotalCorrectionCandidate> {
    val itemSum = items.sumOf { item -> item.totalPrice.value }
    return items.flatMapIndexed { index, item ->
        val candidates = (item.parseMetadata.candidates.map { candidate -> candidate.value } +
            visuallySimilarSingleDigitTotals(item.totalPrice.value))
            .distinct()
            .filter { candidate -> candidate > 0 && candidate != item.totalPrice.value }

        candidates.mapNotNull { candidate ->
            if (itemSum - item.totalPrice.value + candidate == subtotalValue) {
                SubtotalCorrectionCandidate(
                    index = index,
                    itemName = item.name,
                    fromMinor = item.totalPrice.value,
                    toMinor = candidate,
                )
            } else {
                null
            }
        }
    }.distinct()
}

private fun visuallySimilarSingleDigitTotals(value: Long): List<Long> {
    val digits = value.toString()
    return digits.flatMapIndexed { index, digit ->
        visuallySimilarDigits[digit].orEmpty().mapNotNull { replacement ->
            digits.replaceRange(index, index + 1, replacement.toString()).toLongOrNull()
        }
    }
}

private fun correctedUnitPrice(
    item: ReceiptItem,
    correctedTotal: MoneyMinor,
): MoneyMinor {
    val quantity = item.quantity.value
    return if (correctedTotal.value % quantity == 0L) {
        MoneyMinor(correctedTotal.value / quantity)
    } else {
        item.unitPrice
    }
}

private data class SubtotalCorrectionCandidate(
    val index: Int,
    val itemName: String,
    val fromMinor: Long,
    val toMinor: Long,
)

private val visuallySimilarDigits = mapOf(
    '0' to listOf('6', '8'),
    '3' to listOf('8'),
    '5' to listOf('6'),
    '6' to listOf('0', '5', '8'),
    '8' to listOf('0', '3', '6', '9'),
    '9' to listOf('8'),
)
