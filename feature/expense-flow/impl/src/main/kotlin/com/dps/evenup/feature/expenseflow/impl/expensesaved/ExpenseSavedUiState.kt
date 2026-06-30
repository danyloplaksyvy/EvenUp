package com.dps.evenup.feature.expenseflow.impl.expensesaved

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ExpenseSavedUiState(
    val shareUrl: String,
    val guestPasscode: String,
    val isWorking: Boolean = false,
    val snackbarMessage: String? = null,
    val snackbarMessageId: Long = 0L,
) {
    val bareShareLink: String = shareUrl.trim()
    val normalizedGuestCode: String = guestPasscode.trim().uppercase()
    val hasShareLink: Boolean = bareShareLink.isNotBlank()
    val hasGuestCode: Boolean = normalizedGuestCode.isNotBlank()
    val canCopyLink: Boolean = hasShareLink
    val canCopyCode: Boolean = hasGuestCode
    val canShareInvite: Boolean = hasShareLink && hasGuestCode
    val fullInviteMessage: String = buildInviteMessage(bareShareLink, normalizedGuestCode)
    val copyLinkPayload: String = bareShareLink
    val copyCodePayload: String = normalizedGuestCode
    val qrAccessUrl: String? = buildQrAccessUrl(bareShareLink, normalizedGuestCode)
    val canShowQr: Boolean = qrAccessUrl != null
    val canOpenQr: Boolean = qrAccessUrl != null
    val guestCodeHelperText: String = if (hasGuestCode) "For manual access only" else "Not available"
    val guestCodeCopyContentDescription: String = if (hasGuestCode) {
        "Guest code $normalizedGuestCode. Tap to copy only the guest code."
    } else {
        "Guest code is not available"
    }
    val copyLinkContentDescription: String = "Copy only the expense share link"
}

private fun buildInviteMessage(
    bareShareLink: String,
    guestCode: String,
): String {
    val lines = mutableListOf("EvenUp expense breakdown")
    if (bareShareLink.isNotBlank()) {
        lines += bareShareLink
    }
    if (guestCode.isNotBlank()) {
        lines += "Guest code: $guestCode"
    }
    return lines.joinToString(separator = "\n")
}

internal fun buildQrAccessUrl(
    bareShareLink: String,
    guestCode: String,
): String? {
    if (bareShareLink.isBlank() || guestCode.isBlank()) {
        return null
    }

    val fragmentStart = bareShareLink.indexOf('#')
    val base = if (fragmentStart >= 0) bareShareLink.substring(0, fragmentStart) else bareShareLink
    val fragment = if (fragmentStart >= 0) bareShareLink.substring(fragmentStart) else ""
    val separator = if (base.contains('?')) "&" else "?"
    val encodedCode = URLEncoder.encode(guestCode, StandardCharsets.UTF_8.name())
    return "$base${separator}code=$encodedCode$fragment"
}
