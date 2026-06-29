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
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import kotlinx.coroutines.launch

@Composable
fun ExpenseSavedRoute(
    shareUrl: String,
    guestPasscode: String,
    draftRepository: ExpenseDraftRepository,
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
                        uiState = uiState.copy(message = "Share invite is not ready yet.")
                    }
                }
                ExpenseSavedUiEvent.CopyLinkClick -> {
                    if (uiState.canCopyLink) {
                        copyText(context, "EvenUp share link", uiState.copyLinkPayload)
                        uiState = uiState.copy(message = "Link copied.")
                    } else {
                        uiState = uiState.copy(message = "Share link is not ready yet.")
                    }
                }
                ExpenseSavedUiEvent.CopyCodeClick -> {
                    if (uiState.canCopyCode) {
                        copyText(context, "EvenUp guest code", uiState.copyCodePayload)
                        uiState = uiState.copy(message = "Guest code copied.")
                    } else {
                        uiState = uiState.copy(message = "Guest code is not ready yet.")
                    }
                }
                ExpenseSavedUiEvent.CopyInviteClick -> {
                    if (uiState.canShareInvite) {
                        copyText(context, "EvenUp invite", uiState.copyInvitePayload)
                        uiState = uiState.copy(message = "Invite copied.")
                    } else {
                        uiState = uiState.copy(message = "Share invite is not ready yet.")
                    }
                }
                ExpenseSavedUiEvent.AddAnotherClick -> {
                    coroutineScope.launch {
                        uiState = uiState.copy(isWorking = true, message = null)
                        try {
                            draftRepository.clearDraft()
                            onAddAnother()
                        } catch (_: RuntimeException) {
                            uiState = uiState.copy(
                                isWorking = false,
                                message = "Could not start a new expense. Try again.",
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier,
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
