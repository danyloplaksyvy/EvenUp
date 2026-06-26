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
import java.time.LocalDate
import java.util.UUID

class ManualReceiptEntryPresenter(
    private val draftRepository: ExpenseDraftRepository,
    private val validateReceipt: ValidateReceiptUseCase,
) {
    fun reduce(
        state: ManualReceiptEntryUiState,
        event: ManualReceiptEntryUiEvent,
    ): ManualReceiptEntryUiState {
        val clearedState = state.copy(
            fieldErrors = emptyMap(),
            firstBlockingSection = null,
            firstBlockingItemId = null,
            submitError = null,
        )
        return when (event) {
            is ManualReceiptEntryUiEvent.EditTargetSelected -> clearedState.copy(
                editDraft = clearedState.draftFor(event.target),
            )
            ManualReceiptEntryUiEvent.EditDismissed -> clearedState.copy(editDraft = null)
            ManualReceiptEntryUiEvent.EditCommitClick -> commitEditDraft(clearedState)
            is ManualReceiptEntryUiEvent.MerchantNameChanged -> clearedState.updateDraft {
                when (this) {
                    is ManualReceiptEditDraft.Merchant -> copy(value = event.value.take(MAX_MERCHANT_LENGTH))
                    else -> this
                }
            }
            is ManualReceiptEntryUiEvent.DateChanged -> clearedState.updateDraft {
                when (this) {
                    is ManualReceiptEditDraft.Date -> copy(value = event.value.take(ISO_DATE_LENGTH))
                    else -> this
                }
            }
            is ManualReceiptEntryUiEvent.CurrencyChanged -> clearedState.updateDraft {
                when (this) {
                    is ManualReceiptEditDraft.Currency -> copy(value = sanitizeCurrency(event.value))
                    else -> this
                }
            }
            is ManualReceiptEntryUiEvent.ItemNameChanged -> clearedState.updateDraft {
                when (this) {
                    is ManualReceiptEditDraft.Item -> copy(name = event.value.take(MAX_ITEM_NAME_LENGTH))
                    else -> this
                }
            }
            is ManualReceiptEntryUiEvent.ItemQuantityChanged -> clearedState.updateDraft {
                when (this) {
                    is ManualReceiptEditDraft.Item -> copy(quantity = sanitizeQuantity(event.value))
                    else -> this
                }.recalculateMoneyForQuantityChange()
            }
            is ManualReceiptEntryUiEvent.ItemQuantityStepped -> clearedState.updateDraft {
                when (this) {
                    is ManualReceiptEditDraft.Item -> {
                        val currentQuantity = quantity.toIntOrNull()?.coerceAtLeast(MIN_MANUAL_QUANTITY)
                            ?: MIN_MANUAL_QUANTITY
                        copy(
                            quantity = (currentQuantity + event.delta)
                                .coerceIn(MIN_MANUAL_QUANTITY, MAX_MANUAL_QUANTITY)
                                .toString(),
                        )
                    }
                    else -> this
                }.recalculateMoneyForQuantityChange()
            }
            is ManualReceiptEntryUiEvent.ItemUnitPriceChanged -> clearedState.updateDraft {
                when (this) {
                    is ManualReceiptEditDraft.Item -> copy(
                        unitPrice = sanitizeMoneyInput(event.value),
                        lastEditedMoneyField = ManualReceiptMoneyField.PriceEach,
                    )
                    else -> this
                }.recalculateLineTotalFromUnitPrice()
            }
            is ManualReceiptEntryUiEvent.ItemLineTotalChanged -> clearedState.updateDraft {
                when (this) {
                    is ManualReceiptEditDraft.Item -> copy(
                        lineTotal = sanitizeMoneyInput(event.value),
                        lastEditedMoneyField = ManualReceiptMoneyField.ItemTotal,
                    )
                    else -> this
                }.deriveUnitPriceFromLineTotal()
            }
            ManualReceiptEntryUiEvent.AddItemClick -> clearedState.copy(
                editDraft = ManualReceiptEditDraft.Item(
                    itemId = null,
                    name = "",
                    quantity = "1",
                    unitPrice = "",
                    lineTotal = "",
                    lastEditedMoneyField = ManualReceiptMoneyField.ItemTotal,
                    isNew = true,
                ),
            )
            is ManualReceiptEntryUiEvent.RemoveItemClick -> clearedState.copy(
                items = state.items.filterNot { item -> item.id == event.itemId },
                editDraft = state.editDraft.takeUnless { draft ->
                    draft is ManualReceiptEditDraft.Item && draft.itemId == event.itemId
                },
            )
            is ManualReceiptEntryUiEvent.FeeTypeChanged -> clearedState.updateDraft {
                when (this) {
                    is ManualReceiptEditDraft.Fee -> {
                        val nextType = event.value.takeUnless { it == FeeType.Discount } ?: FeeType.Other
                        copy(
                            type = nextType,
                            label = if (nextType == FeeType.Other) {
                                if (type == FeeType.Other) label.take(MAX_FEE_LABEL_LENGTH) else ""
                            } else {
                                manualFeeDisplayLabel(nextType)
                            },
                        )
                    }
                    else -> this
                }
            }
            is ManualReceiptEntryUiEvent.FeeLabelChanged -> clearedState.updateDraft {
                when (this) {
                    is ManualReceiptEditDraft.Fee -> copy(label = event.value.take(MAX_FEE_LABEL_LENGTH))
                    else -> this
                }
            }
            is ManualReceiptEntryUiEvent.FeeAmountChanged -> clearedState.updateDraft {
                when (this) {
                    is ManualReceiptEditDraft.Fee -> copy(amount = sanitizeMoneyInput(event.value))
                    else -> this
                }
            }
            ManualReceiptEntryUiEvent.AddFeeClick -> clearedState.copy(
                editDraft = ManualReceiptEditDraft.Fee(
                    feeId = null,
                    type = FeeType.Tax,
                    label = manualFeeDisplayLabel(FeeType.Tax),
                    amount = "",
                    isNew = true,
                ),
            )
            is ManualReceiptEntryUiEvent.RemoveFeeClick -> clearedState.copy(
                fees = state.fees.filterNot { fee -> fee.id == event.feeId },
                editDraft = state.editDraft.takeUnless { draft ->
                    draft is ManualReceiptEditDraft.Fee && draft.feeId == event.feeId
                },
            )
            ManualReceiptEntryUiEvent.BackClick,
            ManualReceiptEntryUiEvent.ContinueClick,
            -> state
        }
    }

    fun validateVisibleState(state: ManualReceiptEntryUiState): ManualReceiptEntryUiState {
        val validation = state.visibleValidation()
        return state.copy(
            fieldErrors = validation.errors,
            firstBlockingSection = validation.firstBlockingSection,
            firstBlockingItemId = validation.firstBlockingItemId,
            validationRequestId = state.validationRequestId + 1,
            submitError = null,
        )
    }

    suspend fun saveDraft(state: ManualReceiptEntryUiState): SaveManualReceiptDraftResult {
        val visibleValidation = state.visibleValidation()
        if (visibleValidation.errors.isNotEmpty()) {
            return SaveManualReceiptDraftResult.Invalid(
                fieldErrors = visibleValidation.errors,
                firstBlockingSection = visibleValidation.firstBlockingSection,
                firstBlockingItemId = visibleValidation.firstBlockingItemId,
            )
        }

        val receiptResult = state.toReceipt()
        val receipt = when (receiptResult) {
            is ReceiptBuildResult.Invalid -> return SaveManualReceiptDraftResult.Invalid(
                fieldErrors = receiptResult.fieldErrors,
                firstBlockingSection = receiptResult.firstBlockingSection,
            )
            is ReceiptBuildResult.Valid -> receiptResult.receipt
        }

        val validation = validateReceipt.validate(receipt)
        if (!validation.isValid) {
            return SaveManualReceiptDraftResult.Invalid(
                fieldErrors = validation.errors.toFieldErrors(),
                firstBlockingSection = validation.errors.toBlockingSection(),
            )
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

    private fun ManualReceiptEntryUiState.draftFor(target: ManualReceiptEditTarget): ManualReceiptEditDraft? {
        return when (target) {
            ManualReceiptEditTarget.Merchant -> ManualReceiptEditDraft.Merchant(merchantName)
            ManualReceiptEditTarget.Date -> ManualReceiptEditDraft.Date(dateLabel)
            ManualReceiptEditTarget.Currency -> ManualReceiptEditDraft.Currency(currencyCode)
            is ManualReceiptEditTarget.Item -> items.firstOrNull { item -> item.id == target.itemId }?.let { item ->
                val quantity = item.quantity.toIntOrNull()?.coerceIn(MIN_MANUAL_QUANTITY, MAX_MANUAL_QUANTITY)
                    ?: MIN_MANUAL_QUANTITY
                val unitPrice = parseMoneyMinor(item.unitPriceAmount)?.let(::formatMoneyMinor).orEmpty()
                val lastEditedMoneyField = if (item.isUnitPriceTrusted()) {
                    ManualReceiptMoneyField.PriceEach
                } else {
                    ManualReceiptMoneyField.ItemTotal
                }
                ManualReceiptEditDraft.Item(
                    itemId = item.id,
                    name = item.name,
                    quantity = quantity.toString(),
                    unitPrice = unitPrice,
                    lineTotal = item.amount,
                    lastEditedMoneyField = lastEditedMoneyField,
                    isNew = false,
                    initialName = item.name,
                    initialQuantity = quantity.toString(),
                    initialUnitPrice = unitPrice,
                    initialLineTotal = item.amount,
                )
            }
            is ManualReceiptEditTarget.Fee -> fees.firstOrNull { fee -> fee.id == target.feeId }?.let { fee ->
                ManualReceiptEditDraft.Fee(
                    feeId = fee.id,
                    type = fee.type,
                    label = fee.label.ifBlank { manualFeeDisplayLabel(fee.type) },
                    amount = fee.amount,
                    isNew = false,
                )
            }
        }
    }

    private fun ManualReceiptEntryUiState.updateDraft(
        transform: ManualReceiptEditDraft.() -> ManualReceiptEditDraft,
    ): ManualReceiptEntryUiState = copy(editDraft = editDraft?.transform())

    private fun ManualReceiptEditDraft.recalculateMoneyForQuantityChange(): ManualReceiptEditDraft {
        if (this !is ManualReceiptEditDraft.Item) return this
        return when (lastEditedMoneyField) {
            ManualReceiptMoneyField.PriceEach -> recalculateLineTotalFromUnitPrice()
            ManualReceiptMoneyField.ItemTotal -> deriveUnitPriceFromLineTotal()
        }
    }

    private fun ManualReceiptEditDraft.recalculateLineTotalFromUnitPrice(): ManualReceiptEditDraft {
        if (this !is ManualReceiptEditDraft.Item) return this
        val quantityValue = quantity.toIntOrNull()?.coerceIn(MIN_MANUAL_QUANTITY, MAX_MANUAL_QUANTITY)
            ?: return this
        val unitPriceMinor = parseMoneyMinor(unitPrice)?.value ?: return this
        return copy(lineTotal = formatMoneyMinor(MoneyMinor(unitPriceMinor * quantityValue)))
    }

    private fun ManualReceiptEditDraft.deriveUnitPriceFromLineTotal(): ManualReceiptEditDraft {
        if (this !is ManualReceiptEditDraft.Item) return this
        val quantityValue = quantity.toIntOrNull()?.coerceIn(MIN_MANUAL_QUANTITY, MAX_MANUAL_QUANTITY)
            ?: return this
        val lineTotalMinor = parseMoneyMinor(lineTotal)?.value ?: return this
        val averageUnitPrice = BigDecimal(lineTotalMinor).divide(BigDecimal(quantityValue), 2, RoundingMode.HALF_UP)
        return copy(unitPrice = formatManualMoneyInput(averageUnitPrice))
    }

    private fun commitEditDraft(state: ManualReceiptEntryUiState): ManualReceiptEntryUiState {
        return when (val draft = state.editDraft) {
            null -> state
            is ManualReceiptEditDraft.Merchant -> state.copy(
                merchantName = draft.value.trim(),
                editDraft = null,
            )
            is ManualReceiptEditDraft.Date -> {
                val value = draft.value.trim()
                val date = value.toManualLocalDateOrNull()
                when {
                    value.isBlank() -> state.copy(fieldErrors = mapOf("date" to "Choose a date."))
                    date == null -> state.copy(fieldErrors = mapOf("date" to "Use YYYY-MM-DD."))
                    date.isAfter(LocalDate.now()) -> state.copy(fieldErrors = mapOf("date" to FUTURE_DATE_ERROR))
                    else -> state.copy(dateLabel = date.toString(), editDraft = null)
                }
            }
            is ManualReceiptEditDraft.Currency -> {
                val value = sanitizeCurrency(draft.value)
                if (value.length != 3) {
                    state.copy(fieldErrors = mapOf("currency" to "Use a 3-letter code."))
                } else {
                    state.copy(currencyCode = value, editDraft = null)
                }
            }
            is ManualReceiptEditDraft.Item -> commitItemDraft(state, draft)
            is ManualReceiptEditDraft.Fee -> commitFeeDraft(state, draft)
        }
    }

    private fun commitItemDraft(
        state: ManualReceiptEntryUiState,
        draft: ManualReceiptEditDraft.Item,
    ): ManualReceiptEntryUiState {
        val errors = mutableMapOf<String, String>()
        val fieldId = draft.itemId ?: "draft"
        val name = draft.name.trim()
        val quantity = draft.quantity.toIntOrNull()
        val lineTotal = parseMoneyMinor(draft.lineTotal)

        if (name.isBlank()) errors["item_name_$fieldId"] = "Item name is required."
        if (draft.name.length > MAX_ITEM_NAME_LENGTH) errors["item_name_$fieldId"] = "Use 80 characters or fewer."
        if (quantity == null || quantity !in MIN_MANUAL_QUANTITY..MAX_MANUAL_QUANTITY) {
            errors["item_quantity_$fieldId"] = "Use 1 to 999."
        }
        if (lineTotal == null || lineTotal.value <= 0) {
            errors["item_amount_$fieldId"] = "Enter a positive item total."
        }
        if (errors.isNotEmpty()) return state.copy(fieldErrors = errors)

        val item = ManualReceiptItemUiState(
            id = draft.itemId ?: nextItemId(state.items),
            name = name,
            quantity = requireNotNull(quantity).toString(),
            unitPriceAmount = parseMoneyMinor(draft.unitPrice)?.let(::formatMoneyMinor)
                ?: formatAverageUnitPrice(requireNotNull(lineTotal).value, requireNotNull(quantity)),
            amount = formatMoneyMinor(requireNotNull(lineTotal)),
        )
        val items = if (draft.itemId == null) {
            state.items + item
        } else {
            state.items.map { existing -> if (existing.id == draft.itemId) item else existing }
        }
        return state.copy(items = items, editDraft = null)
    }

    private fun commitFeeDraft(
        state: ManualReceiptEntryUiState,
        draft: ManualReceiptEditDraft.Fee,
    ): ManualReceiptEntryUiState {
        val amount = parseMoneyMinor(draft.amount)
        if (draft.amount.isBlank() || amount?.value == 0L) {
            return if (draft.feeId == null) {
                state.copy(editDraft = null)
            } else {
                state.copy(
                    fees = state.fees.filterNot { fee -> fee.id == draft.feeId },
                    editDraft = null,
                )
            }
        }

        val errors = mutableMapOf<String, String>()
        val fieldId = draft.feeId ?: "draft"
        val label = if (draft.type == FeeType.Other) {
            draft.label.trim()
        } else {
            manualFeeDisplayLabel(draft.type)
        }

        if (draft.type == FeeType.Discount) errors["fee_type_$fieldId"] = "Manual discounts are not supported yet."
        if (label.isBlank()) errors["fee_label_$fieldId"] = "Fee name is required."
        if (amount == null || amount.value <= 0) errors["fee_amount_$fieldId"] = "Enter a positive amount."
        if (errors.isNotEmpty()) return state.copy(fieldErrors = errors)

        val fee = ManualReceiptFeeUiState(
            id = draft.feeId ?: nextFeeId(state.fees),
            type = draft.type,
            label = label,
            amount = formatMoneyMinor(requireNotNull(amount)),
        )
        val fees = if (draft.feeId == null) {
            state.fees + fee
        } else {
            state.fees.map { existing -> if (existing.id == draft.feeId) fee else existing }
        }
        return state.copy(fees = fees, editDraft = null)
    }

    private fun ManualReceiptEntryUiState.visibleValidation(): ManualReceiptValidationResult {
        val errors = mutableMapOf<String, String>()
        var firstBlockingSection: ManualReceiptEntrySection? = null
        var firstBlockingItemId: String? = null

        fun addError(
            key: String,
            message: String,
            section: ManualReceiptEntrySection,
            itemId: String? = null,
        ) {
            if (!errors.containsKey(key)) errors[key] = message
            if (firstBlockingSection == null) {
                firstBlockingSection = section
                firstBlockingItemId = itemId
            }
        }

        val parsedDate = dateLabel.toManualLocalDateOrNull()
        when {
            dateLabel.isBlank() -> addError("date", "Choose a date.", ManualReceiptEntrySection.Details)
            parsedDate == null -> addError("date", "Use YYYY-MM-DD.", ManualReceiptEntrySection.Details)
            parsedDate.isAfter(LocalDate.now()) -> addError("date", FUTURE_DATE_ERROR, ManualReceiptEntrySection.Details)
        }

        try {
            CurrencyCode(currencyCode.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            addError("currency", "Use a 3-letter code.", ManualReceiptEntrySection.Details)
        }

        if (items.isEmpty()) {
            addError("items", "Add at least one item.", ManualReceiptEntrySection.Items)
        }

        items.forEach { item ->
            val name = item.name.trim()
            val quantity = item.quantity.toIntOrNull()
            val amount = parseMoneyMinor(item.amount)
            if (name.isBlank()) {
                addError("item_name_${item.id}", "Required.", ManualReceiptEntrySection.Items, item.id)
            }
            if (item.name.length > MAX_ITEM_NAME_LENGTH) {
                addError("item_name_${item.id}", "Use 80 characters or fewer.", ManualReceiptEntrySection.Items, item.id)
            }
            if (quantity == null || quantity !in MIN_MANUAL_QUANTITY..MAX_MANUAL_QUANTITY) {
                addError("item_quantity_${item.id}", "Use 1 to 999.", ManualReceiptEntrySection.Items, item.id)
            }
            if (amount == null || amount.value <= 0) {
                addError("item_amount_${item.id}", "Enter a positive amount.", ManualReceiptEntrySection.Items, item.id)
            }
        }

        fees.forEach { fee ->
            val label = if (fee.type == FeeType.Other) fee.label.trim() else fee.displayLabel
            val amount = parseMoneyMinor(fee.amount)
            if (fee.type == FeeType.Discount) {
                addError("fee_type_${fee.id}", "Manual discounts are not supported yet.", ManualReceiptEntrySection.Fees)
            }
            if (label.isBlank()) {
                addError("fee_label_${fee.id}", "Fee name is required.", ManualReceiptEntrySection.Fees)
            }
            if (amount == null || amount.value <= 0) {
                addError("fee_amount_${fee.id}", "Enter a positive amount.", ManualReceiptEntrySection.Fees)
            }
        }

        return ManualReceiptValidationResult(
            errors = errors,
            firstBlockingSection = firstBlockingSection,
            firstBlockingItemId = firstBlockingItemId,
        )
    }

    private fun ManualReceiptEntryUiState.toReceipt(): ReceiptBuildResult {
        val errors = mutableMapOf<String, String>()
        val merchant = merchantName.trim().ifBlank { MANUAL_RECEIPT_FALLBACK_LABEL }
        val date = dateLabel.trim()
        val parsedDate = date.toManualLocalDateOrNull()
        when {
            date.isBlank() -> errors["date"] = "Choose a date."
            parsedDate == null -> errors["date"] = "Use YYYY-MM-DD."
            parsedDate.isAfter(LocalDate.now()) -> errors["date"] = FUTURE_DATE_ERROR
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
            val unitPrice = parseMoneyMinor(item.unitPriceAmount)

            if (name.isBlank()) errors["item_name_${item.id}"] = "Required."
            if (item.name.length > MAX_ITEM_NAME_LENGTH) errors["item_name_${item.id}"] = "Use 80 characters or fewer."
            if (quantity == null || quantity !in MIN_MANUAL_QUANTITY..MAX_MANUAL_QUANTITY) {
                errors["item_quantity_${item.id}"] = "Use 1 to 999."
            }
            if (amount == null || amount.value <= 0) errors["item_amount_${item.id}"] = "Enter an amount."

            if (name.isBlank() || item.name.length > MAX_ITEM_NAME_LENGTH ||
                quantity == null || quantity !in MIN_MANUAL_QUANTITY..MAX_MANUAL_QUANTITY ||
                amount == null || amount.value <= 0
            ) {
                null
            } else {
                ReceiptItem(
                    id = ReceiptItemId(item.id),
                    name = name,
                    quantity = Quantity(quantity),
                    unitPrice = unitPrice ?: MoneyMinor(amount.value / quantity),
                    totalPrice = amount,
                )
            }
        }
        if (receiptItems.isEmpty()) {
            errors["items"] = "Add at least one valid item."
        }

        val receiptFees = fees.mapNotNull { fee ->
            val label = fee.displayLabel.trim()
            val amount = parseMoneyMinor(fee.amount)

            if (fee.type == FeeType.Discount) errors["fee_type_${fee.id}"] = "Manual discounts are not supported yet."
            if (label.isBlank()) errors["fee_label_${fee.id}"] = "Required."
            if (amount == null || amount.value <= 0) errors["fee_amount_${fee.id}"] = "Enter an amount."

            if (fee.type == FeeType.Discount || label.isBlank() || amount == null || amount.value <= 0) {
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

        if (errors.isNotEmpty()) {
            return ReceiptBuildResult.Invalid(errors, errors.toFirstBlockingSection())
        }

        val subtotal = MoneyMinor(receiptItems.sumOf { item -> item.totalPrice.value })
        val total = MoneyMinor(subtotal.value + receiptFees.sumOf { fee -> fee.amount.value })
        return ReceiptBuildResult.Valid(
            Receipt(
                merchantName = merchant,
                currencyCode = currency,
                transactionDateLabel = requireNotNull(parsedDate).toString(),
                items = receiptItems,
                fees = receiptFees,
                total = total,
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
            ReceiptValidationError.NonPositiveFeeAmount -> "fees" to "Review fee amounts."
            ReceiptValidationError.TotalMismatch -> "summary" to "Total must equal items plus fees."
            ReceiptValidationError.NegativeTotal -> "summary" to "Total cannot be negative."
            ReceiptValidationError.FutureDate -> "date" to FUTURE_DATE_ERROR
        }
    }

    private fun Set<ReceiptValidationError>.toBlockingSection(): ManualReceiptEntrySection? = when {
        any { error -> error == ReceiptValidationError.BlankMerchantName || error == ReceiptValidationError.FutureDate } -> {
            ManualReceiptEntrySection.Details
        }
        any { error -> error == ReceiptValidationError.NoItems || error == ReceiptValidationError.BlankItemName || error == ReceiptValidationError.NonPositiveItemAmount } -> {
            ManualReceiptEntrySection.Items
        }
        any { error -> error == ReceiptValidationError.NonPositiveFeeAmount } -> ManualReceiptEntrySection.Fees
        any { error -> error == ReceiptValidationError.TotalMismatch || error == ReceiptValidationError.NegativeTotal } -> {
            ManualReceiptEntrySection.Summary
        }
        else -> null
    }

    private fun Map<String, String>.toFirstBlockingSection(): ManualReceiptEntrySection? = when {
        keys.any { key -> key == "merchant" || key == "date" || key == "currency" } -> ManualReceiptEntrySection.Details
        keys.any { key -> key == "items" || key.startsWith("item_") } -> ManualReceiptEntrySection.Items
        keys.any { key -> key == "fees" || key.startsWith("fee_") } -> ManualReceiptEntrySection.Fees
        keys.any { key -> key == "summary" } -> ManualReceiptEntrySection.Summary
        else -> null
    }

    private fun ManualReceiptItemUiState.isUnitPriceTrusted(): Boolean {
        val quantityValue = quantity.toIntOrNull() ?: return false
        val unitPrice = parseMoneyMinor(unitPriceAmount)?.value ?: return false
        val total = parseMoneyMinor(amount)?.value ?: return false
        return unitPrice * quantityValue == total
    }

    private fun parseMoneyMinor(value: String): MoneyMinor? {
        val amountMinor = parseManualMoneyMinorValue(value) ?: return null
        return MoneyMinor(amountMinor)
    }

    private fun formatMoneyMinor(value: MoneyMinor): String {
        return BigDecimal(value.value).movePointLeft(2).setScale(2).toPlainString()
    }

    private fun formatAverageUnitPrice(
        totalMinor: Long,
        quantity: Int,
    ): String {
        val averageUnitPrice = BigDecimal(totalMinor).divide(BigDecimal(quantity.coerceAtLeast(1)), 2, RoundingMode.HALF_UP)
        return formatManualMoneyInput(averageUnitPrice)
    }

    private fun nextItemId(items: List<ManualReceiptItemUiState>): String {
        val nextNumber = items.mapNotNull { item -> item.id.removePrefix("item-").toIntOrNull() }.maxOrNull() ?: 0
        return "item-${nextNumber + 1}"
    }

    private fun nextFeeId(fees: List<ManualReceiptFeeUiState>): String {
        val nextNumber = fees.mapNotNull { fee -> fee.id.removePrefix("fee-").toIntOrNull() }.maxOrNull() ?: 0
        return "fee-${nextNumber + 1}"
    }

    private fun sanitizeCurrency(value: String): String = value.uppercase().filter { it in 'A'..'Z' }.take(3)

    private fun sanitizeQuantity(value: String): String {
        val digits = value.filter(Char::isDigit).take(3)
        if (digits.isBlank()) return ""
        return digits.toIntOrNull()?.coerceIn(MIN_MANUAL_QUANTITY, MAX_MANUAL_QUANTITY)?.toString().orEmpty()
    }

    private fun sanitizeMoneyInput(value: String): String {
        val withoutSymbols = value.trim()
            .removePrefix("$")
            .removePrefix("€")
            .removePrefix("£")
            .replace(',', '.')
        if ('-' in withoutSymbols) return ""
        val builder = StringBuilder()
        var hasDecimal = false
        var decimalDigits = 0

        withoutSymbols.forEach { char ->
            when {
                char.isDigit() && (!hasDecimal || decimalDigits < MONEY_DECIMAL_PLACES) -> {
                    builder.append(char)
                    if (hasDecimal) decimalDigits += 1
                }
                char == '.' && !hasDecimal -> {
                    builder.append(char)
                    hasDecimal = true
                }
            }
        }

        return builder.toString()
    }

    private companion object {
        const val ISO_DATE_LENGTH = 10
        const val MAX_MERCHANT_LENGTH = 80
        const val MAX_ITEM_NAME_LENGTH = 80
        const val MAX_FEE_LABEL_LENGTH = 40
        const val MONEY_DECIMAL_PLACES = 2
        const val FUTURE_DATE_ERROR = "Date cannot be in the future."
        const val PENDING_PAYER_ID = "pending-payer"
    }
}

sealed interface SaveManualReceiptDraftResult {
    data object Saved : SaveManualReceiptDraftResult

    data class Invalid(
        val fieldErrors: Map<String, String>,
        val firstBlockingSection: ManualReceiptEntrySection? = null,
        val firstBlockingItemId: String? = null,
    ) : SaveManualReceiptDraftResult
}

private sealed interface ReceiptBuildResult {
    data class Valid(val receipt: Receipt) : ReceiptBuildResult

    data class Invalid(
        val fieldErrors: Map<String, String>,
        val firstBlockingSection: ManualReceiptEntrySection? = null,
    ) : ReceiptBuildResult
}

data class ManualReceiptValidationResult(
    val errors: Map<String, String>,
    val firstBlockingSection: ManualReceiptEntrySection?,
    val firstBlockingItemId: String? = null,
)
