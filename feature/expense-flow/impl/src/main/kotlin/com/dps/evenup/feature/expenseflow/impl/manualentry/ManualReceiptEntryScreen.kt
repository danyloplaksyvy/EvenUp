package com.dps.evenup.feature.expenseflow.impl.manualentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpBottomActionBar
import com.dps.evenup.core.designsystem.api.EvenUpCard
import com.dps.evenup.core.designsystem.api.EvenUpCollapsingTopBarScaffold
import com.dps.evenup.core.designsystem.api.EvenUpMoneyField
import com.dps.evenup.core.designsystem.api.EvenUpTextButton
import com.dps.evenup.core.designsystem.api.EvenUpTextField
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.feature.expenseflow.impl.receiptentry.CurrencySelector
import com.dps.evenup.feature.expenseflow.impl.receiptentry.DeleteReceiptRowButton
import com.dps.evenup.feature.expenseflow.impl.receiptentry.ReceiptDatePickerField

@Composable
fun ManualReceiptEntryScreen(
    uiState: ManualReceiptEntryUiState,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    EvenUpCollapsingTopBarScaffold(
        title = "Manual entry",
        onNavigationClick = { onEvent(ManualReceiptEntryUiEvent.BackClick) },
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            EvenUpBottomActionBar(
                primaryText = if (uiState.isSaving) "Saving..." else "Continue",
                onPrimaryClick = { onEvent(ManualReceiptEntryUiEvent.ContinueClick) },
                primaryEnabled = !uiState.isSaving,
            )
        },
    ) { innerPadding ->
        ManualReceiptEntryContent(
            uiState = uiState,
            onEvent = onEvent,
            contentPadding = innerPadding,
        )
    }
}

@Composable
private fun ManualReceiptEntryContent(
    uiState: ManualReceiptEntryUiState,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = EvenUpTheme.spacing.space20)
            .padding(top = EvenUpTheme.spacing.space16, bottom = EvenUpTheme.spacing.space24),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space24),
    ) {
        ManualReceiptEssentialsCard(uiState = uiState, onEvent = onEvent)
        ManualReceiptItemsSection(uiState = uiState, onEvent = onEvent)
        ManualReceiptFeesSection(uiState = uiState, onEvent = onEvent)
    }
}

