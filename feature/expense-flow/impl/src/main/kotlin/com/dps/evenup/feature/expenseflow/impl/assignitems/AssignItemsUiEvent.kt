package com.dps.evenup.feature.expenseflow.impl.assignitems

sealed interface AssignItemsUiEvent {
    data class ParticipantSelected(val participantId: String) : AssignItemsUiEvent

    data class ItemTapped(val itemId: String) : AssignItemsUiEvent

    data class ItemSplitClick(val itemId: String) : AssignItemsUiEvent

    data object SplitDismissed : AssignItemsUiEvent

    data class SplitModeSelected(val mode: AssignItemsSplitMode) : AssignItemsUiEvent

    data class SplitQuantityChanged(val participantId: String, val delta: Int) : AssignItemsUiEvent

    data class SplitSharedParticipantToggled(val participantId: String) : AssignItemsUiEvent

    data class SplitCustomAmountChanged(val participantId: String, val value: String) : AssignItemsUiEvent

    data class SplitPercentageChanged(val participantId: String, val value: String) : AssignItemsUiEvent

    data object SplitSaveClick : AssignItemsUiEvent

    data object ContinueClick : AssignItemsUiEvent

    data object BackClick : AssignItemsUiEvent
}
