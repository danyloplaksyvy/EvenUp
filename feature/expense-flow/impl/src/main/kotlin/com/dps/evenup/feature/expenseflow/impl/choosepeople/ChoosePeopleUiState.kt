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
    val selectedParticipants: List<SelectedParticipantUiState> = participants.map { participant ->
        val isPayer = participant.id == payerId
        SelectedParticipantUiState(
            id = participant.id,
            name = participant.name,
            colorIndex = participant.colorIndex,
            isSavedLocalName = participant.isSavedLocalName,
            isPayer = isPayer,
            payerActionLabel = if (isPayer) "Payer" else "Set payer",
            removeContentDescription = "Remove ${participant.name}",
            setPayerContentDescription = if (isPayer) {
                "${participant.name}, payer"
            } else {
                "Set ${participant.name} as payer"
            },
        )
    }
    val selectedCountLabel: String = when (participants.size) {
        0 -> ""
        else -> participants.size.toString()
    }
    val helperText: String = when {
        participants.isEmpty() -> "Add at least 2 people to continue."
        participants.size == 1 -> "Add 1 more person to continue."
        payerId == null || participants.none { participant -> participant.id == payerId } -> "Choose who paid to continue."
        else -> "Tap a person to change who paid."
    }
    val typedAddLabel: String? = participantNameInput.trim().takeIf { name ->
        name.isNotBlank() &&
            participants.none { participant -> participant.name.equals(name, ignoreCase = true) } &&
            savedSuggestions.none { suggestion -> suggestion.name.equals(name, ignoreCase = true) }
    }?.let { name -> "Add “$name”" }
    val canContinue: Boolean = !isLoading && !isSaving && participants.size >= 2 &&
        payerId != null && participants.any { participant -> participant.id == payerId }
}

data class ChoosePeopleParticipantUiState(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val isSavedLocalName: Boolean = false,
)

data class SelectedParticipantUiState(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val isSavedLocalName: Boolean,
    val isPayer: Boolean,
    val payerActionLabel: String,
    val removeContentDescription: String,
    val setPayerContentDescription: String,
)

data class SavedParticipantSuggestionUiState(
    val name: String,
    val colorIndex: Int,
)
