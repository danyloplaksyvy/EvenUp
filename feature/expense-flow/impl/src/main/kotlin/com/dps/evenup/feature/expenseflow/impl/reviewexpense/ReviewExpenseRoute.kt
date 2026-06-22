package com.dps.evenup.feature.expenseflow.impl.reviewexpense

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.expense.api.ExpenseDataException
import com.dps.evenup.data.expense.api.ExpenseDataFailureReason
import com.dps.evenup.data.expense.api.ExpenseRepository
import com.dps.evenup.domain.expense.api.CalculateExpenseSummaryUseCase
import com.dps.evenup.domain.expense.api.ValidateExpenseBeforeSaveUseCase
import kotlinx.coroutines.launch

@Composable
fun ReviewExpenseRoute(
    draftRepository: ExpenseDraftRepository,
    expenseRepository: ExpenseRepository,
    calculateSummary: CalculateExpenseSummaryUseCase,
    validateExpenseBeforeSave: ValidateExpenseBeforeSaveUseCase,
    onBack: () -> Boolean,
    onSaved: (shareUrl: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presenter = remember(draftRepository, expenseRepository, calculateSummary, validateExpenseBeforeSave) {
        ReviewExpensePresenter(
            draftRepository = draftRepository,
            expenseRepository = expenseRepository,
            calculateSummary = calculateSummary,
            validateExpenseBeforeSave = validateExpenseBeforeSave,
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    var uiState by remember { mutableStateOf(ReviewExpenseUiState()) }

    fun saveExpense() {
        coroutineScope.launch {
            uiState = uiState.copy(isSaving = true, submitError = null)
            uiState = try {
                when (val result = presenter.saveDraft()) {
                    is SaveReviewExpenseResult.Saved -> {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSaved(result.shareUrl)
                        uiState.copy(isSaving = false)
                    }
                    SaveReviewExpenseResult.MissingDraft -> uiState.copy(
                        isSaving = false,
                        missingDraft = true,
                        submitError = "No expense draft was found.",
                    )
                    is SaveReviewExpenseResult.Invalid -> uiState.copy(
                        isSaving = false,
                        validationError = result.message,
                        canSave = false,
                    )
                }
            } catch (error: ExpenseDataException) {
                uiState.copy(
                    isSaving = false,
                    submitError = error.toUserMessage(),
                )
            } catch (_: RuntimeException) {
                uiState.copy(
                    isSaving = false,
                    submitError = "Could not save expense. Try again.",
                )
            }
        }
    }

    LaunchedEffect(presenter) {
        uiState = try {
            presenter.load()
        } catch (_: RuntimeException) {
            ReviewExpenseUiState(
                isLoading = false,
                submitError = "Could not load expense review. Try again.",
            )
        }
    }

    ReviewExpenseScreen(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                ReviewExpenseUiEvent.BackClick -> onBack()
                ReviewExpenseUiEvent.SaveClick,
                ReviewExpenseUiEvent.SaveRetryClick,
                -> saveExpense()
                else -> {
                    coroutineScope.launch {
                        uiState = try {
                            presenter.reduce(uiState, event)
                        } catch (_: RuntimeException) {
                            uiState.copy(submitError = "Could not update expense review. Try again.")
                        }
                    }
                }
            }
        },
        modifier = modifier,
    )
}

private fun ExpenseDataException.toUserMessage(): String = when (reason) {
    ExpenseDataFailureReason.Connection -> "No internet connection. Check your connection and try saving again."
    ExpenseDataFailureReason.Timeout -> "Saving took too long. Try again."
    ExpenseDataFailureReason.Rejected -> "The expense could not be saved. Review the details and try again."
    ExpenseDataFailureReason.Unknown -> "Could not save expense. Try again."
}
