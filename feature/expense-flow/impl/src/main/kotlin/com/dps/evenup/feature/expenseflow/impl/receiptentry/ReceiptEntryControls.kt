package com.dps.evenup.feature.expenseflow.impl.receiptentry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpIconButton
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal val SupportedCurrencyCodes = listOf("EUR", "USD", "GBP")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReceiptDatePickerField(
    value: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerVisible by remember { mutableStateOf(false) }
    val todayMillis = remember {
        LocalDate.now()
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = resolveDatePickerInitialSelectedMillis(value, todayMillis),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis

            override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
        },
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { pickerVisible = true },
        shape = EvenUpTheme.shapes.input,
        color = EvenUpTheme.colors.surfaceElevated,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, EvenUpTheme.colors.border),
    ) {
        Row(
            modifier = Modifier.padding(EvenUpTheme.spacing.space16),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Date",
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textSecondary,
            )
            Text(
                text = value.ifBlank { "Choose date" },
                style = EvenUpTheme.typography.bodyStrong,
                color = if (value.isBlank()) EvenUpTheme.colors.textTertiary else EvenUpTheme.colors.textPrimary,
                textAlign = TextAlign.End,
            )
        }
    }

    if (pickerVisible) {
        DatePickerDialog(
            onDismissRequest = { pickerVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDateSelected((datePickerState.selectedDateMillis ?: todayMillis).toIsoDate())
                        pickerVisible = false
                    },
                ) {
                    Text(text = "Choose")
                }
            },
            dismissButton = {
                TextButton(onClick = { pickerVisible = false }) {
                    Text(text = "Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
internal fun CurrencySelector(
    selectedCurrencyCode: String,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    currencyCodes: List<String> = SupportedCurrencyCodes,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        currencyCodes.distinct().forEach { currencyCode ->
            val selected = selectedCurrencyCode.equals(currencyCode, ignoreCase = true)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onCurrencySelected(currencyCode) },
                shape = EvenUpTheme.shapes.chip,
                color = if (selected) EvenUpTheme.colors.primary else EvenUpTheme.colors.surfaceElevated,
                contentColor = if (selected) EvenUpTheme.colors.onPrimary else EvenUpTheme.colors.textPrimary,
                border = BorderStroke(1.dp, if (selected) EvenUpTheme.colors.primary else EvenUpTheme.colors.border),
            ) {
                Text(
                    text = currencyCode,
                    modifier = Modifier.padding(vertical = EvenUpTheme.spacing.space12),
                    style = EvenUpTheme.typography.button,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun DeleteReceiptRowButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    EvenUpIconButton(
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(
            imageVector = Icons.Filled.DeleteOutline,
            contentDescription = null,
            tint = if (enabled) EvenUpTheme.colors.error else EvenUpTheme.colors.textTertiary,
        )
    }
}

internal fun resolveDatePickerInitialSelectedMillis(
    value: String,
    todayMillis: Long,
): Long = value.toEpochMillisOrNull()?.coerceAtMost(todayMillis) ?: todayMillis

private fun String.toEpochMillisOrNull(): Long? {
    return runCatching {
        LocalDate.parse(this.trim())
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }.getOrNull()
}

private fun Long.toIsoDate(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toString()
}
