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
import com.dps.evenup.domain.receipt.api.ReceiptItem
import com.dps.evenup.domain.receipt.api.ReceiptItemId
import java.math.BigDecimal
import java.math.RoundingMode

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

        val selectedParticipantId = draft.participants.first().id.value
        return buildState(
            merchantName = draft.receipt.merchantName,
            dateLabel = draft.receipt.transactionDateLabel,
            participants = draft.participants,
            items = draft.receipt.items,
            assignments = draft.itemAssignments,
            selectedParticipantId = selectedParticipantId,
            isLoading = false,
        )
    }

    suspend fun reduce(
        state: AssignItemsUiState,
        event: AssignItemsUiEvent,
    ): AssignItemsUiState {
        return when (event) {
            is AssignItemsUiEvent.ParticipantSelected -> state.copy(
                selectedParticipantId = event.participantId,
                participants = state.participants.map { participant ->
                    participant.copy(selected = participant.id == event.participantId)
                },
                fieldErrors = emptyMap(),
                submitError = null,
            )
            is AssignItemsUiEvent.ItemTapped -> assignItemToSelectedParticipant(state, event.itemId)
            is AssignItemsUiEvent.ItemSplitClick -> openSplitSheet(state, event.itemId)
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
        val selectedParticipantId = state.selectedParticipantId
            ?: return state.copy(fieldErrors = mapOf("assignment" to "Pick a person first."))
        val draft = draftRepository.getDraft()
            ?: return state.copy(missingDraft = true, submitError = "No expense draft was found.")
        val currentAssignments = state.toAssignments(draft.receipt.items)
        val item = draft.receipt.items.firstOrNull { receiptItem -> receiptItem.id.value == itemId }
            ?: return state

        val nextAssignments = if (item.quantity.value == 1) {
            currentAssignments.replaceAssignment(item.fullAssignment(selectedParticipantId))
        } else {
            val existing = currentAssignments.firstOrNull { assignment -> assignment.receiptItemId == item.id }
            currentAssignments.replaceAssignment(item.nextUnitAssignment(existing, selectedParticipantId))
        }

        return buildState(
            merchantName = draft.receipt.merchantName,
            dateLabel = draft.receipt.transactionDateLabel,
            participants = draft.participants,
            items = draft.receipt.items,
            assignments = nextAssignments,
            selectedParticipantId = selectedParticipantId,
            isLoading = false,
        )
    }

    private fun buildState(
        merchantName: String,
        dateLabel: String?,
        participants: List<Participant>,
        items: List<ReceiptItem>,
        assignments: List<ItemAssignment>,
        selectedParticipantId: String,
        isLoading: Boolean,
    ): AssignItemsUiState {
        val participantsById = participants.associateBy { participant -> participant.id }
        val assignedItemCount = items.count { item ->
            val assignment = assignments.firstOrNull { it.receiptItemId == item.id }
            assignment?.isCompleteFor(item) == true
        }
        return AssignItemsUiState(
            isLoading = isLoading,
            merchantName = merchantName,
            dateLabel = dateLabel,
            participants = participants.mapIndexed { index, participant ->
                AssignItemsParticipantUiState(
                    id = participant.id.value,
                    name = participant.name,
                    colorIndex = index,
                    selected = participant.id.value == selectedParticipantId,
                )
            },
            selectedParticipantId = selectedParticipantId,
            items = items.map { item ->
                val assignment = assignments.firstOrNull { it.receiptItemId == item.id }
                item.toUiState(assignment, participantsById)
            },
            subtotalLabel = formatMoney(MoneyMinor(items.sumOf { item -> item.totalPrice.value })),
            progressLabel = "$assignedItemCount of ${items.size} items assigned",
            canContinue = items.isNotEmpty() && assignedItemCount == items.size,
        )
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
                    if (receiptItem.quantity.value == 1 && quantities.size == 1) {
                        ItemAssignment(
                            receiptItemId = receiptItem.id,
                            mode = ItemAssignmentMode.Full,
                            shares = listOf(
                                ItemParticipantShare(
                                    participantId = ParticipantId(quantities.keys.first()),
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
    ): AssignItemsReceiptItemUiState {
        val shares = assignment.toShares(participantsById)
        val assignees = shares.map { share ->
            AssignItemsAssigneeUiState(
                participantId = share.participantId,
                name = share.name,
                colorIndex = share.colorIndex,
                detail = when {
                    share.quantity > 1 -> "${share.name} x${share.quantity}"
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
            quantityLabel = "${quantity.value}x",
            unitPriceLabel = "${formatMoney(unitPrice)} each",
            totalLabel = formatMoney(totalPrice),
            assignmentState = assignmentState,
            splitMode = assignment.toSplitMode(),
            shares = shares,
            assignees = assignees,
        )
    }

    private fun ItemAssignment?.toShares(
        participantsById: Map<ParticipantId, Participant>,
    ): List<AssignItemsShareUiState> {
        if (this == null) return emptyList()
        return shares.mapNotNull { share ->
            val participant = participantsById[share.participantId] ?: return@mapNotNull null
            AssignItemsShareUiState(
                participantId = participant.id.value,
                name = participant.name,
                colorIndex = participant.creationOrder,
                amountMinor = share.amount.value,
                quantity = share.quantity?.value ?: 1,
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

    private fun ReceiptItem.nextUnitAssignment(
        existing: ItemAssignment?,
        selectedParticipantId: String,
    ): ItemAssignment {
        val units = buildList {
            existing?.shares.orEmpty().forEach { share ->
                repeat(share.quantity?.value ?: 0) {
                    add(share.participantId.value)
                }
            }
        }.toMutableList()
        if (units.size < quantity.value) {
            units += selectedParticipantId
        } else {
            units.removeAt(0)
            units += selectedParticipantId
        }

        val quantities = units.groupingBy { participantId -> participantId }
            .eachCount()
        val amounts = allocateAmountsByQuantity(quantities)
        val shares = quantities
            .map { (participantId, assignedUnits) ->
                ItemParticipantShare(
                    participantId = ParticipantId(participantId),
                    amount = MoneyMinor(amounts.getValue(participantId)),
                    quantity = Quantity(assignedUnits),
                )
            }
        return ItemAssignment(
            receiptItemId = id,
            mode = ItemAssignmentMode.ByUnits,
            shares = shares,
        )
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
        return state.copy(splitSheet = item.toSplitSheet(state.participants))
    }

    private fun updateSplitMode(
        state: AssignItemsUiState,
        mode: AssignItemsSplitMode,
    ): AssignItemsUiState {
        val sheet = state.splitSheet ?: return state
        val item = state.items.firstOrNull { receiptItem -> receiptItem.id == sheet.itemId } ?: return state
        return state.copy(splitSheet = item.toSplitSheet(state.participants, mode))
    }

    private fun updateSplitQuantity(
        state: AssignItemsUiState,
        participantId: String,
        delta: Int,
    ): AssignItemsUiState {
        val sheet = state.splitSheet ?: return state
        val rows = sheet.rows.map { row ->
            if (row.participantId == participantId) {
                row.copy(quantity = (row.quantity + delta).coerceAtLeast(0))
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
        val rows = sheet.rows.map { row ->
            if (row.participantId == participantId) row.copy(amount = value) else row
        }
        return state.copy(splitSheet = sheet.copy(rows = rows).validated())
    }

    private fun updatePercentage(
        state: AssignItemsUiState,
        participantId: String,
        value: String,
    ): AssignItemsUiState {
        val sheet = state.splitSheet ?: return state
        val rows = sheet.rows.map { row ->
            if (row.participantId == participantId) row.copy(percentage = value) else row
        }
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
                            share.quantity > 1 -> "${share.name} x${share.quantity}"
                            share.percentageBasisPoints > 0 -> "${share.name} ${share.percentageBasisPoints / 100}%"
                            else -> share.name
                        },
                        quantity = maxOf(share.quantity, 1),
                    )
                }
                item.copy(
                    assignmentState = AssignItemsItemState.Assigned,
                    splitMode = sheet.mode,
                    shares = shares,
                    assignees = assignees,
                )
            } else {
                item
            }
        }
        val assignedItemCount = items.count { item -> item.assignmentState == AssignItemsItemState.Assigned }
        return state.copy(
            items = items,
            splitSheet = null,
            fieldErrors = emptyMap(),
            submitError = null,
            progressLabel = "$assignedItemCount of ${items.size} items assigned",
            canContinue = items.isNotEmpty() && assignedItemCount == items.size,
        )
    }

    private fun AssignItemsReceiptItemUiState.toSplitSheet(
        participants: List<AssignItemsParticipantUiState>,
        modeOverride: AssignItemsSplitMode? = null,
    ): AssignItemsSplitSheetUiState {
        val targetMode = modeOverride ?: splitMode
        val rows = participants.map { participant ->
            val share = shares.firstOrNull { existingShare -> existingShare.participantId == participant.id }
            AssignItemsSplitRowUiState(
                participantId = participant.id,
                name = participant.name,
                colorIndex = participant.colorIndex,
                quantity = when (targetMode) {
                    AssignItemsSplitMode.Units -> share?.quantity ?: 0
                    else -> 0
                },
                amount = when (targetMode) {
                    AssignItemsSplitMode.CustomAmount -> share?.amountMinor?.takeIf { it > 0 }?.let(::formatMoneyInput).orEmpty()
                    else -> ""
                },
                percentage = when (targetMode) {
                    AssignItemsSplitMode.Percentage -> share?.percentageBasisPoints?.takeIf { it > 0 }
                        ?.let { basisPoints -> BigDecimal(basisPoints).movePointLeft(2).stripTrailingZeros().toPlainString() }
                        .orEmpty()
                    else -> ""
                },
                included = when (targetMode) {
                    AssignItemsSplitMode.SharedEqual -> share != null
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
            totalMinor = parseDisplayMoney(totalLabel) ?: 0,
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
                    assignedUnits != quantity -> "Assigned units must equal $quantity."
                    else -> null
                }
                copy(
                    error = error,
                    statusLabel = "Assigned: $assignedUnits of $quantity units",
                )
            }
            AssignItemsSplitMode.SharedEqual -> {
                val includedCount = rows.count { row -> row.included }
                val error = when {
                    includedCount < 2 -> "Choose at least two people for a shared split."
                    else -> null
                }
                copy(
                    error = error,
                    statusLabel = "$includedCount people selected",
                )
            }
            AssignItemsSplitMode.CustomAmount -> {
                val amounts = rows.map { row -> parseMoneyInput(row.amount) }
                val hasInvalid = rows.any { row -> row.amount.isNotBlank() && parseMoneyInput(row.amount) == null }
                val assignedMinor = amounts.filterNotNull().sum()
                val error = when {
                    hasInvalid -> "Enter valid custom amounts."
                    assignedMinor != totalMinor -> "Custom amounts must equal $totalLabel."
                    else -> null
                }
                copy(
                    error = error,
                    statusLabel = "Assigned: ${formatMoney(MoneyMinor(assignedMinor))} of $totalLabel",
                )
            }
            AssignItemsSplitMode.Percentage -> {
                val percentages = rows.map { row -> parsePercentageBasisPoints(row.percentage) }
                val hasInvalid = rows.any { row -> row.percentage.isNotBlank() && parsePercentageBasisPoints(row.percentage) == null }
                val assignedBasisPoints = percentages.filterNotNull().sum()
                val error = when {
                    hasInvalid -> "Enter valid percentages."
                    assignedBasisPoints != PercentageBasisPoints.MAX_BASIS_POINTS -> "Percentages must add to 100%."
                    else -> null
                }
                copy(
                    error = error,
                    statusLabel = "Assigned: ${assignedBasisPoints / 100}% of 100%",
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

    private fun formatMoney(value: MoneyMinor): String {
        return "€${BigDecimal(value.value).movePointLeft(2).setScale(2).toPlainString()}"
    }

    private fun formatMoneyInput(value: Long): String {
        return BigDecimal(value).movePointLeft(2).setScale(2).toPlainString()
    }

    private fun parseMoneyInput(value: String): Long? {
        val normalized = value.trim().removePrefix("€").removePrefix("\$").replace(",", "")
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

    private fun parseDisplayMoney(value: String): Long? {
        return parseMoneyInput(value)
    }

    private fun parsePercentageBasisPoints(value: String): Int? {
        if (value.trim().isBlank()) return 0
        return try {
            BigDecimal(value.trim().removeSuffix("%"))
                .multiply(BigDecimal(100))
                .setScale(0, RoundingMode.UNNECESSARY)
                .intValueExact()
                .takeIf { basisPoints -> basisPoints in 0..PercentageBasisPoints.MAX_BASIS_POINTS }
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
