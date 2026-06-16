package com.dps.evenup.feature.expenseflow.impl.assignitems

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.domain.expense.api.ValidateItemAssignmentsUseCase
import kotlinx.coroutines.launch

@Composable
fun AssignItemsRoute(
    draftRepository: ExpenseDraftRepository,
    validateItemAssignments: ValidateItemAssignmentsUseCase,
    onBack: () -> Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presenter = remember(draftRepository, validateItemAssignments) {
        AssignItemsPresenter(
            draftRepository = draftRepository,
            validateItemAssignments = validateItemAssignments,
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf(AssignItemsUiState()) }

    LaunchedEffect(presenter) {
        uiState = try {
            presenter.load()
        } catch (_: RuntimeException) {
            AssignItemsUiState(
                isLoading = false,
                submitError = "Could not load item assignments. Try again.",
            )
        }
    }

    AssignItemsScreen(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                AssignItemsUiEvent.BackClick -> onBack()
                AssignItemsUiEvent.ContinueClick -> {
                    coroutineScope.launch {
                        uiState = uiState.copy(isSaving = true, fieldErrors = emptyMap(), submitError = null)
                        uiState = try {
                            when (val result = presenter.saveDraft(uiState)) {
                                SaveAssignItemsResult.Saved -> {
                                    onContinue()
                                    uiState.copy(isSaving = false)
                                }
                                SaveAssignItemsResult.MissingDraft -> {
                                    uiState.copy(
                                        isSaving = false,
                                        missingDraft = true,
                                        submitError = "No expense draft was found.",
                                    )
                                }
                                is SaveAssignItemsResult.Invalid -> {
                                    uiState.copy(isSaving = false, fieldErrors = result.fieldErrors)
                                }
                            }
                        } catch (_: RuntimeException) {
                            uiState.copy(
                                isSaving = false,
                                submitError = "Could not save item assignments. Try again.",
                            )
                        }
                    }
                }
                else -> {
                    coroutineScope.launch {
                        uiState = try {
                            presenter.reduce(uiState, event)
                        } catch (_: RuntimeException) {
                            uiState.copy(submitError = "Could not update item assignments. Try again.")
                        }
                    }
                }
            }
        },
        modifier = modifier,
    )
}
