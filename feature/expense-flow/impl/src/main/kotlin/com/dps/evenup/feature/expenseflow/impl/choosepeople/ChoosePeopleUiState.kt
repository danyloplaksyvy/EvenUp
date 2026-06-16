package com.dps.evenup.feature.expenseflow.impl.choosepeople

data class ChoosePeopleUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val missingDraft: Boolean = false,
    val participantNameInput: String = "",
    val participants: List<ChoosePeopleParticipantUiState> = emptyList(),
    val payerId: String? = null,
    val savedSuggestions: List<SavedParticipantSuggestionUiState> = emptyList(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val submitError: String? = null,
) {
    val canContinue: Boolean = !isLoading && !isSaving && participants.size >= 2 && payerId != null
}

data class ChoosePeopleParticipantUiState(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val isSavedLocalName: Boolean = false,
)

data class SavedParticipantSuggestionUiState(
    val name: String,
    val colorIndex: Int,
)
