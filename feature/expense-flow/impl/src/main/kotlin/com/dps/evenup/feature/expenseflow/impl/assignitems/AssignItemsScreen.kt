package com.dps.evenup.feature.expenseflow.impl.assignitems

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpBottomSheet
import com.dps.evenup.core.designsystem.api.EvenUpBottomActionBar
import com.dps.evenup.core.designsystem.api.EvenUpCard
import com.dps.evenup.core.designsystem.api.EvenUpErrorState
import com.dps.evenup.core.designsystem.api.EvenUpIconButton
import com.dps.evenup.core.designsystem.api.EvenUpLoadingState
import com.dps.evenup.core.designsystem.api.EvenUpMoneyField
import com.dps.evenup.core.designsystem.api.EvenUpParticipantChip
import com.dps.evenup.core.designsystem.api.EvenUpParticipantAvatar
import com.dps.evenup.core.designsystem.api.EvenUpPrimaryButton
import com.dps.evenup.core.designsystem.api.EvenUpReceiptAssignee
import com.dps.evenup.core.designsystem.api.EvenUpReceiptItemRow
import com.dps.evenup.core.designsystem.api.EvenUpReceiptItemState
import com.dps.evenup.core.designsystem.api.EvenUpSecondaryButton
import com.dps.evenup.core.designsystem.api.EvenUpTextButton
import com.dps.evenup.core.designsystem.api.EvenUpTextField
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpTopBar
import com.dps.evenup.core.designsystem.api.EvenUpValidationMessage
import com.dps.evenup.core.designsystem.api.EvenUpValidationSeverity

