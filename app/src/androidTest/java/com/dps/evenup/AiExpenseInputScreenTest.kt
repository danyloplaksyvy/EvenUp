package com.dps.evenup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.domain.expenseinput.api.AiExpensePhase
import com.dps.evenup.feature.expenseflow.impl.newexpense.NewExpenseScreen
import com.dps.evenup.feature.expenseflow.impl.newexpense.NewExpenseUiState
import org.junit.Rule
import org.junit.Test

class AiExpenseInputScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun offlineStateKeepsAlternativeInputMethodsVisible() {
        render(NewExpenseUiState(description = "Dinner with Lee", isOnline = false, isLoading = false))

        composeRule.onNodeWithText("You're offline. Keep editing, then send when you're connected.").assertIsDisplayed()
        composeRule.onNodeWithText("Scan receipt").assertIsDisplayed()
        composeRule.onNodeWithText("Enter manually").assertIsDisplayed()
    }

    @Test
    fun clarificationKeepsDescriptionAndRequiredPayerQuestionVisible() {
        render(
            NewExpenseUiState(
                description = "Dinner was $48 with Lee",
                phase = AiExpensePhase.NeedsClarification,
                clarificationQuestion = "Who paid for this expense?",
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("Dinner was $48 with Lee").assertIsDisplayed()
        composeRule.onNodeWithText("Who paid for this expense?").assertIsDisplayed()
        composeRule.onNodeWithText("Review all details").assertIsDisplayed()
    }

    @Test
    fun recordingStateExposesStopControl() {
        render(
            NewExpenseUiState(
                description = "Dinner",
                isRecordingDescription = true,
                isLoading = false,
            ),
        )

        composeRule.onNodeWithContentDescription("Stop dictation").assertIsDisplayed()
    }

    @Test
    fun processingOverlayExposesCancelAndHidesUnderlyingActions() {
        render(
            NewExpenseUiState(
                description = "Dinner",
                phase = AiExpensePhase.Processing,
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("Organizing expense…").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Cancel processing").assertIsDisplayed()
        composeRule.onAllNodesWithText("Scan receipt").assertCountEquals(0)
        composeRule.onAllNodesWithText("Defaults: name not set · USD").assertCountEquals(0)
    }

    @Test
    fun partialExtractionUsesReviewActionWithoutInlineSummaryCard() {
        render(
            NewExpenseUiState(
                description = "Dinner was $48 with Lee",
                extractedSummary = listOf("Dinner", "USD 48.00"),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("Review all details").assertIsDisplayed()
        composeRule.onAllNodesWithText("Extracted details").assertCountEquals(0)
        composeRule.onAllNodesWithText("transactionDate").assertCountEquals(0)
    }

    @Test
    fun regenerationWarningExplainsManualEditReplacement() {
        render(NewExpenseUiState(replaceDialogVisible = true, isLoading = false))

        composeRule.onNodeWithText("Replace manual edits?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Interpreting the changed description again will replace structured details you edited manually.",
        ).assertIsDisplayed()
    }

    private fun render(state: NewExpenseUiState) {
        composeRule.setContent {
            EvenUpTheme {
                NewExpenseScreen(uiState = state, onEvent = {})
            }
        }
    }
}
