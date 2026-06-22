package com.dps.evenup.feature.expenseflow.impl.feesallocation

sealed interface FeesAllocationUiEvent {
    data class ModeSelected(val mode: FeesAllocationModeUiState) : FeesAllocationUiEvent

    data class CustomAmountChanged(
        val feeId: String,
        val participantId: String,
        val value: String,
    ) : FeesAllocationUiEvent

    data class FeeEditorOpenClick(val feeId: String) : FeesAllocationUiEvent

    data object FeeEditorDismissed : FeesAllocationUiEvent

    data object FeeEditorDoneClick : FeesAllocationUiEvent

    data object AssignAllFeesClick : FeesAllocationUiEvent

    data class AssignThisFeeClick(val feeId: String) : FeesAllocationUiEvent

    data object ParticipantPickerDismissed : FeesAllocationUiEvent

    data class ParticipantPicked(val participantId: String) : FeesAllocationUiEvent

    data object ResetToProportionalClick : FeesAllocationUiEvent

    data object ResetToProportionalDismissed : FeesAllocationUiEvent

    data object ResetToProportionalConfirmed : FeesAllocationUiEvent

    data object UndoAutomaticChangeClick : FeesAllocationUiEvent

    data object UndoAutomaticChangeDismissed : FeesAllocationUiEvent

    data object ContinueClick : FeesAllocationUiEvent

    data object BackClick : FeesAllocationUiEvent
}
