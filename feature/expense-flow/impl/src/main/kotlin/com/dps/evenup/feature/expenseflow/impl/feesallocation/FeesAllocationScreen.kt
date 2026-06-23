package com.dps.evenup.feature.expenseflow.impl.feesallocation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpBottomActionBar
import com.dps.evenup.core.designsystem.api.EvenUpBottomSheet
import com.dps.evenup.core.designsystem.api.EvenUpCard
import com.dps.evenup.core.designsystem.api.EvenUpCollapsingTopBarScaffold
import com.dps.evenup.core.designsystem.api.EvenUpErrorState
import com.dps.evenup.core.designsystem.api.EvenUpLoadingState
import com.dps.evenup.core.designsystem.api.EvenUpMoneyField
import com.dps.evenup.core.designsystem.api.EvenUpParticipantAvatar
import com.dps.evenup.core.designsystem.api.EvenUpParticipantChip
import com.dps.evenup.core.designsystem.api.EvenUpPrimaryButton
import com.dps.evenup.core.designsystem.api.EvenUpSecondaryButton
import com.dps.evenup.core.designsystem.api.EvenUpTextButton
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpValidationMessage

@Composable
fun FeesAllocationScreen(
    uiState: FeesAllocationUiState,
    onEvent: (FeesAllocationUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(uiState.feedback?.id) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    EvenUpCollapsingTopBarScaffold(
        title = "Allocate fees",
        onNavigationClick = { onEvent(FeesAllocationUiEvent.BackClick) },
        modifier = modifier.fillMaxSize(),
        showStickyNavigationButton = false,
        bottomBar = {
            if (!uiState.isLoading && !uiState.missingDraft) {
                EvenUpBottomActionBar(
                    primaryText = if (uiState.isSaving) "Saving..." else "Continue",
                    onPrimaryClick = { onEvent(FeesAllocationUiEvent.ContinueClick) },
                    primaryEnabled = uiState.canContinue && !uiState.isSaving,
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> EvenUpLoadingState(
                message = "Loading fees...",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            uiState.missingDraft -> EvenUpErrorState(
                title = "Fees unavailable",
                message = uiState.submitError ?: "Complete the previous steps before allocating fees.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                retryText = "Go back",
                onRetryClick = { onEvent(FeesAllocationUiEvent.BackClick) },
            )
            else -> FeesAllocationContent(
                uiState = uiState,
                onEvent = onEvent,
                contentPadding = innerPadding,
            )
        }
    }
    FocusedFeeEditorSheet(uiState = uiState, onEvent = onEvent)
    ParticipantPickerSheet(uiState = uiState, onEvent = onEvent)
    ResetToProportionalDialog(uiState = uiState, onEvent = onEvent)
}

@Composable
private fun FeesAllocationContent(
    uiState: FeesAllocationUiState,
    onEvent: (FeesAllocationUiEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.undoSnackbarId) {
        if (uiState.undoSnackbarId == 0L || uiState.undoSnapshot == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = uiState.feedback?.message ?: "Fees updated",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onEvent(FeesAllocationUiEvent.UndoAutomaticChangeClick)
        } else {
            onEvent(FeesAllocationUiEvent.UndoAutomaticChangeDismissed)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = EvenUpTheme.spacing.space20)
                .padding(top = EvenUpTheme.spacing.space16, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space24),
        ) {
            Header(uiState = uiState)
            FeeSummaryList(uiState = uiState)
            ModeSelector(selectedMode = uiState.mode, onEvent = onEvent)
            Text(
                text = uiState.helperText,
                modifier = Modifier.fillMaxWidth(),
                style = EvenUpTheme.typography.bodySmall,
                color = EvenUpTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Crossfade(targetState = uiState.mode, label = "FeesAllocationMode") { mode ->
                if (mode == FeesAllocationModeUiState.Custom) {
                    CustomAllocationSection(uiState = uiState, onEvent = onEvent)
                } else {
                    ParticipantPreview(uiState = uiState)
                }
            }
            uiState.invalidReason?.let { reason ->
                EvenUpValidationMessage(message = reason)
            }
            uiState.fieldErrors["fees"]?.let { error ->
                EvenUpValidationMessage(message = error)
            }
            uiState.submitError?.let { error ->
                EvenUpValidationMessage(message = error)
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = EvenUpTheme.spacing.space16)
                .padding(bottom = EvenUpTheme.spacing.space16),
        )
        AccessibilityAnnouncement(
            feedback = uiState.feedback,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

@Composable
private fun AccessibilityAnnouncement(
    feedback: FeesAllocationFeedbackUiState?,
    modifier: Modifier = Modifier,
) {
    if (feedback == null) return
    key(feedback.id) {
        Box(
            modifier = modifier
                .size(1.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = feedback.message
                },
        )
    }
}

@Composable
private fun Header(uiState: FeesAllocationUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        Text(
            text = "Extra fees",
            style = EvenUpTheme.typography.screenTitle,
            color = EvenUpTheme.colors.textPrimary,
        )
        Text(
            text = uiState.headerSubtitle,
            style = EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun FeeSummaryList(uiState: FeesAllocationUiState) {
    if (uiState.feeRows.isEmpty()) {
        EvenUpCard {
            Text(
                text = "No extra fees on this receipt.",
                style = EvenUpTheme.typography.body,
                color = EvenUpTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    EvenUpCard {
        Text(
            text = "Fees",
            style = EvenUpTheme.typography.sectionTitle,
            color = EvenUpTheme.colors.textPrimary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12)) {
            uiState.feeRows.forEach { fee ->
                SummaryLine(label = fee.label, value = fee.amountLabel)
            }
        }
    }
}

@Composable
private fun ModeSelector(
    selectedMode: FeesAllocationModeUiState,
    onEvent: (FeesAllocationUiEvent) -> Unit,
) {
    val descriptions = mapOf(
        FeesAllocationModeUiState.Equal to "Every participant receives the same share.",
        FeesAllocationModeUiState.Proportional to "Fees follow each person's assigned item subtotal.",
        FeesAllocationModeUiState.Custom to "Set exact fee amounts manually.",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = EvenUpTheme.shapes.input,
        color = EvenUpTheme.colors.surface,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, EvenUpTheme.colors.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EvenUpTheme.spacing.space4),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
        ) {
            ModeButton(
                text = "Equal",
                selected = selectedMode == FeesAllocationModeUiState.Equal,
                accessibilityDescription = descriptions.getValue(FeesAllocationModeUiState.Equal),
                onClick = { onEvent(FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Equal)) },
                modifier = Modifier.weight(1f),
            )
            ModeButton(
                text = "Proportional",
                selected = selectedMode == FeesAllocationModeUiState.Proportional,
                accessibilityDescription = descriptions.getValue(FeesAllocationModeUiState.Proportional),
                onClick = { onEvent(FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Proportional)) },
                modifier = Modifier.weight(1f),
            )
            ModeButton(
                text = "Custom",
                selected = selectedMode == FeesAllocationModeUiState.Custom,
                accessibilityDescription = descriptions.getValue(FeesAllocationModeUiState.Custom),
                onClick = { onEvent(FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    accessibilityDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .semantics {
                this.selected = selected
                contentDescription = "$text ${if (selected) "selected" else "not selected"}. $accessibilityDescription"
            }
            .clickable(onClick = onClick),
        shape = EvenUpTheme.shapes.input,
        color = if (selected) EvenUpTheme.colors.primary else EvenUpTheme.colors.surfaceElevated,
        contentColor = if (selected) EvenUpTheme.colors.onPrimary else EvenUpTheme.colors.textPrimary,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(vertical = EvenUpTheme.spacing.space12),
            style = EvenUpTheme.typography.button,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ParticipantPreview(uiState: FeesAllocationUiState) {
    EvenUpCard(modifier = Modifier.animateContentSize()) {
        uiState.participants.forEach { participant ->
            ParticipantTotalRow(
                name = participant.name,
                colorIndex = participant.colorIndex,
                amountLabel = participant.allocatedFeesLabel,
                contentDescription = "${participant.name}, fee share ${participant.allocatedFeesLabel}.",
            )
        }
        HorizontalDivider(color = EvenUpTheme.colors.border)
        SummaryLine(label = "Total fees", value = uiState.totalFeesLabel)
    }
}

@Composable
private fun CustomAllocationSection(
    uiState: FeesAllocationUiState,
    onEvent: (FeesAllocationUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space16),
    ) {
        EvenUpSecondaryButton(
            text = "Assign all fees to one person",
            onClick = { onEvent(FeesAllocationUiEvent.AssignAllFeesClick) },
        )
        EvenUpTextButton(
            text = "Reset to proportional",
            onClick = { onEvent(FeesAllocationUiEvent.ResetToProportionalClick) },
            enabled = uiState.canResetToProportional,
            modifier = Modifier.fillMaxWidth(),
        )
        FeeEditorTabs(feeCards = uiState.feeCards, onEvent = onEvent)
        EvenUpCard(modifier = Modifier.animateContentSize()) {
            Text(
                text = "Custom allocation",
                style = EvenUpTheme.typography.sectionTitle,
                color = EvenUpTheme.colors.textPrimary,
            )
            uiState.customOverviewRows.forEach { row ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
                ) {
                    ParticipantTotalRow(
                        name = row.name,
                        colorIndex = row.colorIndex,
                        amountLabel = row.totalLabel,
                        contentDescription = "${row.name}, fee share ${row.totalLabel}. ${row.breakdownLabel}.",
                    )
                    Text(
                        text = row.breakdownLabel,
                        style = EvenUpTheme.typography.bodySmall,
                        color = EvenUpTheme.colors.textSecondary,
                    )
                }
            }
            HorizontalDivider(color = EvenUpTheme.colors.border)
            SummaryLine(label = "Total fees", value = uiState.totalFeesLabel)
        }
    }
}

@Composable
private fun ParticipantTotalRow(
    name: String,
    colorIndex: Int,
    amountLabel: String,
    contentDescription: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvenUpParticipantAvatar(name = name, colorIndex = colorIndex)
            Text(
                text = name,
                style = EvenUpTheme.typography.body,
                color = EvenUpTheme.colors.textPrimary,
            )
        }
        Text(
            text = amountLabel,
            style = EvenUpTheme.typography.moneyValue,
            color = EvenUpTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun FeeEditorTabs(
    feeCards: List<FeeAllocationCardUiState>,
    onEvent: (FeesAllocationUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        feeCards.forEach { fee ->
            FeeEditorTab(
                fee = fee,
                onClick = { onEvent(FeesAllocationUiEvent.FeeEditorOpenClick(fee.id)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FeeEditorTab(
    fee: FeeAllocationCardUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .semantics { contentDescription = fee.statusContentDescription() }
            .clickable(onClick = onClick),
        shape = EvenUpTheme.shapes.input,
        color = EvenUpTheme.colors.surfaceElevated,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, if (fee.error == null) EvenUpTheme.colors.border else EvenUpTheme.colors.error),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = EvenUpTheme.spacing.space16, vertical = EvenUpTheme.spacing.space12),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
            ) {
                Text(
                    text = fee.label,
                    style = EvenUpTheme.typography.bodyStrong,
                    color = EvenUpTheme.colors.textPrimary,
                )
                Text(
                    text = fee.statusLabel,
                    style = EvenUpTheme.typography.bodySmall,
                    color = if (fee.error == null) EvenUpTheme.colors.textSecondary else EvenUpTheme.colors.error,
                )
            }
            Text(
                text = fee.amountLabel,
                style = EvenUpTheme.typography.moneyValue,
                color = EvenUpTheme.colors.textPrimary,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun FocusedFeeEditorSheet(
    uiState: FeesAllocationUiState,
    onEvent: (FeesAllocationUiEvent) -> Unit,
) {
    val editor = uiState.selectedFeeEditor
    EvenUpBottomSheet(
        visible = editor != null,
        onDismissRequest = { onEvent(FeesAllocationUiEvent.FeeEditorDismissed) },
        title = editor?.title,
    ) {
        val visibleEditor = editor ?: return@EvenUpBottomSheet
        Text(
            text = visibleEditor.totalLabel,
            style = EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textSecondary,
        )
        FocusedFeeQuickAssignChips(editor = visibleEditor, onEvent = onEvent)
        Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
            val feeTitle = visibleEditor.title.removePrefix("Edit ")
            visibleEditor.rows.forEach { row ->
                FocusedFeeEditorRow(
                    feeId = visibleEditor.feeId,
                    feeTitle = feeTitle,
                    row = row,
                    currencySymbol = uiState.currencySymbol,
                    onEvent = onEvent,
                )
            }
        }
        HorizontalDivider(color = EvenUpTheme.colors.divider)
        Text(
            text = visibleEditor.statusLabel,
            modifier = Modifier.fillMaxWidth(),
            style = EvenUpTheme.typography.bodySmall,
            color = if (visibleEditor.error == null) EvenUpTheme.colors.textSecondary else EvenUpTheme.colors.error,
            textAlign = TextAlign.Center,
        )
        EvenUpPrimaryButton(
            text = "Done",
            onClick = { onEvent(FeesAllocationUiEvent.FeeEditorDoneClick) },
            enabled = visibleEditor.canSave,
        )
    }
}

@Composable
private fun FocusedFeeQuickAssignChips(
    editor: FocusedFeeEditorUiState,
    onEvent: (FeesAllocationUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        Text(
            text = "Assign to one person",
            style = EvenUpTheme.typography.bodySmall,
            color = EvenUpTheme.colors.textSecondary,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
        ) {
            items(editor.participantChips, key = { chip -> chip.id }) { chip ->
                EvenUpParticipantChip(
                    name = chip.name,
                    colorIndex = chip.colorIndex,
                    selected = chip.selected,
                    onClick = {
                        onEvent(
                            FeesAllocationUiEvent.AssignThisFeeToParticipantClick(
                                feeId = editor.feeId,
                                participantId = chip.id,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FocusedFeeEditorRow(
    feeId: String,
    feeTitle: String,
    row: FeeAllocationRowUiState,
    currencySymbol: String,
    onEvent: (FeesAllocationUiEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EvenUpParticipantAvatar(
            name = row.name,
            colorIndex = row.colorIndex,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = row.name,
            modifier = Modifier.weight(1f),
            style = EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textPrimary,
        )
        EvenUpMoneyField(
            value = row.customAmount,
            onValueChange = {
                onEvent(
                    FeesAllocationUiEvent.CustomAmountChanged(
                        feeId = feeId,
                        participantId = row.participantId,
                        value = it,
                    ),
                )
            },
            label = "",
            currencySymbol = currencySymbol,
            enabled = row.customEnabled,
            isError = row.customIsError,
            supportingText = if (row.customIsError) "Check" else null,
            modifier = Modifier
                .width(132.dp)
                .semantics {
                    contentDescription = "${row.name} $feeTitle amount, ${row.amountLabel}."
                },
        )
    }
}

@Composable
private fun ParticipantPickerSheet(
    uiState: FeesAllocationUiState,
    onEvent: (FeesAllocationUiEvent) -> Unit,
) {
    val picker = uiState.participantPicker
    EvenUpBottomSheet(
        visible = picker != null,
        onDismissRequest = { onEvent(FeesAllocationUiEvent.ParticipantPickerDismissed) },
        title = picker?.title,
    ) {
        val visiblePicker = picker ?: return@EvenUpBottomSheet
        visiblePicker.participants.forEach { participant ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = participant.pickerContentDescription(visiblePicker) }
                    .clickable { onEvent(FeesAllocationUiEvent.ParticipantPicked(participant.id)) },
                shape = EvenUpTheme.shapes.screenCard,
                color = EvenUpTheme.colors.surfaceElevated,
                border = BorderStroke(1.dp, EvenUpTheme.colors.border),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(EvenUpTheme.spacing.space16),
                    horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EvenUpParticipantAvatar(
                        name = participant.name,
                        colorIndex = participant.colorIndex,
                    )
                    Text(
                        text = participant.name,
                        style = EvenUpTheme.typography.body,
                        color = EvenUpTheme.colors.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResetToProportionalDialog(
    uiState: FeesAllocationUiState,
    onEvent: (FeesAllocationUiEvent) -> Unit,
) {
    if (!uiState.showResetToProportionalConfirmation) return
    AlertDialog(
        onDismissRequest = { onEvent(FeesAllocationUiEvent.ResetToProportionalDismissed) },
        title = {
            Text(
                text = "Reset custom allocation?",
                style = EvenUpTheme.typography.cardTitle,
                color = EvenUpTheme.colors.textPrimary,
            )
        },
        text = {
            Text(
                text = "Your custom fee changes will be replaced by proportional allocation.",
                style = EvenUpTheme.typography.body,
                color = EvenUpTheme.colors.textSecondary,
            )
        },
        confirmButton = {
            EvenUpTextButton(
                text = "Reset",
                onClick = { onEvent(FeesAllocationUiEvent.ResetToProportionalConfirmed) },
            )
        },
        dismissButton = {
            EvenUpTextButton(
                text = "Cancel",
                onClick = { onEvent(FeesAllocationUiEvent.ResetToProportionalDismissed) },
            )
        },
    )
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label, $value" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = EvenUpTheme.typography.bodySmall,
            color = EvenUpTheme.colors.textSecondary,
        )
        Text(
            text = value,
            style = EvenUpTheme.typography.moneyValue,
            color = EvenUpTheme.colors.textPrimary,
        )
    }
}

private fun FeeAllocationCardUiState.statusContentDescription(): String {
    return if (statusLabel.endsWith("remaining")) {
        "$label has $statusLabel."
    } else {
        "$label $statusLabel."
    }
}

private fun FeePickerParticipantUiState.pickerContentDescription(
    picker: FeeParticipantPickerUiState,
): String {
    return if (picker.feeId == null) {
        "Assign all fees to $name."
    } else {
        "${picker.title.removeSuffix(":")} $name."
    }
}
