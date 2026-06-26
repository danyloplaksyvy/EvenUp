package com.dps.evenup.feature.expenseflow.impl.manualentry

import androidx.compose.runtime.Composable
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
fun ManualReceiptEntryRoute(
    draftRepository: ExpenseDraftRepository,
    validateReceipt: ValidateReceiptUseCase,
    onBack: () -> Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presenter = remember(draftRepository, validateReceipt) {
        ManualReceiptEntryPresenter(
            draftRepository = draftRepository,
            validateReceipt = validateReceipt,
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf(ManualReceiptEntryUiState()) }

    ManualReceiptEntryScreen(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                ManualReceiptEntryUiEvent.BackClick -> onBack()
                ManualReceiptEntryUiEvent.ContinueClick -> {
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
                                SaveManualReceiptDraftResult.Saved -> {
                                    onContinue()
                                    uiState.copy(isSaving = false)
                                }
                                is SaveManualReceiptDraftResult.Invalid -> {
                                    uiState.copy(
                                        isSaving = false,
                                        fieldErrors = result.fieldErrors,
                                        firstBlockingSection = result.firstBlockingSection,
                                        firstBlockingItemId = result.firstBlockingItemId,
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
