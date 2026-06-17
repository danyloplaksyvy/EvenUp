package com.dps.evenup.core.designsystem.api

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun EvenUpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    leadingContent: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        isError = isError,
        singleLine = singleLine,
        label = { Text(text = label) },
        placeholder = placeholder?.let { { Text(text = it) } },
        supportingText = supportingText?.let { { Text(text = it) } },
        leadingIcon = leadingContent,
        keyboardOptions = keyboardOptions,
        shape = EvenUpTheme.shapes.input,
        textStyle = EvenUpTheme.typography.body,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = EvenUpTheme.colors.textPrimary,
            unfocusedTextColor = EvenUpTheme.colors.textPrimary,
            disabledTextColor = EvenUpTheme.colors.textTertiary,
            focusedContainerColor = EvenUpTheme.colors.surfaceElevated,
            unfocusedContainerColor = EvenUpTheme.colors.surfaceElevated,
            disabledContainerColor = EvenUpTheme.colors.surface,
            errorContainerColor = EvenUpTheme.colors.surfaceElevated,
            focusedBorderColor = EvenUpTheme.colors.primary,
            unfocusedBorderColor = EvenUpTheme.colors.border,
            disabledBorderColor = EvenUpTheme.colors.divider,
            errorBorderColor = EvenUpTheme.colors.error,
            focusedLabelColor = EvenUpTheme.colors.textSecondary,
            unfocusedLabelColor = EvenUpTheme.colors.textSecondary,
            errorLabelColor = EvenUpTheme.colors.error,
            focusedPlaceholderColor = EvenUpTheme.colors.textTertiary,
            unfocusedPlaceholderColor = EvenUpTheme.colors.textTertiary,
            focusedSupportingTextColor = EvenUpTheme.colors.textSecondary,
            unfocusedSupportingTextColor = EvenUpTheme.colors.textSecondary,
            errorSupportingTextColor = EvenUpTheme.colors.error,
        ),
    )
}

@Composable
fun EvenUpMoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    currencySymbol: String = "$",
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    EvenUpTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        leadingContent = {
            Text(
                text = currencySymbol,
                style = EvenUpTheme.typography.body,
                color = EvenUpTheme.colors.textSecondary,
            )
        },
    )
}
