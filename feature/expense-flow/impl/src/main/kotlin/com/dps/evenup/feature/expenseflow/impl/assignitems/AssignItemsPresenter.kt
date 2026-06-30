package com.dps.evenup.feature.expenseflow.impl.assignitems

import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.domain.expense.api.ItemAssignment
import com.dps.evenup.domain.expense.api.ItemAssignmentMode
import com.dps.evenup.domain.expense.api.ItemAssignmentValidationError
import com.dps.evenup.domain.expense.api.ItemParticipantShare
import com.dps.evenup.domain.expense.api.PercentageBasisPoints
import com.dps.evenup.domain.expense.api.ValidateItemAssignmentsUseCase
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Quantity
import com.dps.evenup.domain.receipt.api.CurrencyCode
import com.dps.evenup.domain.receipt.api.ReceiptItem
import com.dps.evenup.domain.receipt.api.ReceiptItemId
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

class AssignItemsPresenter(
    private val draftRepository: ExpenseDraftRepository,
    private val validateItemAssignments: ValidateItemAssignmentsUseCase,
) {
    suspend fun load(): AssignItemsUiState {
        val draft = draftRepository.getDraft() ?: return AssignItemsUiState(
            isLoading = false,
            missingDraft = true,
            submitError = "No expense draft was found.",
        )
        if (draft.participants.isEmpty()) {
            return AssignItemsUiState(
                isLoading = false,
                missingDraft = true,
                submitError = "Add people before assigning items.",
            )
        }

        return buildState(
            merchantName = draft.receipt.merchantName,
            dateLabel = draft.receipt.transactionDateLabel,
            currencyCode = draft.receipt.currencyCode,
            participants = draft.participants,
            items = draft.receipt.items,
            assignments = draft.itemAssignments,
            selectedParticipantIds = listOf(draft.participants.first().id.value),
            isLoading = false,
        )
    }

    suspend fun reduce(
        state: AssignItemsUiState,
        event: AssignItemsUiEvent,
    ): AssignItemsUiState {
        return when (event) {
            is AssignItemsUiEvent.ParticipantSelected -> state.toggleParticipant(event.participantId)
            is AssignItemsUiEvent.ItemTapped -> assignItemToSelectedParticipant(state, event.itemId)
            is AssignItemsUiEvent.ItemActionClick -> handleItemAction(state, event.itemId)
            is AssignItemsUiEvent.ItemSplitClick -> openSplitSheet(state, event.itemId)
            AssignItemsUiEvent.ApplyEqualSplitClick -> requestEqualSplit(state)
            AssignItemsUiEvent.ApplyEqualSplitDismissed -> state.copy(showEqualSplitConfirmation = false)
            AssignItemsUiEvent.ApplyEqualSplitConfirmed -> applyEqualSplitToAllItems(state)
            AssignItemsUiEvent.ClearAssignmentsClick -> {
                if (state.canClearAssignments) state.copy(showClearAssignmentsConfirmation = true) else state
            }
            AssignItemsUiEvent.ClearAssignmentsDismissed -> state.copy(showClearAssignmentsConfirmation = false)
            AssignItemsUiEvent.ClearAssignmentsConfirmed -> clearAssignments(state)
            AssignItemsUiEvent.ClearAssignmentsUndoClick -> undoClearAssignments(state)
            AssignItemsUiEvent.ClearAssignmentsUndoDismissed -> state.copy(clearUndoItems = null)
            AssignItemsUiEvent.SplitDismissed -> state.copy(splitSheet = null)
            is AssignItemsUiEvent.SplitModeSelected -> updateSplitMode(state, event.mode)
            is AssignItemsUiEvent.SplitQuantityChanged -> updateSplitQuantity(state, event.participantId, event.delta)
            is AssignItemsUiEvent.SplitSharedParticipantToggled -> toggleSharedParticipant(state, event.participantId)
            is AssignItemsUiEvent.SplitCustomAmountChanged -> updateCustomAmount(state, event.participantId, event.value)
            is AssignItemsUiEvent.SplitPercentageChanged -> updatePercentage(state, event.participantId, event.value)
            AssignItemsUiEvent.SplitSaveClick -> saveSplitSheet(state)
            AssignItemsUiEvent.BackClick,
            AssignItemsUiEvent.ContinueClick,
            -> state
        }
    }

    suspend fun saveDraft(state: AssignItemsUiState): SaveAssignItemsResult {
        val draft = draftRepository.getDraft() ?: return SaveAssignItemsResult.MissingDraft
        val assignments = state.toAssignments(draft.receipt.items)
        val validation = validateItemAssignments.validate(
            receiptItems = draft.receipt.items,
            participants = draft.participants,
            assignments = assignments,
        )
        if (!validation.isValid) {
            return SaveAssignItemsResult.Invalid(validation.errors.toFieldErrors())
        }

        draftRepository.saveDraft(draft.copy(itemAssignments = assignments, feeAllocations = emptyList()))
        return SaveAssignItemsResult.Saved
    }

    private suspend fun assignItemToSelectedParticipant(
        state: AssignItemsUiState,
        itemId: String,
    ): AssignItemsUiState {
        val selectedParticipantIds = state.selectedParticipantIds
        if (selectedParticipantIds.isEmpty()) {
            return state.copy(fieldErrors = mapOf("assignment" to "Select people, then assign items."))
        }
        val draft = draftRepository.getDraft()
            ?: return state.copy(missingDraft = true, submitError = "No expense draft was found.")
        val currentAssignments = state.toAssignments(draft.receipt.items)
        val item = draft.receipt.items.firstOrNull { receiptItem -> receiptItem.id.value == itemId }
            ?: return state
        val nextAssignment = item.assignmentForParticipants(selectedParticipantIds)
        val nextAssignments = currentAssignments.replaceAssignment(item.id, nextAssignment)

        return buildState(
            merchantName = draft.receipt.merchantName,
            dateLabel = draft.receipt.transactionDateLabel,
            currencyCode = draft.receipt.currencyCode,
            participants = draft.participants,
            items = draft.receipt.items,
            assignments = nextAssignments,
            selectedParticipantIds = selectedParticipantIds,
            isLoading = false,
        ).withAssignmentFeedback(
            previousState = state,
            itemId = itemId,
        )
    }

    private fun buildState(
        merchantName: String,
        dateLabel: String?,
        currencyCode: CurrencyCode,
        participants: List<Participant>,
        items: List<ReceiptItem>,
        assignments: List<ItemAssignment>,
        selectedParticipantIds: List<String>,
        isLoading: Boolean,
    ): AssignItemsUiState {
        val participantsById = participants.associateBy { participant -> participant.id }
        val selectedIds = selectedParticipantIds.filter { participantId ->
            participants.any { participant -> participant.id.value == participantId }
        }
        val participantUiStates = participants.mapIndexed { index, participant ->
            AssignItemsParticipantUiState(
                id = participant.id.value,
                name = participant.name,
                colorIndex = index,
                selected = participant.id.value in selectedIds,
            )
        }
        val assignedItemCount = items.count { item ->
            val assignment = assignments.firstOrNull { it.receiptItemId == item.id }
            assignment?.isCompleteFor(item) == true
        }
        return AssignItemsUiState(
            isLoading = isLoading,
            merchantName = merchantName,
            dateLabel = dateLabel,
            currencyCode = currencyCode.value,
            participants = participantUiStates,
            selectedParticipantIds = selectedIds,
            items = items.map { item ->
                val assignment = assignments.firstOrNull { it.receiptItemId == item.id }
                item.toUiState(assignment, participantsById, currencyCode).let { itemState ->
                    itemState.withAssignmentLabels(selectedIds)
                }
            },
            subtotalLabel = formatMoney(MoneyMinor(items.sumOf { item -> item.totalPrice.value }), currencyCode),
            progressLabel = "$assignedItemCount of ${items.size} items assigned",
            helperText = selectedIds.assignmentContextLabelFromDomain(participants),
            continueHelperText = when (val remaining = items.size - assignedItemCount) {
                0 -> "All items assigned"
                1 -> "Assign 1 item to continue"
                else -> "Assign $remaining items to continue"
            },
            canContinue = items.isNotEmpty() && assignedItemCount == items.size,
        )
    }

    private suspend fun applyEqualSplitToAllItems(state: AssignItemsUiState): AssignItemsUiState {
        val draft = draftRepository.getDraft()
            ?: return state.copy(missingDraft = true, submitError = "No expense draft was found.")
        if (draft.participants.size < 2) {
            return state.copy(fieldErrors = mapOf("assignment" to "Add at least two people to split equally."))
        }

        val participantIds = draft.participants.map { participant -> participant.id.value }
        val assignments = draft.receipt.items.map { item -> item.sharedEqualAssignment(participantIds) }
        val selectedParticipantIds = state.selectedParticipantIds.ifEmpty { listOf(draft.participants.first().id.value) }
        return buildState(
            merchantName = draft.receipt.merchantName,
            dateLabel = draft.receipt.transactionDateLabel,
            currencyCode = draft.receipt.currencyCode,
            participants = draft.participants,
            items = draft.receipt.items,
            assignments = assignments,
            selectedParticipantIds = selectedParticipantIds,
            isLoading = false,
        ).copy(
            fieldErrors = emptyMap(),
            submitError = null,
            splitSheet = null,
            showEqualSplitConfirmation = false,
            feedback = state.nextFeedback("All items split equally"),
        )
    }

    private suspend fun requestEqualSplit(state: AssignItemsUiState): AssignItemsUiState {
        val draft = draftRepository.getDraft()
            ?: return state.copy(missingDraft = true, submitError = "No expense draft was found.")
        return if (state.canClearAssignments || draft.itemAssignments.isNotEmpty()) {
            state.copy(showEqualSplitConfirmation = true)
        } else {
            applyEqualSplitToAllItems(state)
        }
    }

    private suspend fun clearAssignments(state: AssignItemsUiState): AssignItemsUiState {
        val draft = draftRepository.getDraft()
            ?: return state.copy(missingDraft = true, submitError = "No expense draft was found.")
        return buildState(
            merchantName = draft.receipt.merchantName,
            dateLabel = draft.receipt.transactionDateLabel,
            currencyCode = draft.receipt.currencyCode,
            participants = draft.participants,
            items = draft.receipt.items,
            assignments = emptyList(),
            selectedParticipantIds = state.selectedParticipantIds.ifEmpty { listOf(draft.participants.first().id.value) },
            isLoading = false,
        ).copy(
            fieldErrors = emptyMap(),
            submitError = null,
            splitSheet = null,
            showClearAssignmentsConfirmation = false,
            clearUndoItems = state.items,
            clearUndoSnackbarId = state.clearUndoSnackbarId + 1L,
            scrollToItemId = null,
            feedback = state.nextFeedback("Assignments cleared"),
        )
    }

    private fun undoClearAssignments(state: AssignItemsUiState): AssignItemsUiState {
        val restoredItems = state.clearUndoItems ?: return state
        return state.copy(
            items = restoredItems,
            clearUndoItems = null,
            fieldErrors = emptyMap(),
            submitError = null,
            scrollToItemId = restoredItems.firstOrNull { item -> item.assignmentState == AssignItemsItemState.Unassigned }?.id,
            feedback = state.nextFeedback("Assignments restored"),
        ).withDerivedAssignmentProgress()
    }

    private fun AssignItemsUiState.toAssignments(items: List<ReceiptItem>): List<ItemAssignment> {
        val itemsById = items.associateBy { item -> item.id.value }
        return this.items.mapNotNull { item ->
            val receiptItem = itemsById[item.id] ?: return@mapNotNull null
            if (item.assignees.isEmpty()) return@mapNotNull null
            when (item.splitMode) {
                AssignItemsSplitMode.Units -> {
                    val quantities = item.shares
                        .filter { share -> share.quantity > 0 }
                        .associate { share -> share.participantId to share.quantity }
                    val amounts = receiptItem.allocateAmountsByQuantity(quantities)
                    if (item.directFullAssignment && item.assignees.size == 1) {
                        ItemAssignment(
                            receiptItemId = receiptItem.id,
                            mode = ItemAssignmentMode.Full,
                            shares = listOf(
                                ItemParticipantShare(
                                    participantId = ParticipantId(item.assignees.single().participantId),
                                    amount = receiptItem.totalPrice,
                                ),
                            ),
                        )
                    } else {
                        ItemAssignment(
                            receiptItemId = receiptItem.id,
                            mode = ItemAssignmentMode.ByUnits,
                            shares = quantities.map { (participantId, quantity) ->
                                ItemParticipantShare(
                                    participantId = ParticipantId(participantId),
                                    amount = MoneyMinor(amounts.getValue(participantId)),
                                    quantity = Quantity(quantity),
                                )
                            },
                        )
                    }
                }
                AssignItemsSplitMode.SharedEqual -> {
                    val includedShares = item.shares.filter { share -> share.amountMinor > 0 }
                    ItemAssignment(
                        receiptItemId = receiptItem.id,
                        mode = ItemAssignmentMode.SharedEqual,
                        shares = includedShares.map { share ->
                            ItemParticipantShare(
                                participantId = ParticipantId(share.participantId),
                                amount = MoneyMinor(share.amountMinor),
                            )
                        },
                    )
                }
                AssignItemsSplitMode.CustomAmount -> {
                    ItemAssignment(
                        receiptItemId = receiptItem.id,
                        mode = ItemAssignmentMode.CustomAmount,
                        shares = item.shares.filter { share -> share.amountMinor > 0 }.map { share ->
                            ItemParticipantShare(
                                participantId = ParticipantId(share.participantId),
                                amount = MoneyMinor(share.amountMinor),
                            )
                        },
                    )
                }
                AssignItemsSplitMode.Percentage -> {
                    ItemAssignment(
                        receiptItemId = receiptItem.id,
                        mode = ItemAssignmentMode.Percentage,
                        shares = item.shares.filter { share -> share.percentageBasisPoints > 0 }.map { share ->
                            ItemParticipantShare(
                                participantId = ParticipantId(share.participantId),
                                amount = MoneyMinor(share.amountMinor),
                                percentage = PercentageBasisPoints(share.percentageBasisPoints),
                            )
                        },
                    )
                }
            }
        }
    }

    private fun ReceiptItem.toUiState(
        assignment: ItemAssignment?,
        participantsById: Map<ParticipantId, Participant>,
        currencyCode: CurrencyCode,
    ): AssignItemsReceiptItemUiState {
        val shares = assignment.toShares(participantsById, quantity.value)
        val assignees = shares.map { share ->
            AssignItemsAssigneeUiState(
                participantId = share.participantId,
                name = share.name,
                colorIndex = share.colorIndex,
                detail = when {
                    share.quantity > 0 -> "${share.name} ${share.quantity}x"
                    share.percentageBasisPoints > 0 -> "${share.name} ${share.percentageBasisPoints / 100}%"
                    else -> share.name
                },
                quantity = maxOf(share.quantity, 1),
            )
        }
        val assignmentState = when {
            assignees.isEmpty() -> AssignItemsItemState.Unassigned
            assignment?.isCompleteFor(this) == true -> AssignItemsItemState.Assigned
            else -> AssignItemsItemState.Partial
        }
        return AssignItemsReceiptItemUiState(
            id = id.value,
            name = name,
            quantity = quantity.value,
            totalMinor = totalPrice.value,
            quantityLabel = "${quantity.value}x",
            unitPriceLabel = "${formatMoney(unitPrice, currencyCode)} each",
            totalLabel = formatMoney(totalPrice, currencyCode),
            assignmentSummaryLabel = "",
            itemActionLabel = "",
            itemActionContentDescription = "",
            assignmentState = assignmentState,
            splitMode = assignment.toSplitMode(),
            directFullAssignment = assignment?.mode == ItemAssignmentMode.Full,
            shares = shares,
            assignees = assignees,
        )
    }

    private fun ItemAssignment?.toShares(
        participantsById: Map<ParticipantId, Participant>,
        itemQuantity: Int,
    ): List<AssignItemsShareUiState> {
        if (this == null) return emptyList()
        return shares.mapNotNull { share ->
            val participant = participantsById[share.participantId] ?: return@mapNotNull null
            AssignItemsShareUiState(
                participantId = participant.id.value,
                name = participant.name,
                colorIndex = participant.creationOrder,
                amountMinor = share.amount.value,
                quantity = share.quantity?.value ?: if (mode == ItemAssignmentMode.Full) itemQuantity else 1,
                percentageBasisPoints = share.percentage?.value ?: 0,
            )
        }
    }

    private fun ItemAssignment?.toSplitMode(): AssignItemsSplitMode {
        return when (this?.mode) {
            ItemAssignmentMode.SharedEqual -> AssignItemsSplitMode.SharedEqual
            ItemAssignmentMode.CustomAmount -> AssignItemsSplitMode.CustomAmount
            ItemAssignmentMode.Percentage -> AssignItemsSplitMode.Percentage
            ItemAssignmentMode.Full,
            ItemAssignmentMode.ByUnits,
            null,
            -> AssignItemsSplitMode.Units
        }
    }

    private fun ReceiptItem.fullAssignment(participantId: String): ItemAssignment {
        return ItemAssignment(
            receiptItemId = id,
            mode = ItemAssignmentMode.Full,
            shares = listOf(
                ItemParticipantShare(
                    participantId = ParticipantId(participantId),
                    amount = totalPrice,
                ),
            ),
        )
    }

    private fun ReceiptItem.sharedEqualAssignment(participantIds: List<String>): ItemAssignment {
        val amounts = allocateEqual(totalPrice.value, participantIds)
        return ItemAssignment(
            receiptItemId = id,
            mode = ItemAssignmentMode.SharedEqual,
            shares = participantIds.map { participantId ->
                ItemParticipantShare(
                    participantId = ParticipantId(participantId),
                    amount = MoneyMinor(amounts.getValue(participantId)),
                )
            },
        )
    }

    private fun ReceiptItem.assignmentForParticipants(participantIds: List<String>): ItemAssignment {
        return when {
            participantIds.size == 1 -> fullAssignment(participantIds.single())
            quantity.value == participantIds.size -> byUnitsAssignment(participantIds)
            else -> sharedEqualAssignment(participantIds)
        }
    }

    private fun ReceiptItem.byUnitsAssignment(participantIds: List<String>): ItemAssignment {
        val quantities = participantIds.associateWith { 1 }
        val amounts = allocateAmountsByQuantity(quantities)
        return ItemAssignment(
            receiptItemId = id,
            mode = ItemAssignmentMode.ByUnits,
            shares = participantIds.map { participantId ->
                ItemParticipantShare(
                    participantId = ParticipantId(participantId),
                    amount = MoneyMinor(amounts.getValue(participantId)),
                    quantity = Quantity(1),
                )
            },
        )
    }

    private fun List<ItemAssignment>.replaceAssignment(
        receiptItemId: ReceiptItemId,
        nextAssignment: ItemAssignment?,
    ): List<ItemAssignment> {
        return filterNot { assignment -> assignment.receiptItemId == receiptItemId }.let { remainingAssignments ->
            if (nextAssignment == null) {
                remainingAssignments
            } else {
                remainingAssignments + nextAssignment
            }
        }
    }

    private fun List<ItemAssignment>.replaceAssignment(nextAssignment: ItemAssignment): List<ItemAssignment> {
        return filterNot { assignment -> assignment.receiptItemId == nextAssignment.receiptItemId } + nextAssignment
    }

    private fun ItemAssignment.isCompleteFor(item: ReceiptItem): Boolean {
        return when (mode) {
            ItemAssignmentMode.Full -> shares.size == 1 && shares.sumOf { share -> share.amount.value } == item.totalPrice.value
            ItemAssignmentMode.ByUnits -> {
                shares.sumOf { share -> share.quantity?.value ?: 0 } == item.quantity.value &&
                    shares.sumOf { share -> share.amount.value } == item.totalPrice.value
            }
            ItemAssignmentMode.SharedEqual,
            ItemAssignmentMode.CustomAmount,
            ItemAssignmentMode.Percentage,
            -> shares.sumOf { share -> share.amount.value } == item.totalPrice.value
        }
    }

    private fun openSplitSheet(
        state: AssignItemsUiState,
        itemId: String,
    ): AssignItemsUiState {
        val item = state.items.firstOrNull { receiptItem -> receiptItem.id == itemId } ?: return state
        return state.copy(
            splitSheet = item.toSplitSheet(
                participants = state.participants,
                currencyCode = state.currencyCode,
                selectedParticipantIds = state.selectedParticipantIds,
            ),
        )
    }

    private fun updateSplitMode(
        state: AssignItemsUiState,
        mode: AssignItemsSplitMode,
    ): AssignItemsUiState {
        val sheet = state.splitSheet ?: return state
        val item = state.items.firstOrNull { receiptItem -> receiptItem.id == sheet.itemId } ?: return state
        return state.copy(
            splitSheet = item.toSplitSheet(
                participants = state.participants,
                currencyCode = state.currencyCode,
                selectedParticipantIds = state.selectedParticipantIds,
                modeOverride = mode,
            ),
        )
    }

    private fun updateSplitQuantity(
        state: AssignItemsUiState,
        participantId: String,
        delta: Int,
    ): AssignItemsUiState {
        val sheet = state.splitSheet ?: return state
        val currentTotal = sheet.rows.sumOf { row -> row.quantity }
        val currentRowQuantity = sheet.rows.firstOrNull { row -> row.participantId == participantId }?.quantity ?: return state
        val nextQuantity = currentRowQuantity + delta
        if (nextQuantity < 0 || (delta > 0 && currentTotal >= sheet.quantity)) {
            return state
        }
        val rows = sheet.rows.map { row ->
            if (row.participantId == participantId) {
                row.copy(quantity = nextQuantity)
            } else {
                row
            }
        }
        return state.copy(splitSheet = sheet.copy(rows = rows).validated())
    }

    private fun toggleSharedParticipant(
        state: AssignItemsUiState,
        participantId: String,
    ): AssignItemsUiState {
        val sheet = state.splitSheet ?: return state
        val rows = sheet.rows.map { row ->
            if (row.participantId == participantId) row.copy(included = !row.included) else row
        }
        return state.copy(splitSheet = sheet.copy(rows = rows).validated())
    }

    private fun updateCustomAmount(
        state: AssignItemsUiState,
        participantId: String,
        value: String,
    ): AssignItemsUiState {
        val sheet = state.splitSheet ?: return state
        val changedRows = sheet.rows.map { row ->
            if (row.participantId == participantId) {
                row.copy(
                    amount = value,
                    amountGenerated = false,
                    amountEdited = true,
                    included = row.included || value.isNotBlank(),
                )
            } else {
                row
            }
        }
        val rows = changedRows.autoFillSingleRemainingAmount(sheet.totalMinor)
        return state.copy(splitSheet = sheet.copy(rows = rows).validated())
    }

    private fun updatePercentage(
        state: AssignItemsUiState,
        participantId: String,
        value: String,
    ): AssignItemsUiState {
        val sheet = state.splitSheet ?: return state
        val changedRows = sheet.rows.map { row ->
            if (row.participantId == participantId) {
                row.copy(
                    percentage = value,
                    percentageGenerated = false,
                    percentageEdited = true,
                    included = true,
                )
            } else {
                row
            }
        }
        val rows = changedRows.autoFillSingleRemainingPercentage()
        return state.copy(splitSheet = sheet.copy(rows = rows).validated())
    }

    private fun saveSplitSheet(state: AssignItemsUiState): AssignItemsUiState {
        val sheet = state.splitSheet?.validated() ?: return state
        if (sheet.error != null) {
            return state.copy(splitSheet = sheet)
        }
        val shares = sheet.toShares()
        val items = state.items.map { item ->
            if (item.id == sheet.itemId) {
                val assignees = shares.map { share ->
                    AssignItemsAssigneeUiState(
                        participantId = share.participantId,
                        name = share.name,
                        colorIndex = share.colorIndex,
                        detail = when {
                            share.quantity > 0 -> "${share.name} ${share.quantity}x"
                            share.percentageBasisPoints > 0 -> "${share.name} ${share.percentageBasisPoints / 100}%"
                            else -> share.name
                        },
                        quantity = maxOf(share.quantity, 1),
                    )
                }
                item.copy(
                    assignmentState = AssignItemsItemState.Assigned,
                    splitMode = sheet.mode,
                    directFullAssignment = false,
                    shares = shares,
                    assignees = assignees,
                ).withAssignmentLabels(state.selectedParticipantIds)
            } else {
                item
            }
        }
        return state.copy(
            items = items,
            splitSheet = null,
            fieldErrors = emptyMap(),
            submitError = null,
        ).withDerivedAssignmentProgress()
            .withAssignmentFeedback(
                previousState = state,
                itemId = sheet.itemId,
            )
    }

    private fun AssignItemsReceiptItemUiState.toSplitSheet(
        participants: List<AssignItemsParticipantUiState>,
        currencyCode: String,
        selectedParticipantIds: List<String>,
        modeOverride: AssignItemsSplitMode? = null,
    ): AssignItemsSplitSheetUiState {
        val selectedIds = selectedParticipantIds.filter { selectedId ->
            participants.any { participant -> participant.id == selectedId }
        }
        val existingShareIds = shares.map { share -> share.participantId }.toSet()
        val prefillIds = when {
            existingShareIds.isNotEmpty() -> participants.map { participant -> participant.id }.filter { participantId ->
                participantId in existingShareIds
            }
            assignmentState == AssignItemsItemState.Unassigned && selectedIds.isNotEmpty() -> selectedIds
            selectedIds.isNotEmpty() -> selectedIds
            else -> emptyList()
        }
        val targetMode = modeOverride ?: when {
            assignmentState != AssignItemsItemState.Unassigned -> splitMode
            prefillIds.size > 1 && prefillIds.size != quantity -> AssignItemsSplitMode.SharedEqual
            else -> AssignItemsSplitMode.Units
        }
        val unitPrefill = prefillIds.toUnitPrefill(quantity)
        val percentagePrefill = prefillIds.toEqualPercentages()
        val rows = participants.map { participant ->
            val share = shares.firstOrNull { existingShare -> existingShare.participantId == participant.id }
            val prefilledAmount = if (prefillIds.size == 1 && participant.id in prefillIds) {
                totalMinor
            } else {
                0L
            }
            val shareAmountMinor = share?.amountMinor?.takeIf { it > 0 }
            val generatedAmountMinor = prefilledAmount.takeIf { it > 0 && shareAmountMinor == null }
            val sharePercentageBasisPoints = share?.percentageBasisPoints?.takeIf { it > 0 }
            val generatedPercentageBasisPoints = percentagePrefill[participant.id]
                ?.takeIf { sharePercentageBasisPoints == null }
            AssignItemsSplitRowUiState(
                participantId = participant.id,
                name = participant.name,
                colorIndex = participant.colorIndex,
                quantity = when (targetMode) {
                    AssignItemsSplitMode.Units -> share?.quantity ?: unitPrefill[participant.id] ?: 0
                    else -> 0
                },
                amount = when (targetMode) {
                    AssignItemsSplitMode.CustomAmount -> shareAmountMinor?.let(::formatMoneyInput)
                        ?: generatedAmountMinor?.let(::formatMoneyInput)
                        ?: ""
                    else -> ""
                },
                amountGenerated = targetMode == AssignItemsSplitMode.CustomAmount && generatedAmountMinor != null,
                amountEdited = targetMode == AssignItemsSplitMode.CustomAmount && shareAmountMinor != null,
                percentage = when (targetMode) {
                    AssignItemsSplitMode.Percentage -> (sharePercentageBasisPoints ?: generatedPercentageBasisPoints)
                        ?.let(::formatBasisPointsInput)
                        .orEmpty()
                    else -> ""
                },
                percentageGenerated = targetMode == AssignItemsSplitMode.Percentage && generatedPercentageBasisPoints != null,
                percentageEdited = targetMode == AssignItemsSplitMode.Percentage && sharePercentageBasisPoints != null,
                included = when (targetMode) {
                    AssignItemsSplitMode.SharedEqual -> share != null || participant.id in prefillIds
                    AssignItemsSplitMode.CustomAmount,
                    AssignItemsSplitMode.Percentage,
                    -> participant.id in prefillIds || share != null
                    else -> false
                },
            )
        }
        return AssignItemsSplitSheetUiState(
            itemId = id,
            itemName = name,
            quantity = quantity,
            unitPriceLabel = unitPriceLabel,
            totalLabel = totalLabel,
            currencyCode = currencyCode,
            currencySymbol = currencySymbol(currencyCode),
            totalMinor = totalMinor,
            mode = targetMode,
            rows = rows,
            statusLabel = "",
        ).validated()
    }

    private fun AssignItemsSplitSheetUiState.validated(): AssignItemsSplitSheetUiState {
        return when (mode) {
            AssignItemsSplitMode.Units -> {
                val assignedUnits = rows.sumOf { row -> row.quantity }
                val error = when {
                    assignedUnits > quantity -> "Over-assigned by ${assignedUnits - quantity} ${if (assignedUnits - quantity == 1) "unit" else "units"}."
                    assignedUnits != quantity -> "Assigned units must equal $quantity."
                    else -> null
                }
                copy(
                    error = error,
                    statusLabel = when {
                        assignedUnits > quantity -> "Over-assigned by ${assignedUnits - quantity} ${if (assignedUnits - quantity == 1) "unit" else "units"}"
                        quantity - assignedUnits == 0 -> "All units assigned"
                        quantity - assignedUnits == 1 -> "Assign 1 more unit"
                        else -> "Assign ${quantity - assignedUnits} more units"
                    },
                )
            }
            AssignItemsSplitMode.SharedEqual -> {
                val includedCount = rows.count { row -> row.included }
                val error = when {
                    includedCount < 2 -> "Choose at least two people for a shared split."
                    else -> null
                }
                val includedIds = rows.filter { row -> row.included }.map { row -> row.participantId }
                val amounts = allocateEqual(totalMinor, includedIds)
                val updatedRows = rows.map { row ->
                    row.copy(amountLabel = amounts[row.participantId]?.let { amount -> formatMoney(MoneyMinor(amount), currencyCode) }.orEmpty())
                }
                val includedAmounts = includedIds.mapNotNull { participantId -> amounts[participantId] }
                val perPerson = if (includedAmounts.isNotEmpty() && includedAmounts.distinct().size == 1) {
                    " · ${formatMoney(MoneyMinor(includedAmounts.first()), currencyCode)} each"
                } else if (includedAmounts.isNotEmpty()) {
                    " · amounts shown"
                } else {
                    ""
                }
                copy(
                    rows = updatedRows,
                    error = error,
                    statusLabel = if (includedCount < 2) "Select at least 2 people" else "$includedCount people selected$perPerson",
                )
            }
            AssignItemsSplitMode.CustomAmount -> {
                val amounts = rows.map { row -> parseMoneyInput(row.amount) }
                val hasInvalid = rows.any { row -> row.amount.isNotBlank() && parseMoneyInput(row.amount) == null }
                val assignedMinor = amounts.filterNotNull().sum()
                val error = when {
                    hasInvalid -> "Enter valid custom amounts."
                    assignedMinor < totalMinor -> "Assign the remaining ${formatMoney(MoneyMinor(totalMinor - assignedMinor), currencyCode)}."
                    assignedMinor > totalMinor -> "Reduce assigned amount by ${formatMoney(MoneyMinor(assignedMinor - totalMinor), currencyCode)}."
                    else -> null
                }
                val remainingLabel = when {
                    assignedMinor <= totalMinor -> "${formatMoney(MoneyMinor(totalMinor - assignedMinor), currencyCode)} remaining"
                    else -> "Over by ${formatMoney(MoneyMinor(assignedMinor - totalMinor), currencyCode)}"
                }
                copy(
                    error = error,
                    statusLabel = remainingLabel,
                )
            }
            AssignItemsSplitMode.Percentage -> {
                val percentages = rows.map { row -> parsePercentageBasisPoints(row.percentage) }
                val hasInvalid = rows.any { row -> row.percentage.isNotBlank() && parsePercentageBasisPoints(row.percentage) == null }
                val assignedBasisPoints = percentages.filterNotNull().sum()
                val error = when {
                    hasInvalid -> "Enter valid percentages."
                    assignedBasisPoints < PercentageBasisPoints.MAX_BASIS_POINTS ->
                        "Assign the remaining ${(PercentageBasisPoints.MAX_BASIS_POINTS - assignedBasisPoints) / 100}%."
                    assignedBasisPoints > PercentageBasisPoints.MAX_BASIS_POINTS ->
                        "Reduce assigned percentage by ${(assignedBasisPoints - PercentageBasisPoints.MAX_BASIS_POINTS) / 100}%."
                    else -> null
                }
                val amountLabels = percentageAmountLabels(totalMinor, currencyCode, rows, percentages)
                copy(
                    rows = rows.mapIndexed { index, row ->
                        row.copy(amountLabel = amountLabels[index])
                    },
                    error = error,
                    statusLabel = when {
                        assignedBasisPoints <= PercentageBasisPoints.MAX_BASIS_POINTS ->
                            "${formatBasisPointsDisplay(PercentageBasisPoints.MAX_BASIS_POINTS - assignedBasisPoints)} remaining"
                        else -> "Over by ${formatBasisPointsDisplay(assignedBasisPoints - PercentageBasisPoints.MAX_BASIS_POINTS)}"
                    },
                )
            }
        }
    }

    private fun AssignItemsSplitSheetUiState.toShares(): List<AssignItemsShareUiState> {
        return when (mode) {
            AssignItemsSplitMode.Units -> {
                val quantities = rows.filter { row -> row.quantity > 0 }
                    .associate { row -> row.participantId to row.quantity }
                val amounts = allocateAmountsByQuantity(totalMinor, quantity, quantities)
                rows.filter { row -> row.quantity > 0 }.map { row ->
                    AssignItemsShareUiState(
                        participantId = row.participantId,
                        name = row.name,
                        colorIndex = row.colorIndex,
                        amountMinor = amounts.getValue(row.participantId),
                        quantity = row.quantity,
                    )
                }
            }
            AssignItemsSplitMode.SharedEqual -> {
                val includedRows = rows.filter { row -> row.included }
                val amounts = allocateEqual(totalMinor, includedRows.map { row -> row.participantId })
                includedRows.map { row ->
                    AssignItemsShareUiState(
                        participantId = row.participantId,
                        name = row.name,
                        colorIndex = row.colorIndex,
                        amountMinor = amounts.getValue(row.participantId),
                    )
                }
            }
            AssignItemsSplitMode.CustomAmount -> rows.mapNotNull { row ->
                val amount = parseMoneyInput(row.amount) ?: return@mapNotNull null
                if (amount <= 0) return@mapNotNull null
                AssignItemsShareUiState(
                    participantId = row.participantId,
                    name = row.name,
                    colorIndex = row.colorIndex,
                    amountMinor = amount,
                )
            }
            AssignItemsSplitMode.Percentage -> {
                val percentages = rows.mapNotNull { row ->
                    val basisPoints = parsePercentageBasisPoints(row.percentage) ?: return@mapNotNull null
                    if (basisPoints <= 0) return@mapNotNull null
                    row to basisPoints
                }
                val amounts = allocateByBasisPoints(totalMinor, percentages.associate { (row, basisPoints) ->
                    row.participantId to basisPoints
                })
                percentages.map { (row, basisPoints) ->
                    AssignItemsShareUiState(
                        participantId = row.participantId,
                        name = row.name,
                        colorIndex = row.colorIndex,
                        amountMinor = amounts.getValue(row.participantId),
                        percentageBasisPoints = basisPoints,
                    )
                }
            }
        }
    }

    private fun ReceiptItem.allocateAmountsByQuantity(quantities: Map<String, Int>): Map<String, Long> {
        return allocateAmountsByQuantity(totalPrice.value, quantity.value, quantities)
    }

    private fun allocateAmountsByQuantity(
        totalMinor: Long,
        totalQuantity: Int,
        quantities: Map<String, Int>,
    ): Map<String, Long> {
        val baseUnitAmount = totalMinor / totalQuantity
        var remainder = totalMinor - (baseUnitAmount * totalQuantity)
        return quantities.mapValues { (_, assignedQuantity) ->
            var amount = baseUnitAmount * assignedQuantity
            val extra = minOf(remainder, assignedQuantity.toLong())
            amount += extra
            remainder -= extra
            amount
        }
    }

    private fun allocateEqual(totalMinor: Long, participantIds: List<String>): Map<String, Long> {
        if (participantIds.isEmpty()) return emptyMap()
        val baseAmount = totalMinor / participantIds.size
        var remainder = totalMinor - (baseAmount * participantIds.size)
        return participantIds.associateWith {
            val extra = if (remainder > 0) 1L else 0L
            remainder -= extra
            baseAmount + extra
        }
    }

    private fun allocateByBasisPoints(totalMinor: Long, percentages: Map<String, Int>): Map<String, Long> {
        var assigned = 0L
        val entries = percentages.entries.toList()
        return entries.mapIndexed { index, entry ->
            val amount = if (index == entries.lastIndex) {
                totalMinor - assigned
            } else {
                (totalMinor * entry.value) / PercentageBasisPoints.MAX_BASIS_POINTS
            }
            assigned += amount
            entry.key to amount
        }.toMap()
    }

    private fun List<String>.toUnitPrefill(quantity: Int): Map<String, Int> {
        return when {
            isEmpty() -> emptyMap()
            size == 1 -> mapOf(single() to quantity)
            size == quantity -> associateWith { 1 }
            else -> emptyMap()
        }
    }

    private fun List<String>.toEqualPercentages(): Map<String, Int> {
        if (isEmpty()) return emptyMap()
        if (size <= 100) {
            val baseWholePercent = 100 / size
            var remainderWholePercent = 100 - (baseWholePercent * size)
            return associateWith {
                val extra = if (remainderWholePercent > 0) 1 else 0
                remainderWholePercent -= extra
                (baseWholePercent + extra) * 100
            }
        }
        val baseBasisPoints = PercentageBasisPoints.MAX_BASIS_POINTS / size
        var remainderBasisPoints = PercentageBasisPoints.MAX_BASIS_POINTS - (baseBasisPoints * size)
        return associateWith {
            val extra = if (remainderBasisPoints > 0) 1 else 0
            remainderBasisPoints -= extra
            baseBasisPoints + extra
        }
    }

    private fun List<AssignItemsSplitRowUiState>.autoFillSingleRemainingAmount(
        totalMinor: Long,
    ): List<AssignItemsSplitRowUiState> {
        val activeRows = filter { row -> row.included || row.amount.isNotBlank() }
        val targetRows = activeRows.filter { row ->
            !row.amountEdited && (row.amount.isBlank() || row.amountGenerated)
        }
        if (targetRows.size != 1) return this
        val assignedMinor = activeRows
            .filterNot { row -> row.participantId == targetRows.single().participantId }
            .sumOf { row -> parseMoneyInput(row.amount) ?: return this }
        val remainingMinor = totalMinor - assignedMinor
        if (remainingMinor < 0) return this
        val targetParticipantId = targetRows.single().participantId
        return map { row ->
            if (row.participantId == targetParticipantId) {
                row.copy(
                    amount = formatMoneyInput(remainingMinor),
                    amountGenerated = true,
                    amountEdited = false,
                    included = true,
                )
            } else {
                row
            }
        }
    }

    private fun List<AssignItemsSplitRowUiState>.autoFillSingleRemainingPercentage(): List<AssignItemsSplitRowUiState> {
        val activeRows = filter { row -> row.included || row.percentage.isNotBlank() }
        val targetRows = activeRows.filter { row ->
            !row.percentageEdited && (row.percentage.isBlank() || row.percentageGenerated)
        }
        if (targetRows.size != 1) return this
        val assignedBasisPoints = activeRows
            .filterNot { row -> row.participantId == targetRows.single().participantId }
            .sumOf { row -> parsePercentageBasisPoints(row.percentage) ?: return this }
        val remainingBasisPoints = PercentageBasisPoints.MAX_BASIS_POINTS - assignedBasisPoints
        if (remainingBasisPoints < 0) return this
        val targetParticipantId = targetRows.single().participantId
        return map { row ->
            if (row.participantId == targetParticipantId) {
                row.copy(
                    percentage = formatBasisPointsInput(remainingBasisPoints),
                    percentageGenerated = true,
                    percentageEdited = false,
                    included = true,
                )
            } else {
                row
            }
        }
    }

    private fun percentageAmountLabels(
        totalMinor: Long,
        currencyCode: String,
        rows: List<AssignItemsSplitRowUiState>,
        percentages: List<Int?>,
    ): List<String> {
        val validPercentages = rows.zip(percentages).mapNotNull { (row, basisPoints) ->
            basisPoints?.takeIf { it > 0 }?.let { row.participantId to it }
        }
        val amounts = if (validPercentages.sumOf { (_, basisPoints) -> basisPoints } == PercentageBasisPoints.MAX_BASIS_POINTS) {
            allocateByBasisPoints(totalMinor, validPercentages.toMap())
        } else {
            validPercentages.associate { (participantId, basisPoints) ->
                participantId to ((totalMinor * basisPoints) / PercentageBasisPoints.MAX_BASIS_POINTS)
            }
        }
        return rows.map { row ->
            amounts[row.participantId]?.let { amount -> formatMoney(MoneyMinor(amount), currencyCode) }.orEmpty()
        }
    }

    private fun Set<ItemAssignmentValidationError>.toFieldErrors(): Map<String, String> = associate { error ->
        val message = when (error) {
            ItemAssignmentValidationError.MissingItemAssignment -> "Assign every item before continuing."
            ItemAssignmentValidationError.UnknownReceiptItem -> "An assigned item no longer exists."
            ItemAssignmentValidationError.UnknownParticipant -> "An assignment references a missing person."
            ItemAssignmentValidationError.EmptyShares -> "Each assigned item needs at least one person."
            ItemAssignmentValidationError.AmountTotalMismatch -> "Assigned amounts must match item totals."
            ItemAssignmentValidationError.QuantityTotalMismatch -> "Assigned units must match item quantities."
            ItemAssignmentValidationError.PercentageTotalMismatch -> "Assigned percentages must add to 100%."
            ItemAssignmentValidationError.NegativeShareAmount -> "Assigned amounts cannot be negative."
            ItemAssignmentValidationError.InvalidModeShape -> "One or more item assignments are invalid."
        }
        "assignment" to message
    }

    private fun AssignItemsUiState.toggleParticipant(participantId: String): AssignItemsUiState {
        val participantName = participants.firstOrNull { participant -> participant.id == participantId }?.name
        val nextSelectedIds = if (participantId in selectedParticipantIds) {
            selectedParticipantIds - participantId
        } else {
            selectedParticipantIds + participantId
        }
        val feedbackMessage = participantName?.let { name ->
            if (participantId in nextSelectedIds) "$name selected" else "$name unselected"
        }
        return copy(
            selectedParticipantIds = nextSelectedIds,
            participants = participants.map { participant ->
                participant.copy(selected = participant.id in nextSelectedIds)
            },
            helperText = nextSelectedIds.assignmentContextLabelFromUi(participants),
            items = items.map { item ->
                item.withAssignmentLabels(nextSelectedIds)
            },
            fieldErrors = emptyMap(),
            submitError = null,
            feedback = feedbackMessage?.let { message -> nextFeedback(message) } ?: feedback,
        )
    }

    private fun AssignItemsUiState.withDerivedAssignmentProgress(): AssignItemsUiState {
        val assignedItemCount = items.count { item -> item.assignmentState == AssignItemsItemState.Assigned }
        return copy(
            progressLabel = "$assignedItemCount of ${items.size} items assigned",
            continueHelperText = when (val remaining = items.size - assignedItemCount) {
                0 -> if (items.isEmpty()) "Add receipt items to continue" else "All items assigned"
                1 -> "Assign 1 item to continue"
                else -> "Assign $remaining items to continue"
            },
            canContinue = items.isNotEmpty() && assignedItemCount == items.size,
        )
    }

    private fun AssignItemsUiState.withAssignmentFeedback(
        previousState: AssignItemsUiState,
        itemId: String,
    ): AssignItemsUiState {
        val item = items.firstOrNull { receiptItem -> receiptItem.id == itemId } ?: return this
        val message = if (!previousState.canContinue && canContinue) {
            "All items assigned"
        } else {
            item.assignmentAnnouncement()
        }
        return copy(
            feedback = previousState.nextFeedback(message),
            scrollToItemId = nextUnassignedItemId(afterItemId = itemId),
        )
    }

    private fun AssignItemsUiState.nextUnassignedItemId(afterItemId: String): String? {
        if (canContinue) return null
        val assignedIndex = items.indexOfFirst { item -> item.id == afterItemId }
        val searchOrder = if (assignedIndex >= 0) {
            items.drop(assignedIndex + 1) + items.take(assignedIndex + 1)
        } else {
            items
        }
        return searchOrder.firstOrNull { item -> item.assignmentState != AssignItemsItemState.Assigned }?.id
    }

    private fun AssignItemsUiState.withFeedback(message: String): AssignItemsUiState {
        return copy(feedback = nextFeedback(message))
    }

    private fun AssignItemsUiState.nextFeedback(message: String): AssignItemsFeedbackUiState {
        return AssignItemsFeedbackUiState(
            id = (feedback?.id ?: 0L) + 1L,
            message = message,
        )
    }

    private fun AssignItemsReceiptItemUiState.assignmentAnnouncement(): String {
        return when {
            assignees.isEmpty() -> "$name unassigned"
            assignees.size == 1 -> "$name assigned to ${assignees.single().name}"
            else -> "$name split between ${assignees.joinToString(" and ") { assignee -> assignee.name }}"
        }
    }

    private fun List<String>.assignmentContextLabelFromDomain(participants: List<Participant>): String {
        val names = mapNotNull { participantId ->
            participants.firstOrNull { participant -> participant.id.value == participantId }?.name
        }
        return names.assignmentContextLabel()
    }

    private fun List<String>.assignmentContextLabelFromUi(participants: List<AssignItemsParticipantUiState>): String {
        val names = mapNotNull { participantId ->
            participants.firstOrNull { participant -> participant.id == participantId }?.name
        }
        return names.assignmentContextLabel()
    }

    private fun List<String>.assignmentContextLabel(): String {
        return when (size) {
            0 -> "Select people, then assign items"
            1 -> "Tap items to assign to ${single()}"
            2 -> "Assigning to ${this[0]} and ${this[1]}"
            else -> "Assigning to $size people"
        }
    }

    private suspend fun handleItemAction(
        state: AssignItemsUiState,
        itemId: String,
    ): AssignItemsUiState {
        val item = state.items.firstOrNull { receiptItem -> receiptItem.id == itemId } ?: return state
        return if (item.assignmentState == AssignItemsItemState.Unassigned) {
            assignItemToSelectedParticipant(state, itemId)
        } else {
            openSplitSheet(state, itemId)
        }
    }

    private fun AssignItemsReceiptItemUiState.withAssignmentLabels(
        selectedParticipantIds: List<String>,
    ): AssignItemsReceiptItemUiState {
        val summaryLabel = assignmentSummaryLabel()
        val actionLabel = if (assignmentState == AssignItemsItemState.Unassigned) "Assign" else "Edit"
        return copy(
            assignmentSummaryLabel = summaryLabel,
            itemActionLabel = actionLabel,
            itemActionContentDescription = when {
                assignmentState != AssignItemsItemState.Unassigned -> "Edit assignment for $name"
                selectedParticipantIds.isEmpty() -> "Select people before assigning $name"
                else -> "Assign $name"
            },
        )
    }

    private fun AssignItemsReceiptItemUiState.assignmentSummaryLabel(): String {
        if (assignmentState != AssignItemsItemState.Unassigned) {
            return when {
                assignees.size >= 3 && splitMode == AssignItemsSplitMode.Units -> "${assignees.size} people · Units"
                assignees.size >= 3 && splitMode == AssignItemsSplitMode.SharedEqual -> "${assignees.size} people · Equal"
                assignees.size >= 3 && splitMode == AssignItemsSplitMode.CustomAmount -> "${assignees.size} people · Custom"
                assignees.size >= 3 && splitMode == AssignItemsSplitMode.Percentage -> "${assignees.size} people · Percent"
                splitMode == AssignItemsSplitMode.Units && assignees.size > 1 -> {
                    assignees.joinToString(" · ") { assignee -> "${assignee.name} ${assignee.quantity}x" }
                }
                splitMode == AssignItemsSplitMode.SharedEqual && assignees.size > 1 -> {
                    "${assignees.size} people · Equal"
                }
                splitMode == AssignItemsSplitMode.CustomAmount -> "${assignees.size} ${if (assignees.size == 1) "person" else "people"} · Custom"
                splitMode == AssignItemsSplitMode.Percentage -> "${assignees.size} ${if (assignees.size == 1) "person" else "people"} · Percent"
                assignees.size == 1 -> assignees.single().name
                else -> "${assignees.size} people"
            }
        }
        return "Unassigned"
    }

    private fun formatMoney(
        value: MoneyMinor,
        currencyCode: CurrencyCode,
    ): String {
        return "${currencySymbol(currencyCode.value)}${BigDecimal(value.value).movePointLeft(2).setScale(2).toPlainString()}"
    }

    private fun formatMoney(
        value: MoneyMinor,
        currencyCode: String,
    ): String {
        return "${currencySymbol(currencyCode)}${BigDecimal(value.value).movePointLeft(2).setScale(2).toPlainString()}"
    }

    private fun currencySymbol(currencyCode: String): String {
        return runCatching {
            Currency.getInstance(currencyCode.uppercase()).getSymbol(Locale.US)
        }.getOrDefault("${currencyCode.uppercase()} ")
    }

    private fun formatMoneyInput(value: Long): String {
        return BigDecimal(value).movePointLeft(2).setScale(2).toPlainString()
    }

    private fun formatBasisPointsInput(value: Int): String {
        return BigDecimal(value)
            .movePointLeft(2)
            .stripTrailingZeros()
            .toPlainString()
    }

    private fun formatBasisPointsDisplay(value: Int): String {
        return "${formatBasisPointsInput(value)}%"
    }

    private fun parseMoneyInput(value: String): Long? {
        val normalized = value.trim()
            .replace(",", "")
            .filter { char -> char.isDigit() || char == '.' }
        if (normalized.isBlank()) return 0
        return try {
            BigDecimal(normalized)
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun parsePercentageBasisPoints(value: String): Int? {
        if (value.trim().isBlank()) return 0
        return try {
            BigDecimal(value.trim().removeSuffix("%"))
                .multiply(BigDecimal(100))
                .setScale(0, RoundingMode.UNNECESSARY)
                .intValueExact()
                .takeIf { basisPoints -> basisPoints >= 0 }
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }
}

sealed interface SaveAssignItemsResult {
    data object Saved : SaveAssignItemsResult

    data object MissingDraft : SaveAssignItemsResult

    data class Invalid(val fieldErrors: Map<String, String>) : SaveAssignItemsResult
}
