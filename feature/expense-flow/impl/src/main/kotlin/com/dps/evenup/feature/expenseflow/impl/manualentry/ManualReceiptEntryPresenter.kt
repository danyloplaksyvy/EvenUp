package com.dps.evenup.feature.expenseflow.impl.manualentry

import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.ExpenseDraftId
import com.dps.evenup.domain.participant.api.ParticipantId
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
import java.util.UUID

class ManualReceiptEntryPresenter(
    private val draftRepository: ExpenseDraftRepository,
    private val validateReceipt: ValidateReceiptUseCase,
) {
    fun reduce(
        state: ManualReceiptEntryUiState,
        event: ManualReceiptEntryUiEvent,
    ): ManualReceiptEntryUiState {
        val clearedState = state.copy(fieldErrors = emptyMap(), submitError = null)
        return when (event) {
            is ManualReceiptEntryUiEvent.MerchantNameChanged -> clearedState.copy(merchantName = event.value)
            is ManualReceiptEntryUiEvent.DateChanged -> clearedState.copy(dateLabel = event.value)
            is ManualReceiptEntryUiEvent.CurrencyChanged -> clearedState.copy(
                currencyCode = event.value.uppercase().filter { it in 'A'..'Z' }.take(3),
            )
            is ManualReceiptEntryUiEvent.ItemNameChanged -> clearedState.copy(
                items = state.items.map { item ->
                    if (item.id == event.itemId) item.copy(name = event.value) else item
                },
            )
            is ManualReceiptEntryUiEvent.ItemQuantityChanged -> clearedState.copy(
                items = state.items.map { item ->
                    if (item.id == event.itemId) item.copy(quantity = event.value.filter(Char::isDigit).take(3)) else item
                },
            )
            is ManualReceiptEntryUiEvent.ItemAmountChanged -> clearedState.copy(
                items = state.items.map { item ->
                    if (item.id == event.itemId) item.copy(amount = event.value) else item
                },
            )
            ManualReceiptEntryUiEvent.AddItemClick -> clearedState.copy(
                items = state.items + ManualReceiptItemUiState(id = nextItemId(state.items)),
            )
            is ManualReceiptEntryUiEvent.RemoveItemClick -> clearedState.copy(
                items = state.items.filterNot { item -> item.id == event.itemId }.ifEmpty {
                    listOf(ManualReceiptItemUiState(id = nextItemId(state.items)))
                },
            )
            is ManualReceiptEntryUiEvent.TaxChanged -> clearedState.copy(taxAmount = event.value)
            is ManualReceiptEntryUiEvent.TipChanged -> clearedState.copy(tipAmount = event.value)
            is ManualReceiptEntryUiEvent.TotalChanged -> clearedState.copy(totalAmount = event.value)
            ManualReceiptEntryUiEvent.BackClick,
            ManualReceiptEntryUiEvent.ContinueClick,
            -> state
        }
    }

    suspend fun saveDraft(state: ManualReceiptEntryUiState): SaveManualReceiptDraftResult {
        val receiptResult = state.toReceipt()
        val receipt = when (receiptResult) {
            is ReceiptBuildResult.Invalid -> return SaveManualReceiptDraftResult.Invalid(receiptResult.fieldErrors)
            is ReceiptBuildResult.Valid -> receiptResult.receipt
        }

        val validation = validateReceipt.validate(receipt)
        if (!validation.isValid) {
            return SaveManualReceiptDraftResult.Invalid(validation.errors.toFieldErrors())
        }

        val draft = ExpenseDraft(
            id = ExpenseDraftId("draft-${UUID.randomUUID()}"),
            receipt = receipt,
            participants = emptyList(),
            payerId = ParticipantId(PENDING_PAYER_ID),
            itemAssignments = emptyList(),
            feeAllocations = emptyList(),
        )
        draftRepository.saveDraft(draft)
        return SaveManualReceiptDraftResult.Saved
    }

    private fun ManualReceiptEntryUiState.toReceipt(): ReceiptBuildResult {
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

        val receiptItems = items.mapIndexedNotNull { index, item ->
            val name = item.name.trim()
            val quantity = item.quantity.toIntOrNull()
            val amount = parseMoneyMinor(item.amount)
            if (name.isBlank()) {
                errors["item_name_${item.id}"] = "Required."
            }
            if (quantity == null || quantity <= 0) {
                errors["item_quantity_${item.id}"] = "Use 1 or more."
            }
            if (amount == null || amount.value <= 0) {
                errors["item_amount_${item.id}"] = "Enter an amount."
            }
            if (name.isBlank() || quantity == null || quantity <= 0 || amount == null || amount.value <= 0) {
                null
            } else {
                ReceiptItem(
                    id = ReceiptItemId("item-${index + 1}"),
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

        val fees = buildList {
            parseOptionalFee("tax", taxAmount, FeeType.Tax, "Tax", errors)?.let(::add)
            parseOptionalFee("tip", tipAmount, FeeType.Tip, "Tip", errors)?.let(::add)
        }
        val total = parseMoneyMinor(totalAmount)
        if (total == null || total.value < 0) {
            errors["total"] = "Enter the receipt total."
        }

        if (errors.isNotEmpty()) {
            return ReceiptBuildResult.Invalid(errors)
        }

        return ReceiptBuildResult.Valid(
            Receipt(
                merchantName = merchant,
                currencyCode = currency,
                transactionDateLabel = dateLabel.trim().ifBlank { null },
                items = receiptItems,
                fees = fees,
                total = requireNotNull(total),
            ),
        )
    }

    private fun parseOptionalFee(
        key: String,
        amountText: String,
        type: FeeType,
        label: String,
        errors: MutableMap<String, String>,
    ): ReceiptFee? {
        if (amountText.isBlank()) return null
        val amount = parseMoneyMinor(amountText)
        if (amount == null || amount.value < 0) {
            errors[key] = "Enter a valid amount."
            return null
        }
        if (amount.value == 0L) return null
        return ReceiptFee(
            id = FeeId(key),
            type = type,
            label = label,
            amount = amount,
        )
    }

    private fun Set<ReceiptValidationError>.toFieldErrors(): Map<String, String> = associate { error ->
        when (error) {
            ReceiptValidationError.BlankMerchantName -> "merchant" to "Merchant is required."
            ReceiptValidationError.NoItems -> "items" to "Add at least one item."
            ReceiptValidationError.BlankItemName -> "items" to "Each item needs a name."
            ReceiptValidationError.NonPositiveItemAmount -> "items" to "Each item needs a positive amount."
            ReceiptValidationError.NonPositiveFeeAmount -> "fees" to "Fees must be positive."
            ReceiptValidationError.TotalMismatch -> "total" to "Total must equal items plus tax and tip."
            ReceiptValidationError.NegativeTotal -> "total" to "Total cannot be negative."
        }
    }

    private fun parseMoneyMinor(value: String): MoneyMinor? {
        val normalized = value.trim().removePrefix("$").replace(",", "")
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

    private fun nextItemId(items: List<ManualReceiptItemUiState>): String {
        return ((items.maxOfOrNull { item -> item.id.toIntOrNull() ?: 0 } ?: 0) + 1).toString()
    }

    private companion object {
        const val PENDING_PAYER_ID = "pending-payer"
    }
}

sealed interface SaveManualReceiptDraftResult {
    data object Saved : SaveManualReceiptDraftResult

    data class Invalid(val fieldErrors: Map<String, String>) : SaveManualReceiptDraftResult
}

private sealed interface ReceiptBuildResult {
    data class Valid(val receipt: Receipt) : ReceiptBuildResult

    data class Invalid(val fieldErrors: Map<String, String>) : ReceiptBuildResult
}
