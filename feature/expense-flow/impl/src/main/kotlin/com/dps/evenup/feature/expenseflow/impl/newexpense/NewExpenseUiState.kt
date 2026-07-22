package com.dps.evenup.feature.expenseflow.impl.newexpense

import com.dps.evenup.domain.expenseinput.api.AiExpensePhase

data class NewExpenseUiState(
    val isLoading: Boolean = true,
    val title: String = "Add expense",
    val helperText: String = "Describe the expense and EvenUp will organize the details.",
    val description: String = "",
    val answer: String = "",
    val phase: AiExpensePhase = AiExpensePhase.Idle,
    val isOnline: Boolean = true,
    val isRecordingDescription: Boolean = false,
    val isRecordingAnswer: Boolean = false,
    val clarificationQuestion: String? = null,
    val clarificationCandidates: List<String> = emptyList(),
    val extractedSummary: List<String> = emptyList(),
    val errorMessage: String? = null,
    val personalName: String? = null,
    val defaultCurrency: String = "USD",
    val defaultsDialogVisible: Boolean = false,
    val defaultsNameDraft: String = "",
    val defaultsCurrencyDraft: String = "USD",
    val discardDialogVisible: Boolean = false,
    val replaceDialogVisible: Boolean = false,
) {
    val isProcessing: Boolean get() = phase == AiExpensePhase.Processing
    val canSubmitDescription: Boolean get() = description.isNotBlank() && isOnline && !isProcessing
    val canSubmitAnswer: Boolean get() = answer.isNotBlank() && isOnline && !isProcessing
    val hasUnsavedInput: Boolean get() = description.isNotBlank() || extractedSummary.isNotEmpty()
    val hasPartialExtraction: Boolean get() = extractedSummary.isNotEmpty()
}

enum class NewExpenseInputMode {
    Scan,
    Manual,
}

sealed interface NewExpenseEffect {
    data object OpenReview : NewExpenseEffect
    data object OpenExtractedDetails : NewExpenseEffect
    data class OpenInputMode(val mode: NewExpenseInputMode) : NewExpenseEffect
}
