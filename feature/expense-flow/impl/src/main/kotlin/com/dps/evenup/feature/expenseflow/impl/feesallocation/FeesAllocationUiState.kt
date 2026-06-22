package com.dps.evenup.feature.expenseflow.impl.feesallocation

data class FeesAllocationUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val missingDraft: Boolean = false,
    val merchantName: String = "",
    val currencyCode: String = "USD",
    val currencySymbol: String = "$",
    val mode: FeesAllocationModeUiState = FeesAllocationModeUiState.Equal,
    val feeRows: List<FeeSummaryRowUiState> = emptyList(),
    val feeCards: List<FeeAllocationCardUiState> = emptyList(),
    val participants: List<FeeParticipantUiState> = emptyList(),
    val totalFeesLabel: String = "",
    val headerSubtitle: String = "",
    val helperText: String = "",
    val invalidReason: String? = null,
    val customOverviewRows: List<CustomAllocationOverviewRowUiState> = emptyList(),
    val selectedFeeEditor: FocusedFeeEditorUiState? = null,
    val participantPicker: FeeParticipantPickerUiState? = null,
    val showResetToProportionalConfirmation: Boolean = false,
    val canResetToProportional: Boolean = false,
    val undoSnapshot: FeesAllocationUndoSnapshot? = null,
    val undoSnackbarId: Long = 0L,
    val feedback: FeesAllocationFeedbackUiState? = null,
    val canContinue: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val submitError: String? = null,
)

enum class FeesAllocationModeUiState {
    Equal,
    Proportional,
    Custom,
}

data class FeeSummaryRowUiState(
    val id: String,
    val label: String,
    val amountLabel: String,
)

data class FeeAllocationCardUiState(
    val id: String,
    val label: String,
    val amountMinor: Long,
    val amountLabel: String,
    val statusLabel: String = "",
    val coveringParticipantId: String? = null,
    val participantRows: List<FeeAllocationRowUiState>,
    val error: String? = null,
)

data class FeeAllocationRowUiState(
    val participantId: String,
    val name: String,
    val colorIndex: Int,
    val amountLabel: String,
    val customAmount: String = "",
    val customEnabled: Boolean = true,
    val customIsError: Boolean = false,
)

data class FeeParticipantUiState(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val allocatedFeesLabel: String,
)

data class CustomAllocationOverviewRowUiState(
    val participantId: String,
    val name: String,
    val colorIndex: Int,
    val totalLabel: String,
    val breakdownLabel: String,
)

data class FocusedFeeEditorUiState(
    val feeId: String,
    val title: String,
    val totalLabel: String,
    val statusLabel: String,
    val canSave: Boolean,
    val rows: List<FeeAllocationRowUiState>,
    val error: String? = null,
)

data class FeeParticipantPickerUiState(
    val title: String,
    val feeId: String? = null,
    val participants: List<FeePickerParticipantUiState>,
)

data class FeePickerParticipantUiState(
    val id: String,
    val name: String,
    val colorIndex: Int,
)

data class FeesAllocationUndoSnapshot(
    val mode: FeesAllocationModeUiState,
    val feeCards: List<FeeAllocationCardUiState>,
)

data class FeesAllocationFeedbackUiState(
    val id: Long,
    val message: String,
)
