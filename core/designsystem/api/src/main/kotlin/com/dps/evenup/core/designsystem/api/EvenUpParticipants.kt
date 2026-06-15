package com.dps.evenup.core.designsystem.api

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun EvenUpParticipantAvatar(
    name: String,
    modifier: Modifier = Modifier,
    colorIndex: Int = 0,
    selected: Boolean = false,
) {
    val palette = EvenUpTheme.colors.avatarPalette
    val avatarColor = palette[colorIndex.mod(palette.size)]
    val borderColor = if (selected) EvenUpTheme.colors.primary else Color.Transparent

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(EvenUpTheme.shapes.avatar)
            .background(avatarColor)
            .semantics { contentDescription = "$name participant avatar" },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.matchParentSize(),
            shape = EvenUpTheme.shapes.avatar,
            color = Color.Transparent,
            border = BorderStroke(2.dp, borderColor),
        ) {}
        Text(
            text = name.initials(),
            style = EvenUpTheme.typography.caption,
            color = EvenUpTheme.colors.onPrimary,
        )
    }
}

@Composable
fun EvenUpParticipantChip(
    name: String,
    modifier: Modifier = Modifier,
    colorIndex: Int = 0,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val background = if (selected) EvenUpTheme.colors.primary else EvenUpTheme.colors.surfaceElevated
    val contentColor = if (selected) EvenUpTheme.colors.onPrimary else EvenUpTheme.colors.textPrimary
    val borderColor = if (selected) EvenUpTheme.colors.primary else EvenUpTheme.colors.border

    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        ),
        shape = EvenUpTheme.shapes.chip,
        color = background,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = EvenUpTheme.spacing.space12,
                vertical = EvenUpTheme.spacing.space8,
            ),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvenUpParticipantAvatar(
                name = name,
                colorIndex = colorIndex,
                selected = false,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = name,
                style = EvenUpTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun String.initials(): String {
    return trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }
}