@Composable
fun AssignItemsScreen(
    uiState: AssignItemsUiState,
    onEvent: (AssignItemsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        EvenUpTopBar(
            title = "Assign items",
            onNavigationClick = { onEvent(AssignItemsUiEvent.BackClick) },
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
        )
        when {
            uiState.isLoading -> EvenUpLoadingState(
                message = "Loading receipt items...",
                modifier = Modifier.weight(1f),
            )
            uiState.missingDraft -> EvenUpErrorState(
                title = "Items unavailable",
                message = uiState.submitError ?: "Complete the previous steps before assigning items.",
                modifier = Modifier.weight(1f),
                retryText = "Go back",
                onRetryClick = { onEvent(AssignItemsUiEvent.BackClick) },
            )
            else -> AssignItemsContent(
                uiState = uiState,
                onEvent = onEvent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AssignItemsContent(
    uiState: AssignItemsUiState,
    onEvent: (AssignItemsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = EvenUpTheme.spacing.space20)
                .padding(top = EvenUpTheme.spacing.space16, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space24),
        ) {
            Text(
                text = "Pick a person, then tap what they had.",
                modifier = Modifier.fillMaxWidth(),
                style = EvenUpTheme.typography.body,
                color = EvenUpTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            ParticipantSelector(uiState = uiState, onEvent = onEvent)
            EvenUpSecondaryButton(
                text = "Split all equally",
                onClick = { onEvent(AssignItemsUiEvent.ApplyEqualSplitClick) },
                enabled = uiState.canApplyEqualSplit,
            )
            ReceiptAssignmentCard(uiState = uiState, onEvent = onEvent)
            uiState.fieldErrors["assignment"]?.let { error ->
                EvenUpValidationMessage(message = error)
            }
            uiState.submitError?.let { error ->
                EvenUpValidationMessage(message = error)
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = uiState.progressLabel,
                style = EvenUpTheme.typography.bodySmall,
                color = EvenUpTheme.colors.textSecondary,
            )
            EvenUpBottomActionBar(
                primaryText = if (uiState.isSaving) "Saving..." else "Continue",
                onPrimaryClick = { onEvent(AssignItemsUiEvent.ContinueClick) },
                primaryEnabled = uiState.canContinue && !uiState.isSaving,
            )
        }
    }
    AssignItemSplitSheet(
        sheet = uiState.splitSheet,
        onEvent = onEvent,
    )
}

@Composable
private fun ParticipantSelector(
    uiState: AssignItemsUiState,
    onEvent: (AssignItemsUiEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        uiState.participants.forEach { participant ->
            EvenUpParticipantChip(
                name = participant.name,
                colorIndex = participant.colorIndex,
                selected = participant.selected,
                onClick = { onEvent(AssignItemsUiEvent.ParticipantSelected(participant.id)) },
            )
        }
    }
}

@Composable
private fun ReceiptAssignmentCard(
    uiState: AssignItemsUiState,
    onEvent: (AssignItemsUiEvent) -> Unit,
) {
    EvenUpCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
        ) {
            Text(
                text = uiState.merchantName.uppercase(),
                style = EvenUpTheme.typography.cardTitle,
                color = EvenUpTheme.colors.textPrimary,
            )
            uiState.dateLabel?.let { dateLabel ->
                Text(
                    text = dateLabel,
                    style = EvenUpTheme.typography.caption,
                    color = EvenUpTheme.colors.textSecondary,
                )
            }
        }
        uiState.items.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4)) {
                EvenUpReceiptItemRow(
                    itemName = item.name,
                    totalLabel = item.totalLabel,
                    quantityLabel = item.quantityLabel,
                    unitPriceLabel = item.unitPriceLabel,
                    state = item.assignmentState.toDesignState(),
                    assignees = item.assignees.map { assignee ->
                        EvenUpReceiptAssignee(
                            name = assignee.name,
                            colorIndex = assignee.colorIndex,
                            detail = assignee.detail,
                        )
                    },
                    onClick = { onEvent(AssignItemsUiEvent.ItemTapped(item.id)) },
                )
                if (item.assignmentState != AssignItemsItemState.Assigned) {
                    EvenUpValidationMessage(
                        message = item.assignmentWarning,
                        severity = EvenUpValidationSeverity.Warning,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    EvenUpTextButton(
                        text = "Edit split",
                        onClick = { onEvent(AssignItemsUiEvent.ItemSplitClick(item.id)) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Subtotal (items only)",
                style = EvenUpTheme.typography.bodySmall,
                color = EvenUpTheme.colors.textSecondary,
            )
            Text(
                text = uiState.subtotalLabel,
                style = EvenUpTheme.typography.moneyValue,
                color = EvenUpTheme.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun AssignItemSplitSheet(
    sheet: AssignItemsSplitSheetUiState?,
    onEvent: (AssignItemsUiEvent) -> Unit,
) {
    EvenUpBottomSheet(
        visible = sheet != null,
        onDismissRequest = { onEvent(AssignItemsUiEvent.SplitDismissed) },
        title = "Split item",
    ) {
        val visibleSheet = sheet ?: return@EvenUpBottomSheet
        SheetHeader(sheet = visibleSheet, onDismiss = { onEvent(AssignItemsUiEvent.SplitDismissed) })
        SplitModeSelector(sheet = visibleSheet, onEvent = onEvent)
        when (visibleSheet.mode) {
            AssignItemsSplitMode.Units -> UnitSplitRows(sheet = visibleSheet, onEvent = onEvent)
            AssignItemsSplitMode.SharedEqual -> SharedSplitRows(sheet = visibleSheet, onEvent = onEvent)
            AssignItemsSplitMode.CustomAmount -> CustomAmountRows(sheet = visibleSheet, onEvent = onEvent)
            AssignItemsSplitMode.Percentage -> PercentageRows(sheet = visibleSheet, onEvent = onEvent)
        }
        SplitSheetFooter(sheet = visibleSheet, onEvent = onEvent)
    }
}

@Composable
private fun SheetHeader(
    sheet: AssignItemsSplitSheetUiState,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
        ) {
            Text(
                text = sheet.itemName,
                style = EvenUpTheme.typography.cardTitle,
                color = EvenUpTheme.colors.textPrimary,
            )
            Text(
                text = "${sheet.quantity} units · ${sheet.unitPriceLabel} · ${sheet.totalLabel}",
                style = EvenUpTheme.typography.bodySmall,
                color = EvenUpTheme.colors.textSecondary,
            )
        }
        EvenUpIconButton(
            contentDescription = "Close split editor",
            onClick = onDismiss,
        ) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = null)
        }
    }
}

@Composable
private fun SplitModeSelector(
    sheet: AssignItemsSplitSheetUiState,
    onEvent: (AssignItemsUiEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        SplitModeButton(
            text = "Units",
            selected = sheet.mode == AssignItemsSplitMode.Units,
            onClick = { onEvent(AssignItemsUiEvent.SplitModeSelected(AssignItemsSplitMode.Units)) },
            modifier = Modifier.weight(1f),
        )
        SplitModeButton(
            text = "Shared",
            selected = sheet.mode == AssignItemsSplitMode.SharedEqual,
            onClick = { onEvent(AssignItemsUiEvent.SplitModeSelected(AssignItemsSplitMode.SharedEqual)) },
            modifier = Modifier.weight(1f),
        )
        SplitModeButton(
            text = "Custom",
            selected = sheet.mode == AssignItemsSplitMode.CustomAmount,
            onClick = { onEvent(AssignItemsUiEvent.SplitModeSelected(AssignItemsSplitMode.CustomAmount)) },
            modifier = Modifier.weight(1f),
        )
        SplitModeButton(
            text = "%",
            selected = sheet.mode == AssignItemsSplitMode.Percentage,
            onClick = { onEvent(AssignItemsUiEvent.SplitModeSelected(AssignItemsSplitMode.Percentage)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SplitModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = EvenUpTheme.shapes.chip,
        color = if (selected) EvenUpTheme.colors.primary else EvenUpTheme.colors.surfaceElevated,
        contentColor = if (selected) EvenUpTheme.colors.onPrimary else EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, if (selected) EvenUpTheme.colors.primary else EvenUpTheme.colors.border),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = EvenUpTheme.spacing.space12),
            style = EvenUpTheme.typography.button,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UnitSplitRows(
    sheet: AssignItemsSplitSheetUiState,
    onEvent: (AssignItemsUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        sheet.rows.forEach { row ->
            SplitPersonRow(row = row) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EvenUpIconButton(
                        contentDescription = "Remove one unit for ${row.name}",
                        onClick = { onEvent(AssignItemsUiEvent.SplitQuantityChanged(row.participantId, -1)) },
                        enabled = row.quantity > 0,
                    ) {
                        Icon(imageVector = Icons.Filled.Remove, contentDescription = null)
                    }
                    Text(
                        text = row.quantity.toString(),
                        modifier = Modifier.size(width = 32.dp, height = 44.dp),
                        style = EvenUpTheme.typography.cardTitle,
                        color = EvenUpTheme.colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    EvenUpIconButton(
                        contentDescription = "Add one unit for ${row.name}",
                        onClick = { onEvent(AssignItemsUiEvent.SplitQuantityChanged(row.participantId, 1)) },
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedSplitRows(
    sheet: AssignItemsSplitSheetUiState,
    onEvent: (AssignItemsUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        sheet.rows.forEach { row ->
            SharedSplitRow(row = row, onClick = {
                onEvent(AssignItemsUiEvent.SplitSharedParticipantToggled(row.participantId))
            })
        }
    }
}

@Composable
private fun CustomAmountRows(
    sheet: AssignItemsSplitSheetUiState,
    onEvent: (AssignItemsUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        sheet.rows.forEach { row ->
            SplitPersonRow(row = row) {
                EvenUpMoneyField(
                    value = row.amount,
                    onValueChange = {
                        onEvent(AssignItemsUiEvent.SplitCustomAmountChanged(row.participantId, it))
                    },
                    label = "Amount",
                    currencySymbol = "€",
                    isError = sheet.error != null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PercentageRows(
    sheet: AssignItemsSplitSheetUiState,
    onEvent: (AssignItemsUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        sheet.rows.forEach { row ->
            SplitPersonRow(row = row) {
                EvenUpTextField(
                    value = row.percentage,
                    onValueChange = {
                        onEvent(AssignItemsUiEvent.SplitPercentageChanged(row.participantId, it))
                    },
                    label = "Percent",
                    isError = sheet.error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SharedSplitRow(
    row: AssignItemsSplitRowUiState,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = EvenUpTheme.shapes.input,
        color = if (row.included) EvenUpTheme.colors.surfaceElevated else EvenUpTheme.colors.background,
        border = BorderStroke(1.dp, if (row.included) EvenUpTheme.colors.primary else EvenUpTheme.colors.border),
    ) {
        Row(
            modifier = Modifier.padding(EvenUpTheme.spacing.space12),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvenUpParticipantAvatar(
                name = row.name,
                colorIndex = row.colorIndex,
                selected = row.included,
            )
            Text(
                text = row.name,
                modifier = Modifier.weight(1f),
                style = EvenUpTheme.typography.body,
                color = EvenUpTheme.colors.textPrimary,
            )
            if (row.included) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = EvenUpTheme.colors.primary,
                )
            }
        }
    }
}

@Composable
private fun SplitPersonRow(
    row: AssignItemsSplitRowUiState,
    action: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EvenUpParticipantAvatar(
            name = row.name,
            colorIndex = row.colorIndex,
        )
        Text(
            text = row.name,
            modifier = Modifier.weight(1f),
            style = EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textPrimary,
        )
        action()
    }
}

@Composable
private fun SplitSheetFooter(
    sheet: AssignItemsSplitSheetUiState,
    onEvent: (AssignItemsUiEvent) -> Unit,
) {
    HorizontalDivider(color = EvenUpTheme.colors.divider)
    Text(
        text = sheet.statusLabel,
        modifier = Modifier.fillMaxWidth(),
        style = EvenUpTheme.typography.bodySmall,
        color = EvenUpTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
    sheet.error?.let { error ->
        EvenUpValidationMessage(
            message = error,
            severity = EvenUpValidationSeverity.Warning,
        )
    }
    EvenUpPrimaryButton(
        text = "Save split",
        onClick = { onEvent(AssignItemsUiEvent.SplitSaveClick) },
        enabled = sheet.canSave,
    )
}

private val AssignItemsReceiptItemUiState.assignmentWarning: String
    get() = when (assignmentState) {
        AssignItemsItemState.Unassigned -> "This item is not assigned yet. Tap a person, then tap this item."
        AssignItemsItemState.Partial -> "This item is only partially assigned. Use Edit split to finish it."
        AssignItemsItemState.Assigned -> ""
    }

private fun AssignItemsItemState.toDesignState(): EvenUpReceiptItemState {
    return when (this) {
        AssignItemsItemState.Unassigned -> EvenUpReceiptItemState.Unassigned
        AssignItemsItemState.Partial -> EvenUpReceiptItemState.Partial
        AssignItemsItemState.Assigned -> EvenUpReceiptItemState.Assigned
    }
}
