package com.dps.evenup.feature.expenseflow.impl.assignitems

data class AssignItemsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val missingDraft: Boolean = false,
    val merchantName: String = "",
    val dateLabel: String? = null,
    val participants: List<AssignItemsParticipantUiState> = emptyList(),
    val selectedParticipantId: String? = null,
    val items: List<AssignItemsReceiptItemUiState> = emptyList(),
    val splitSheet: AssignItemsSplitSheetUiState? = null,
    val subtotalLabel: String = "",
    val progressLabel: String = "0 of 0 items assigned",
    val canContinue: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val submitError: String? = null,
) {
    val canApplyEqualSplit: Boolean = participants.size >= 2 && items.isNotEmpty() && !isSaving
}

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
    val quantityLabel: String,
    val unitPriceLabel: String,
    val totalLabel: String,
    val assignmentState: AssignItemsItemState,
    val splitMode: AssignItemsSplitMode,
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
    val percentage: String = "",
    val included: Boolean = false,
)
