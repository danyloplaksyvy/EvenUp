package com.dps.evenup.feature.expenseflow.impl.newexpense

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpCard
import com.dps.evenup.core.designsystem.api.EvenUpComposerField
import com.dps.evenup.core.designsystem.api.EvenUpSecondaryButton
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpTopBar
import com.dps.evenup.core.designsystem.api.EvenUpValidationMessage
import com.dps.evenup.core.designsystem.api.EvenUpValidationSeverity
import com.dps.evenup.domain.expenseinput.api.AiExpensePhase

@Composable
fun NewExpenseScreen(
    uiState: NewExpenseUiState,
    onEvent: (NewExpenseUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    closeEnabled: Boolean = false,
) {
    BackHandler(enabled = uiState.isProcessing) {
        onEvent(NewExpenseUiEvent.CancelProcessing)
    }
    Surface(
        modifier = modifier.fillMaxSize(),
        color = EvenUpTheme.colors.background,
        contentColor = EvenUpTheme.colors.textPrimary,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = if (uiState.isProcessing) Modifier.clearAndSetSemantics { } else Modifier,
            ) {
                EvenUpTopBar(
                    title = "EvenUp",
                    onNavigationClick = if (closeEnabled) ({ onEvent(NewExpenseUiEvent.CloseClick) }) else null,
                    navigationContentDescription = "Close new expense",
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = EvenUpTheme.spacing.space20)
                        .padding(top = EvenUpTheme.spacing.space16, bottom = EvenUpTheme.spacing.space24)
                        .widthIn(max = 520.dp)
                        .align(Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space20),
                ) {
                    NewExpenseHero(uiState = uiState)
                    AiDescriptionComposer(uiState, onEvent)
                    if (!uiState.isOnline) {
                        EvenUpValidationMessage(
                            message = "You're offline. Keep editing, then send when you're connected.",
                            severity = EvenUpValidationSeverity.Warning,
                        )
                    }
                    uiState.errorMessage?.let { EvenUpValidationMessage(message = it) }
                    if (uiState.phase == AiExpensePhase.NeedsClarification) {
                        ClarificationCard(uiState, onEvent)
                    } else if (!uiState.isProcessing && uiState.hasPartialExtraction) {
                        EvenUpSecondaryButton("Review all details", { onEvent(NewExpenseUiEvent.ReviewAllDetailsClick) })
                    }
//                    TextButton(onClick = { onEvent(NewExpenseUiEvent.DefaultsClick) }) {
//                        Text(
//                            text = "Defaults: ${uiState.personalName ?: "name not set"} · ${uiState.defaultCurrency}",
//                            color = EvenUpTheme.colors.textSecondary,
//                            style = EvenUpTheme.typography.bodySmall,
//                        )
//                    }
                    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12)) {
                        NewExpenseActionCard(
                            title = "Scan receipt",
                            description = "Snap a photo to auto-fill details",
                            icon = ScanReceiptIcon,
                            onClick = { onEvent(NewExpenseUiEvent.ScanReceiptClick) },
                        )
                        NewExpenseActionCard(
                            title = "Enter manually",
                            description = "Fill in the details yourself",
                            icon = EnterManuallyIcon,
                            onClick = { onEvent(NewExpenseUiEvent.EnterManuallyClick) },
                        )
                    }
                    Spacer(modifier = Modifier.height(EvenUpTheme.spacing.space24))
                    Text(
                        text = "Transparent item-level splitting.",
                        modifier = Modifier.fillMaxWidth(),
                        style = EvenUpTheme.typography.caption,
                        color = EvenUpTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (uiState.isProcessing) {
                ProcessingOverlay(onCancel = { onEvent(NewExpenseUiEvent.CancelProcessing) })
            }
        }
    }
    DefaultsDialog(uiState, onEvent)
    ConfirmDialog(
        visible = uiState.discardDialogVisible,
        title = "Discard AI expense?",
        message = "Your description and extracted details will be removed before switching input methods.",
        confirmText = "Discard",
        onConfirm = { onEvent(NewExpenseUiEvent.DiscardConfirmed) },
        onDismiss = { onEvent(NewExpenseUiEvent.DialogDismissed) },
    )
    ConfirmDialog(
        visible = uiState.replaceDialogVisible,
        title = "Replace manual edits?",
        message = "Interpreting the changed description again will replace structured details you edited manually.",
        confirmText = "Replace",
        onConfirm = { onEvent(NewExpenseUiEvent.ReplaceConfirmed) },
        onDismiss = { onEvent(NewExpenseUiEvent.DialogDismissed) },
    )
}

