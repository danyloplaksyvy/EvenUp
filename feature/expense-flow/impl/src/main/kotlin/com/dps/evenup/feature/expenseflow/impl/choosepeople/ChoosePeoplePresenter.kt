package com.dps.evenup.feature.expenseflow.impl.choosepeople

import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.participant.api.SavedParticipantRepository
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.participant.api.ParticipantValidationError
import com.dps.evenup.domain.participant.api.SavedParticipantName
import com.dps.evenup.domain.participant.api.ValidateParticipantsUseCase
import java.util.UUID

class ChoosePeoplePresenter(
    private val draftRepository: ExpenseDraftRepository,
    private val savedParticipantRepository: SavedParticipantRepository,
    private val validateParticipants: ValidateParticipantsUseCase,
) {
    suspend fun load(): ChoosePeopleUiState {
        val draft = draftRepository.getDraft() ?: return ChoosePeopleUiState(
            isLoading = false,
            missingDraft = true,
            submitError = "No receipt draft was found.",
        )
        val savedNames = savedParticipantRepository.getSavedParticipantNames()
            .map { name -> name.value.trim() }
            .filter { name -> name.isNotBlank() }

        val participants = draft.participants.mapIndexed { index, participant ->
            ChoosePeopleParticipantUiState(
                id = participant.id.value,
                name = participant.name,
                colorIndex = index,
                isSavedLocalName = participant.isSavedLocalName,
            )
        }
        val payerId = draft.payerId.value.takeIf { id ->
            participants.any { participant -> participant.id == id }
        } ?: participants.firstOrNull()?.id

        return ChoosePeopleUiState(
            isLoading = false,
            participants = participants,
            payerId = payerId,
            savedSuggestions = savedNames.toSuggestions(participants, query = ""),
        )
    }

    suspend fun reduce(
        state: ChoosePeopleUiState,
        event: ChoosePeopleUiEvent,
    ): ChoosePeopleUiState {
        val clearedState = state.copy(fieldErrors = emptyMap(), submitError = null)
        return when (event) {
            is ChoosePeopleUiEvent.ParticipantNameInputChanged -> {
                val savedNames = savedParticipantRepository.getSavedParticipantNames().map { savedName -> savedName.value }
                clearedState.copy(
                    participantNameInput = event.value,
                    savedSuggestions = savedNames.toSuggestions(clearedState.participants, query = event.value),
                )
            }
            ChoosePeopleUiEvent.AddParticipantClick -> addParticipantFromInput(clearedState)
            is ChoosePeopleUiEvent.AddSavedParticipantClick -> addParticipant(clearedState, event.name, isSavedLocalName = true)
            is ChoosePeopleUiEvent.DeleteSavedParticipantClick -> deleteSavedParticipant(clearedState, event.name)
            is ChoosePeopleUiEvent.RemoveParticipantClick -> removeParticipant(clearedState, event.participantId)
            is ChoosePeopleUiEvent.PayerSelected -> clearedState.copy(payerId = event.participantId)
            ChoosePeopleUiEvent.BackClick,
            ChoosePeopleUiEvent.ContinueClick,
            -> state
        }
    }

    suspend fun saveDraft(state: ChoosePeopleUiState): SaveChoosePeopleResult {
        val draft = draftRepository.getDraft() ?: return SaveChoosePeopleResult.MissingDraft
        val participants = state.participants.mapIndexed { index, participant ->
            Participant(
                id = ParticipantId(participant.id),
                name = participant.name.trim(),
                creationOrder = index,
                isSavedLocalName = participant.isSavedLocalName,
            )
        }
        val payerId = state.payerId?.let(::ParticipantId) ?: ParticipantId(MISSING_PAYER_ID)
        val validation = validateParticipants.validate(participants = participants, payerId = payerId)
        if (!validation.isValid) {
            return SaveChoosePeopleResult.Invalid(validation.errors.toFieldErrors())
        }

        draftRepository.saveDraft(
            draft.copy(
                participants = participants,
                payerId = payerId,
                itemAssignments = emptyList(),
                feeAllocations = emptyList(),
            ),
        )
        participants.forEach { participant ->
            savedParticipantRepository.addSavedParticipantName(SavedParticipantName(participant.name))
        }
        return SaveChoosePeopleResult.Saved
    }

    private suspend fun addParticipantFromInput(state: ChoosePeopleUiState): ChoosePeopleUiState {
        return addParticipant(state, state.participantNameInput, isSavedLocalName = false)
    }

    private suspend fun addParticipant(
        state: ChoosePeopleUiState,
        rawName: String,
        isSavedLocalName: Boolean,
    ): ChoosePeopleUiState {
        val name = rawName.trim()
        if (name.isBlank()) {
            return state.copy(fieldErrors = mapOf("participantName" to "Enter a name."))
        }
        if (name.length > MAX_PARTICIPANT_NAME_LENGTH) {
            return state.copy(fieldErrors = mapOf("participantName" to "Use ${MAX_PARTICIPANT_NAME_LENGTH} characters or fewer."))
        }
        if (state.participants.any { participant -> participant.name.equals(name, ignoreCase = true) }) {
            return state.copy(fieldErrors = mapOf("participantName" to "$name is already selected."))
        }

        val participant = ChoosePeopleParticipantUiState(
            id = "participant-${UUID.randomUUID()}",
            name = name,
            colorIndex = state.participants.size,
            isSavedLocalName = isSavedLocalName,
        )
        val participants = state.participants + participant
        val savedNames = savedParticipantRepository.getSavedParticipantNames().map { savedName -> savedName.value }
        return state.copy(
            participantNameInput = "",
            participants = participants,
            payerId = state.payerId ?: participant.id,
            savedSuggestions = savedNames.toSuggestions(participants, query = ""),
        )
    }

    private suspend fun deleteSavedParticipant(
        state: ChoosePeopleUiState,
        rawName: String,
    ): ChoosePeopleUiState {
        val name = rawName.trim()
        if (name.isNotBlank()) {
            savedParticipantRepository.deleteSavedParticipantName(SavedParticipantName(name))
        }
        val savedNames = savedParticipantRepository.getSavedParticipantNames().map { savedName -> savedName.value }
        return state.copy(savedSuggestions = savedNames.toSuggestions(state.participants, query = state.participantNameInput))
    }

    private suspend fun removeParticipant(
        state: ChoosePeopleUiState,
        participantId: String,
    ): ChoosePeopleUiState {
        val participants = state.participants
            .filterNot { participant -> participant.id == participantId }
            .mapIndexed { index, participant -> participant.copy(colorIndex = index) }
        val payerId = state.payerId
            ?.takeIf { id -> participants.any { participant -> participant.id == id } }
            ?: participants.firstOrNull()?.id
        val savedNames = savedParticipantRepository.getSavedParticipantNames().map { savedName -> savedName.value }
        return state.copy(
            participants = participants,
            payerId = payerId,
            savedSuggestions = savedNames.toSuggestions(participants, query = state.participantNameInput),
        )
    }

    private fun List<String>.toSuggestions(
        participants: List<ChoosePeopleParticipantUiState>,
        query: String,
    ): List<SavedParticipantSuggestionUiState> {
        val normalizedQuery = query.trim()
        return map { name -> name.trim() }
            .filter { name -> name.isNotBlank() }
            .distinctBy { name -> name.lowercase() }
            .filterNot { name ->
                participants.any { participant -> participant.name.equals(name, ignoreCase = true) }
            }
            .filter { name ->
                normalizedQuery.isBlank() || name.contains(normalizedQuery, ignoreCase = true)
            }
            .mapIndexed { index, name ->
                SavedParticipantSuggestionUiState(
                    name = name,
                    colorIndex = participants.size + index,
                )
            }
    }

    private fun Set<ParticipantValidationError>.toFieldErrors(): Map<String, String> = associate { error ->
        when (error) {
            ParticipantValidationError.TooFewParticipants -> "participants" to "Add at least two people."
            ParticipantValidationError.BlankParticipantName -> "participants" to "Each person needs a name."
            ParticipantValidationError.DuplicateParticipantId -> "participants" to "Participant ids must be unique."
            ParticipantValidationError.PayerNotFound -> "payer" to "Choose who paid."
        }
    }

    private companion object {
        const val MISSING_PAYER_ID = "missing-payer"
    }
}

sealed interface SaveChoosePeopleResult {
    data object Saved : SaveChoosePeopleResult

    data object MissingDraft : SaveChoosePeopleResult

    data class Invalid(val fieldErrors: Map<String, String>) : SaveChoosePeopleResult
}
