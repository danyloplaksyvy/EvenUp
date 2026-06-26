package com.dps.evenup.feature.expenseflow.impl.expensesaved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpCard
import com.dps.evenup.core.designsystem.api.EvenUpIconButton
import com.dps.evenup.core.designsystem.api.EvenUpPrimaryButton
import com.dps.evenup.core.designsystem.api.EvenUpSecondaryButton
import com.dps.evenup.core.designsystem.api.EvenUpTextButton
import com.dps.evenup.core.designsystem.api.EvenUpTheme

@Composable
fun ExpenseSavedScreen(
    uiState: ExpenseSavedUiState,
    onEvent: (ExpenseSavedUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = EvenUpTheme.spacing.space20),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space24),
        ) {
            SuccessMark()
            Text(
                text = "Expense saved",
                style = EvenUpTheme.typography.displayLargeTotal,
                color = EvenUpTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Share the breakdown and code so everyone sees the same numbers.",
                style = EvenUpTheme.typography.body,
                color = EvenUpTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            LinkCard(uiState = uiState, onEvent = onEvent)
//            uiState.message?.let { message ->
//                EvenUpValidationMessage(message = message)
//            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = EvenUpTheme.spacing.space16),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
        ) {
            EvenUpPrimaryButton(
                text = "Share details",
                onClick = { onEvent(ExpenseSavedUiEvent.ShareClick) },
            )
            EvenUpSecondaryButton(
                text = "Copy details",
                onClick = { onEvent(ExpenseSavedUiEvent.CopyClick) },
            )
            EvenUpTextButton(
                text = if (uiState.isWorking) "Starting..." else "Add another expense",
                onClick = { onEvent(ExpenseSavedUiEvent.AddAnotherClick) },
                enabled = !uiState.isWorking,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SuccessMark() {
    Surface(
        modifier = Modifier.size(96.dp),
        shape = EvenUpTheme.shapes.avatar,
        color = EvenUpTheme.colors.surfaceElevated,
        contentColor = EvenUpTheme.colors.textPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
private fun LinkCard(
    uiState: ExpenseSavedUiState,
    onEvent: (ExpenseSavedUiEvent) -> Unit,
) {
    EvenUpCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space16),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4)) {
                Text(
                    text = "Share link",
                    style = EvenUpTheme.typography.caption,
                    color = EvenUpTheme.colors.textSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = uiState.shareUrl,
                        modifier = Modifier.weight(1f),
                        style = EvenUpTheme.typography.body,
                        color = EvenUpTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    EvenUpIconButton(
                        contentDescription = "Copy share details",
                        onClick = { onEvent(ExpenseSavedUiEvent.CopyClick) },
                    ) {
                        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                    }
                    EvenUpIconButton(
                        contentDescription = "Share details",
                        onClick = { onEvent(ExpenseSavedUiEvent.ShareClick) },
                    ) {
                        Icon(imageVector = Icons.Filled.IosShare, contentDescription = null)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4)) {
                Text(
                    text = "Guest code",
                    style = EvenUpTheme.typography.caption,
                    color = EvenUpTheme.colors.textSecondary,
                )
                Text(
                    text = uiState.guestPasscode,
                    style = EvenUpTheme.typography.screenTitle,
                    color = EvenUpTheme.colors.textPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}
