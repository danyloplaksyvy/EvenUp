package com.dps.evenup.feature.expenseflow.impl.newexpense

import com.dps.evenup.domain.expenseinput.api.AiExpensePhase
import com.dps.evenup.feature.expenseflow.api.NewExpenseDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewExpenseStateTest {
    @Test
    fun `submission requires non-empty online non-processing input`() {
        assertFalse(NewExpenseUiState(description = "").canSubmitDescription)
        assertFalse(NewExpenseUiState(description = "Dinner", isOnline = false).canSubmitDescription)
        assertFalse(
            NewExpenseUiState(description = "Dinner", phase = AiExpensePhase.Processing).canSubmitDescription,
        )
        assertTrue(NewExpenseUiState(description = "Dinner").canSubmitDescription)
    }

    @Test
    fun `partial transcripts replace only the current dictation segment`() {
        val prefix = "Dana paid for"

        assertEquals("Dana paid for dinner", mergeDictationText(prefix, "dinner"))
        assertEquals("Dana paid for dinner with Lee", mergeDictationText(prefix, "dinner with Lee"))
        assertEquals(prefix, mergeDictationText(prefix, ""))
    }

    @Test
    fun `clarification answer follows the same offline and processing guards`() {
        assertFalse(NewExpenseUiState(answer = "Dana", isOnline = false).canSubmitAnswer)
        assertFalse(NewExpenseUiState(answer = "Dana", phase = AiExpensePhase.Processing).canSubmitAnswer)
        assertTrue(NewExpenseUiState(answer = "Dana", phase = AiExpensePhase.NeedsClarification).canSubmitAnswer)
    }

    @Test
    fun `partial extraction exposes review action without requiring inline details`() {
        val state = NewExpenseUiState(extractedSummary = listOf("Dinner", "USD 48.00"))

        assertTrue(state.hasPartialExtraction)
    }

    @Test
    fun `fresh add expense destinations always have different entry identity`() {
        val first = NewExpenseDestination.fresh()
        val second = NewExpenseDestination.fresh()

        assertTrue(first.instanceId.isNotBlank())
        assertTrue(second.instanceId.isNotBlank())
        assertFalse(first == second)
    }
}
