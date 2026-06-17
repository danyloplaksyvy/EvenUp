package com.dps.evenup.feature.expenseflow.impl.choosepeople

sealed interface ChoosePeopleUiEvent {
    data class ParticipantNameInputChanged(val value: String) : ChoosePeopleUiEvent

    data object AddParticipantClick : ChoosePeopleUiEvent

    data class AddSavedParticipantClick(val name: String) : ChoosePeopleUiEvent

    data class DeleteSavedParticipantClick(val name: String) : ChoosePeopleUiEvent

    data class RemoveParticipantClick(val participantId: String) : ChoosePeopleUiEvent

    data class PayerSelected(val participantId: String) : ChoosePeopleUiEvent

    data object ContinueClick : ChoosePeopleUiEvent

    data object BackClick : ChoosePeopleUiEvent
}
