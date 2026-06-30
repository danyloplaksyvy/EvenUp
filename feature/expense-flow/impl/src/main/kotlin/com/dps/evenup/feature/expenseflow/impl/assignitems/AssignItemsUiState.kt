package com.dps.evenup.feature.expenseflow.impl.assignitems

data class AssignItemsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val missingDraft: Boolean = false,
    val merchantName: String = "",
    val dateLabel: String? = null,
    val currencyCode: String = "USD",
    val participants: List<AssignItemsParticipantUiState> = emptyList(),
    val selectedParticipantIds: List<String> = emptyList(),
    val items: List<AssignItemsReceiptItemUiState> = emptyList(),
    val splitSheet: AssignItemsSplitSheetUiState? = null,
    val subtotalLabel: String = "",
    val progressLabel: String = "0 of 0 items assigned",
    val helperText: String = "Select a person, then tap their items.",
    val continueHelperText: String = "Assign items to continue",
    val canContinue: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val submitError: String? = null,
    val showClearAssignmentsConfirmation: Boolean = false,
    val showEqualSplitConfirmation: Boolean = false,
    val clearUndoItems: List<AssignItemsReceiptItemUiState>? = null,
    val clearUndoSnackbarId: Long = 0L,
    val feedback: AssignItemsFeedbackUiState? = null,
    val scrollToItemId: String? = null,
) {
    val canApplyEqualSplit: Boolean = participants.size >= 2 && items.isNotEmpty() && !isSaving
    val canClearAssignments: Boolean = items.any { item -> item.assignmentState != AssignItemsItemState.Unassigned }
}

data class AssignItemsFeedbackUiState(
    val id: Long,
    val message: String,
)

data class AssignItemsParticipantUiState(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val selected: Boolean = false,
)

data class AssignItemsReceiptItemUiState(
    val id: String,
    val name: String,
    val quantity: Int,
    val totalMinor: Long,
    val quantityLabel: String,
    val unitPriceLabel: String,
    val totalLabel: String,
    val assignmentSummaryLabel: String,
    val itemActionLabel: String,
    val itemActionContentDescription: String,
    val assignmentState: AssignItemsItemState,
    val splitMode: AssignItemsSplitMode,
    val directFullAssignment: Boolean = false,
    val shares: List<AssignItemsShareUiState>,
    val assignees: List<AssignItemsAssigneeUiState>,
)

data class AssignItemsAssigneeUiState(
    val participantId: String,
    val name: String,
    val colorIndex: Int,
    val detail: String,
    val quantity: Int = 1,
)

enum class AssignItemsItemState {
    Unassigned,
    Partial,
    Assigned,
}

enum class AssignItemsSplitMode {
    Units,
    SharedEqual,
    CustomAmount,
    Percentage,
}

data class AssignItemsShareUiState(
    val participantId: String,
    val name: String,
    val colorIndex: Int,
    val amountMinor: Long = 0,
    val quantity: Int = 0,
    val percentageBasisPoints: Int = 0,
)

data class AssignItemsSplitSheetUiState(
    val itemId: String,
    val itemName: String,
    val quantity: Int,
    val unitPriceLabel: String,
    val totalLabel: String,
    val currencyCode: String,
    val currencySymbol: String,
    val totalMinor: Long,
    val mode: AssignItemsSplitMode,
    val rows: List<AssignItemsSplitRowUiState>,
    val statusLabel: String,
    val error: String? = null,
) {
    val canSave: Boolean = error == null
}

data class AssignItemsSplitRowUiState(
    val participantId: String,
    val name: String,
    val colorIndex: Int,
    val quantity: Int = 0,
    val amount: String = "",
    val amountGenerated: Boolean = false,
    val amountEdited: Boolean = false,
    val percentage: String = "",
    val percentageGenerated: Boolean = false,
    val percentageEdited: Boolean = false,
    val amountLabel: String = "",
    val included: Boolean = false,
)
