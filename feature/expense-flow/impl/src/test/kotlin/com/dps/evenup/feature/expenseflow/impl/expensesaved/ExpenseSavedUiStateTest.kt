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
            state.fullInviteMessage,
        )
        assertFalse(state.copyLinkPayload.contains("Guest code"))
        assertFalse(state.copyLinkPayload.contains("EvenUp expense breakdown"))
        assertTrue(state.canCopyLink)
        assertTrue(state.canCopyCode)
        assertTrue(state.canShareInvite)
        assertEquals("For manual access only", state.guestCodeHelperText)
        assertEquals(
            "Guest code KTRQ. Tap to copy only the guest code.",
            state.guestCodeCopyContentDescription,
        )
        assertEquals("Copy only the expense share link", state.copyLinkContentDescription)
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
        assertTrue(state.canOpenQr)
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
        assertFalse(state.canOpenQr)
        assertEquals("For manual access only", state.guestCodeHelperText)
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
        assertFalse(state.canOpenQr)
        assertEquals("Not available", state.guestCodeHelperText)
        assertEquals("Guest code is not available", state.guestCodeCopyContentDescription)
        assertNull(state.qrAccessUrl)
    }

    @Test
    fun `long share urls are copied as full bare link`() {
        val longUrl = "https://evenup.example/e/A8xQ2Lm9?source=very-long-share-source&campaign=summer-dinner"
        val state = ExpenseSavedUiState(
            shareUrl = " $longUrl ",
            guestPasscode = "KTRQ",
        )

        assertEquals(longUrl, state.bareShareLink)
        assertEquals(longUrl, state.copyLinkPayload)
        assertEquals("KTRQ", state.copyCodePayload)
    }

    @Test
    fun `snackbar state can represent repeated feedback`() {
        val state = ExpenseSavedUiState(
            shareUrl = "https://evenup.example/e/A8xQ2Lm9",
            guestPasscode = "KTRQ",
            snackbarMessage = "Link copied",
            snackbarMessageId = 2L,
        )

        assertEquals("Link copied", state.snackbarMessage)
        assertEquals(2L, state.snackbarMessageId)
    }
}
