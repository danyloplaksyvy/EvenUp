package com.dps.evenup.feature.expenseflow.impl.expensesaved

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseSavedUiStateTest {
    @Test
    fun `share and copy payloads are explicit`() {
        val state = ExpenseSavedUiState(
            shareUrl = " https://evenup.example/e/A8xQ2Lm9 ",
            guestPasscode = " ktrq ",
        )

        assertEquals("https://evenup.example/e/A8xQ2Lm9", state.bareShareLink)
        assertEquals("KTRQ", state.normalizedGuestCode)
        assertEquals("https://evenup.example/e/A8xQ2Lm9", state.copyLinkPayload)
        assertEquals("KTRQ", state.copyCodePayload)
        assertEquals(
            "EvenUp expense breakdown\nhttps://evenup.example/e/A8xQ2Lm9\nGuest code: KTRQ",
            state.copyInvitePayload,
        )
        assertEquals(state.copyInvitePayload, state.fullInviteMessage)
        assertTrue(state.canCopyLink)
        assertTrue(state.canCopyCode)
        assertTrue(state.canShareInvite)
    }

    @Test
    fun `qr access url appends guest code without changing bare link`() {
        val state = ExpenseSavedUiState(
            shareUrl = "https://evenup.example/e/A8xQ2Lm9",
            guestPasscode = "KTRQ",
        )

        assertEquals("https://evenup.example/e/A8xQ2Lm9", state.copyLinkPayload)
        assertEquals("https://evenup.example/e/A8xQ2Lm9?code=KTRQ", state.qrAccessUrl)
        assertTrue(state.canShowQr)
    }

    @Test
    fun `qr access url preserves existing query and fragment`() {
        assertEquals(
            "https://evenup.example/e/A8xQ2Lm9?source=share&code=KTRQ#breakdown",
            buildQrAccessUrl("https://evenup.example/e/A8xQ2Lm9?source=share#breakdown", "KTRQ"),
        )
    }

    @Test
    fun `missing link disables invite and qr but still allows code copy`() {
        val state = ExpenseSavedUiState(
            shareUrl = "",
            guestPasscode = "KTRQ",
        )

        assertFalse(state.canCopyLink)
        assertTrue(state.canCopyCode)
        assertFalse(state.canShareInvite)
        assertFalse(state.canShowQr)
        assertNull(state.qrAccessUrl)
    }

    @Test
    fun `missing guest code disables invite and qr but still allows link copy`() {
        val state = ExpenseSavedUiState(
            shareUrl = "https://evenup.example/e/A8xQ2Lm9",
            guestPasscode = "",
        )

        assertTrue(state.canCopyLink)
        assertFalse(state.canCopyCode)
        assertFalse(state.canShareInvite)
        assertFalse(state.canShowQr)
        assertNull(state.qrAccessUrl)
    }
}
