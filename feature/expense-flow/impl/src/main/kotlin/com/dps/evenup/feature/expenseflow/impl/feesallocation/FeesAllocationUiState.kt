package com.dps.evenup.feature.expenseflow.impl.feesallocation

data class FeesAllocationUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val missingDraft: Boolean = false,
    val merchantName: String = "",
    val mode: FeesAllocationModeUiState = FeesAllocationModeUiState.Equal,
    val feeCards: List<FeeAllocationCardUiState> = emptyList(),
    val participants: List<FeeParticipantUiState> = emptyList(),
    val totalFeesLabel: String = "",
    val helperText: String = "",
    val canContinue: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val submitError: String? = null,
)

enum class FeesAllocationModeUiState {
    Equal,
    Proportional,
    Custom,
}

data class FeeAllocationCardUiState(
    val id: String,
    val label: String,
    val amountLabel: String,
    val participantRows: List<FeeAllocationRowUiState>,
    val error: String? = null,
)

data class FeeAllocationRowUiState(
    val participantId: String,
    val name: String,
    val colorIndex: Int,
    val amountLabel: String,
    val customAmount: String = "",
)

data class FeeParticipantUiState(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val allocatedFeesLabel: String,
)
