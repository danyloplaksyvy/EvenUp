package com.dps.evenup.core.designsystem.api

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = EvenUpTheme.shapes.bottomSheet,
        containerColor = EvenUpTheme.colors.background,
        contentColor = EvenUpTheme.colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = EvenUpTheme.spacing.space20,
                    end = EvenUpTheme.spacing.space20,
                    bottom = EvenUpTheme.spacing.space24,
                ),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space16),
        ) {
            if (title != null) {
                Text(text = title, style = EvenUpTheme.typography.sectionTitle)
                HorizontalDivider(color = EvenUpTheme.colors.divider)
            }
            content()
        }
    }
}
