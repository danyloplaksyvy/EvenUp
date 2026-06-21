package com.dps.evenup.feature.expenseflow.impl.receiptreview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.domain.receipt.api.ValidateReceiptUseCase
import kotlinx.coroutines.launch

@Composable
fun ReceiptReviewRoute(
    draftRepository: ExpenseDraftRepository,
    validateReceipt: ValidateReceiptUseCase,
    onBack: () -> Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presenter = remember(draftRepository, validateReceipt) {
        ReceiptReviewPresenter(
            draftRepository = draftRepository,
            validateReceipt = validateReceipt,
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf(ReceiptReviewUiState()) }

    LaunchedEffect(presenter) {
        uiState = try {
            presenter.load()
        } catch (_: RuntimeException) {
            ReceiptReviewUiState(
                isLoading = false,
                submitError = "Could not load this receipt. Try again.",
            )
        }
    }

    ReceiptReviewScreen(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                ReceiptReviewUiEvent.BackClick -> onBack()
                ReceiptReviewUiEvent.ContinueClick -> {
                    coroutineScope.launch {
                        val validatedState = presenter.validateVisibleState(
                            uiState.copy(fieldErrors = emptyMap(), submitError = null),
                        )
                        if (validatedState.fieldErrors.isNotEmpty()) {
                            uiState = validatedState
                            return@launch
                        }
                        uiState = validatedState.copy(isSaving = true)
                        uiState = try {
                            when (val result = presenter.saveDraft(uiState)) {
                                SaveReceiptReviewResult.Saved -> {
                                    onContinue()
                                    uiState.copy(isSaving = false)
                                }
                                SaveReceiptReviewResult.MissingDraft -> {
                                    uiState.copy(
                                        isSaving = false,
                                        missingDraft = true,
                                        submitError = "No receipt draft was found.",
                                    )
                                }
                                is SaveReceiptReviewResult.Invalid -> {
                                    uiState.copy(
                                        isSaving = false,
                                        fieldErrors = result.fieldErrors,
                                        firstBlockingSection = result.firstBlockingSection,
                                        validationRequestId = uiState.validationRequestId + 1,
                                    )
                                }
                            }
                        } catch (_: RuntimeException) {
                            uiState.copy(
                                isSaving = false,
                                submitError = "Could not save this receipt. Try again.",
                            )
                        }
                    }
                }
                else -> uiState = presenter.reduce(uiState, event)
            }
        },
        modifier = modifier,
    )
}