@Composable
private fun ManualReceiptEssentialsCard(
    uiState: ManualReceiptEntryUiState,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
) {
    EvenUpCard {
        EvenUpTextField(
            value = uiState.merchantName,
            onValueChange = { onEvent(ManualReceiptEntryUiEvent.MerchantNameChanged(it)) },
            label = "Merchant",
            placeholder = "e.g. Trattoria",
            isError = uiState.fieldErrors.containsKey("merchant"),
            supportingText = uiState.fieldErrors["merchant"],
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        ReceiptDatePickerField(
            value = uiState.dateLabel,
            onDateSelected = { onEvent(ManualReceiptEntryUiEvent.DateChanged(it)) },
        )
        CurrencySelector(
            selectedCurrencyCode = uiState.currencyCode,
            onCurrencySelected = { onEvent(ManualReceiptEntryUiEvent.CurrencyChanged(it)) },
        )
        uiState.fieldErrors["currency"]?.let { error ->
            Text(
                text = error,
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.error,
            )
        }
    }
}

@Composable
private fun ManualReceiptItemsSection(
    uiState: ManualReceiptEntryUiState,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Items",
                style = EvenUpTheme.typography.button,
                color = EvenUpTheme.colors.textPrimary,
            )
            Text(
                text = uiState.itemCountLabel,
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textSecondary,
            )
        }
        EvenUpCard {
            uiState.fieldErrors["items"]?.let { error ->
                Text(
                    text = error,
                    style = EvenUpTheme.typography.caption,
                    color = EvenUpTheme.colors.error,
                )
            }
            uiState.items.forEachIndexed { index, item ->
                ManualReceiptItemRow(
                    item = item,
                    itemIndex = index,
                    canRemove = uiState.items.size > 1,
                    fieldErrors = uiState.fieldErrors,
                    onEvent = onEvent,
                )
                if (index != uiState.items.lastIndex) {
                    HorizontalDivider(color = EvenUpTheme.colors.divider)
                }
            }
            EvenUpTextButton(
                text = "Add another item",
                onClick = { onEvent(ManualReceiptEntryUiEvent.AddItemClick) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ManualReceiptItemRow(
    item: ManualReceiptItemUiState,
    itemIndex: Int,
    canRemove: Boolean,
    fieldErrors: Map<String, String>,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        EvenUpTextField(
            value = item.name,
            onValueChange = { onEvent(ManualReceiptEntryUiEvent.ItemNameChanged(item.id, it)) },
            label = "Item ${itemIndex + 1}",
            placeholder = "Item name",
            isError = fieldErrors.containsKey("item_name_${item.id}"),
            supportingText = fieldErrors["item_name_${item.id}"],
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
            verticalAlignment = Alignment.Top,
        ) {
            EvenUpTextField(
                value = item.quantity,
                onValueChange = { onEvent(ManualReceiptEntryUiEvent.ItemQuantityChanged(item.id, it)) },
                label = "Qty",
                modifier = Modifier.width(84.dp),
                isError = fieldErrors.containsKey("item_quantity_${item.id}"),
                supportingText = fieldErrors["item_quantity_${item.id}"],
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            EvenUpMoneyField(
                value = item.amount,
                onValueChange = { onEvent(ManualReceiptEntryUiEvent.ItemAmountChanged(item.id, it)) },
                label = "Amount",
                modifier = Modifier.weight(1f),
                isError = fieldErrors.containsKey("item_amount_${item.id}"),
                supportingText = fieldErrors["item_amount_${item.id}"],
            )
            DeleteReceiptRowButton(
                contentDescription = "Delete item",
                onClick = { onEvent(ManualReceiptEntryUiEvent.RemoveItemClick(item.id)) },
                enabled = canRemove,
                modifier = Modifier.padding(top = EvenUpTheme.spacing.space12),
            )
        }
    }
}

@Composable
private fun ManualReceiptFeesSection(
    uiState: ManualReceiptEntryUiState,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        Text(
            text = "Fees",
            style = EvenUpTheme.typography.button,
            color = EvenUpTheme.colors.textPrimary,
        )
        EvenUpCard {
            uiState.fieldErrors["fees"]?.let { error ->
                Text(
                    text = error,
                    style = EvenUpTheme.typography.caption,
                    color = EvenUpTheme.colors.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12)) {
                EvenUpMoneyField(
                    value = uiState.taxAmount,
                    onValueChange = { onEvent(ManualReceiptEntryUiEvent.TaxChanged(it)) },
                    label = "Tax",
                    modifier = Modifier.weight(1f),
                    isError = uiState.fieldErrors.containsKey("tax"),
                    supportingText = uiState.fieldErrors["tax"],
                )
                EvenUpMoneyField(
                    value = uiState.tipAmount,
                    onValueChange = { onEvent(ManualReceiptEntryUiEvent.TipChanged(it)) },
                    label = "Tip",
                    modifier = Modifier.weight(1f),
                    isError = uiState.fieldErrors.containsKey("tip"),
                    supportingText = uiState.fieldErrors["tip"],
                )
            }
            EvenUpMoneyField(
                value = uiState.totalAmount,
                onValueChange = { onEvent(ManualReceiptEntryUiEvent.TotalChanged(it)) },
                label = "Total amount",
                isError = uiState.fieldErrors.containsKey("total"),
                supportingText = uiState.fieldErrors["total"],
            )
            uiState.submitError?.let { error ->
                Text(
                    text = error,
                    modifier = Modifier.fillMaxWidth(),
                    style = EvenUpTheme.typography.caption,
                    color = EvenUpTheme.colors.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
