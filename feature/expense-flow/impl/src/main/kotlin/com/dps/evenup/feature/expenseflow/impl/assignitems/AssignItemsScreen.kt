package com.dps.evenup.feature.expenseflow.impl.assignitems

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpBottomActionBar
import com.dps.evenup.core.designsystem.api.EvenUpBottomSheet
import com.dps.evenup.core.designsystem.api.EvenUpCard
import com.dps.evenup.core.designsystem.api.EvenUpPinnedTopBarScaffold
import com.dps.evenup.core.designsystem.api.EvenUpErrorState
import com.dps.evenup.core.designsystem.api.EvenUpIconButton
import com.dps.evenup.core.designsystem.api.EvenUpLoadingState
import com.dps.evenup.core.designsystem.api.EvenUpMoneyField
import com.dps.evenup.core.designsystem.api.EvenUpParticipantAvatar
import com.dps.evenup.core.designsystem.api.EvenUpParticipantChip
import com.dps.evenup.core.designsystem.api.EvenUpPrimaryButton
import com.dps.evenup.core.designsystem.api.EvenUpReceiptAssignee
import com.dps.evenup.core.designsystem.api.EvenUpTextButton
import com.dps.evenup.core.designsystem.api.EvenUpTextField
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpValidationMessage

@Composable
fun AssignItemsScreen(
    uiState: AssignItemsUiState,
    onEvent: (AssignItemsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val view = LocalView.current
    LaunchedEffect(uiState.feedback?.id) {
        val feedback = uiState.feedback ?: return@LaunchedEffect
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        view.announceForAccessibility(feedback.message)
    }

    EvenUpPinnedTopBarScaffold(
        title = "Assign items",
        onNavigationClick = { onEvent(AssignItemsUiEvent.BackClick) },
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (!uiState.isLoading && !uiState.missingDraft) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EvenUpBottomActionBar(
                        primaryText = if (uiState.isSaving) "Saving..." else "Continue",
                        onPrimaryClick = { onEvent(AssignItemsUiEvent.ContinueClick) },
                        primaryEnabled = uiState.canContinue && !uiState.isSaving,
                    )
                }
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> EvenUpLoadingState(
                message = "Loading receipt items...",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            uiState.missingDraft -> EvenUpErrorState(
                title = "Items unavailable",
                message = uiState.submitError
                    ?: "Complete the previous steps before assigning items.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                retryText = "Go back",
                onRetryClick = { onEvent(AssignItemsUiEvent.BackClick) },
            )

            else -> AssignItemsContent(
                uiState = uiState,
                onEvent = onEvent,
                contentPadding = innerPadding,
            )
        }
    }
}

@Composable
private fun AssignItemsContent(
    uiState: AssignItemsUiState,
    onEvent: (AssignItemsUiEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.clearUndoSnackbarId) {
        if (uiState.clearUndoSnackbarId == 0L || uiState.clearUndoItems == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Assignments cleared",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onEvent(AssignItemsUiEvent.ClearAssignmentsUndoClick)
        } else {
            onEvent(AssignItemsUiEvent.ClearAssignmentsUndoDismissed)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            AssignmentHeader(uiState = uiState, onEvent = onEvent)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = EvenUpTheme.spacing.space20)
                    .padding(top = EvenUpTheme.spacing.space16, bottom = EvenUpTheme.spacing.space24),
                verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space16),
            ) {
                if (uiState.items.isEmpty()) {
                    EvenUpErrorState(
                        title = "No receipt items",
                        message = "Add receipt items before assigning them.",
                        retryText = "Go back",
                        onRetryClick = { onEvent(AssignItemsUiEvent.BackClick) },
                    )
                } else {
                    ReceiptAssignmentCard(uiState = uiState, onEvent = onEvent)
                }
                uiState.fieldErrors["assignment"]?.let { error ->
                    EvenUpValidationMessage(message = error)
                }
                uiState.submitError?.let { error ->
                    EvenUpValidationMessage(message = error)
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = EvenUpTheme.spacing.space16)
                .padding(bottom = EvenUpTheme.spacing.space16),
        )
    }
    AssignItemSplitSheet(
        sheet = uiState.splitSheet,
        onEvent = onEvent,
    )
    ClearAssignmentsDialog(uiState = uiState, onEvent = onEvent)
    EqualSplitConfirmationDialog(uiState = uiState, onEvent = onEvent)
}

