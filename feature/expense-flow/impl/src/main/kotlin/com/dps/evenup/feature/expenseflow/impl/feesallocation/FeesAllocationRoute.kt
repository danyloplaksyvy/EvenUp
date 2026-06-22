package com.dps.evenup.feature.expenseflow.impl.feesallocation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.domain.expense.api.AllocateFeesUseCase
import kotlinx.coroutines.launch

@Composable
fun FeesAllocationRoute(
    draftRepository: ExpenseDraftRepository,
    allocateFees: AllocateFeesUseCase,
    onBack: () -> Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presenter = remember(draftRepository, allocateFees) {
        FeesAllocationPresenter(
            draftRepository = draftRepository,
            allocateFees = allocateFees,
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf(FeesAllocationUiState()) }

    LaunchedEffect(presenter) {
        uiState = try {
            val draft = draftRepository.getDraft()
            if (draft != null && !shouldShowFeesAllocation(draft)) {
                onContinue()
                return@LaunchedEffect
            }
            presenter.load()
        } catch (_: RuntimeException) {
            FeesAllocationUiState(
                isLoading = false,
                submitError = "Could not load fees. Try again.",
            )
        }
    }

    FeesAllocationScreen(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                FeesAllocationUiEvent.BackClick -> onBack()
                FeesAllocationUiEvent.ContinueClick -> {
                    coroutineScope.launch {
                        uiState = uiState.copy(isSaving = true, fieldErrors = emptyMap(), submitError = null)
                        uiState = try {
                            when (val result = presenter.saveDraft(uiState)) {
                                SaveFeesAllocationResult.Saved -> {
                                    onContinue()
                                    uiState.copy(isSaving = false)
                                }
                                SaveFeesAllocationResult.MissingDraft -> {
                                    uiState.copy(
                                        isSaving = false,
                                        missingDraft = true,
                                        submitError = "No expense draft was found.",
                                    )
                                }
                                is SaveFeesAllocationResult.Invalid -> {
                                    uiState.copy(isSaving = false, fieldErrors = result.fieldErrors)
                                }
                            }
                        } catch (_: RuntimeException) {
                            uiState.copy(
                                isSaving = false,
                                submitError = "Could not save fee allocations. Try again.",
                            )
                        }
                    }
                }
                else -> {
                    coroutineScope.launch {
                        uiState = try {
                            presenter.reduce(uiState, event)
                        } catch (_: RuntimeException) {
                            uiState.copy(submitError = "Could not update fee allocations. Try again.")
                        }
                    }
                }
            }
        },
        modifier = modifier,
    )
}
