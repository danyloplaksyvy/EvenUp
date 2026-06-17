package com.dps.evenup.feature.expenseflow.impl.receiptreview

import android.util.Log
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
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
import com.dps.evenup.domain.receipt.api.ValidateReceiptUseCase
import java.math.BigDecimal
import java.math.RoundingMode

class ReceiptReviewPresenter(
    private val draftRepository: ExpenseDraftRepository,
    private val validateReceipt: ValidateReceiptUseCase,
) {
    suspend fun load(): ReceiptReviewUiState {
        val draft = draftRepository.getDraft() ?: return ReceiptReviewUiState(
            isLoading = false,
            missingDraft = true,
            submitError = "No receipt draft was found.",
        )

        val receipt = draft.receipt
        receipt.logParseNotes()
        return ReceiptReviewUiState(
            isLoading = false,
            merchantName = receipt.merchantName,
            dateLabel = receipt.transactionDateLabel.orEmpty(),
            currencyCode = receipt.currencyCode.value,
            items = receipt.items.map { item ->
                ReceiptReviewItemUiState(
                    id = item.id.value,
                    name = item.name,
                    quantity = item.quantity.value.toString(),
                    amount = formatMoneyMinor(item.totalPrice),
                )
            },
            fees = receipt.fees.map { fee ->
                ReceiptReviewFeeUiState(
                    id = fee.id.value,
                    type = fee.type,
                    label = fee.label,
                    amount = formatMoneyMinor(fee.amount),
                )
            },
            subtotalAmount = receipt.subtotal?.let(::formatMoneyMinor),
            totalAmount = formatMoneyMinor(receipt.total),
            reviewWarningCount = receipt.parseMetadata.reviewWarnings.size,
            uncertainItemCount = receipt.items.count { item -> item.parseMetadata.needsReview },
        )
    }

    fun reduce(
        state: ReceiptReviewUiState,
        event: ReceiptReviewUiEvent,
    ): ReceiptReviewUiState {
        val clearedState = state.copy(fieldErrors = emptyMap(), submitError = null)
        return when (event) {
            is ReceiptReviewUiEvent.EditTargetSelected -> clearedState.copy(editTarget = event.target)
            ReceiptReviewUiEvent.EditDismissed -> clearedState.copy(editTarget = null)
            is ReceiptReviewUiEvent.MerchantNameChanged -> clearedState.copy(merchantName = event.value)
            is ReceiptReviewUiEvent.DateChanged -> clearedState.copy(dateLabel = event.value)
            is ReceiptReviewUiEvent.CurrencyChanged -> clearedState.copy(
                currencyCode = event.value.uppercase().filter { it in 'A'..'Z' }.take(3),
            )
            is ReceiptReviewUiEvent.ItemNameChanged -> clearedState.copy(
                items = state.items.map { item ->
                    if (item.id == event.itemId) item.copy(name = event.value) else item
                },
            )
            is ReceiptReviewUiEvent.ItemQuantityChanged -> clearedState.copy(
                items = state.items.map { item ->
                    if (item.id == event.itemId) item.copy(quantity = event.value.filter(Char::isDigit).take(3)) else item
                },
            )
            is ReceiptReviewUiEvent.ItemQuantityStepped -> clearedState.copy(
                items = state.items.map { item ->
                    if (item.id == event.itemId) {
                        val currentQuantity = item.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        item.copy(quantity = (currentQuantity + event.delta).coerceAtLeast(1).coerceAtMost(999).toString())
                    } else {
                        item
                    }
                },
            )
            is ReceiptReviewUiEvent.ItemAmountChanged -> clearedState.copy(
                items = state.items.map { item ->
                    if (item.id == event.itemId) item.copy(amount = event.value) else item
                },
            )
            ReceiptReviewUiEvent.AddItemClick -> {
                val itemId = nextItemId(state.items)
                clearedState.copy(
                    items = state.items + ReceiptReviewItemUiState(id = itemId),
                    editTarget = ReceiptReviewEditTarget.Item(itemId),
                )
            }
            is ReceiptReviewUiEvent.RemoveItemClick -> clearedState.copy(
                items = state.items.filterNot { item -> item.id == event.itemId }.ifEmpty {
                    listOf(ReceiptReviewItemUiState(id = nextItemId(state.items)))
                },
                editTarget = state.editTarget.takeUnless { target ->
                    target is ReceiptReviewEditTarget.Item && target.itemId == event.itemId
                },
            )
            is ReceiptReviewUiEvent.FeeLabelChanged -> clearedState.copy(
                fees = state.fees.map { fee ->
                    if (fee.id == event.feeId) fee.copy(label = event.value) else fee
                },
            )
            is ReceiptReviewUiEvent.FeeAmountChanged -> clearedState.copy(
                fees = state.fees.map { fee ->
                    if (fee.id == event.feeId) fee.copy(amount = event.value) else fee
                },
            )
            ReceiptReviewUiEvent.AddFeeClick -> {
                val feeId = nextFeeId(state.fees)
                clearedState.copy(
                    fees = state.fees + ReceiptReviewFeeUiState(id = feeId, label = "Adjustment"),
                    editTarget = ReceiptReviewEditTarget.Fee(feeId),
                )
            }
            is ReceiptReviewUiEvent.RemoveFeeClick -> clearedState.copy(
                fees = state.fees.filterNot { fee -> fee.id == event.feeId },
                editTarget = state.editTarget.takeUnless { target ->
                    target is ReceiptReviewEditTarget.Fee && target.feeId == event.feeId
                },
            )
            is ReceiptReviewUiEvent.SubtotalChanged -> clearedState.copy(subtotalAmount = event.value)
            is ReceiptReviewUiEvent.TotalChanged -> clearedState.copy(totalAmount = event.value)
            ReceiptReviewUiEvent.BackClick,
            ReceiptReviewUiEvent.ContinueClick,
            -> state
        }
    }

    suspend fun saveDraft(state: ReceiptReviewUiState): SaveReceiptReviewResult {
        val existingDraft = draftRepository.getDraft() ?: return SaveReceiptReviewResult.MissingDraft
        val receiptResult = state.toReceipt()
        val receipt = when (receiptResult) {
            is ReceiptReviewBuildResult.Invalid -> return SaveReceiptReviewResult.Invalid(receiptResult.fieldErrors)
            is ReceiptReviewBuildResult.Valid -> receiptResult.receipt
        }

        val validation = validateReceipt.validate(receipt)
        if (!validation.isValid) {
            return SaveReceiptReviewResult.Invalid(validation.errors.toFieldErrors())
        }

        draftRepository.saveDraft(existingDraft.copy(receipt = receipt))
        return SaveReceiptReviewResult.Saved
    }

    private fun ReceiptReviewUiState.toReceipt(): ReceiptReviewBuildResult {
        val errors = mutableMapOf<String, String>()
        val merchant = merchantName.trim()
        if (merchant.isBlank()) {
            errors["merchant"] = "Merchant is required."
        }

        val currency = try {
            CurrencyCode(currencyCode.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            errors["currency"] = "Use a 3-letter code."
            CurrencyCode("USD")
        }

        val receiptItems = items.mapNotNull { item ->
            val name = item.name.trim()
            val quantity = item.quantity.toIntOrNull()
            val amount = parseMoneyMinor(item.amount)

            if (name.isBlank()) errors["item_name_${item.id}"] = "Required."
            if (quantity == null || quantity <= 0) errors["item_quantity_${item.id}"] = "Use 1 or more."
            if (amount == null || amount.value <= 0) errors["item_amount_${item.id}"] = "Enter an amount."

            if (name.isBlank() || quantity == null || quantity <= 0 || amount == null || amount.value <= 0) {
                null
            } else {
                ReceiptItem(
                    id = ReceiptItemId(item.id),
                    name = name,
                    quantity = Quantity(quantity),
                    unitPrice = MoneyMinor(amount.value / quantity),
                    totalPrice = amount,
                )
            }
        }
        if (receiptItems.isEmpty()) {
            errors["items"] = "Add at least one valid item."
        }

        val receiptFees = fees.mapNotNull { fee ->
            val label = fee.label.trim()
            val amount = parseMoneyMinor(fee.amount)

            if (label.isBlank()) errors["fee_label_${fee.id}"] = "Required."
            if (amount == null || amount.value <= 0) errors["fee_amount_${fee.id}"] = "Enter an amount."

            if (label.isBlank() || amount == null || amount.value <= 0) {
                null
            } else {
                ReceiptFee(
                    id = FeeId(fee.id),
                    type = fee.type,
                    label = label,
                    amount = amount,
                )
            }
        }

        val total = parseMoneyMinor(totalAmount)
        if (total == null || total.value < 0) {
            errors["total"] = "Enter the receipt total."
        }
        val subtotal = receiptItems.takeIf { it.isNotEmpty() }
            ?.let { validItems -> MoneyMinor(validItems.sumOf { item -> item.totalPrice.value }) }

        if (errors.isNotEmpty()) {
            return ReceiptReviewBuildResult.Invalid(errors)
        }

        return ReceiptReviewBuildResult.Valid(
            Receipt(
                merchantName = merchant,
                currencyCode = currency,
                transactionDateLabel = dateLabel.trim().ifBlank { null },
                items = receiptItems,
                fees = receiptFees,
                total = requireNotNull(total),
                subtotal = subtotal,
            ),
        )
    }

    private fun Set<ReceiptValidationError>.toFieldErrors(): Map<String, String> = associate { error ->
        when (error) {
            ReceiptValidationError.BlankMerchantName -> "merchant" to "Merchant is required."
            ReceiptValidationError.NoItems -> "items" to "Add at least one item."
            ReceiptValidationError.BlankItemName -> "items" to "Each item needs a name."
            ReceiptValidationError.NonPositiveItemAmount -> "items" to "Each item needs a positive amount."
            ReceiptValidationError.NonPositiveFeeAmount -> "fees" to "Fees must be positive."
            ReceiptValidationError.TotalMismatch -> "total" to "Total must equal items plus fees."
            ReceiptValidationError.NegativeTotal -> "total" to "Total cannot be negative."
        }
    }

    private fun Receipt.logParseNotes() {
        parseMetadata.corrections.forEach { correction ->
            val itemLabel = correction.itemName?.takeIf { it.isNotBlank() } ?: "receipt item"
            Log.i(
                TAG,
                "Corrected $itemLabel from ${formatCurrency(formatMoneyMinor(correction.from), currencyCode.value)} " +
                    "to ${formatCurrency(formatMoneyMinor(correction.to), currencyCode.value)}. Reason: ${correction.reason}",
            )
        }
        parseMetadata.reviewWarnings.forEach { warning ->
            Log.w(TAG, warning)
        }
    }

    private fun parseMoneyMinor(value: String): MoneyMinor? {
        val normalized = value.trim().removePrefix("\$").replace(",", "")
        if (normalized.isBlank()) return null
        return try {
            val decimal = BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY)
            MoneyMinor(decimal.movePointRight(2).longValueExact())
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun formatMoneyMinor(value: MoneyMinor): String {
        return BigDecimal(value.value).movePointLeft(2).setScale(2).toPlainString()
    }

    private fun nextItemId(items: List<ReceiptReviewItemUiState>): String {
        val nextNumber = items.mapNotNull { item -> item.id.removePrefix("item-").toIntOrNull() }.maxOrNull() ?: 0
        return "item-${nextNumber + 1}"
    }

    private fun nextFeeId(fees: List<ReceiptReviewFeeUiState>): String {
        val nextNumber = fees.mapNotNull { fee -> fee.id.removePrefix("fee-").toIntOrNull() }.maxOrNull() ?: 0
        return "fee-${nextNumber + 1}"
    }

    private companion object {
        const val TAG = "ReceiptReview"
    }
}

sealed interface SaveReceiptReviewResult {
    data object Saved : SaveReceiptReviewResult

    data object MissingDraft : SaveReceiptReviewResult

    data class Invalid(val fieldErrors: Map<String, String>) : SaveReceiptReviewResult
}

private sealed interface ReceiptReviewBuildResult {
    data class Valid(val receipt: Receipt) : ReceiptReviewBuildResult

    data class Invalid(val fieldErrors: Map<String, String>) : ReceiptReviewBuildResult
}
