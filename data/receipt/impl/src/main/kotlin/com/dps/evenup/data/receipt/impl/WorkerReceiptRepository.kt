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
        val requestBodyBytes = requestBody.toByteArray(Charsets.UTF_8).size
        val response = postJsonWithRetry(
            requestId = requestId,
            requestBody = requestBody,
            requestBodyBytes = requestBodyBytes,
        )
        return when (response) {
            is WorkerApiResult.Failure -> {
                throw ReceiptDataException(
                    message = "Receipt parse request failed.",
                    reason = response.error.toReceiptFailureReason(json),
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
                    try {
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
                    } catch (error: ReceiptDataException) {
                        logTiming(
                            requestId = requestId,
                            stage = "response_mapping_failed",
                            durationMs = 0L,
                            metadata = error.safeMappingMetadata(),
                            warning = true,
                        )
                        throw error
                    }
                }
            }
        }
    }

    private suspend fun postJsonWithRetry(
        requestId: String,
        requestBody: String,
        requestBodyBytes: Int,
    ): WorkerApiResult {
        var attempt = 1
        while (true) {
            val response = timedStage(
                requestId = requestId,
                stage = "network_post",
                metadata = listOf(
                    "attempt=$attempt",
                    "requestBodyBytes=$requestBodyBytes",
                ),
            ) {
                workerApiClient.postJson(
                    path = "/v1/receipts/parse",
                    body = requestBody,
                    headers = mapOf(REQUEST_ID_HEADER to requestId),
                )
            }

            when (response) {
                is WorkerApiResult.Success -> {
                    logTiming(
                        requestId = requestId,
                        stage = "network_post_result",
                        durationMs = 0L,
                        metadata = listOf(
                            "attempt=$attempt",
                            "status=${response.response.statusCode}",
                            "responseBodyBytes=${response.response.body.toByteArray(Charsets.UTF_8).size}",
                        ),
                    )
                    return response
                }
                is WorkerApiResult.Failure -> {
                    val retryable = response.error.isRetryableReceiptParseFailure()
                    logTiming(
                        requestId = requestId,
                        stage = "network_post_result",
                        durationMs = 0L,
                        metadata = response.error.safeMetadata() + listOf(
                            "attempt=$attempt",
                            "retryable=$retryable",
                        ),
                        warning = true,
                    )
                    if (!retryable || attempt >= MAX_PARSE_ATTEMPTS) {
                        return response
                    }
                    logTiming(
                        requestId = requestId,
                        stage = "network_post_retry",
                        durationMs = 0L,
                        metadata = response.error.safeMetadata() + listOf("nextAttempt=${attempt + 1}"),
                        warning = true,
                    )
                    attempt += 1
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
        const val MAX_PARSE_ATTEMPTS = 2
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

private fun WorkerNetworkError.toReceiptFailureReason(json: Json): ReceiptDataFailureReason = when (this) {
    WorkerNetworkError.ConnectionFailed,
    WorkerNetworkError.InvalidBaseUrl,
    WorkerNetworkError.InvalidPath,
    -> ReceiptDataFailureReason.Connection
    WorkerNetworkError.Timeout -> ReceiptDataFailureReason.Timeout
    is WorkerNetworkError.HttpFailure -> body.toReceiptFailureReason(json) ?: ReceiptDataFailureReason.ParseRejected
    WorkerNetworkError.Unknown -> ReceiptDataFailureReason.Unknown
}

private fun String.toReceiptFailureReason(json: Json): ReceiptDataFailureReason? {
    val code = runCatching {
        json.decodeFromString<ReceiptErrorResponseDto>(this).error?.code
    }.getOrNull()

    return when (code) {
        "RECEIPT_IMAGE_UNSUPPORTED" -> ReceiptDataFailureReason.UnsupportedImage
        "RECEIPT_IMAGE_TOO_LARGE" -> ReceiptDataFailureReason.ImageTooLarge
        "RECEIPT_PARSE_FAILED" -> ReceiptDataFailureReason.ParseRejected
        else -> null
    }
}

private fun WorkerNetworkError.isRetryableReceiptParseFailure(): Boolean = when (this) {
    WorkerNetworkError.ConnectionFailed,
    WorkerNetworkError.Timeout,
    WorkerNetworkError.Unknown,
    -> true
    is WorkerNetworkError.HttpFailure -> statusCode == 429 || statusCode in 500..599
    WorkerNetworkError.InvalidBaseUrl,
    WorkerNetworkError.InvalidPath,
    -> false
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
    requireReceiptMapping(
        condition = merchantName.isNotBlank(),
        reason = "blank_merchant_name",
        fieldPath = "merchantName",
    )
    val normalizedCurrency = currency.trim().uppercase()
    requireReceiptMapping(
        condition = CURRENCY_CODE_PATTERN.matches(normalizedCurrency),
        reason = "invalid_currency",
        fieldPath = "currency",
    )
    requireReceiptMapping(
        condition = items.isNotEmpty(),
        reason = "no_items",
        fieldPath = "items",
    )
    val receipt = Receipt(
        merchantName = merchantName.trim(),
        currencyCode = CurrencyCode(normalizedCurrency),
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
    return receipt.sanitizeIncludedTaxFees().reconcileSubtotalIfNeeded()
}

private fun ReceiptItemDto.toDomain(index: Int): ReceiptItem {
    val fieldPrefix = "items[$index]"
    val quantityInt = quantity.toInt()
    requireReceiptMapping(
        condition = quantity > 0.0 && quantity == quantityInt.toDouble(),
        reason = "invalid_item_quantity",
        fieldPath = "$fieldPrefix.quantity",
    )
    requireReceiptMapping(
        condition = name.isNotBlank(),
        reason = "blank_item_name",
        fieldPath = "$fieldPrefix.name",
    )
    requireReceiptMapping(
        condition = unitPriceMinor > 0,
        reason = "non_positive_unit_price",
        fieldPath = "$fieldPrefix.unitPriceMinor",
    )
    requireReceiptMapping(
        condition = totalPriceMinor > 0,
        reason = "non_positive_total_price",
        fieldPath = "$fieldPrefix.totalPriceMinor",
    )

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
    val fieldPrefix = "fees[$index]"
    val feeType = type.toFeeType()
    requireReceiptMapping(
        condition = label.isNotBlank(),
        reason = "blank_fee_label",
        fieldPath = "$fieldPrefix.label",
    )
    requireReceiptMapping(
        condition = if (feeType == FeeType.Discount) amountMinor < 0 else amountMinor > 0,
        reason = if (feeType == FeeType.Discount) "invalid_discount_amount" else "non_positive_fee_amount",
        fieldPath = "$fieldPrefix.amountMinor",
    )
    return ReceiptFee(
        id = FeeId("fee_${index + 1}"),
        type = feeType,
        label = label.trim(),
        amount = MoneyMinor(amountMinor),
    )
}

private fun String.toFeeType(): FeeType = when (uppercase()) {
    "TAX" -> FeeType.Tax
    "TIP" -> FeeType.Tip
    "SERVICE_FEE" -> FeeType.ServiceFee
    "DISCOUNT" -> FeeType.Discount
    else -> FeeType.Other
}

private class ReceiptMappingException(
    val reason: String,
    val fieldPath: String,
) : IllegalArgumentException("$reason fieldPath=$fieldPath")

private fun requireReceiptMapping(
    condition: Boolean,
    reason: String,
    fieldPath: String,
) {
    if (!condition) {
        throw ReceiptMappingException(reason = reason, fieldPath = fieldPath)
    }
}

private fun ReceiptDataException.safeMappingMetadata(): List<String> {
    val mappingCause = cause as? ReceiptMappingException
    return if (mappingCause != null) {
        listOf(
            "errorType=${this::class.java.simpleName}",
            "reason=${mappingCause.reason}",
            "fieldPath=${mappingCause.fieldPath}",
        )
    } else {
        listOf(
            "errorType=${this::class.java.simpleName}",
            "causeType=${cause?.javaClass?.simpleName ?: "None"}",
            "causeMessage=${cause?.message?.sanitizeLogValue() ?: "None"}",
        )
    }
}

private fun String.sanitizeLogValue(): String = replace(Regex("\\s+"), "_")
    .filter { character -> character.isLetterOrDigit() || character in "._:-=[]" }
    .take(MAX_SAFE_LOG_VALUE_LENGTH)

private val CURRENCY_CODE_PATTERN = Regex("^[A-Z]{3}$")
private const val MAX_SAFE_LOG_VALUE_LENGTH = 80

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

@Serializable
private data class ReceiptErrorResponseDto(
    val error: ReceiptErrorDto? = null,
)

@Serializable
private data class ReceiptErrorDto(
    val code: String? = null,
)

private fun ReceiptParseCorrectionDto.toDomain(): ReceiptParseCorrection = ReceiptParseCorrection(
    field = field,
    itemName = itemName,
    from = MoneyMinor(fromMinor),
    to = MoneyMinor(toMinor),
    reason = reason,
)

private fun Receipt.reconcileSubtotalIfNeeded(): Receipt {
    val itemSum = items.sumOf { item -> item.totalPrice.value }
    val feeSum = fees.sumOf { fee -> fee.amount.value }
    val subtotalValue = subtotal?.value
    if (subtotalValue != null && itemSum == subtotalValue && subtotalValue + feeSum == total.value) return this

    val candidateCorrection = candidateCorrections(itemSubtotalTargets()).singleOrNull() ?: return this
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
        subtotal = MoneyMinor(candidateCorrection.targetSubtotal),
        parseMetadata = parseMetadata.copy(
            corrections = parseMetadata.corrections + ReceiptParseCorrection(
                field = "items[${candidateCorrection.index}].totalPriceMinor",
                itemName = candidateCorrection.itemName,
                from = MoneyMinor(candidateCorrection.fromMinor),
                to = MoneyMinor(candidateCorrection.toMinor),
                reason = candidateCorrection.reason,
            ),
        ),
    )
}

private fun Receipt.sanitizeIncludedTaxFees(): Receipt {
    val corrections = mutableListOf<ReceiptParseCorrection>()
    val keptFees = fees.filterIndexed { index, fee ->
        val shouldRemove = shouldRemoveIncludedTaxFee(fee = fee, feeIndex = index)
        if (shouldRemove) {
            corrections += ReceiptParseCorrection(
                field = "fees[$index].amountMinor",
                itemName = null,
                from = fee.amount,
                to = MoneyMinor(0),
                reason = "Removed included VAT/tax duplicated from receipt total.",
            )
        }
        !shouldRemove
    }
    if (corrections.isEmpty()) return this

    return copy(
        fees = keptFees,
        parseMetadata = parseMetadata.copy(
            corrections = parseMetadata.corrections + corrections,
        ),
    )
}

private fun Receipt.shouldRemoveIncludedTaxFee(
    fee: ReceiptFee,
    feeIndex: Int,
): Boolean {
    if (!fee.isTaxLike() || fee.amount.value <= 0L) return false
    if (fee.label.isIncludedTaxLabel()) return true
    if (fee.amount.value == total.value || fee.amount.value == subtotal?.value) return true

    val feesWithoutCurrent = fees.filterIndexed { index, _ -> index != feeIndex }
    return reconcilesWithFees(feesWithoutCurrent)
}

private fun Receipt.reconcilesWithFees(fees: List<ReceiptFee>): Boolean {
    val itemSum = items.sumOf { item -> item.totalPrice.value }
    val feeSum = fees.sumOf { fee -> fee.amount.value }
    val subtotalValue = subtotal?.value
    return if (subtotalValue != null) {
        itemSum == subtotalValue && subtotalValue + feeSum == total.value
    } else {
        itemSum + feeSum == total.value
    }
}

private fun ReceiptFee.isTaxLike(): Boolean {
    val normalized = label.normalizedForTaxMatching()
    return type == FeeType.Tax || normalized.split(" ").any { word -> word in taxLabelWords }
}

private fun String.isIncludedTaxLabel(): Boolean {
    val normalized = normalizedForTaxMatching()
    return includedTaxPhrases.any { phrase -> normalized.contains(phrase) }
}

private fun String.normalizedForTaxMatching(): String {
    return lowercase()
        .replace(".", " ")
        .replace("-", " ")
        .replace("_", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun Receipt.itemSubtotalTargets(): List<Long> {
    return listOfNotNull(
        subtotal?.value,
        total.value - fees.sumOf { fee -> fee.amount.value },
    ).filter { target -> target > 0L }.distinct()
}

private fun Receipt.candidateCorrections(subtotalTargets: List<Long>): List<SubtotalCorrectionCandidate> {
    val itemSum = items.sumOf { item -> item.totalPrice.value }
    return items.flatMapIndexed { index, item ->
        val candidates = (item.parseMetadata.candidates.map { candidate -> candidate.value } +
            item.quantityLineTotalCandidates() +
            visuallySimilarSingleDigitTotals(item.totalPrice.value))
            .distinct()
            .filter { candidate -> candidate > 0 && candidate != item.totalPrice.value }

        candidates.flatMap { candidate ->
            subtotalTargets.mapNotNull { subtotalValue ->
                if (itemSum - item.totalPrice.value + candidate != subtotalValue) return@mapNotNull null
                SubtotalCorrectionCandidate(
                    index = index,
                    itemName = item.name,
                    fromMinor = item.totalPrice.value,
                    toMinor = candidate,
                    targetSubtotal = subtotalValue,
                    reason = correctionReason(item, candidate, subtotalValue),
                )
            }
        }
    }.distinctBy { candidate -> candidate.index to candidate.toMinor }
}

private fun ReceiptItem.quantityLineTotalCandidates(): List<Long> {
    val quantity = quantity.value
    if (quantity <= 1) return emptyList()
    return listOf(totalPrice.value * quantity, unitPrice.value * quantity)
        .filter { candidate -> candidate > 0L }
        .distinct()
}

private fun correctionReason(
    item: ReceiptItem,
    candidate: Long,
    subtotalValue: Long,
): String {
    return if (candidate in item.quantityLineTotalCandidates()) {
        "Corrected quantity line total to match expected item subtotal $subtotalValue; unit price was likely parsed as the line total."
    } else {
        "Corrected locally to match expected item subtotal $subtotalValue; digit likely misread."
    }
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
    val targetSubtotal: Long,
    val reason: String,
)

private val visuallySimilarDigits = mapOf(
    '0' to listOf('6', '8'),
    '3' to listOf('8'),
    '5' to listOf('6'),
    '6' to listOf('0', '5', '8'),
    '8' to listOf('0', '3', '6', '9'),
    '9' to listOf('8'),
)

private val includedTaxPhrases = listOf(
    "di cui iva",
    "iva inclusa",
    "vat included",
    "incl vat",
    "includes tax",
    "tax included",
    "mwst enthalten",
    "tva incluse",
    "iva incluido",
)

private val taxLabelWords = listOf(
    "tax",
    "vat",
    "iva",
    "gst",
    "mwst",
    "tva",
)
