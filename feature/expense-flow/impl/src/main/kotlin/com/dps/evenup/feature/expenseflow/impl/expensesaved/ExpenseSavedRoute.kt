package com.dps.evenup.feature.expenseflow.impl.expensesaved

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.expenseinput.api.AiExpenseSessionRepository
import kotlinx.coroutines.launch

@Composable
fun ExpenseSavedRoute(
    shareUrl: String,
    guestPasscode: String,
    draftRepository: ExpenseDraftRepository,
    aiSessionRepository: AiExpenseSessionRepository,
    onAddAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var uiState by remember(shareUrl, guestPasscode) {
        mutableStateOf(
            ExpenseSavedUiState(
                shareUrl = shareUrl,
                guestPasscode = guestPasscode,
            ),
        )
    }

    ExpenseSavedScreen(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                ExpenseSavedUiEvent.ShareInviteClick -> {
                    if (uiState.canShareInvite) {
                        shareInvite(context, uiState.fullInviteMessage)
                    } else {
                        uiState = uiState.withSnackbar("Share invite is not ready yet")
                    }
                }
                ExpenseSavedUiEvent.CopyLinkClick -> {
                    if (uiState.canCopyLink) {
                        copyText(context, "EvenUp share link", uiState.copyLinkPayload)
                        uiState = uiState.withSnackbar("Link copied")
                    } else {
                        uiState = uiState.withSnackbar("Share link is not ready yet")
                    }
                }
                ExpenseSavedUiEvent.CopyCodeClick -> {
                    if (uiState.canCopyCode) {
                        copyText(context, "EvenUp guest code", uiState.copyCodePayload)
                        uiState = uiState.withSnackbar("Code copied")
                    } else {
                        uiState = uiState.withSnackbar("Guest code is not ready yet")
                    }
                }
                ExpenseSavedUiEvent.QrOpenClick -> {
                    val qrAccessUrl = uiState.qrAccessUrl
                    if (qrAccessUrl != null) {
                        if (!openLink(context, qrAccessUrl)) {
                            uiState = uiState.withSnackbar("Could not open breakdown")
                        }
                    } else {
                        uiState = uiState.withSnackbar("Breakdown link is not ready yet")
                    }
                }
                ExpenseSavedUiEvent.AddAnotherClick -> {
                    coroutineScope.launch {
                        uiState = uiState.copy(isWorking = true, snackbarMessage = null)
                        try {
                            draftRepository.clearDraft()
                            aiSessionRepository.clearSession()
                            onAddAnother()
                        } catch (_: RuntimeException) {
                            uiState = uiState
                                .copy(isWorking = false)
                                .withSnackbar("Could not start a new expense. Try again.")
                        }
                    }
                }
            }
        },
        modifier = modifier,
    )
}

private fun ExpenseSavedUiState.withSnackbar(message: String): ExpenseSavedUiState {
    return copy(
        snackbarMessage = message,
        snackbarMessageId = snackbarMessageId + 1L,
    )
}

private fun shareInvite(
    context: Context,
    inviteMessage: String,
) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, inviteMessage)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share EvenUp invite"))
}

private fun copyText(
    context: Context,
    label: String,
    value: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun openLink(
    context: Context,
    url: String,
): Boolean {
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        true
    } catch (_: RuntimeException) {
        false
    }
}
