package com.dps.evenup.data.receipt.impl

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
        val requestBody = json.encodeToString(request.toDto())
        val response = workerApiClient.postJson("/v1/receipts/parse", requestBody)
        return when (response) {
            is WorkerApiResult.Failure -> throw ReceiptDataException(
                message = "Receipt parse request failed.",
                reason = response.error.toReceiptFailureReason(),
            )
            is WorkerApiResult.Success -> mapReceipt(response.response.body)
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

private fun ReceiptParseResponseDto.toDomain(): Receipt = Receipt(
    merchantName = merchantName.trim().ifEmpty { throw IllegalArgumentException("Merchant name is required.") },
    currencyCode = CurrencyCode(currency.uppercase()),
    items = items.mapIndexed { index, item -> item.toDomain(index) },
    fees = fees.mapIndexed { index, fee -> fee.toDomain(index) },
    total = MoneyMinor(totalMinor),
    transactionDateLabel = transactionDate,
)

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
    val subtotalMinor: Long,
    val totalMinor: Long,
    val confidence: Double,
)

@Serializable
private data class ReceiptItemDto(
    val name: String,
    val quantity: Double,
    val unitPriceMinor: Long,
    val totalPriceMinor: Long,
)

@Serializable
private data class ReceiptFeeDto(
    val type: String,
    val label: String,
    val amountMinor: Long,
)
