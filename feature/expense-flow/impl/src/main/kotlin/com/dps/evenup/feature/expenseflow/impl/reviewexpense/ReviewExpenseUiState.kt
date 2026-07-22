package com.dps.evenup.feature.expenseflow.impl.reviewexpense

data class ReviewExpenseUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val missingDraft: Boolean = false,
    val merchantName: String = "",
    val originalDescription: String? = null,
    val dateLabel: String = "",
    val currencyLabel: String = "",
    val pricingModeLabel: String = "",
    val itemsSummaryLabel: String = "",
    val assignmentsSummaryLabel: String = "",
    val feesSummaryLabel: String = "",
    val reviewNotices: Set<ReviewNoticeSection> = emptySet(),
    val totalLabel: String = "",
    val totalContentDescription: String = "",
    val payerName: String = "",
    val payerColorIndex: Int = 0,
    val paidByLabel: String = "",
    val participantCountLabel: String = "",
    val paidByContentDescription: String = "",
    val settlementRows: List<SettlementRowUiState> = emptyList(),
    val payerSummary: PayerSummaryUiState = PayerSummaryUiState(),
    val detailRows: List<ParticipantCalculationDetailUiState> = emptyList(),
    val detailsSheetVisible: Boolean = false,
    val balanceStatusLabel: String? = null,
    val balanceStatusContentDescription: String? = null,
    val calculationDetailsContentDescription: String = "",
    val canSave: Boolean = false,
    val validationError: String? = null,
    val submitError: String? = null,
)

enum class ReviewNoticeSection {
    Expense,
    People,
    Items,
    Fees,
    Split,
}

data class SettlementRowUiState(
    val fromParticipantName: String,
    val fromParticipantColorIndex: Int,
    val toParticipantName: String,
    val amountLabel: String,
    val actionLabel: String,
    val contentDescription: String,
)

data class PayerSummaryUiState(
    val paidLabel: String = "",
    val shareLabel: String = "",
    val resultLabel: String = "",
    val rows: List<PayerSummaryRowUiState> = emptyList(),
    val contentDescription: String = "",
)

data class PayerSummaryRowUiState(
    val label: String,
    val valueLabel: String,
    val contentDescription: String,
    val emphasized: Boolean = false,
)

data class ParticipantCalculationDetailUiState(
    val participantId: String,
    val participantName: String,
    val participantColorIndex: Int,
    val itemSubtotalLabel: String,
    val baseShareLabel: String? = null,
    val feesLabel: String,
    val discountsLabel: String? = null,
    val totalShareLabel: String,
    val amountPaidLabel: String,
    val resultLabel: String,
    val contentDescription: String,
)
