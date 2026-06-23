package com.dps.evenup.feature.expenseflow.impl.feesallocation

import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.domain.expense.api.AllocateFeesUseCase
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.FeeAllocation
import com.dps.evenup.domain.expense.api.FeeAllocationMode
import com.dps.evenup.domain.expense.api.FeeParticipantShare
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.CurrencyCode
import com.dps.evenup.domain.receipt.api.FeeId
import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.ReceiptFee
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

class FeesAllocationPresenter(
    private val draftRepository: ExpenseDraftRepository,
    private val allocateFees: AllocateFeesUseCase,
) {
    suspend fun load(): FeesAllocationUiState {
        val draft = draftRepository.getDraft() ?: return FeesAllocationUiState(
            isLoading = false,
            missingDraft = true,
            submitError = "No expense draft was found.",
        )
        if (draft.participants.isEmpty()) {
            return FeesAllocationUiState(
                isLoading = false,
                missingDraft = true,
                submitError = "Add people before allocating fees.",
            )
        }
        val mode = draft.initialMode()
        val allocations = draft.allocationsFor(mode)
        return buildState(draft, mode, allocations)
    }

    suspend fun reduce(
        state: FeesAllocationUiState,
        event: FeesAllocationUiEvent,
    ): FeesAllocationUiState {
        return when (event) {
            is FeesAllocationUiEvent.ModeSelected -> {
                val draft = draftRepository.getDraft()
                    ?: return state.copy(missingDraft = true, submitError = "No expense draft was found.")
                buildState(
                    draft = draft,
                    mode = event.mode,
                    allocations = draft.allocationsFor(event.mode, fallbackState = state),
                )
            }
            is FeesAllocationUiEvent.CustomAmountChanged -> {
                val draft = draftRepository.getDraft()
                    ?: return state.copy(missingDraft = true, submitError = "No expense draft was found.")
                updateCustomAmount(state, event, draft)
            }
            is FeesAllocationUiEvent.FeeEditorOpenClick -> state.withFocusedFeeEditor(event.feeId)
            FeesAllocationUiEvent.FeeEditorDismissed -> state.copy(selectedFeeEditor = null)
            FeesAllocationUiEvent.FeeEditorDoneClick -> {
                if (state.selectedFeeEditor?.canSave == true) state.copy(selectedFeeEditor = null) else state
            }
            FeesAllocationUiEvent.AssignAllFeesClick -> state.copy(
                participantPicker = state.participantPicker(title = "Who covers all fees?", feeId = null),
            )
            is FeesAllocationUiEvent.AssignThisFeeClick -> {
                val fee = state.feeCards.firstOrNull { card -> card.id == event.feeId } ?: return state
                state.copy(
                    participantPicker = state.participantPicker(title = "Assign ${fee.label.lowercase()} to:", feeId = fee.id),
                )
            }
            FeesAllocationUiEvent.ParticipantPickerDismissed -> state.copy(participantPicker = null)
            is FeesAllocationUiEvent.ParticipantPicked -> {
                val draft = draftRepository.getDraft()
                    ?: return state.copy(missingDraft = true, submitError = "No expense draft was found.")
                state.applyParticipantPick(event.participantId, draft)
            }
            FeesAllocationUiEvent.ResetToProportionalClick -> {
                if (state.canResetToProportional) {
                    state.copy(showResetToProportionalConfirmation = true)
                } else {
                    state
                }
            }
            FeesAllocationUiEvent.ResetToProportionalDismissed -> state.copy(showResetToProportionalConfirmation = false)
            FeesAllocationUiEvent.ResetToProportionalConfirmed -> {
                val draft = draftRepository.getDraft()
                    ?: return state.copy(missingDraft = true, submitError = "No expense draft was found.")
                val previousSnapshot = state.automaticChangeUndoSnapshot()
                buildState(
                    draft = draft,
                    mode = FeesAllocationModeUiState.Proportional,
                    allocations = draft.allocationsFor(FeesAllocationModeUiState.Proportional),
                ).copy(
                    showResetToProportionalConfirmation = false,
                    undoSnapshot = previousSnapshot,
                    undoSnackbarId = state.undoSnackbarId + 1L,
                    feedback = state.nextFeedback("Reset to proportional"),
                )
            }
            FeesAllocationUiEvent.UndoAutomaticChangeClick -> {
                val draft = draftRepository.getDraft()
                    ?: return state.copy(missingDraft = true, submitError = "No expense draft was found.")
                state.restoreAutomaticChange(draft)
            }
            FeesAllocationUiEvent.UndoAutomaticChangeDismissed -> state.copy(undoSnapshot = null)
            FeesAllocationUiEvent.BackClick,
            FeesAllocationUiEvent.ContinueClick,
            -> state
        }
    }

    suspend fun saveDraft(state: FeesAllocationUiState): SaveFeesAllocationResult {
        val draft = draftRepository.getDraft() ?: return SaveFeesAllocationResult.MissingDraft
        val allocations = state.toAllocations(draft)
        val validation = validateAllocations(draft, allocations)
        if (validation.isNotEmpty()) {
            return SaveFeesAllocationResult.Invalid(validation)
        }

        draftRepository.saveDraft(draft.copy(feeAllocations = allocations))
        return SaveFeesAllocationResult.Saved
    }

    private fun updateCustomAmount(
        state: FeesAllocationUiState,
        event: FeesAllocationUiEvent.CustomAmountChanged,
        draft: ExpenseDraft,
    ): FeesAllocationUiState {
        val nextCards = state.feeCards.map { feeCard ->
            if (feeCard.id != event.feeId) return@map feeCard
            val editedAmount = parseMoneyInput(event.value)
            val coveringParticipantId = feeCard.coveringParticipantId
            val rowsWithEdit = feeCard.participantRows.map { row ->
                if (row.participantId == event.participantId) {
                    row.copy(customAmount = event.value)
                } else {
                    row
                }
            }
            val balancedRows = if (
                coveringParticipantId != null &&
                coveringParticipantId != event.participantId &&
                editedAmount != null
            ) {
                val nonCoveringTotal = rowsWithEdit
                    .filter { row -> row.participantId != coveringParticipantId }
                    .sumOf { row -> parseMoneyInput(row.customAmount) ?: 0L }
                val coveringAmount = (feeCard.amountMinor - nonCoveringTotal).coerceAtLeast(0L)
                rowsWithEdit.map { row ->
                    if (row.participantId == coveringParticipantId) {
                        row.copy(customAmount = formatMoneyInput(coveringAmount))
                    } else {
                        row
                    }
                }
            } else {
                rowsWithEdit
            }
            feeCard.copy(
                participantRows = balancedRows,
            )
        }
        return state.copy(
            feeCards = nextCards,
            fieldErrors = emptyMap(),
            submitError = null,
        ).validatedCustom(draft = draft)
            .withCustomCompletionFeedback(previousCards = state.feeCards)
    }

    private fun buildState(
        draft: ExpenseDraft,
        mode: FeesAllocationModeUiState,
        allocations: List<FeeAllocation>,
    ): FeesAllocationUiState {
        val positiveFees = draft.positiveFees()
        val currencyCode = draft.receipt.currencyCode
        val totalFees = MoneyMinor(positiveFees.sumOf { fee -> fee.amount.value })
        val participantColorIndexes = draft.participants.mapIndexed { index, participant ->
            participant.id to index
        }.toMap()
        val feeCards = positiveFees.map { fee ->
            fee.toCard(
                participants = draft.participants,
                allocation = allocations.firstOrNull { allocation -> allocation.feeId == fee.id },
                participantColorIndexes = participantColorIndexes,
                currencyCode = currencyCode,
            )
        }
        val participantRows = draft.participants.map { participant ->
            val allocatedTotal = allocations
                .flatMap { allocation -> allocation.shares }
                .filter { share -> share.participantId == participant.id }
                .sumOf { share -> share.amount.value }
            FeeParticipantUiState(
                id = participant.id.value,
                name = participant.name,
                colorIndex = participantColorIndexes.getValue(participant.id),
                allocatedFeesLabel = formatMoney(MoneyMinor(allocatedTotal), currencyCode),
            )
        }
        return FeesAllocationUiState(
            isLoading = false,
            merchantName = draft.receipt.merchantName,
            currencyCode = currencyCode.value,
            currencySymbol = currencySymbol(currencyCode),
            mode = mode,
            feeRows = positiveFees.map { fee ->
                FeeSummaryRowUiState(
                    id = fee.id.value,
                    label = fee.label,
                    amountLabel = formatMoney(fee.amount, currencyCode),
                )
            },
            feeCards = feeCards,
            participants = participantRows,
            totalFeesLabel = formatMoney(totalFees, currencyCode),
            headerSubtitle = "Choose how ${formatMoney(totalFees, currencyCode)} in fees should be shared.",
            helperText = mode.helperText(fellBackToEqual = !draft.hasAssignedItemSubtotal()),
            canContinue = positiveFees.isEmpty() || feeCards.all { card -> card.error == null },
        ).let { state ->
            if (mode == FeesAllocationModeUiState.Custom) state.validatedCustom(draft = draft) else state
        }
    }

    private fun ReceiptFee.toCard(
        participants: List<Participant>,
        allocation: FeeAllocation?,
        participantColorIndexes: Map<ParticipantId, Int>,
        currencyCode: CurrencyCode,
    ): FeeAllocationCardUiState {
        val sharesByParticipant = allocation?.shares.orEmpty().associateBy { share -> share.participantId }
        return FeeAllocationCardUiState(
            id = id.value,
            label = label,
            amountMinor = amount.value,
            amountLabel = formatMoney(amount, currencyCode),
            statusLabel = "",
            participantRows = participants.map { participant ->
                val amount = sharesByParticipant[participant.id]?.amount ?: MoneyMinor.Zero
                FeeAllocationRowUiState(
                    participantId = participant.id.value,
                    name = participant.name,
                    colorIndex = participantColorIndexes.getValue(participant.id),
                    amountLabel = formatMoney(amount, currencyCode),
                    customAmount = formatMoneyInput(amount.value),
                )
            },
        )
    }

    private fun ExpenseDraft.allocationsFor(
        mode: FeesAllocationModeUiState,
        fallbackState: FeesAllocationUiState? = null,
    ): List<FeeAllocation> {
        val positiveFees = positiveFees()
        val positiveFeeIds = positiveFees.map { fee -> fee.id }.toSet()
        return when (mode) {
            FeesAllocationModeUiState.Equal -> positiveFees.map { fee ->
                allocateFees.allocateEqual(fee, participants)
            }
            FeesAllocationModeUiState.Proportional -> positiveFees.map { fee ->
                allocateFees.allocateProportional(fee, participants, itemAssignments)
            }
            FeesAllocationModeUiState.Custom -> {
                val existingCustom = fallbackState?.toAllocations(this).orEmpty()
                    .filter { allocation -> allocation.feeId in positiveFeeIds }
                    .takeIf { allocations -> allocations.isNotEmpty() }
                existingCustom
                    ?: feeAllocations
                        .filter { allocation -> allocation.feeId in positiveFeeIds }
                        .takeIf { allocations -> allocations.isNotEmpty() }
                    ?: positiveFees.map { fee ->
                        allocateFees.allocateEqual(fee, participants).copy(mode = FeeAllocationMode.Custom)
                    }
            }
        }
    }

    private fun FeesAllocationUiState.toAllocations(draft: ExpenseDraft): List<FeeAllocation> {
        val positiveFees = draft.positiveFees()
        return when (mode) {
            FeesAllocationModeUiState.Equal -> positiveFees.map { fee ->
                allocateFees.allocateEqual(fee, draft.participants)
            }
            FeesAllocationModeUiState.Proportional -> positiveFees.map { fee ->
                allocateFees.allocateProportional(fee, draft.participants, draft.itemAssignments)
            }
            FeesAllocationModeUiState.Custom -> feeCards.map { feeCard ->
                FeeAllocation(
                    feeId = FeeId(feeCard.id),
                    mode = FeeAllocationMode.Custom,
                    shares = feeCard.participantRows.map { row ->
                        FeeParticipantShare(
                            participantId = ParticipantId(row.participantId),
                            amount = MoneyMinor(parseMoneyInput(row.customAmount) ?: 0),
                        )
                    },
                )
            }
        }
    }

    private fun FeesAllocationUiState.validatedCustom(
        draft: ExpenseDraft? = null,
        selectedFeeId: String? = selectedFeeEditor?.feeId,
    ): FeesAllocationUiState {
        if (mode != FeesAllocationModeUiState.Custom) return copy(canContinue = true)
        val currency = CurrencyCode(currencyCode)
        val cards = feeCards.map { card ->
            val parsedRows = card.participantRows.map { row ->
                row to parseMoneyInput(row.customAmount)
            }
            val hasInvalid = parsedRows.any { (row, amount) ->
                row.customAmount.isNotBlank() && amount == null
            }
            val assignedTotal = parsedRows.sumOf { (_, amount) -> amount ?: 0 }
            val remaining = card.amountMinor - assignedTotal
            val statusLabel = when {
                hasInvalid -> "Enter valid amounts."
                remaining > 0 -> "${formatMoney(MoneyMinor(remaining), currency)} remaining"
                remaining == 0L -> "Fully allocated"
                else -> "Over by ${formatMoney(MoneyMinor(-remaining), currency)}"
            }
            val error = when {
                hasInvalid -> "Enter valid amounts for ${card.label}."
                remaining > 0 -> statusLabel
                remaining < 0 -> statusLabel
                else -> null
            }
            val fullAndValid = !hasInvalid && remaining == 0L
            val hasCoveringParticipant = card.coveringParticipantId != null
            card.copy(
                statusLabel = statusLabel,
                error = error,
                participantRows = parsedRows.map { (row, amount) ->
                    val rowAmount = amount ?: 0
                    row.copy(
                        amountLabel = formatMoney(MoneyMinor(rowAmount), currency),
                        customEnabled = hasCoveringParticipant || !fullAndValid || rowAmount > 0,
                        customIsError = (row.customAmount.isNotBlank() && amount == null) ||
                            (remaining < 0 && rowAmount > 0),
                    )
                },
            )
        }
        val participants = participants.map { participant ->
            val allocatedTotal = cards
                .flatMap { card -> card.participantRows }
                .filter { row -> row.participantId == participant.id }
                .sumOf { row -> parseMoneyInput(row.customAmount) ?: 0 }
            participant.copy(allocatedFeesLabel = formatMoney(MoneyMinor(allocatedTotal), currency))
        }
        val overviewRows = participants.map { participant ->
            val breakdown = cards.joinToString(" · ") { card ->
                val row = card.participantRows.first { candidate -> candidate.participantId == participant.id }
                "${card.label} ${row.amountLabel}"
            }
            CustomAllocationOverviewRowUiState(
                participantId = participant.id,
                name = participant.name,
                colorIndex = participant.colorIndex,
                totalLabel = participant.allocatedFeesLabel,
                breakdownLabel = breakdown,
            )
        }
        val selectedEditor = selectedFeeId
            ?.let { feeId -> cards.firstOrNull { card -> card.id == feeId } }
            ?.toEditor()
        val canContinue = cards.all { card -> card.error == null }
        return copy(
            feeCards = cards,
            participants = participants,
            customOverviewRows = overviewRows,
            selectedFeeEditor = selectedEditor,
            invalidReason = if (canContinue) null else "Allocate all fees to continue.",
            canResetToProportional = draft?.let { customAllocationsDifferFromProportional(it, cards) }
                ?: canResetToProportional,
            canContinue = canContinue,
        )
    }

    private fun FeeAllocationCardUiState.toEditor(): FocusedFeeEditorUiState {
        return FocusedFeeEditorUiState(
            feeId = id,
            title = "Edit ${label.lowercase()}",
            totalLabel = "$label total $amountLabel",
            statusLabel = statusLabel,
            canSave = error == null,
            rows = participantRows,
            error = error,
        )
    }

    private fun FeesAllocationUiState.withFocusedFeeEditor(feeId: String): FeesAllocationUiState {
        return validatedCustom(selectedFeeId = feeId)
    }

    private fun FeesAllocationUiState.participantPicker(
        title: String,
        feeId: String?,
    ): FeeParticipantPickerUiState {
        return FeeParticipantPickerUiState(
            title = title,
            feeId = feeId,
            participants = participants.map { participant ->
                FeePickerParticipantUiState(
                    id = participant.id,
                    name = participant.name,
                    colorIndex = participant.colorIndex,
                )
            },
        )
    }

    private fun FeesAllocationUiState.applyParticipantPick(
        participantId: String,
        draft: ExpenseDraft,
    ): FeesAllocationUiState {
        val picker = participantPicker ?: return this
        val pickedName = participants.firstOrNull { participant -> participant.id == participantId }?.name.orEmpty()
        val changedFee = picker.feeId?.let { feeId -> feeCards.firstOrNull { card -> card.id == feeId } }
        val feedbackMessage = if (changedFee == null) {
            "Assigned all fees to $pickedName"
        } else {
            "Assigned ${changedFee.label.lowercase()} to $pickedName"
        }
        val nextCards = feeCards.map { card ->
            if (picker.feeId != null && card.id != picker.feeId) return@map card
            card.copy(
                coveringParticipantId = participantId,
                participantRows = card.participantRows.map { row ->
                    row.copy(
                        customAmount = if (row.participantId == participantId) {
                            formatMoneyInput(card.amountMinor)
                        } else {
                            formatMoneyInput(0L)
                        },
                    )
                },
            )
        }
        return copy(
            mode = FeesAllocationModeUiState.Custom,
            feeCards = nextCards,
            participantPicker = null,
            undoSnapshot = automaticChangeUndoSnapshot(),
            undoSnackbarId = undoSnackbarId + 1L,
            fieldErrors = emptyMap(),
            submitError = null,
        ).validatedCustom(draft = draft)
            .copy(feedback = nextFeedback(feedbackMessage))
    }

    private fun FeesAllocationUiState.restoreAutomaticChange(draft: ExpenseDraft): FeesAllocationUiState {
        val snapshot = undoSnapshot ?: return this
        return copy(
            mode = snapshot.mode,
            feeCards = snapshot.feeCards,
            selectedFeeEditor = null,
            participantPicker = null,
            showResetToProportionalConfirmation = false,
            undoSnapshot = null,
            fieldErrors = emptyMap(),
            submitError = null,
        ).let { restored ->
            if (snapshot.mode == FeesAllocationModeUiState.Custom) {
                restored.validatedCustom(draft = draft)
            } else {
                buildState(
                    draft = draft,
                    mode = snapshot.mode,
                    allocations = restored.toAllocations(draft),
                )
            }
        }.copy(feedback = nextFeedback("Fee changes restored"))
    }

    private fun FeesAllocationUiState.automaticChangeUndoSnapshot(): FeesAllocationUndoSnapshot {
        return FeesAllocationUndoSnapshot(
            mode = mode,
            feeCards = feeCards,
        )
    }

    private fun FeesAllocationUiState.withCustomCompletionFeedback(
        previousCards: List<FeeAllocationCardUiState>,
    ): FeesAllocationUiState {
        val previousById = previousCards.associateBy { card -> card.id }
        val completedCard = feeCards.firstOrNull { card ->
            previousById[card.id]?.error != null && card.error == null
        } ?: return this
        val wasInvalid = previousCards.any { card -> card.error != null }
        val isValid = feeCards.all { card -> card.error == null }
        val message = if (wasInvalid && isValid) {
            "All fees allocated"
        } else {
            "${completedCard.label} fully allocated"
        }
        return copy(feedback = nextFeedback(message))
    }

    private fun FeesAllocationUiState.nextFeedback(message: String): FeesAllocationFeedbackUiState {
        return FeesAllocationFeedbackUiState(
            id = (feedback?.id ?: 0L) + 1L,
            message = message,
        )
    }

    private fun customAllocationsDifferFromProportional(
        draft: ExpenseDraft,
        cards: List<FeeAllocationCardUiState>,
    ): Boolean {
        val proportional = draft.allocationsFor(FeesAllocationModeUiState.Proportional)
            .associate { allocation ->
                allocation.feeId.value to allocation.shares.associate { share ->
                    share.participantId.value to share.amount.value
                }
            }
        return cards.any { card ->
            val proportionalShares = proportional[card.id].orEmpty()
            card.participantRows.any { row ->
                (parseMoneyInput(row.customAmount) ?: 0L) != proportionalShares.getOrDefault(row.participantId, 0L)
            }
        }
    }

    private fun validateAllocations(
        draft: ExpenseDraft,
        allocations: List<FeeAllocation>,
    ): Map<String, String> {
        val positiveFees = draft.positiveFees()
        if (positiveFees.isEmpty()) return emptyMap()
        val errors = mutableMapOf<String, String>()
        positiveFees.forEach { fee ->
            val allocation = allocations.firstOrNull { candidate -> candidate.feeId == fee.id }
            if (allocation == null) {
                errors["fees"] = "Every fee needs an allocation."
                return@forEach
            }
            val validation = allocateFees.validateCustom(fee, draft.participants, allocation.shares)
            if (!validation.isValid) {
                errors["fees"] = "${fee.label} allocations must equal ${formatMoney(fee.amount, draft.receipt.currencyCode)}."
            }
        }
        return errors
    }

    private fun ExpenseDraft.initialMode(): FeesAllocationModeUiState {
        return if (feeAllocations.isNotEmpty()) {
            feeAllocations.toUiMode()
        } else if (hasAssignedItemSubtotal()) {
            FeesAllocationModeUiState.Proportional
        } else {
            FeesAllocationModeUiState.Equal
        }
    }

    private fun ExpenseDraft.positiveFees(): List<ReceiptFee> {
        return receipt.fees.filter { fee -> fee.type != FeeType.Discount && fee.amount.value > 0L }
    }

    private fun ExpenseDraft.hasAssignedItemSubtotal(): Boolean {
        return itemAssignments
            .flatMap { assignment -> assignment.shares }
            .sumOf { share -> share.amount.value } > 0L
    }

    private fun List<FeeAllocation>.toUiMode(): FeesAllocationModeUiState {
        val modes = map { allocation -> allocation.mode }.toSet()
        return when (modes.singleOrNull()) {
            FeeAllocationMode.Proportional -> FeesAllocationModeUiState.Proportional
            FeeAllocationMode.Custom -> FeesAllocationModeUiState.Custom
            FeeAllocationMode.Equal,
            null,
            -> FeesAllocationModeUiState.Equal
        }
    }

    private fun FeesAllocationModeUiState.helperText(fellBackToEqual: Boolean): String {
        return when (this) {
            FeesAllocationModeUiState.Equal -> if (fellBackToEqual) {
                "Using equal split because item subtotals are unavailable."
            } else {
                "Every participant receives the same share."
            }
            FeesAllocationModeUiState.Proportional -> "Fees follow each person's assigned item subtotal."
            FeesAllocationModeUiState.Custom -> "Set exact fee amounts manually."
        }
    }

    private fun formatMoney(
        value: MoneyMinor,
        currencyCode: CurrencyCode,
    ): String {
        return currencySymbol(currencyCode) +
            BigDecimal(value.value).movePointLeft(2).setScale(2).toPlainString()
    }

    private fun formatMoneyInput(value: Long): String {
        return BigDecimal(value).movePointLeft(2).setScale(2).toPlainString()
    }

    private fun parseMoneyInput(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return 0
        if ('-' in trimmed) return null
        val compact = trimmed.replace(",", "").replace(" ", "")
        val normalized = compact.trim { char -> !char.isDigit() && char != '.' }
        if (normalized.isBlank()) return null
        val normalizedIndex = compact.indexOf(normalized)
        val prefix = compact.take(normalizedIndex)
        val suffix = compact.drop(normalizedIndex + normalized.length)
        if (prefix.any { char -> char.isLetterOrDigit() || char == '.' }) return null
        if (suffix.any { char -> char.isLetterOrDigit() || char == '.' }) return null
        if (normalized.any { char -> !char.isDigit() && char != '.' }) return null
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

    private fun currencySymbol(currencyCode: CurrencyCode): String {
        return runCatching {
            Currency.getInstance(currencyCode.value).getSymbol(Locale.US)
        }.getOrDefault("${currencyCode.value} ")
    }
}

sealed interface SaveFeesAllocationResult {
    data object Saved : SaveFeesAllocationResult

    data object MissingDraft : SaveFeesAllocationResult

    data class Invalid(val fieldErrors: Map<String, String>) : SaveFeesAllocationResult
}
