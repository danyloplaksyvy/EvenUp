package com.dps.evenup.core.designsystem.api

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun EvenUpStickyBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Navigate back",
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .statusBarsPadding()
            .padding(start = EvenUpTheme.spacing.space8, top = EvenUpTheme.spacing.space8)
            .size(48.dp)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        shape = EvenUpTheme.shapes.avatar,
        color = EvenUpTheme.colors.background,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, EvenUpTheme.colors.border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
            )
        }
    }
}
