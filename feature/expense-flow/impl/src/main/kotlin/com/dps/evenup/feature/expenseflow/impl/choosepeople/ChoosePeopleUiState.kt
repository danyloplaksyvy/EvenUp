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
                "${participant.name}, involved, payer. Double tap to keep ${participant.name} as payer."
            } else {
                "${participant.name}, involved. Double tap to set ${participant.name} as payer."
            },
        )
    }
    val selectedCountLabel: String = participants.size.takeIf { count -> count > 0 }?.toString().orEmpty()
    val helperText: String = when {
        participants.size < 2 -> "Add at least two people for this expense."
        payerId == null || participants.none { participant -> participant.id == payerId } -> "Choose who paid for this expense."
        else -> "Tap a person to change who paid."
    }
    val shouldShowHelperText: Boolean = participants.size < 2 ||
        payerId == null ||
        participants.none { participant -> participant.id == payerId } ||
        participants.size > 1
    val canContinue: Boolean = !isLoading && !isSaving && participants.size >= 2 &&
        payerId != null && participants.any { participant -> participant.id == payerId }
    val footerPrimaryLabel: String = if (participants.size < 2) "Add person" else "Continue"
    val footerContentDescription: String = when {
        canContinue -> "Continue to assign items"
        participants.size < 2 -> "Add person disabled. Add at least two people for this expense."
        payerId == null || participants.none { participant -> participant.id == payerId } ->
            "Continue disabled. Choose who paid for this expense."
        else -> "Continue disabled."
    }
    val typedAddLabel: String? = participantNameInput.trim().takeIf { name ->
        name.isNotBlank() &&
            participants.none { participant -> participant.name.equals(name, ignoreCase = true) } &&
            name.length <= MAX_PARTICIPANT_NAME_LENGTH &&
            savedSuggestions.none { suggestion -> suggestion.name.equals(name, ignoreCase = true) }
    }?.let { name -> "Add “$name”" }
}

const val MAX_PARTICIPANT_NAME_LENGTH = 40

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
