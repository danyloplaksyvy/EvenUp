package com.dps.evenup.feature.expenseflow.impl.feesallocation

import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.domain.expense.api.AllocateFeesUseCase
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.FeeAllocation
import com.dps.evenup.domain.expense.api.FeeAllocationMode
import com.dps.evenup.domain.expense.api.FeeParticipantShare
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.FeeId
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.ReceiptFee
import java.math.BigDecimal
import java.math.RoundingMode

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
        val mode = draft.feeAllocations.toUiMode()
        val allocations = draft.allocationsFor(mode)
        return buildState(draft, mode, allocations, isLoading = false)
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
                    isLoading = false,
                )
            }
            is FeesAllocationUiEvent.CustomAmountChanged -> updateCustomAmount(state, event)
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
    ): FeesAllocationUiState {
        val nextCards = state.feeCards.map { feeCard ->
            if (feeCard.id != event.feeId) return@map feeCard
            feeCard.copy(
                participantRows = feeCard.participantRows.map { row ->
                    if (row.participantId == event.participantId) row.copy(customAmount = event.value) else row
                },
            )
        }
        return state.copy(
            feeCards = nextCards,
            fieldErrors = emptyMap(),
            submitError = null,
        ).validatedCustom()
    }

    private fun buildState(
        draft: ExpenseDraft,
        mode: FeesAllocationModeUiState,
        allocations: List<FeeAllocation>,
        isLoading: Boolean,
    ): FeesAllocationUiState {
        val participantColorIndexes = draft.participants.mapIndexed { index, participant ->
            participant.id to index
        }.toMap()
        val feeCards = draft.receipt.fees.map { fee ->
            fee.toCard(
                participants = draft.participants,
                allocation = allocations.firstOrNull { allocation -> allocation.feeId == fee.id },
                participantColorIndexes = participantColorIndexes,
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
                allocatedFeesLabel = formatMoney(MoneyMinor(allocatedTotal)),
            )
        }
        return FeesAllocationUiState(
            isLoading = isLoading,
            merchantName = draft.receipt.merchantName,
            mode = mode,
            feeCards = feeCards,
            participants = participantRows,
            totalFeesLabel = formatMoney(MoneyMinor(draft.receipt.fees.sumOf { fee -> fee.amount.value })),
            helperText = mode.helperText(),
            canContinue = draft.receipt.fees.isEmpty() || feeCards.all { card -> card.error == null },
        ).let { state ->
            if (mode == FeesAllocationModeUiState.Custom) state.validatedCustom() else state
        }
    }

    private fun ReceiptFee.toCard(
        participants: List<Participant>,
        allocation: FeeAllocation?,
        participantColorIndexes: Map<ParticipantId, Int>,
    ): FeeAllocationCardUiState {
        val sharesByParticipant = allocation?.shares.orEmpty().associateBy { share -> share.participantId }
        return FeeAllocationCardUiState(
            id = id.value,
            label = label,
            amountLabel = formatMoney(amount),
            participantRows = participants.map { participant ->
                val amount = sharesByParticipant[participant.id]?.amount ?: MoneyMinor.Zero
                FeeAllocationRowUiState(
                    participantId = participant.id.value,
                    name = participant.name,
                    colorIndex = participantColorIndexes.getValue(participant.id),
                    amountLabel = formatMoney(amount),
                    customAmount = formatMoneyInput(amount.value),
                )
            },
        )
    }

    private fun ExpenseDraft.allocationsFor(
        mode: FeesAllocationModeUiState,
        fallbackState: FeesAllocationUiState? = null,
    ): List<FeeAllocation> {
        return when (mode) {
            FeesAllocationModeUiState.Equal -> receipt.fees.map { fee ->
                allocateFees.allocateEqual(fee, participants)
            }
            FeesAllocationModeUiState.Proportional -> receipt.fees.map { fee ->
                allocateFees.allocateProportional(fee, participants, itemAssignments)
            }
            FeesAllocationModeUiState.Custom -> {
                val existingCustom = fallbackState?.toAllocations(this).orEmpty()
                    .takeIf { allocations -> allocations.isNotEmpty() }
                existingCustom ?: feeAllocations.takeIf { allocations -> allocations.isNotEmpty() }
                    ?: receipt.fees.map { fee -> allocateFees.allocateEqual(fee, participants).copy(mode = FeeAllocationMode.Custom) }
            }
        }
    }

    private fun FeesAllocationUiState.toAllocations(draft: ExpenseDraft): List<FeeAllocation> {
        return when (mode) {
            FeesAllocationModeUiState.Equal -> draft.receipt.fees.map { fee ->
                allocateFees.allocateEqual(fee, draft.participants)
            }
            FeesAllocationModeUiState.Proportional -> draft.receipt.fees.map { fee ->
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

    private fun FeesAllocationUiState.validatedCustom(): FeesAllocationUiState {
        if (mode != FeesAllocationModeUiState.Custom) return copy(canContinue = true)
        val cards = feeCards.map { card ->
            val hasInvalid = card.participantRows.any { row ->
                row.customAmount.isNotBlank() && parseMoneyInput(row.customAmount) == null
            }
            val assignedTotal = card.participantRows.sumOf { row -> parseMoneyInput(row.customAmount) ?: 0 }
            val expectedTotal = parseDisplayMoney(card.amountLabel) ?: 0
            val error = when {
                hasInvalid -> "Enter valid amounts for ${card.label}."
                assignedTotal != expectedTotal -> "${card.label} allocations must equal ${card.amountLabel}."
                else -> null
            }
            card.copy(
                error = error,
                participantRows = card.participantRows.map { row ->
                    val amount = parseMoneyInput(row.customAmount) ?: 0
                    row.copy(amountLabel = formatMoney(MoneyMinor(amount)))
                },
            )
        }
        val participants = participants.map { participant ->
            val allocatedTotal = cards
                .flatMap { card -> card.participantRows }
                .filter { row -> row.participantId == participant.id }
                .sumOf { row -> parseMoneyInput(row.customAmount) ?: 0 }
            participant.copy(allocatedFeesLabel = formatMoney(MoneyMinor(allocatedTotal)))
        }
        return copy(
            feeCards = cards,
            participants = participants,
            canContinue = cards.all { card -> card.error == null },
        )
    }

    private fun validateAllocations(
        draft: ExpenseDraft,
        allocations: List<FeeAllocation>,
    ): Map<String, String> {
        if (draft.receipt.fees.isEmpty()) return emptyMap()
        val errors = mutableMapOf<String, String>()
        draft.receipt.fees.forEach { fee ->
            val allocation = allocations.firstOrNull { candidate -> candidate.feeId == fee.id }
            if (allocation == null) {
                errors["fees"] = "Every fee needs an allocation."
                return@forEach
            }
            val validation = allocateFees.validateCustom(fee, draft.participants, allocation.shares)
            if (!validation.isValid) {
                errors["fees"] = "${fee.label} allocations must equal ${formatMoney(fee.amount)}."
            }
        }
        return errors
    }

    private fun List<FeeAllocation>.toUiMode(): FeesAllocationModeUiState {
        if (isEmpty()) return FeesAllocationModeUiState.Equal
        val modes = map { allocation -> allocation.mode }.toSet()
        return when (modes.singleOrNull()) {
            FeeAllocationMode.Proportional -> FeesAllocationModeUiState.Proportional
            FeeAllocationMode.Custom -> FeesAllocationModeUiState.Custom
            FeeAllocationMode.Equal,
            null,
            -> FeesAllocationModeUiState.Equal
        }
    }

    private fun FeesAllocationModeUiState.helperText(): String {
        return when (this) {
            FeesAllocationModeUiState.Equal -> "Every participant receives the same share of each fee."
            FeesAllocationModeUiState.Proportional -> "Fees follow each person's assigned item subtotal."
            FeesAllocationModeUiState.Custom -> "Enter exact amounts for each fee and participant."
        }
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

    private fun parseDisplayMoney(value: String): Long? = parseMoneyInput(value)
}

sealed interface SaveFeesAllocationResult {
    data object Saved : SaveFeesAllocationResult

    data object MissingDraft : SaveFeesAllocationResult

    data class Invalid(val fieldErrors: Map<String, String>) : SaveFeesAllocationResult
}
