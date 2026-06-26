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
                ExpenseSavedUiEvent.ShareClick -> shareLink(context, uiState.shareMessage)
                ExpenseSavedUiEvent.CopyClick -> {
                    copyShareDetails(context, uiState.shareMessage)
                    uiState = uiState.copy(message = "Share details copied.")
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

private fun shareLink(
    context: Context,
    shareMessage: String,
) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareMessage)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share EvenUp details"))
}

private fun copyShareDetails(
    context: Context,
    shareMessage: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("EvenUp share details", shareMessage))
}
