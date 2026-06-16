package com.dps.evenup.feature.expenseflow.impl.reviewexpense

data class ReviewExpenseUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val missingDraft: Boolean = false,
    val merchantName: String = "",
    val totalLabel: String = "",
    val payerName: String = "",
    val payerColorIndex: Int = 0,
    val settlementRows: List<SettlementRowUiState> = emptyList(),
    val detailRows: List<ParticipantCalculationDetailUiState> = emptyList(),
    val detailsExpanded: Boolean = false,
    val payerShareLabel: String = "",
    val canSave: Boolean = false,
    val validationError: String? = null,
    val submitError: String? = null,
)

data class SettlementRowUiState(
    val fromParticipantName: String,
    val fromParticipantColorIndex: Int,
    val toParticipantName: String,
    val amountLabel: String,
)

data class ParticipantCalculationDetailUiState(
    val participantId: String,
    val participantName: String,
    val participantColorIndex: Int,
    val itemSubtotalLabel: String,
    val feesLabel: String,
    val totalShareLabel: String,
    val amountPaidLabel: String,
    val netBalanceLabel: String,
)
