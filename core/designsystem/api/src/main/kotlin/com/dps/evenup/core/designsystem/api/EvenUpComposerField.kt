package com.dps.evenup.core.designsystem.api

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun EvenUpComposerField(
    value: String,
    onValueChange: (String) -> Unit,
    supportingText: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    readOnly: Boolean = false,
    isError: Boolean = false,
    minLines: Int = 4,
    maxLines: Int = 8,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        isError -> EvenUpTheme.colors.error
        focused -> EvenUpTheme.colors.primary
        else -> EvenUpTheme.colors.border
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = EvenUpTheme.shapes.screenCard,
        color = EvenUpTheme.colors.surfaceElevated,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(if (focused || isError) 2.dp else 1.dp, borderColor),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(EvenUpTheme.spacing.space20),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 112.dp),
                readOnly = readOnly,
                minLines = minLines,
                maxLines = maxLines,
                textStyle = EvenUpTheme.typography.body.copy(color = EvenUpTheme.colors.textPrimary),
                cursorBrush = SolidColor(EvenUpTheme.colors.primary),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = EvenUpTheme.typography.body,
                                color = EvenUpTheme.colors.textTertiary,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = supportingText,
                    modifier = Modifier.weight(1f),
                    style = EvenUpTheme.typography.caption,
                    color = if (isError) EvenUpTheme.colors.error else EvenUpTheme.colors.textSecondary,
                )
                actions()
            }
        }
    }
}
