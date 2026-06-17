package com.dps.evenup.feature.expenseflow.impl.newexpense

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpTheme

@Composable
internal fun NewExpenseHero(
    uiState: NewExpenseUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        Text(
            text = uiState.title,
            style = EvenUpTheme.typography.screenTitle,
            color = EvenUpTheme.colors.textPrimary,
        )
        Text(
            text = uiState.helperText,
            style = EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textSecondary,
        )
    }
}

@Composable
internal fun NewExpenseActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = EvenUpTheme.shapes.screenCard,
        color = EvenUpTheme.colors.surfaceElevated,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, EvenUpTheme.colors.border),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(EvenUpTheme.spacing.space16),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = EvenUpTheme.colors.surface,
                        shape = EvenUpTheme.shapes.avatar,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = EvenUpTheme.colors.textPrimary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
            ) {
                Text(
                    text = title,
                    style = EvenUpTheme.typography.bodyStrong,
                    color = EvenUpTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = EvenUpTheme.typography.caption,
                    color = EvenUpTheme.colors.textSecondary,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = EvenUpTheme.colors.textSecondary,
            )
        }
    }
}

internal val ScanReceiptIcon: ImageVector = Icons.AutoMirrored.Filled.ReceiptLong

internal val EnterManuallyIcon: ImageVector = Icons.Filled.EditNote