@Composable
private fun AssignmentHeader(
    uiState: AssignItemsUiState,
    onEvent: (AssignItemsUiEvent) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = EvenUpTheme.colors.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EvenUpTheme.spacing.space20)
                .padding(top = EvenUpTheme.spacing.space12, bottom = EvenUpTheme.spacing.space12),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        ) {
            ParticipantSelector(uiState = uiState, onEvent = onEvent)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = uiState.helperText,
                    modifier = Modifier.weight(1f),
                    style = EvenUpTheme.typography.bodySmall,
                    color = EvenUpTheme.colors.textPrimary,
                    maxLines = 1,
                )
                Text(
                    text = uiState.assignmentProgressText(),
                    style = EvenUpTheme.typography.bodySmall,
                    color = EvenUpTheme.colors.textSecondary,
                    textAlign = TextAlign.End,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssignmentHeaderActionButton(
                    text = "Split all equally",
                    onClick = { onEvent(AssignItemsUiEvent.ApplyEqualSplitClick) },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.canApplyEqualSplit,
                )
                AssignmentHeaderActionButton(
                    text = "Clear assignments",
                    onClick = { onEvent(AssignItemsUiEvent.ClearAssignmentsClick) },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.canClearAssignments,
                )
            }
        }
    }
}

@Composable
private fun AssignmentHeaderActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 44.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
                contentDescription = if (enabled) text else "$text unavailable"
            },
        shape = EvenUpTheme.shapes.chip,
        color = if (enabled) EvenUpTheme.colors.surfaceElevated else EvenUpTheme.colors.surface,
        contentColor = if (enabled) EvenUpTheme.colors.textPrimary else EvenUpTheme.colors.textTertiary,
        border = BorderStroke(
            1.dp,
            if (enabled) EvenUpTheme.colors.border else EvenUpTheme.colors.divider
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = EvenUpTheme.spacing.space12,
                    vertical = EvenUpTheme.spacing.space12
                ),
            style = EvenUpTheme.typography.button,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ParticipantSelector(
    uiState: AssignItemsUiState,
    onEvent: (AssignItemsUiEvent) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
        contentPadding = PaddingValues(start = EvenUpTheme.spacing.space4, end = EvenUpTheme.spacing.space20,),
    ) {
        items(
            items = uiState.participants,
            key = { participant -> participant.id },
        ) { participant ->
            EvenUpParticipantChip(
                name = participant.name,
                colorIndex = participant.colorIndex,
                selected = participant.selected,
                onClick = { onEvent(AssignItemsUiEvent.ParticipantSelected(participant.id)) },
                modifier = Modifier.semantics {
                    selected = participant.selected
                    contentDescription = if (participant.selected) {
                        "${participant.name} selected"
                    } else {
                        "Select ${participant.name} for assignments"
                    }
                },
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
        uiState.items.forEachIndexed { index, item ->
            CompactAssignmentItemRow(
                quantityLabel = item.quantityLabel,
                itemName = item.name,
                unitPriceLabel = item.unitPriceLabel,
                totalLabel = item.totalLabel,
                assignmentState = item.assignmentState,
                assignees = item.assignees.map { assignee ->
                    EvenUpReceiptAssignee(
                        name = assignee.name,
                        colorIndex = assignee.colorIndex,
                        detail = assignee.detail,
                    )
                },
                assignmentSummaryLabel = item.assignmentSummaryLabel,
                itemActionLabel = item.itemActionLabel,
                itemActionContentDescription = item.itemActionContentDescription,
                accessibilityLabel = "${item.name}, ${item.totalLabel}, ${item.quantityLabel}, ${item.unitPriceLabel}. ${item.assignmentSummaryLabel}. ${item.itemActionLabel}.",
                shouldBringIntoView = uiState.scrollToItemId == item.id,
                onRowClick = { onEvent(AssignItemsUiEvent.ItemTapped(item.id)) },
                onActionClick = { onEvent(AssignItemsUiEvent.ItemActionClick(item.id)) },
            )
            if (index < uiState.items.lastIndex) {
                HorizontalDivider(color = EvenUpTheme.colors.divider)
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
private fun CompactAssignmentItemRow(
    quantityLabel: String,
    itemName: String,
    unitPriceLabel: String,
    totalLabel: String,
    assignmentState: AssignItemsItemState,
    assignees: List<EvenUpReceiptAssignee>,
    assignmentSummaryLabel: String,
    itemActionLabel: String,
    itemActionContentDescription: String,
    accessibilityLabel: String,
    shouldBringIntoView: Boolean,
    onRowClick: () -> Unit,
    onActionClick: () -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(shouldBringIntoView) {
        if (shouldBringIntoView) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .animateContentSize()
            .semantics {
                contentDescription = accessibilityLabel
            }
            .clickable {
                onRowClick()
            },
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = EvenUpTheme.spacing.space8),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
            ) {
                Text(
                    text = itemName,
                    style = EvenUpTheme.typography.body,
                    color = EvenUpTheme.colors.textPrimary,
                    maxLines = 1,
                )
                Text(
                    text = "$quantityLabel · $unitPriceLabel",
                    style = EvenUpTheme.typography.bodySmall,
                    color = EvenUpTheme.colors.textSecondary,
                )
            }
            Text(
                text = totalLabel,
                style = EvenUpTheme.typography.moneyValue,
                color = EvenUpTheme.colors.textPrimary,
                textAlign = TextAlign.End,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = EvenUpTheme.spacing.space8),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssignmentSummary(
                assignmentState = assignmentState,
                assignees = assignees,
                assignmentSummaryLabel = assignmentSummaryLabel,
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EvenUpTextButton(
                    text = itemActionLabel,
                    onClick = onActionClick,
                    modifier = Modifier.semantics {
                        contentDescription = itemActionContentDescription
                    },
                )
            }
        }
    }
}

@Composable
private fun AssignmentSummary(
    assignmentState: AssignItemsItemState,
    assignees: List<EvenUpReceiptAssignee>,
    assignmentSummaryLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        assignees.take(MAX_VISIBLE_ASSIGNEE_AVATARS).forEach { assignee ->
            EvenUpParticipantAvatar(
                name = assignee.name,
                colorIndex = assignee.colorIndex,
                selected = assignmentState == AssignItemsItemState.Assigned,
            )
        }
        if (assignees.size > MAX_VISIBLE_ASSIGNEE_AVATARS) {
            AssignmentOverflowBadge(count = assignees.size - MAX_VISIBLE_ASSIGNEE_AVATARS)
        }
        Crossfade(targetState = assignmentSummaryLabel, label = "AssignmentSummaryLabel") { animatedLabel ->
            Text(
                text = animatedLabel,
                style = EvenUpTheme.typography.bodySmall,
                color = when (assignmentState) {
                    AssignItemsItemState.Unassigned -> EvenUpTheme.colors.textSecondary
                    AssignItemsItemState.Partial -> EvenUpTheme.colors.primary
                    AssignItemsItemState.Assigned -> EvenUpTheme.colors.textSecondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AssignmentOverflowBadge(count: Int) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = EvenUpTheme.shapes.avatar,
        color = EvenUpTheme.colors.surfaceElevated,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, EvenUpTheme.colors.border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "+$count",
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textPrimary,
            )
        }
    }
}

private fun AssignItemsUiState.assignmentProgressText(): String {
    val assignedCount = items.count { item -> item.assignmentState == AssignItemsItemState.Assigned }
    return if (assignedCount == items.size) {
        "All items assigned"
    } else {
        "$assignedCount / ${items.size} assigned"
    }
}

private const val MAX_VISIBLE_ASSIGNEE_AVATARS = 3

@Composable
private fun AssignItemSplitSheet(
    sheet: AssignItemsSplitSheetUiState?,
    onEvent: (AssignItemsUiEvent) -> Unit,
) {
    EvenUpBottomSheet(
        visible = sheet != null,
        onDismissRequest = { onEvent(AssignItemsUiEvent.SplitDismissed) },
    ) {
        val visibleSheet = sheet ?: return@EvenUpBottomSheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space16),
        ) {
            SheetHeader(sheet = visibleSheet, onDismiss = { onEvent(AssignItemsUiEvent.SplitDismissed) })
            SplitModeSelector(sheet = visibleSheet, onEvent = onEvent)
            when (visibleSheet.mode) {
                AssignItemsSplitMode.Units -> UnitSplitRows(sheet = visibleSheet, onEvent = onEvent)
                AssignItemsSplitMode.SharedEqual -> SharedSplitRows(sheet = visibleSheet, onEvent = onEvent)
                AssignItemsSplitMode.CustomAmount -> CustomAmountRows(sheet = visibleSheet, onEvent = onEvent)
                AssignItemsSplitMode.Percentage -> PercentageRows(sheet = visibleSheet, onEvent = onEvent)
            }
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
                text = "Split item",
                style = EvenUpTheme.typography.cardTitle,
                color = EvenUpTheme.colors.textPrimary,
            )
            Text(
                text = "${sheet.itemName} · ${sheet.quantity} units · ${sheet.unitPriceLabel} · ${sheet.totalLabel}",
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
    val modes = listOf(
        "By units" to AssignItemsSplitMode.Units,
        "Equal" to AssignItemsSplitMode.SharedEqual,
        "Amount" to AssignItemsSplitMode.CustomAmount,
        "Percent" to AssignItemsSplitMode.Percentage,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        modes.chunked(2).forEach { rowModes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
            ) {
                rowModes.forEach { (label, mode) ->
                    SplitModeButton(
                        text = label,
                        selected = sheet.mode == mode,
                        onClick = { onEvent(AssignItemsUiEvent.SplitModeSelected(mode)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowModes.size == 1) {
                    Column(modifier = Modifier.weight(1f)) {}
                }
            }
        }
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
                .padding(horizontal = EvenUpTheme.spacing.space4, vertical = EvenUpTheme.spacing.space12),
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
        val assignedUnits = sheet.rows.sumOf { row -> row.quantity }
        sheet.rows.forEach { row ->
            SplitPersonRow(row = row) {
                Surface(
                    shape = EvenUpTheme.shapes.chip,
                    color = EvenUpTheme.colors.surfaceElevated,
                    border = BorderStroke(1.dp, EvenUpTheme.colors.border),
                ) {
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
                            style = EvenUpTheme.typography.cardTitle,
                            color = EvenUpTheme.colors.textPrimary,
                            textAlign = TextAlign.Center,
                        )
                        EvenUpIconButton(
                            contentDescription = "Add one unit for ${row.name}",
                            onClick = { onEvent(AssignItemsUiEvent.SplitQuantityChanged(row.participantId, 1)) },
                            enabled = assignedUnits < sheet.quantity,
                        ) {
                            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                        }
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
                    currencySymbol = sheet.currencySymbol,
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
                if (row.amountLabel.isNotBlank()) {
                    Text(
                        text = row.amountLabel,
                        style = EvenUpTheme.typography.bodySmall,
                        color = EvenUpTheme.colors.textSecondary,
                        textAlign = TextAlign.End,
                    )
                }
                EvenUpTextField(
                    value = row.percentage,
                    onValueChange = {
                        onEvent(AssignItemsUiEvent.SplitPercentageChanged(row.participantId, it))
                    },
                    label = "Percent",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.widthIn(min = 96.dp, max = 128.dp),
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
        border = BorderStroke(
            1.dp,
            if (row.included) EvenUpTheme.colors.primary else EvenUpTheme.colors.border
        ),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(
                    horizontal = EvenUpTheme.spacing.space12,
                    vertical = EvenUpTheme.spacing.space8
                ),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvenUpParticipantAvatar(
                name = row.name,
                colorIndex = row.colorIndex,
                selected = row.included,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = row.name,
                modifier = Modifier.weight(1f),
                style = EvenUpTheme.typography.bodySmall,
                color = EvenUpTheme.colors.textPrimary,
            )
            if (row.amountLabel.isNotBlank()) {
                Text(
                    text = row.amountLabel,
                    style = EvenUpTheme.typography.bodySmall,
                    color = EvenUpTheme.colors.textSecondary,
                    textAlign = TextAlign.End,
                )
            }
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
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
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
            style = EvenUpTheme.typography.bodySmall,
            color = EvenUpTheme.colors.textPrimary,
        )
        action()
    }
}

@Composable
private fun EqualSplitConfirmationDialog(
    uiState: AssignItemsUiState,
    onEvent: (AssignItemsUiEvent) -> Unit,
) {
    if (!uiState.showEqualSplitConfirmation) return
    AlertDialog(
        onDismissRequest = { onEvent(AssignItemsUiEvent.ApplyEqualSplitDismissed) },
        title = {
            Text(
                text = "Split all items equally?",
                style = EvenUpTheme.typography.cardTitle,
                color = EvenUpTheme.colors.textPrimary,
            )
        },
        text = {
            Text(
                text = "This will split every item equally between everyone and replace current item assignments.",
                style = EvenUpTheme.typography.body,
                color = EvenUpTheme.colors.textSecondary,
            )
        },
        confirmButton = {
            EvenUpTextButton(
                text = "Split all",
                onClick = { onEvent(AssignItemsUiEvent.ApplyEqualSplitConfirmed) },
            )
        },
        dismissButton = {
            EvenUpTextButton(
                text = "Cancel",
                onClick = { onEvent(AssignItemsUiEvent.ApplyEqualSplitDismissed) },
            )
        },
    )
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
    EvenUpPrimaryButton(
        text = "Save split",
        onClick = { onEvent(AssignItemsUiEvent.SplitSaveClick) },
        enabled = sheet.canSave,
    )
}

@Composable
private fun ClearAssignmentsDialog(
    uiState: AssignItemsUiState,
    onEvent: (AssignItemsUiEvent) -> Unit,
) {
    EvenUpBottomSheet(
        visible = uiState.showClearAssignmentsConfirmation,
        onDismissRequest = { onEvent(AssignItemsUiEvent.ClearAssignmentsDismissed) },
        title = "Clear all assignments?",
        headerAction = { IconButton(onClick = { onEvent(AssignItemsUiEvent.ClearAssignmentsDismissed) }) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = null)
        } }
    ) {
        Text(
            text = "All item assignments will be removed. Your receipt and people will stay unchanged.",
            style = EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textSecondary,
        )
        DestructiveSheetAction(
            text = "Clear assignments",
            onClick = { onEvent(AssignItemsUiEvent.ClearAssignmentsConfirmed) },
        )
    }
}

@Composable
private fun DestructiveSheetAction(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = text
            },
        shape = EvenUpTheme.shapes.button,
        color = EvenUpTheme.colors.errorContainer,
        contentColor = EvenUpTheme.colors.error,
        border = BorderStroke(1.dp, EvenUpTheme.colors.error.copy(alpha = 0.22f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = EvenUpTheme.spacing.space20,
                vertical = EvenUpTheme.spacing.space16,
            ),
            style = EvenUpTheme.typography.button,
            textAlign = TextAlign.Center,
        )
    }
}
