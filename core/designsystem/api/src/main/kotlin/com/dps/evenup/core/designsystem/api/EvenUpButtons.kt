package com.dps.evenup.core.designsystem.api

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EvenUpPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
        enabled = enabled,
        shape = EvenUpTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = EvenUpTheme.colors.primary,
            contentColor = EvenUpTheme.colors.onPrimary,
            disabledContainerColor = EvenUpTheme.colors.surface,
            disabledContentColor = EvenUpTheme.colors.textTertiary,
        ),
        contentPadding = PaddingValues(
            horizontal = EvenUpTheme.spacing.space20,
            vertical = EvenUpTheme.spacing.space16,
        ),
    ) {
        Text(text = text, style = EvenUpTheme.typography.button)
    }
}

@Composable
fun EvenUpSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
        enabled = enabled,
        shape = EvenUpTheme.shapes.button,
        border = ButtonDefaults.outlinedButtonBorder(enabled = enabled),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = EvenUpTheme.colors.surfaceElevated,
            contentColor = EvenUpTheme.colors.textPrimary,
            disabledContainerColor = EvenUpTheme.colors.surface,
            disabledContentColor = EvenUpTheme.colors.textTertiary,
        ),
        contentPadding = PaddingValues(
            horizontal = EvenUpTheme.spacing.space20,
            vertical = EvenUpTheme.spacing.space16,
        ),
    ) {
        Text(text = text, style = EvenUpTheme.typography.button)
    }
}

@Composable
fun EvenUpTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
        enabled = enabled,
        shape = EvenUpTheme.shapes.button,
        colors = ButtonDefaults.textButtonColors(
            contentColor = EvenUpTheme.colors.textPrimary,
            disabledContentColor = EvenUpTheme.colors.textTertiary,
        ),
        contentPadding = PaddingValues(
            horizontal = EvenUpTheme.spacing.space16,
            vertical = EvenUpTheme.spacing.space12,
        ),
    ) {
        Text(text = text, style = EvenUpTheme.typography.button)
    }
}
