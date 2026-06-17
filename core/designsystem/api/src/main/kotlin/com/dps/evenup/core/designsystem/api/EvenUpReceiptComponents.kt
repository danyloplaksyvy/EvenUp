package com.dps.evenup.core.designsystem.api

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Immutable
data class EvenUpReceiptAssignee(
    val name: String,
    val colorIndex: Int = 0,
    val detail: String? = null,
)

enum class EvenUpReceiptItemState {
    Unassigned,
    Partial,
    Assigned,
    Shared,
}

@Composable
fun EvenUpReceiptItemRow(
    itemName: String,
    totalLabel: String,
    modifier: Modifier = Modifier,
    quantityLabel: String? = null,
    unitPriceLabel: String? = null,
    state: EvenUpReceiptItemState = EvenUpReceiptItemState.Unassigned,
    assignees: List<EvenUpReceiptAssignee> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    val stateMessage = when (state) {
        EvenUpReceiptItemState.Unassigned -> "Unassigned"
        EvenUpReceiptItemState.Partial -> "Partially assigned"
        EvenUpReceiptItemState.Assigned -> null
        EvenUpReceiptItemState.Shared -> "Shared"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = EvenUpTheme.spacing.space12),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
            verticalAlignment = Alignment.Top,
        ) {
            if (quantityLabel != null) {
                Text(
                    text = quantityLabel,
                    style = EvenUpTheme.typography.receiptItemMeta,
                    color = EvenUpTheme.colors.textSecondary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = itemName,
                    style = EvenUpTheme.typography.receiptItemName,
                    color = EvenUpTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unitPriceLabel != null) {
                    Text(
                        text = unitPriceLabel,
                        style = EvenUpTheme.typography.receiptItemMeta,
                        color = EvenUpTheme.colors.textSecondary,
                    )
                }
            }
            Text(
                text = totalLabel,
                style = EvenUpTheme.typography.moneyValue,
                color = EvenUpTheme.colors.textPrimary,
            )
        }

        if (assignees.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
                verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
            ) {
                assignees.forEach { assignee ->
                    EvenUpAssignmentChip(assignee = assignee)
                }
            }
        } else if (stateMessage != null) {
            EvenUpReceiptStatePill(
                text = stateMessage,
                state = state,
            )
        }
    }
}

@Composable
private fun EvenUpAssignmentChip(
    assignee: EvenUpReceiptAssignee,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = EvenUpTheme.shapes.chip,
        color = EvenUpTheme.colors.surfaceElevated,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, EvenUpTheme.colors.border),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = EvenUpTheme.spacing.space8,
                vertical = EvenUpTheme.spacing.space4,
            ),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvenUpParticipantAvatar(
                name = assignee.name,
                colorIndex = assignee.colorIndex,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = assignee.detail ?: assignee.name,
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EvenUpReceiptStatePill(
    text: String,
    state: EvenUpReceiptItemState,
    modifier: Modifier = Modifier,
) {
    val colors = EvenUpTheme.colors
    val contentColor = when (state) {
        EvenUpReceiptItemState.Unassigned,
        EvenUpReceiptItemState.Partial,
        -> colors.warning
        EvenUpReceiptItemState.Assigned,
        EvenUpReceiptItemState.Shared,
        -> colors.textSecondary
    }
    val containerColor = when (state) {
        EvenUpReceiptItemState.Unassigned,
        EvenUpReceiptItemState.Partial,
        -> colors.warningContainer
        EvenUpReceiptItemState.Assigned,
        EvenUpReceiptItemState.Shared,
        -> colors.surface
    }

    Surface(
        modifier = modifier,
        shape = EvenUpTheme.shapes.chip,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.18f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = EvenUpTheme.spacing.space8,
                vertical = EvenUpTheme.spacing.space4,
            ),
            style = EvenUpTheme.typography.caption,
        )
    }
}

@Composable
fun EvenUpReceiptItemsCard(
    title: String,
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    EvenUpCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4)) {
            Text(
                text = title,
                style = EvenUpTheme.typography.cardTitle,
                color = EvenUpTheme.colors.textPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = EvenUpTheme.typography.caption,
                    color = EvenUpTheme.colors.textSecondary,
                )
            }
        }
        items.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(color = EvenUpTheme.colors.divider)
            }
            item()
        }
    }
}
