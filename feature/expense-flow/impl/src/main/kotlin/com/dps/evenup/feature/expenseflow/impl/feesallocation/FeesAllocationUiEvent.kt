package com.dps.evenup.feature.expenseflow.impl.feesallocation

sealed interface FeesAllocationUiEvent {
    data class ModeSelected(val mode: FeesAllocationModeUiState) : FeesAllocationUiEvent

    data class CustomAmountChanged(
        val feeId: String,
        val participantId: String,
        val value: String,
    ) : FeesAllocationUiEvent

    data object ContinueClick : FeesAllocationUiEvent

    data object BackClick : FeesAllocationUiEvent
}
