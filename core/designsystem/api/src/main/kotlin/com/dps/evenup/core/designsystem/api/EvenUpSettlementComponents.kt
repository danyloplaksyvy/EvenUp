package com.dps.evenup.core.designsystem.api

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun EvenUpSettlementRow(
    participantName: String,
    amountLabel: String,
    modifier: Modifier = Modifier,
    relationLabel: String = "owes",
    colorIndex: Int = 0,
    subdued: Boolean = false,
) {
    val primaryColor = if (subdued) EvenUpTheme.colors.textSecondary else EvenUpTheme.colors.textPrimary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = EvenUpTheme.spacing.space12),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EvenUpParticipantAvatar(
            name = participantName,
            colorIndex = colorIndex,
        )
        Text(
            text = participantName,
            modifier = Modifier.weight(1f),
            style = EvenUpTheme.typography.bodyStrong,
            color = primaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = relationLabel,
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textSecondary,
                textAlign = TextAlign.End,
            )
            Text(
                text = amountLabel,
                style = EvenUpTheme.typography.moneyValue,
                color = primaryColor,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
fun EvenUpSummaryCard(
    title: String,
    totalLabel: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    payerLabel: String? = null,
    payerName: String? = null,
    payerColorIndex: Int = 0,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    EvenUpCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
        ) {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = EvenUpTheme.typography.bodySmall,
                    color = EvenUpTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = title,
                style = EvenUpTheme.typography.cardTitle,
                color = EvenUpTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = totalLabel,
                style = EvenUpTheme.typography.displayLargeTotal,
                color = EvenUpTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            if (payerLabel != null && payerName != null) {
                EvenUpPayerPill(
                    label = payerLabel,
                    name = payerName,
                    colorIndex = payerColorIndex,
                )
            }
        }
        content()
    }
}

@Composable
private fun EvenUpPayerPill(
    label: String,
    name: String,
    colorIndex: Int,
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
                horizontal = EvenUpTheme.spacing.space12,
                vertical = EvenUpTheme.spacing.space8,
            ),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textSecondary,
            )
            EvenUpParticipantAvatar(
                name = name,
                colorIndex = colorIndex,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = name,
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textPrimary,
            )
        }
    }
}

@Composable
fun EvenUpExpandableDetailsCard(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leadingLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = EvenUpTheme.shapes.screenCard,
        color = EvenUpTheme.colors.surfaceElevated,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, EvenUpTheme.colors.border),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(EvenUpTheme.spacing.space16),
                horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingLabel != null) {
                    Surface(
                        shape = EvenUpTheme.shapes.chip,
                        color = EvenUpTheme.colors.surface,
                    ) {
                        Text(
                            text = leadingLabel,
                            modifier = Modifier.padding(
                                horizontal = EvenUpTheme.spacing.space8,
                                vertical = EvenUpTheme.spacing.space4,
                            ),
                            style = EvenUpTheme.typography.caption,
                            color = EvenUpTheme.colors.textSecondary,
                        )
                    }
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = EvenUpTheme.typography.bodyStrong,
                    color = EvenUpTheme.colors.textPrimary,
                )
                Text(
                    text = if (expanded) "Hide" else "Show",
                    style = EvenUpTheme.typography.caption,
                    color = EvenUpTheme.colors.textSecondary,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(color = EvenUpTheme.colors.divider)
                    Column(
                        modifier = Modifier.padding(EvenUpTheme.spacing.space16),
                        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
                        content = content,
                    )
                }
            }
        }
    }
}