@Composable
private fun AiDescriptionComposer(uiState: NewExpenseUiState, onEvent: (NewExpenseUiEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12)) {
        Text("Describe the expense", style = EvenUpTheme.typography.cardTitle)
        EvenUpComposerField(
            value = uiState.description,
            onValueChange = { onEvent(NewExpenseUiEvent.DescriptionChanged(it)) },
            supportingText = "",
            readOnly = uiState.isProcessing,
            minLines = 4,
            maxLines = 8,
            placeholder = "Dinner was $84.50. I paid and split everything equally with Maya.",
            actions = {
                IconButton(
                    onClick = {
                        onEvent(if (uiState.isRecordingDescription) NewExpenseUiEvent.StopRecording else NewExpenseUiEvent.DescriptionMicClick)
                    },
                    enabled = !uiState.isProcessing,
                ) {
                    Icon(
                        if (uiState.isRecordingDescription) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (uiState.isRecordingDescription) "Stop dictation" else "Dictate expense",
                    )
                }
                ComposerSendButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Organize expense",
                    enabled = uiState.canSubmitDescription,
                    onClick = { onEvent(NewExpenseUiEvent.SubmitDescription) },
                )
            },
        )
        if (uiState.isRecordingDescription) {
            Text(
                "Listening…",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = EvenUpTheme.colors.textSecondary,
                style = EvenUpTheme.typography.caption,
            )
        }
    }
}

@Composable
private fun ComposerSendButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        enabled = enabled,
        shape = EvenUpTheme.shapes.avatar,
        color = if (enabled) EvenUpTheme.colors.primary else EvenUpTheme.colors.surface,
        contentColor = if (enabled) EvenUpTheme.colors.onPrimary else EvenUpTheme.colors.textTertiary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

@Composable
private fun ProcessingOverlay(onCancel: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EvenUpTheme.colors.background.copy(alpha = 0.92f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            )
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(EvenUpTheme.spacing.space24),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        ) {
            CircularProgressIndicator(color = EvenUpTheme.colors.primary)
            Text("Organizing expense…", style = EvenUpTheme.typography.sectionTitle)
            Text(
                "Finding people, amounts, and split rules.",
                style = EvenUpTheme.typography.bodySmall,
                color = EvenUpTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = onCancel,
                modifier = Modifier.semantics { contentDescription = "Cancel processing" },
            ) { Text("Cancel") }
        }
    }
}

@Composable
private fun ClarificationCard(uiState: NewExpenseUiState, onEvent: (NewExpenseUiEvent) -> Unit) {
    EvenUpCard {
        Text("One detail needed", style = EvenUpTheme.typography.caption, color = EvenUpTheme.colors.textSecondary)
        Text(uiState.clarificationQuestion.orEmpty(), style = EvenUpTheme.typography.sectionTitle)
        if (uiState.clarificationCandidates.isNotEmpty()) {
            Text("Possible matches: ${uiState.clarificationCandidates.joinToString()}", style = EvenUpTheme.typography.bodySmall, color = EvenUpTheme.colors.textSecondary)
        }
        OutlinedTextField(
            value = uiState.answer,
            onValueChange = { onEvent(NewExpenseUiEvent.AnswerChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            placeholder = { Text("Type your answer") },
            trailingIcon = {
                Row {
                    IconButton(onClick = { onEvent(if (uiState.isRecordingAnswer) NewExpenseUiEvent.StopRecording else NewExpenseUiEvent.AnswerMicClick) }) {
                        Icon(if (uiState.isRecordingAnswer) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = "Dictate answer")
                    }
                    IconButton(onClick = { onEvent(NewExpenseUiEvent.SubmitAnswer) }, enabled = uiState.canSubmitAnswer) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send answer")
                    }
                }
            },
        )
        EvenUpSecondaryButton("Review all details", { onEvent(NewExpenseUiEvent.ReviewAllDetailsClick) })
    }
}

@Composable
private fun DefaultsDialog(uiState: NewExpenseUiState, onEvent: (NewExpenseUiEvent) -> Unit) {
    if (!uiState.defaultsDialogVisible) return
    AlertDialog(
        onDismissRequest = { onEvent(NewExpenseUiEvent.DefaultsDismiss) },
        title = { Text("AI defaults") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12)) {
                OutlinedTextField(
                    value = uiState.defaultsNameDraft,
                    onValueChange = { onEvent(NewExpenseUiEvent.DefaultsNameChanged(it)) },
                    label = { Text("Your name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.defaultsCurrencyDraft,
                    onValueChange = { onEvent(NewExpenseUiEvent.DefaultsCurrencyChanged(it)) },
                    label = { Text("Default currency") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onEvent(NewExpenseUiEvent.DefaultsSave) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { onEvent(NewExpenseUiEvent.DefaultsDismiss) }) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep editing") } },
    )
}
