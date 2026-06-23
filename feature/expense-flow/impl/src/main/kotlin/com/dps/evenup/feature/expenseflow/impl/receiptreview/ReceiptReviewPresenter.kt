package com.dps.evenup.feature.expenseflow.impl.receiptreview

import android.util.Log
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.domain.receipt.api.CurrencyCode
import com.dps.evenup.domain.receipt.api.FeeId
import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.NormalizeReceiptUseCase
import com.dps.evenup.domain.receipt.api.Quantity
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptFee
import com.dps.evenup.domain.receipt.api.ReceiptItem
import com.dps.evenup.domain.receipt.api.ReceiptItemId
import com.dps.evenup.domain.receipt.api.ReceiptItemParseMetadata
import com.dps.evenup.domain.receipt.api.ReceiptValidationError
import com.dps.evenup.domain.receipt.api.ReceiptParseCorrection
import com.dps.evenup.domain.receipt.api.ReceiptParseMetadata
import com.dps.evenup.domain.receipt.api.ValidateReceiptUseCase
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class ReceiptReviewPresenter(
    private val draftRepository: ExpenseDraftRepository,
    private val normalizeReceipt: NormalizeReceiptUseCase,
    private val validateReceipt: ValidateReceiptUseCase,
) {
    suspend fun load(): ReceiptReviewUiState {
        val draft = draftRepository.getDraft() ?: return ReceiptReviewUiState(
            isLoading = false,
            missingDraft = true,
            submitError = "No receipt draft was found.",
        )

        val receipt = normalizeReceipt.normalize(draft.receipt)
        receipt.logParseNotes()
        return ReceiptReviewUiState(
            isLoading = false,
            merchantName = receipt.merchantName,
            dateLabel = receipt.transactionDateLabel.orEmpty(),
            currencyCode = receipt.currencyCode.value,
            scannedReceiptTotalAmount = formatMoneyMinor(receipt.total),
            items = receipt.items.mapIndexed { index, item ->
                val correctionMatches = receipt.parseMetadata.corrections.filter { correction ->
                    correction.matchesItem(index = index, itemName = item.name)
                }
                val reviewNote = item.reviewNote(
                    correction = correctionMatches.firstOrNull(),
                    reviewWarnings = receipt.parseMetadata.reviewWarnings,
                    currencyCode = receipt.currencyCode.value,
                )
                val unitPriceTrusted = item.unitPrice.value * item.quantity.value == item.totalPrice.value
                ReceiptReviewItemUiState(
                    id = item.id.value,
                    name = item.name,
                    quantity = item.quantity.value.toString(),
                    unitPriceAmount = if (unitPriceTrusted) {
                        formatMoneyMinor(item.unitPrice)
                    } else {
                        formatAverageUnitPrice(item.totalPrice.value, item.quantity.value)
                    },
                    amount = formatMoneyMinor(item.totalPrice),
                    needsReview = reviewNote != null,
                    reviewNote = reviewNote,
                    parseMetadata = item.parseMetadata,
                    originalName = item.name,
                    correctionFields = correctionMatches.map { correction -> correction.field },
                )
            },
            fees = receipt.fees.map { fee ->
                ReceiptReviewFeeUiState(
                    id = fee.id.value,
                    type = fee.type,
                    label = fee.label,
                    amount = formatMoneyMinor(MoneyMinor(kotlin.math.abs(fee.amount.value))),
                )
            },
            parseCorrections = receipt.parseMetadata.corrections,
            reviewWarnings = receipt.parseMetadata.reviewWarnings,
            reviewWarningCount = receipt.parseMetadata.reviewWarnings.size,
            uncertainItemCount = receipt.items.count { item -> item.parseMetadata.needsReview },
        ).withMismatchDiagnosis()
    }

    fun reduce(
        state: ReceiptReviewUiState,
        event: ReceiptReviewUiEvent,
    ): ReceiptReviewUiState {
        val clearedState = state.copy(fieldErrors = emptyMap(), submitError = null)
        return when (event) {
            is ReceiptReviewUiEvent.EditTargetSelected -> clearedState.copy(
                editDraft = clearedState.draftFor(event.target),
                firstBlockingSection = null,
                firstBlockingItemId = null,
            )
            ReceiptReviewUiEvent.EditDismissed -> clearedState.copy(
                editDraft = null,
                firstBlockingSection = null,
                firstBlockingItemId = null,
            )
            ReceiptReviewUiEvent.EditCommitClick -> commitEditDraft(clearedState)
            ReceiptReviewUiEvent.StatusClick -> openFirstIssue(clearedState)
            is ReceiptReviewUiEvent.IssueSelected -> selectIssue(clearedState, event.issueId)
            ReceiptReviewUiEvent.IssueNavigatorDismissed -> clearedState.copy(issueNavigatorVisible = false)
            ReceiptReviewUiEvent.ReviewHighlightedItemsClick -> clearedState.copy(
                editDraft = null,
                issueNavigatorVisible = false,
                firstBlockingSection = if (state.firstSuggestedCorrectionItemId() != null || state.unresolvedReviewItemCount > 0) {
                    ReceiptReviewSection.Items
                } else {
                    ReceiptReviewSection.Summary
                },
                firstBlockingItemId = state.firstSuggestedCorrectionItemId(),
                validationRequestId = state.validationRequestId + 1,
            )
            ReceiptReviewUiEvent.UseReceiptTotalClick -> useReceiptTotal(clearedState)
            ReceiptReviewUiEvent.KeepCalculatedTotalClick -> keepCalculatedTotal(clearedState)
            ReceiptReviewUiEvent.EditReceiptTotalClick -> clearedState.copy(
                editDraft = ReceiptReviewEditDraft.ReceiptTotal(clearedState.scannedReceiptTotalAmount),
                issueNavigatorVisible = false,
            )
            is ReceiptReviewUiEvent.MerchantNameChanged -> clearedState.updateDraft {
                when (this) {
                    is ReceiptReviewEditDraft.Merchant -> copy(value = event.value.take(MAX_MERCHANT_LENGTH))
                    else -> this
                }
            }
            is ReceiptReviewUiEvent.DateChanged -> clearedState.updateDraft {
                when (this) {
                    is ReceiptReviewEditDraft.Date -> copy(value = event.value.take(ISO_DATE_LENGTH))
                    else -> this
                }
            }
            is ReceiptReviewUiEvent.CurrencyChanged -> clearedState.updateDraft {
                when (this) {
                    is ReceiptReviewEditDraft.Currency -> copy(value = sanitizeCurrency(event.value))
                    else -> this
                }
            }
            is ReceiptReviewUiEvent.ReceiptTotalChanged -> clearedState.updateDraft {
                when (this) {
                    is ReceiptReviewEditDraft.ReceiptTotal -> copy(value = sanitizeMoneyInput(event.value))
                    else -> this
                }
            }
            is ReceiptReviewUiEvent.ItemNameChanged -> clearedState.updateDraft {
                when (this) {
                    is ReceiptReviewEditDraft.Item -> copy(name = event.value.take(MAX_ITEM_NAME_LENGTH))
                    else -> this
                }
            }
            is ReceiptReviewUiEvent.ItemQuantityChanged -> clearedState.updateDraft {
                when (this) {
                    is ReceiptReviewEditDraft.Item -> copy(quantity = sanitizeQuantity(event.value))
                    else -> this
                }.recalculateMoneyForQuantityChange()
            }
            is ReceiptReviewUiEvent.ItemQuantityStepped -> clearedState.updateDraft {
                when (this) {
                    is ReceiptReviewEditDraft.Item -> {
                        val currentQuantity = quantity.toIntOrNull()?.coerceAtLeast(MIN_QUANTITY) ?: MIN_QUANTITY
                        copy(quantity = (currentQuantity + event.delta).coerceIn(MIN_QUANTITY, MAX_QUANTITY).toString())
                    }
                    else -> this
                }.recalculateMoneyForQuantityChange()
            }
            is ReceiptReviewUiEvent.ItemUnitPriceChanged -> clearedState.updateDraft {
                when (this) {
                    is ReceiptReviewEditDraft.Item -> copy(
                        unitPrice = sanitizeMoneyInput(event.value),
                        lastEditedMoneyField = ReceiptReviewMoneyField.PriceEach,
                    )
                    else -> this
                }.recalculateLineTotalFromUnitPrice()
            }
            is ReceiptReviewUiEvent.ItemLineTotalChanged -> clearedState.updateDraft {
                when (this) {
                    is ReceiptReviewEditDraft.Item -> copy(
                        lineTotal = sanitizeMoneyInput(event.value),
                        lastEditedMoneyField = ReceiptReviewMoneyField.ItemTotal,
                    )
                    else -> this
                }.deriveUnitPriceFromLineTotal()
            }
            ReceiptReviewUiEvent.UseSuggestedItemCorrectionClick -> clearedState.updateDraft {
                when (this) {
                    is ReceiptReviewEditDraft.Item -> suggestedCorrection?.let { correction ->
                        copy(
                            lineTotal = formatMoneyInput(correction.suggestedAmountMinor),
                            lastEditedMoneyField = ReceiptReviewMoneyField.ItemTotal,
                            suggestedCorrection = null,
                        ).deriveUnitPriceFromLineTotal()
                    } ?: this
                    else -> this
                }
            }
            ReceiptReviewUiEvent.AddItemClick -> clearedState.copy(
                editDraft = ReceiptReviewEditDraft.Item(
                    itemId = null,
                    name = "",
                    quantity = "1",
                    unitPrice = "",
                    lineTotal = "",
                    lastEditedMoneyField = ReceiptReviewMoneyField.ItemTotal,
                    isNew = true,
                ),
            )
            is ReceiptReviewUiEvent.RemoveItemClick -> clearedState.copy(
                items = state.items.filterNot { item -> item.id == event.itemId }.ifEmpty {
                    listOf(ReceiptReviewItemUiState(id = nextItemId(state.items)))
                },
                editDraft = state.editDraft.takeUnless { draft ->
                    draft is ReceiptReviewEditDraft.Item && draft.itemId == event.itemId
                },
                receiptTotalConfirmedByUser = false,
                firstBlockingSection = null,
                firstBlockingItemId = null,
            )
            is ReceiptReviewUiEvent.FeeTypeChanged -> clearedState.updateDraft {
                when (this) {
                    is ReceiptReviewEditDraft.Fee -> copy(
                        type = event.value,
                        label = if (event.value == FeeType.Other) {
                            if (type == FeeType.Other) label.take(MAX_FEE_LABEL_LENGTH) else ""
                        } else {
                            feeDisplayLabel(event.value)
                        },
                    )
                    else -> this
                }
            }
            is ReceiptReviewUiEvent.FeeLabelChanged -> clearedState.updateDraft {
                when (this) {
                    is ReceiptReviewEditDraft.Fee -> copy(label = event.value.take(MAX_FEE_LABEL_LENGTH))
                    else -> this
                }
            }
            is ReceiptReviewUiEvent.FeeAmountChanged -> clearedState.updateDraft {
                when (this) {
                    is ReceiptReviewEditDraft.Fee -> copy(amount = sanitizeMoneyInput(event.value))
                    else -> this
                }
            }
            ReceiptReviewUiEvent.AddFeeClick -> clearedState.copy(
                editDraft = ReceiptReviewEditDraft.Fee(
                    feeId = null,
                    type = FeeType.Tax,
                    label = feeDisplayLabel(FeeType.Tax),
                    amount = "",
                    isNew = true,
                ),
            )
            is ReceiptReviewUiEvent.RemoveFeeClick -> clearedState.copy(
                fees = state.fees.filterNot { fee -> fee.id == event.feeId },
                editDraft = state.editDraft.takeUnless { draft ->
                    draft is ReceiptReviewEditDraft.Fee && draft.feeId == event.feeId
                },
                receiptTotalConfirmedByUser = false,
                firstBlockingSection = null,
                firstBlockingItemId = null,
            )
            ReceiptReviewUiEvent.BackClick,
            ReceiptReviewUiEvent.ContinueClick,
            -> state
        }.withMismatchDiagnosis()
    }

    private fun ReceiptReviewUiState.draftFor(target: ReceiptReviewEditTarget): ReceiptReviewEditDraft? {
        return when (target) {
            ReceiptReviewEditTarget.Merchant -> ReceiptReviewEditDraft.Merchant(merchantName)
            ReceiptReviewEditTarget.Date -> ReceiptReviewEditDraft.Date(dateLabel)
            ReceiptReviewEditTarget.Currency -> ReceiptReviewEditDraft.Currency(currencyCode)
            ReceiptReviewEditTarget.ReceiptTotal -> ReceiptReviewEditDraft.ReceiptTotal(scannedReceiptTotalAmount)
            ReceiptReviewEditTarget.TotalCheck -> ReceiptReviewEditDraft.TotalCheck
            is ReceiptReviewEditTarget.Item -> items.firstOrNull { item -> item.id == target.itemId }?.let { item ->
                val quantity = item.quantity.toIntOrNull()?.coerceIn(MIN_QUANTITY, MAX_QUANTITY) ?: MIN_QUANTITY
                val unitPrice = parseMoneyMinor(item.unitPriceAmount)?.let(::formatMoneyMinor).orEmpty()
                val lastEditedMoneyField = if (item.isUnitPriceTrusted()) {
                    ReceiptReviewMoneyField.PriceEach
                } else {
                    ReceiptReviewMoneyField.ItemTotal
                }
                ReceiptReviewEditDraft.Item(
                    itemId = item.id,
                    name = item.name,
                    quantity = quantity.toString(),
                    unitPrice = unitPrice,
                    lineTotal = item.amount,
                    lastEditedMoneyField = lastEditedMoneyField,
                    isNew = false,
                    reviewNote = item.reviewNote,
                    suggestedCorrection = item.suggestedCorrection,
                    initialName = item.name,
                    initialQuantity = quantity.toString(),
                    initialUnitPrice = unitPrice,
                    initialLineTotal = item.amount,
                )
            }
            is ReceiptReviewEditTarget.Fee -> fees.firstOrNull { fee -> fee.id == target.feeId }?.let { fee ->
                ReceiptReviewEditDraft.Fee(
                    feeId = fee.id,
                    type = fee.type,
                    label = fee.label.ifBlank { feeDisplayLabel(fee.type) },
                    amount = fee.amount,
                    isNew = false,
                )
            }
        }
    }

    private fun ReceiptReviewUiState.updateDraft(
        transform: ReceiptReviewEditDraft.() -> ReceiptReviewEditDraft,
    ): ReceiptReviewUiState = copy(editDraft = editDraft?.transform())

    private fun openFirstIssue(state: ReceiptReviewUiState): ReceiptReviewUiState {
        return when {
            state.issues.size > 1 -> state.copy(issueNavigatorVisible = true)
            state.issues.size == 1 -> selectIssue(state, state.issues.single().id)
            else -> validateVisibleState(state)
        }
    }

    private fun selectIssue(
        state: ReceiptReviewUiState,
        issueId: String,
    ): ReceiptReviewUiState {
        val issue = state.issues.firstOrNull { candidate -> candidate.id == issueId } ?: return state
        return state.navigateToIssue(issue)
    }

    private fun ReceiptReviewUiState.navigateToIssue(issue: ReceiptReviewIssueUiState): ReceiptReviewUiState {
        return when (val target = issue.target) {
            is ReceiptReviewIssueTarget.Details -> copy(
                issueNavigatorVisible = false,
                editDraft = draftFor(target.editTarget),
                firstBlockingSection = ReceiptReviewSection.Details,
                firstBlockingItemId = null,
                validationRequestId = validationRequestId + 1,
            )
            is ReceiptReviewIssueTarget.Item -> copy(
                issueNavigatorVisible = false,
                editDraft = draftFor(ReceiptReviewEditTarget.Item(target.itemId)),
                firstBlockingSection = ReceiptReviewSection.Items,
                firstBlockingItemId = target.itemId,
                validationRequestId = validationRequestId + 1,
            )
            is ReceiptReviewIssueTarget.Summary -> copy(
                issueNavigatorVisible = false,
                editDraft = draftFor(target.editTarget),
                firstBlockingSection = ReceiptReviewSection.Summary,
                firstBlockingItemId = null,
                validationRequestId = validationRequestId + 1,
            )
            ReceiptReviewIssueTarget.Adjustments -> copy(
                issueNavigatorVisible = false,
                firstBlockingSection = ReceiptReviewSection.Adjustments,
                firstBlockingItemId = null,
                validationRequestId = validationRequestId + 1,
            )
        }
    }

    private fun useReceiptTotal(state: ReceiptReviewUiState): ReceiptReviewUiState {
        val receiptTotal = state.scannedReceiptTotalMinor ?: return state.copy(
            fieldErrors = mapOf("summary" to "Enter the receipt total first."),
        )
        if (receiptTotal == state.calculatedTotalMinor) {
            return state.copy(editDraft = null, receiptTotalConfirmedByUser = true)
        }

        val diagnosis = state.mismatchDiagnosis
        if (diagnosis?.confidence == DiagnosisConfidence.High && diagnosis.suspectedCorrections.isNotEmpty()) {
            val correctionsByItem = diagnosis.suspectedCorrections.associateBy { correction -> correction.itemId }
            val correctedState = state.copy(
                items = state.items.map { item ->
                    val correction = correctionsByItem[item.id] ?: return@map item
                    item.copy(
                        amount = formatMoneyInput(correction.suggestedAmountMinor),
                        unitPriceAmount = formatAverageUnitPrice(
                            totalMinor = correction.suggestedAmountMinor,
                            quantity = item.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        ),
                        needsReview = false,
                        reviewNote = null,
                        reviewConfirmed = true,
                        parseMetadata = item.parseMetadata.copy(candidates = emptyList(), needsReview = false),
                    )
                },
                editDraft = null,
                issueNavigatorVisible = false,
                receiptTotalConfirmedByUser = true,
            ).withMismatchDiagnosis()
            if (correctedState.calculatedTotalMinor == receiptTotal) return correctedState
        }

        if (receiptTotal > state.calculatedTotalMinor) {
            val adjustment = receiptTotal - state.calculatedTotalMinor
            return state.copy(
                fees = state.fees + ReceiptReviewFeeUiState(
                    id = nextFeeId(state.fees),
                    type = FeeType.Other,
                    label = RECEIPT_TOTAL_ADJUSTMENT_LABEL,
                    amount = formatMoneyInput(adjustment),
                ),
                editDraft = null,
                issueNavigatorVisible = false,
                receiptTotalConfirmedByUser = true,
            ).withMismatchDiagnosis()
        }

        return state.copy(
            fieldErrors = mapOf("summary" to "Review item amounts or edit the receipt total to resolve the difference."),
            firstBlockingSection = ReceiptReviewSection.Summary,
            firstBlockingItemId = null,
            validationRequestId = state.validationRequestId + 1,
        )
    }

    private fun keepCalculatedTotal(state: ReceiptReviewUiState): ReceiptReviewUiState {
        return state.copy(
            scannedReceiptTotalAmount = formatMoneyInput(state.calculatedTotalMinor),
            receiptTotalConfirmedByUser = true,
            editDraft = null,
            issueNavigatorVisible = false,
            firstBlockingSection = null,
            firstBlockingItemId = null,
        ).withMismatchDiagnosis()
    }

    private fun ReceiptReviewEditDraft.recalculateMoneyForQuantityChange(): ReceiptReviewEditDraft {
        if (this !is ReceiptReviewEditDraft.Item) return this
        return when (lastEditedMoneyField) {
            ReceiptReviewMoneyField.PriceEach -> recalculateLineTotalFromUnitPrice()
            ReceiptReviewMoneyField.ItemTotal -> deriveUnitPriceFromLineTotal()
        }
    }

    private fun ReceiptReviewEditDraft.recalculateLineTotalFromUnitPrice(): ReceiptReviewEditDraft {
        if (this !is ReceiptReviewEditDraft.Item) return this
        val quantityValue = quantity.toIntOrNull()?.coerceIn(MIN_QUANTITY, MAX_QUANTITY) ?: return this
        val unitPriceMinor = parseMoneyMinor(unitPrice)?.value ?: return this
        return copy(lineTotal = formatMoneyMinor(MoneyMinor(unitPriceMinor * quantityValue)))
    }

    private fun ReceiptReviewEditDraft.deriveUnitPriceFromLineTotal(): ReceiptReviewEditDraft {
        if (this !is ReceiptReviewEditDraft.Item) return this
        val quantityValue = quantity.toIntOrNull()?.coerceIn(MIN_QUANTITY, MAX_QUANTITY) ?: return this
        val lineTotalMinor = parseMoneyMinor(lineTotal)?.value ?: return this
        val averageUnitPrice = BigDecimal(lineTotalMinor).divide(BigDecimal(quantityValue), 2, RoundingMode.HALF_UP)
        return copy(unitPrice = formatMoneyInput(averageUnitPrice))
    }

    private fun commitEditDraft(state: ReceiptReviewUiState): ReceiptReviewUiState {
        return when (val draft = state.editDraft) {
            null -> state
            is ReceiptReviewEditDraft.Merchant -> {
                val value = draft.value.trim()
                if (value.isBlank()) {
                    state.copy(fieldErrors = mapOf("merchant" to "Merchant is required."))
                } else {
                    state.copy(merchantName = value, editDraft = null)
                }
            }
            is ReceiptReviewEditDraft.Date -> {
                val value = draft.value.trim()
                val date = value.toLocalDateOrNull()
                when {
                    value.isBlank() -> state.copy(dateLabel = "", editDraft = null)
                    date == null -> state.copy(fieldErrors = mapOf("date" to "Use YYYY-MM-DD."))
                    date.isAfter(LocalDate.now()) -> state.copy(fieldErrors = mapOf("date" to FUTURE_DATE_ERROR))
                    else -> state.copy(dateLabel = value, editDraft = null)
                }
            }
            is ReceiptReviewEditDraft.Currency -> {
                val value = sanitizeCurrency(draft.value)
                if (value.length != 3) {
                    state.copy(fieldErrors = mapOf("currency" to "Use a 3-letter code."))
                } else {
                    state.copy(
                        currencyCode = value,
                        currencyConfirmedByUser = true,
                        editDraft = null,
                    )
                }
            }
            is ReceiptReviewEditDraft.ReceiptTotal -> {
                val total = parseMoneyMinor(draft.value)
                if (total == null || total.value <= 0) {
                    state.copy(fieldErrors = mapOf("summary" to "Enter a positive receipt total."))
                } else {
                    state.copy(
                        scannedReceiptTotalAmount = formatMoneyMinor(total),
                        receiptTotalConfirmedByUser = true,
                        editDraft = null,
                    )
                }
            }
            ReceiptReviewEditDraft.TotalCheck -> state.copy(editDraft = null)
            is ReceiptReviewEditDraft.Item -> commitItemDraft(state, draft)
            is ReceiptReviewEditDraft.Fee -> commitFeeDraft(state, draft)
        }
    }

    private fun commitItemDraft(
        state: ReceiptReviewUiState,
        draft: ReceiptReviewEditDraft.Item,
    ): ReceiptReviewUiState {
        val errors = mutableMapOf<String, String>()
        val name = draft.name.trim()
        val quantity = draft.quantity.toIntOrNull()
        val lineTotal = parseMoneyMinor(draft.lineTotal)
        val fieldId = draft.itemId ?: "draft"

        if (name.isBlank()) errors["item_name_$fieldId"] = "Item name is required."
        if (draft.name.length > MAX_ITEM_NAME_LENGTH) errors["item_name_$fieldId"] = "Use 80 characters or fewer."
        if (quantity == null || quantity !in MIN_QUANTITY..MAX_QUANTITY) {
            errors["item_quantity_$fieldId"] = "Use 1 to 999."
        }
        if (lineTotal == null || lineTotal.value <= 0) {
            errors["item_amount_$fieldId"] = "Enter a positive item total."
        }
        if (errors.isNotEmpty()) return state.copy(fieldErrors = errors)

        val existingItem = draft.itemId?.let { itemId -> state.items.firstOrNull { item -> item.id == itemId } }
        val item = ReceiptReviewItemUiState(
            id = draft.itemId ?: nextItemId(state.items),
            name = name,
            quantity = requireNotNull(quantity).toString(),
            unitPriceAmount = parseMoneyMinor(draft.unitPrice)?.let(::formatMoneyMinor)
                ?: formatAverageUnitPrice(requireNotNull(lineTotal).value, requireNotNull(quantity)),
            amount = formatMoneyMinor(requireNotNull(lineTotal)),
            needsReview = false,
            reviewNote = null,
            reviewConfirmed = existingItem?.needsReview == true || existingItem?.reviewConfirmed == true,
            parseMetadata = existingItem?.parseMetadata?.copy(
                candidates = emptyList(),
                needsReview = false,
            ) ?: ReceiptItemParseMetadata(),
            originalName = existingItem?.originalName ?: name,
            correctionFields = existingItem?.correctionFields.orEmpty(),
        )
        val items = if (draft.itemId == null) {
            state.items + item
        } else {
            state.items.map { existing -> if (existing.id == draft.itemId) item else existing }
        }
        return state.copy(
            items = items,
            editDraft = null,
            receiptTotalConfirmedByUser = if (draft.hasChanges) false else state.receiptTotalConfirmedByUser,
        )
    }

    private fun commitFeeDraft(
        state: ReceiptReviewUiState,
        draft: ReceiptReviewEditDraft.Fee,
    ): ReceiptReviewUiState {
        val errors = mutableMapOf<String, String>()
        val amount = parseMoneyMinor(draft.amount)
        val fieldId = draft.feeId ?: "draft"
        val label = if (draft.type == FeeType.Other) {
            draft.label.trim()
        } else {
            feeDisplayLabel(draft.type)
        }

        if (label.isBlank()) errors["fee_label_$fieldId"] = "${draft.componentName()} name is required."
        if (amount == null || amount.value <= 0) errors["fee_amount_$fieldId"] = "Enter a positive amount."
        if (errors.isNotEmpty()) return state.copy(fieldErrors = errors)

        val fee = ReceiptReviewFeeUiState(
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
        return state.copy(fees = fees, editDraft = null, receiptTotalConfirmedByUser = false)
    }

    fun validateVisibleState(state: ReceiptReviewUiState): ReceiptReviewUiState {
        val validation = state.visibleValidation()
        return state.copy(
            fieldErrors = validation.errors,
            firstBlockingSection = validation.firstBlockingSection,
            firstBlockingItemId = validation.firstBlockingItemId,
            validationRequestId = state.validationRequestId + 1,
            submitError = null,
        )
    }

    suspend fun saveDraft(state: ReceiptReviewUiState): SaveReceiptReviewResult {
        val visibleValidation = state.visibleValidation()
        if (visibleValidation.errors.isNotEmpty()) {
            return SaveReceiptReviewResult.Invalid(
                fieldErrors = visibleValidation.errors,
                firstBlockingSection = visibleValidation.firstBlockingSection,
                firstBlockingItemId = visibleValidation.firstBlockingItemId,
            )
        }
        val existingDraft = draftRepository.getDraft() ?: return SaveReceiptReviewResult.MissingDraft
        val receiptResult = state.toReceipt()
        val receipt = when (receiptResult) {
            is ReceiptReviewBuildResult.Invalid -> return SaveReceiptReviewResult.Invalid(
                fieldErrors = receiptResult.fieldErrors,
                firstBlockingSection = receiptResult.firstBlockingSection,
            )
            is ReceiptReviewBuildResult.Valid -> receiptResult.receipt
        }

        val normalizedReceipt = normalizeReceipt.normalize(receipt)
        val validation = validateReceipt.validate(normalizedReceipt)
        if (!validation.isValid) {
            return SaveReceiptReviewResult.Invalid(
                fieldErrors = validation.errors.toFieldErrors(),
                firstBlockingSection = validation.errors.toBlockingSection(),
            )
        }

        draftRepository.saveDraft(existingDraft.copy(receipt = normalizedReceipt))
        return SaveReceiptReviewResult.Saved
    }

    private fun ReceiptReviewUiState.visibleValidation(): ReceiptReviewValidationResult {
        val errors = mutableMapOf<String, String>()
        var firstBlockingSection: ReceiptReviewSection? = null
        var firstBlockingItemId: String? = null

        fun addError(
            key: String,
            message: String,
            section: ReceiptReviewSection,
            itemId: String? = null,
        ) {
            if (!errors.containsKey(key)) errors[key] = message
            if (firstBlockingSection == null) {
                firstBlockingSection = section
                firstBlockingItemId = itemId
            }
        }

        val merchant = merchantName.trim()
        if (merchant.isBlank()) addError("merchant", "Merchant is required.", ReceiptReviewSection.Details)

        val receiptDate = dateLabel.trim()
        val parsedDate = receiptDate.toLocalDateOrNull()
        if (receiptDate.isNotBlank()) {
            when {
                parsedDate == null -> addError("date", "Use YYYY-MM-DD.", ReceiptReviewSection.Details)
                parsedDate.isAfter(LocalDate.now()) -> addError("date", FUTURE_DATE_ERROR, ReceiptReviewSection.Details)
            }
        }

        try {
            CurrencyCode(currencyCode.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            addError("currency", "Use a 3-letter code.", ReceiptReviewSection.Details)
        }

        val scannedReceiptTotal = parseMoneyMinor(scannedReceiptTotalAmount)
        if (scannedReceiptTotal == null || scannedReceiptTotal.value <= 0) {
            addError("summary", reconciliation.message, ReceiptReviewSection.Summary)
        } else if (scannedReceiptTotal.value != calculatedTotalMinor) {
            val firstSuggestedItemId = firstSuggestedCorrectionItemId()
            addError(
                key = "summary",
                message = reconciliation.message,
                section = if (firstSuggestedItemId == null) ReceiptReviewSection.Summary else ReceiptReviewSection.Items,
                itemId = firstSuggestedItemId,
            )
        }
        if (unresolvedReviewItemCount > 0) {
            addError(
                key = "items",
                message = reconciliation.message,
                section = ReceiptReviewSection.Items,
                itemId = firstReviewItemId(),
            )
        }

        if (items.isEmpty()) addError("items", "Add at least one valid item.", ReceiptReviewSection.Items)

        items.forEach { item ->
            val name = item.name.trim()
            val quantity = item.quantity.toIntOrNull()
            val amount = parseMoneyMinor(item.amount)
            if (name.isBlank()) addError("item_name_${item.id}", "Required.", ReceiptReviewSection.Items)
            if (item.name.length > MAX_ITEM_NAME_LENGTH) {
                addError("item_name_${item.id}", "Use 80 characters or fewer.", ReceiptReviewSection.Items)
            }
            if (quantity == null || quantity !in MIN_QUANTITY..MAX_QUANTITY) {
                addError("item_quantity_${item.id}", "Use 1 to 999.", ReceiptReviewSection.Items)
            }
            if (amount == null || amount.value <= 0) {
                addError("item_amount_${item.id}", "Enter a positive amount.", ReceiptReviewSection.Items)
            }
        }

        fees.forEach { fee ->
            val label = if (fee.type == FeeType.Other) fee.label.trim() else fee.displayLabel
            val amount = parseMoneyMinor(fee.amount)
            if (label.isBlank()) {
                addError("fee_label_${fee.id}", "${fee.componentName()} name is required.", ReceiptReviewSection.Adjustments)
            }
            if (amount == null || amount.value <= 0) {
                addError("fee_amount_${fee.id}", "Enter a positive amount.", ReceiptReviewSection.Adjustments)
            }
        }

        return ReceiptReviewValidationResult(
            errors = errors,
            firstBlockingSection = firstBlockingSection,
            firstBlockingItemId = firstBlockingItemId,
        )
    }

    private fun ReceiptReviewUiState.toReceipt(): ReceiptReviewBuildResult {
        val errors = mutableMapOf<String, String>()
        val merchant = merchantName.trim()
        if (merchant.isBlank()) {
            errors["merchant"] = "Merchant is required."
        }
        val receiptDate = dateLabel.trim()
        val parsedDate = receiptDate.toLocalDateOrNull()
        if (receiptDate.isNotBlank()) {
            when {
                parsedDate == null -> errors["date"] = "Use YYYY-MM-DD."
                parsedDate.isAfter(LocalDate.now()) -> errors["date"] = FUTURE_DATE_ERROR
            }
        }

        val currency = try {
            CurrencyCode(currencyCode.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            errors["currency"] = "Use a 3-letter code."
            CurrencyCode("USD")
        }

        val scannedReceiptTotal = parseMoneyMinor(scannedReceiptTotalAmount)
        if (scannedReceiptTotal == null || scannedReceiptTotal.value <= 0) {
            errors["summary"] = reconciliation.message
        } else if (scannedReceiptTotal.value != calculatedTotalMinor) {
            errors["summary"] = reconciliation.message
        }
        if (unresolvedReviewItemCount > 0) {
            errors["items"] = reconciliation.message
        }

        val receiptItems = items.mapNotNull { item ->
            val name = item.name.trim()
            val quantity = item.quantity.toIntOrNull()
            val amount = parseMoneyMinor(item.amount)
            val unitPrice = parseMoneyMinor(item.unitPriceAmount)

            if (name.isBlank()) errors["item_name_${item.id}"] = "Required."
            if (item.name.length > MAX_ITEM_NAME_LENGTH) errors["item_name_${item.id}"] = "Use 80 characters or fewer."
            if (quantity == null || quantity !in MIN_QUANTITY..MAX_QUANTITY) errors["item_quantity_${item.id}"] = "Use 1 to 999."
            if (amount == null || amount.value <= 0) errors["item_amount_${item.id}"] = "Enter an amount."

            if (name.isBlank() || item.name.length > MAX_ITEM_NAME_LENGTH || quantity == null ||
                quantity !in MIN_QUANTITY..MAX_QUANTITY || amount == null || amount.value <= 0
            ) {
                null
            } else {
                ReceiptItem(
                    id = ReceiptItemId(item.id),
                    name = name,
                    quantity = Quantity(quantity),
                    unitPrice = unitPrice ?: MoneyMinor(amount.value / quantity),
                    totalPrice = amount,
                    parseMetadata = item.parseMetadataForSave(),
                )
            }
        }
        if (receiptItems.isEmpty()) {
            errors["items"] = "Add at least one valid item."
        }

        val receiptFees = fees.mapNotNull { fee ->
            val label = fee.displayLabel.trim()
            val amount = parseMoneyMinor(fee.amount)

            if (label.isBlank()) errors["fee_label_${fee.id}"] = "Required."
            if (amount == null || amount.value <= 0) errors["fee_amount_${fee.id}"] = "Enter an amount."

            if (label.isBlank() || amount == null || amount.value <= 0) {
                null
            } else {
                val signedAmount = fee.signedAmountMinor ?: return@mapNotNull null
                ReceiptFee(
                    id = FeeId(fee.id),
                    type = fee.type,
                    label = label,
                    amount = MoneyMinor(signedAmount),
                )
            }
        }

        val subtotal = receiptItems.takeIf { it.isNotEmpty() }
            ?.let { validItems -> MoneyMinor(validItems.sumOf { item -> item.totalPrice.value }) }

        if (errors.isNotEmpty()) {
            return ReceiptReviewBuildResult.Invalid(errors, errors.toFirstBlockingSection())
        }

        val confirmedItemCorrectionFields = items
            .filter { item -> item.reviewConfirmed || !item.needsReview }
            .flatMap { item -> item.correctionFields }
            .toSet()
        val confirmedItemNames = items
            .filter { item -> item.reviewConfirmed || !item.needsReview }
            .flatMap { item -> listOf(item.name, item.originalName) }
            .map { name -> name.trim().lowercase() }
            .filter { name -> name.isNotEmpty() }
            .toSet()
        val remainingCorrections = parseCorrections.filterNot { correction ->
            correction.field in confirmedItemCorrectionFields ||
                correction.itemName?.trim()?.lowercase() in confirmedItemNames
        }
        val remainingReviewWarnings = reviewWarnings.filter { warning ->
            warning.shouldKeepReviewWarning(
                currencyConfirmedByUser = currencyConfirmedByUser,
                confirmedItemNames = confirmedItemNames,
                hasUnresolvedReviewItems = unresolvedReviewItemCount > 0,
                hasUnresolvedTotalIssue = reconciliation.type == ReceiptReviewReconciliationType.Mismatch ||
                    reconciliation.type == ReceiptReviewReconciliationType.MissingScannedTotal,
            )
        }

        return ReceiptReviewBuildResult.Valid(
            Receipt(
                merchantName = merchant,
                currencyCode = currency,
                transactionDateLabel = receiptDate.ifBlank { null },
                items = receiptItems,
                fees = receiptFees,
                total = requireNotNull(scannedReceiptTotal),
                subtotal = subtotal,
                parseMetadata = ReceiptParseMetadata(
                    corrections = remainingCorrections,
                    reviewWarnings = remainingReviewWarnings,
                ),
            ),
        )
    }

    private fun Set<ReceiptValidationError>.toFieldErrors(): Map<String, String> = associate { error ->
        when (error) {
            ReceiptValidationError.BlankMerchantName -> "merchant" to "Merchant is required."
            ReceiptValidationError.NoItems -> "items" to "Add at least one item."
            ReceiptValidationError.BlankItemName -> "items" to "Each item needs a name."
            ReceiptValidationError.NonPositiveItemAmount -> "items" to "Each item needs a positive amount."
            ReceiptValidationError.NonPositiveFeeAmount -> "fees" to "Review fee and discount amounts."
            ReceiptValidationError.TotalMismatch -> "summary" to "Total must equal items plus fees."
            ReceiptValidationError.NegativeTotal -> "summary" to "Total cannot be negative."
            ReceiptValidationError.FutureDate -> "date" to FUTURE_DATE_ERROR
        }
    }

    private fun Set<ReceiptValidationError>.toBlockingSection(): ReceiptReviewSection? = when {
        any { error -> error == ReceiptValidationError.BlankMerchantName || error == ReceiptValidationError.FutureDate } -> ReceiptReviewSection.Details
        any { error -> error == ReceiptValidationError.NoItems || error == ReceiptValidationError.BlankItemName || error == ReceiptValidationError.NonPositiveItemAmount } -> ReceiptReviewSection.Items
        any { error -> error == ReceiptValidationError.NonPositiveFeeAmount } -> ReceiptReviewSection.Adjustments
        any { error -> error == ReceiptValidationError.TotalMismatch || error == ReceiptValidationError.NegativeTotal } -> ReceiptReviewSection.Summary
        else -> null
    }

    private fun Map<String, String>.toFirstBlockingSection(): ReceiptReviewSection? = when {
        keys.any { key -> key == "merchant" || key == "date" || key == "currency" } -> ReceiptReviewSection.Details
        keys.any { key -> key == "items" || key.startsWith("item_") } -> ReceiptReviewSection.Items
        keys.any { key -> key == "fees" || key.startsWith("fee_") } -> ReceiptReviewSection.Adjustments
        keys.any { key -> key == "summary" } -> ReceiptReviewSection.Summary
        else -> null
    }

    private fun ReceiptReviewUiState.withMismatchDiagnosis(): ReceiptReviewUiState {
        val correctionsByItemId = mismatchDiagnosis
            ?.takeIf { diagnosis -> diagnosis.confidence == DiagnosisConfidence.High }
            ?.suspectedCorrections
            .orEmpty()
            .associateBy { correction -> correction.itemId }
        return copy(
            items = items.map { item ->
                item.copy(suggestedCorrection = correctionsByItemId[item.id])
            },
        )
    }

    private fun ReceiptReviewUiState.firstSuggestedCorrectionItemId(): String? {
        return items.firstOrNull { item -> item.suggestedCorrection != null }?.id
    }

    private fun ReceiptReviewUiState.firstReviewItemId(): String? {
        return items.firstOrNull { item -> item.needsReview }?.id
    }

    private fun Receipt.logParseNotes() {
        parseMetadata.corrections.forEach { correction ->
            val itemLabel = correction.itemName?.takeIf { it.isNotBlank() } ?: "receipt item"
            runCatching {
                Log.i(
                    TAG,
                    "Corrected $itemLabel from ${formatCurrency(formatMoneyMinor(correction.from), currencyCode.value)} " +
                        "to ${formatCurrency(formatMoneyMinor(correction.to), currencyCode.value)}. Reason: ${correction.reason}",
                )
            }
        }
        parseMetadata.reviewWarnings.forEach { warning ->
            runCatching { Log.w(TAG, warning) }
        }
    }

    private fun ReceiptParseCorrection.matchesItem(
        index: Int,
        itemName: String,
    ): Boolean {
        val fieldIndex = ITEM_FIELD_INDEX.find(field)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return fieldIndex == index || itemName.isNotBlank() && itemName.equals(this.itemName, ignoreCase = true)
    }

    private fun ReceiptItem.reviewNote(
        correction: ReceiptParseCorrection?,
        reviewWarnings: List<String>,
        currencyCode: String,
    ): String? {
        val itemWarning = reviewWarnings.firstOrNull { warning ->
            name.isNotBlank() && warning.contains(name, ignoreCase = true)
        }
        return when {
            correction != null && correction.from.value != correction.to.value -> {
                "Amount was corrected to match subtotal"
            }
            correction != null -> "Amount was corrected to match subtotal"
            parseMetadata.candidates.distinct().size > 1 -> "Check price from receipt"
            parseMetadata.confidence?.let { confidence -> confidence < LOW_CONFIDENCE_THRESHOLD } == true -> {
                "Low-confidence item name or amount"
            }
            itemWarning != null -> itemWarning
            parseMetadata.needsReview -> "Check price from receipt"
            else -> null
        }
    }

    private fun ReceiptReviewItemUiState.parseMetadataForSave(): ReceiptItemParseMetadata {
        return if (reviewConfirmed || !needsReview) {
            parseMetadata.copy(candidates = emptyList(), needsReview = false)
        } else {
            parseMetadata
        }
    }

    private fun String.shouldKeepReviewWarning(
        currencyConfirmedByUser: Boolean,
        confirmedItemNames: Set<String>,
        hasUnresolvedReviewItems: Boolean,
        hasUnresolvedTotalIssue: Boolean,
    ): Boolean {
        val normalized = trim().lowercase()
        if (normalized.isBlank()) return false
        if ("currency" in normalized) return !currencyConfirmedByUser
        if (confirmedItemNames.any { itemName -> itemName in normalized }) return false
        if (normalized.contains("subtotal") ||
            normalized.contains("candidate") ||
            normalized.contains("amount") ||
            normalized.contains("price")
        ) {
            return hasUnresolvedReviewItems || hasUnresolvedTotalIssue
        }
        return true
    }

    private fun ReceiptReviewItemUiState.isUnitPriceTrusted(): Boolean {
        val quantityValue = quantity.toIntOrNull() ?: return false
        val unitPrice = parseMoneyMinor(unitPriceAmount)?.value ?: return false
        val total = parseMoneyMinor(amount)?.value ?: return false
        return unitPrice * quantityValue == total
    }

    private fun ReceiptReviewEditDraft.Fee.componentName(): String {
        return if (type == FeeType.Discount) "Discount" else "Fee"
    }

    private fun ReceiptReviewFeeUiState.componentName(): String {
        return if (type == FeeType.Discount) "Discount" else "Fee"
    }

    private fun parseMoneyMinor(value: String): MoneyMinor? {
        val normalized = value.trim()
            .removePrefix("$")
            .removePrefix("€")
            .removePrefix("£")
            .replace(',', '.')
        if (normalized.isBlank()) return null
        return try {
            val decimal = BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY)
            val amountMinor = decimal.movePointRight(2).longValueExact()
            if (amountMinor !in 0..MAX_RECEIPT_REVIEW_MONEY_MINOR) null else MoneyMinor(amountMinor)
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun formatMoneyMinor(value: MoneyMinor): String {
        return BigDecimal(value.value).movePointLeft(2).setScale(2).toPlainString()
    }

    private fun formatAverageUnitPrice(
        totalMinor: Long,
        quantity: Int,
    ): String {
        val averageUnitPrice = BigDecimal(totalMinor).divide(BigDecimal(quantity.coerceAtLeast(1)), 2, RoundingMode.HALF_UP)
        return formatMoneyInput(averageUnitPrice)
    }

    private fun nextItemId(items: List<ReceiptReviewItemUiState>): String {
        val nextNumber = items.mapNotNull { item -> item.id.removePrefix("item-").toIntOrNull() }.maxOrNull() ?: 0
        return "item-${nextNumber + 1}"
    }

    private fun nextFeeId(fees: List<ReceiptReviewFeeUiState>): String {
        val nextNumber = fees.mapNotNull { fee -> fee.id.removePrefix("fee-").toIntOrNull() }.maxOrNull() ?: 0
        return "fee-${nextNumber + 1}"
    }

    private fun sanitizeCurrency(value: String): String = value.uppercase().filter { it in 'A'..'Z' }.take(3)

    private fun sanitizeQuantity(value: String): String {
        val digits = value.filter(Char::isDigit).take(3)
        if (digits.isBlank()) return ""
        return digits.toIntOrNull()?.coerceIn(MIN_QUANTITY, MAX_QUANTITY)?.toString().orEmpty()
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
        const val TAG = "ReceiptReview"
        const val ISO_DATE_LENGTH = 10
        const val MAX_MERCHANT_LENGTH = 80
        const val MAX_ITEM_NAME_LENGTH = 80
        const val MAX_FEE_LABEL_LENGTH = 40
        const val MIN_QUANTITY = 1
        const val MAX_QUANTITY = 999
        const val MONEY_DECIMAL_PLACES = 2
        const val FUTURE_DATE_ERROR = "Date cannot be in the future."
        const val RECEIPT_TOTAL_ADJUSTMENT_LABEL = "Receipt total fee"
        const val LOW_CONFIDENCE_THRESHOLD = 0.75
        val ITEM_FIELD_INDEX = Regex("""items\[(\d+)]""")
    }
}

sealed interface SaveReceiptReviewResult {
    data object Saved : SaveReceiptReviewResult

    data object MissingDraft : SaveReceiptReviewResult

    data class Invalid(
        val fieldErrors: Map<String, String>,
        val firstBlockingSection: ReceiptReviewSection? = null,
        val firstBlockingItemId: String? = null,
    ) : SaveReceiptReviewResult
}

private sealed interface ReceiptReviewBuildResult {
    data class Valid(val receipt: Receipt) : ReceiptReviewBuildResult

    data class Invalid(
        val fieldErrors: Map<String, String>,
        val firstBlockingSection: ReceiptReviewSection? = null,
    ) : ReceiptReviewBuildResult
}

data class ReceiptReviewValidationResult(
    val errors: Map<String, String>,
    val firstBlockingSection: ReceiptReviewSection?,
    val firstBlockingItemId: String? = null,
)
