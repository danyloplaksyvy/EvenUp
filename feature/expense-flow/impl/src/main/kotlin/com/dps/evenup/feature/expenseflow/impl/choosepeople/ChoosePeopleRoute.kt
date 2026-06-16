package com.dps.evenup.feature.expenseflow.impl.choosepeople

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.participant.api.SavedParticipantRepository
import com.dps.evenup.domain.participant.api.ValidateParticipantsUseCase
import kotlinx.coroutines.launch

@Composable
fun ChoosePeopleRoute(
    draftRepository: ExpenseDraftRepository,
    savedParticipantRepository: SavedParticipantRepository,
    validateParticipants: ValidateParticipantsUseCase,
    onBack: () -> Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presenter = remember(draftRepository, savedParticipantRepository, validateParticipants) {
        ChoosePeoplePresenter(
            draftRepository = draftRepository,
            savedParticipantRepository = savedParticipantRepository,
            validateParticipants = validateParticipants,
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf(ChoosePeopleUiState()) }

    LaunchedEffect(presenter) {
        uiState = try {
            presenter.load()
        } catch (_: RuntimeException) {
            ChoosePeopleUiState(
                isLoading = false,
                submitError = "Could not load people. Try again.",
            )
        }
    }

    ChoosePeopleScreen(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                ChoosePeopleUiEvent.BackClick -> onBack()
                ChoosePeopleUiEvent.ContinueClick -> {
                    coroutineScope.launch {
                        uiState = uiState.copy(isSaving = true, fieldErrors = emptyMap(), submitError = null)
                        uiState = try {
                            when (val result = presenter.saveDraft(uiState)) {
                                SaveChoosePeopleResult.Saved -> {
                                    onContinue()
                                    uiState.copy(isSaving = false)
                                }
                                SaveChoosePeopleResult.MissingDraft -> {
                                    uiState.copy(
                                        isSaving = false,
                                        missingDraft = true,
                                        submitError = "No receipt draft was found.",
                                    )
                                }
                                is SaveChoosePeopleResult.Invalid -> {
                                    uiState.copy(isSaving = false, fieldErrors = result.fieldErrors)
                                }
                            }
                        } catch (_: RuntimeException) {
                            uiState.copy(
                                isSaving = false,
                                submitError = "Could not save people. Try again.",
                            )
                        }
                    }
                }
                else -> {
                    coroutineScope.launch {
                        uiState = try {
                            presenter.reduce(uiState, event)
                        } catch (_: RuntimeException) {
                            uiState.copy(submitError = "Could not update people. Try again.")
                        }
                    }
                }
            }
        },
        modifier = modifier,
    )
}
