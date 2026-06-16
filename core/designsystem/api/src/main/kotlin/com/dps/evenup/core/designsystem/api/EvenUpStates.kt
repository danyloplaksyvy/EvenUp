package com.dps.evenup.core.designsystem.api

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class EvenUpValidationSeverity {
    Error,
    Warning,
    Success,
}

@Composable
fun EvenUpValidationMessage(
    message: String,
    modifier: Modifier = Modifier,
    severity: EvenUpValidationSeverity = EvenUpValidationSeverity.Error,
) {
    val colors = EvenUpTheme.colors
    val containerColor: Color
    val contentColor: Color

    when (severity) {
        EvenUpValidationSeverity.Error -> {
            containerColor = colors.errorContainer
            contentColor = colors.error
        }
        EvenUpValidationSeverity.Warning -> {
            containerColor = colors.warningContainer
            contentColor = colors.warning
        }
        EvenUpValidationSeverity.Success -> {
            containerColor = colors.successContainer
            contentColor = colors.success
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = EvenUpTheme.shapes.input,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.18f)),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(EvenUpTheme.spacing.space12),
            style = EvenUpTheme.typography.bodySmall,
        )
    }
}

@Composable
fun EvenUpLoadingState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(EvenUpTheme.spacing.space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space16),
    ) {
        CircularProgressIndicator(color = EvenUpTheme.colors.primary)
        Text(
            text = message,
            style = EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textSecondary,
        )
    }
}

@Composable
fun EvenUpErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    retryText: String? = null,
    onRetryClick: (() -> Unit)? = null,
) {
    EvenUpStateContent(
        title = title,
        message = message,
        modifier = modifier,
        accentColor = EvenUpTheme.colors.error,
        icon = Icons.Filled.ErrorOutline,
        actionText = retryText,
        onActionClick = onRetryClick,
    )
}

@Composable
fun EvenUpSuccessState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    EvenUpStateContent(
        title = title,
        message = message,
        modifier = modifier,
        accentColor = EvenUpTheme.colors.success,
        icon = Icons.Filled.CheckCircle,
        actionText = actionText,
        onActionClick = onActionClick,
    )
}

@Composable
private fun EvenUpStateContent(
    title: String,
    message: String,
    accentColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(EvenUpTheme.spacing.space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
    ) {
        Surface(
            shape = EvenUpTheme.shapes.avatar,
            color = accentColor.copy(alpha = 0.12f),
            contentColor = accentColor,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(12.dp)
                    .size(28.dp),
            )
        }
        Text(
            text = title,
            style = EvenUpTheme.typography.sectionTitle,
            color = EvenUpTheme.colors.textPrimary,
        )
        Text(
            text = message,
            style = EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textSecondary,
        )
        if (actionText != null && onActionClick != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                EvenUpSecondaryButton(text = actionText, onClick = onActionClick)
            }
        }
    }
}
