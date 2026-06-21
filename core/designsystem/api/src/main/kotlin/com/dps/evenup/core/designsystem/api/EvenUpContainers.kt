package com.dps.evenup.core.designsystem.api

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun EvenUpCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(EvenUpTheme.spacing.space20),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = EvenUpTheme.shapes.screenCard,
        color = EvenUpTheme.colors.surface,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, EvenUpTheme.colors.border),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
            content = content,
        )
    }
}

@Composable
fun EvenUpTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigationClick: (() -> Unit)? = null,
    navigationContentDescription: String = "Navigate back",
    navigationIcon: ImageVector = Icons.Filled.Close,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = EvenUpTheme.colors.background,
        contentColor = EvenUpTheme.colors.textPrimary,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = EvenUpTheme.spacing.space8),
            contentAlignment = Alignment.Center,
        ) {
            if (onNavigationClick != null) {
                EvenUpIconButton(
                    contentDescription = navigationContentDescription,
                    onClick = onNavigationClick,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        imageVector = navigationIcon,
                        contentDescription = null,
                        tint = EvenUpTheme.colors.textPrimary,
                    )
                }
            }
            Text(
                text = title,
                style = EvenUpTheme.typography.sectionTitle,
                color = EvenUpTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                Box(modifier = Modifier.width(44.dp))
            }
        }
    }
}

@Composable
fun EvenUpBottomActionBar(
    primaryText: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    secondaryText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = EvenUpTheme.colors.background,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, EvenUpTheme.colors.divider),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(EvenUpTheme.spacing.space16),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
        ) {
            EvenUpPrimaryButton(
                text = primaryText,
                onClick = onPrimaryClick,
                enabled = primaryEnabled,
            )
            if (secondaryText != null && onSecondaryClick != null) {
                EvenUpTextButton(
                    text = secondaryText,
                    onClick = onSecondaryClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvenUpBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = EvenUpTheme.shapes.bottomSheet,
        containerColor = EvenUpTheme.colors.background,
        contentColor = EvenUpTheme.colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(
                    start = EvenUpTheme.spacing.space20,
                    end = EvenUpTheme.spacing.space20,
                    bottom = EvenUpTheme.spacing.space24,
                ),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space16),
        ) {
            if (title != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = EvenUpTheme.typography.sectionTitle,
                    )
                    headerAction?.invoke()
                }
                HorizontalDivider(color = EvenUpTheme.colors.divider)
            }
            content()
        }
    }
}
