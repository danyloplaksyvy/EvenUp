package com.dps.evenup.feature.expenseflow.impl.receiptreview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import com.dps.evenup.core.designsystem.api.EvenUpErrorState
import com.dps.evenup.core.designsystem.api.EvenUpLoadingState
import com.dps.evenup.core.designsystem.api.EvenUpMoneyField
import com.dps.evenup.core.designsystem.api.EvenUpTextButton
import com.dps.evenup.core.designsystem.api.EvenUpTextField
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpTopBar
import com.dps.evenup.core.designsystem.api.EvenUpValidationMessage

@Composable
fun ReceiptReviewScreen(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        EvenUpTopBar(
            title = "Receipt review",
            onNavigationClick = { onEvent(ReceiptReviewUiEvent.BackClick) },
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
        )
        when {
            uiState.isLoading -> EvenUpLoadingState(
                message = "Loading receipt...",
                modifier = Modifier.weight(1f),
            )
            uiState.missingDraft -> EvenUpErrorState(
                title = "Receipt unavailable",
                message = uiState.submitError ?: "Start a new receipt to continue.",
                modifier = Modifier.weight(1f),
                retryText = "Go back",
                onRetryClick = { onEvent(ReceiptReviewUiEvent.BackClick) },
            )
            else -> ReceiptReviewContent(
                uiState = uiState,
                onEvent = onEvent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReceiptReviewContent(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = EvenUpTheme.spacing.space20)
                .padding(top = EvenUpTheme.spacing.space16, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space24),
        ) {
            ReceiptReviewSummary(uiState = uiState)
            ReceiptReviewDetailsCard(uiState = uiState, onEvent = onEvent)
            ReceiptReviewItemsCard(uiState = uiState, onEvent = onEvent)
            ReceiptReviewFeesCard(uiState = uiState, onEvent = onEvent)
            ReceiptReviewOriginalReceiptRow()
            uiState.submitError?.let { error ->
                EvenUpValidationMessage(message = error)
            }
        }
        EvenUpBottomActionBar(
            primaryText = if (uiState.isSaving) "Saving..." else "Continue",
            onPrimaryClick = { onEvent(ReceiptReviewUiEvent.ContinueClick) },
            primaryEnabled = !uiState.isSaving,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ReceiptReviewSummary(uiState: ReceiptReviewUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Receipt summary",
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textSecondary,
            )
            ReceiptReviewStatusPill(text = uiState.statusLabel, isValid = uiState.fieldErrors.isEmpty())
        }
        Text(
            text = uiState.summaryTotalLabel,
            style = EvenUpTheme.typography.displayLargeTotal,
            color = EvenUpTheme.colors.textPrimary,
        )
        Text(
            text = uiState.merchantName.ifBlank { "Unnamed merchant" },
            style = EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun ReceiptReviewStatusPill(
    text: String,
    isValid: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (isValid) EvenUpTheme.colors.success else EvenUpTheme.colors.warning
    val containerColor = if (isValid) EvenUpTheme.colors.successContainer else EvenUpTheme.colors.warningContainer
    Surface(
        modifier = modifier,
        shape = EvenUpTheme.shapes.chip,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = EvenUpTheme.spacing.space12, vertical = EvenUpTheme.spacing.space8),
            style = EvenUpTheme.typography.caption,
        )
    }
}

@Composable
private fun ReceiptReviewDetailsCard(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    EvenUpCard {
        EvenUpTextField(
            value = uiState.merchantName,
            onValueChange = { onEvent(ReceiptReviewUiEvent.MerchantNameChanged(it)) },
            label = "Merchant",
            isError = uiState.fieldErrors.containsKey("merchant"),
            supportingText = uiState.fieldErrors["merchant"],
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12)) {
            EvenUpTextField(
                value = uiState.dateLabel,
                onValueChange = { onEvent(ReceiptReviewUiEvent.DateChanged(it)) },
                label = "Date",
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            EvenUpTextField(
                value = uiState.currencyCode,
                onValueChange = { onEvent(ReceiptReviewUiEvent.CurrencyChanged(it)) },
                label = "Currency",
                modifier = Modifier.weight(1f),
                isError = uiState.fieldErrors.containsKey("currency"),
                supportingText = uiState.fieldErrors["currency"],
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                ),
            )
        }
    }
}

@Composable
private fun ReceiptReviewItemsCard(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        SectionHeader(title = "Items", detail = uiState.itemCountLabel)
        EvenUpCard {
            uiState.fieldErrors["items"]?.let { error ->
                EvenUpValidationMessage(message = error)
            }
            uiState.items.forEachIndexed { index, item ->
                ReceiptReviewItemRow(
                    item = item,
                    fieldErrors = uiState.fieldErrors,
                    canRemove = uiState.items.size > 1,
                    onEvent = onEvent,
                )
                if (index != uiState.items.lastIndex) {
                    HorizontalDivider(color = EvenUpTheme.colors.divider)
                }
            }
            EvenUpTextButton(
                text = "Add item",
                onClick = { onEvent(ReceiptReviewUiEvent.AddItemClick) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReceiptReviewItemRow(
    item: ReceiptReviewItemUiState,
    fieldErrors: Map<String, String>,
    canRemove: Boolean,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
            verticalAlignment = Alignment.Top,
        ) {
            EvenUpTextField(
                value = item.name,
                onValueChange = { onEvent(ReceiptReviewUiEvent.ItemNameChanged(item.id, it)) },
                label = "Item",
                modifier = Modifier.weight(1f),
                isError = fieldErrors.containsKey("item_name_${item.id}"),
                supportingText = fieldErrors["item_name_${item.id}"],
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            EvenUpTextField(
                value = item.quantity,
                onValueChange = { onEvent(ReceiptReviewUiEvent.ItemQuantityChanged(item.id, it)) },
                label = "Qty",
                modifier = Modifier.weight(0.42f),
                isError = fieldErrors.containsKey("item_quantity_${item.id}"),
                supportingText = fieldErrors["item_quantity_${item.id}"],
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        EvenUpMoneyField(
            value = item.amount,
            onValueChange = { onEvent(ReceiptReviewUiEvent.ItemAmountChanged(item.id, it)) },
            label = "Amount",
            isError = fieldErrors.containsKey("item_amount_${item.id}"),
            supportingText = fieldErrors["item_amount_${item.id}"],
        )
        if (canRemove) {
            EvenUpTextButton(
                text = "Delete item",
                onClick = { onEvent(ReceiptReviewUiEvent.RemoveItemClick(item.id)) },
            )
        }
    }
}

@Composable
private fun ReceiptReviewFeesCard(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        SectionHeader(title = "Tax, tip, and fees", detail = "${uiState.fees.size} added")
        EvenUpCard {
            uiState.fieldErrors["fees"]?.let { error ->
                EvenUpValidationMessage(message = error)
            }
            uiState.fees.forEachIndexed { index, fee ->
                ReceiptReviewFeeRow(
                    fee = fee,
                    fieldErrors = uiState.fieldErrors,
                    onEvent = onEvent,
                )
                if (index != uiState.fees.lastIndex) {
                    HorizontalDivider(color = EvenUpTheme.colors.divider)
                }
            }
            EvenUpTextButton(
                text = "Add fee",
                onClick = { onEvent(ReceiptReviewUiEvent.AddFeeClick) },
                modifier = Modifier.fillMaxWidth(),
            )
            EvenUpMoneyField(
                value = uiState.totalAmount,
                onValueChange = { onEvent(ReceiptReviewUiEvent.TotalChanged(it)) },
                label = "Total",
                isError = uiState.fieldErrors.containsKey("total"),
                supportingText = uiState.fieldErrors["total"],
            )
        }
    }
}

@Composable
private fun ReceiptReviewFeeRow(
    fee: ReceiptReviewFeeUiState,
    fieldErrors: Map<String, String>,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
            verticalAlignment = Alignment.Top,
        ) {
            EvenUpTextField(
                value = fee.label,
                onValueChange = { onEvent(ReceiptReviewUiEvent.FeeLabelChanged(fee.id, it)) },
                label = "Fee",
                modifier = Modifier.weight(1f),
                isError = fieldErrors.containsKey("fee_label_${fee.id}"),
                supportingText = fieldErrors["fee_label_${fee.id}"],
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            EvenUpMoneyField(
                value = fee.amount,
                onValueChange = { onEvent(ReceiptReviewUiEvent.FeeAmountChanged(fee.id, it)) },
                label = "Amount",
                modifier = Modifier.weight(1f),
                isError = fieldErrors.containsKey("fee_amount_${fee.id}"),
                supportingText = fieldErrors["fee_amount_${fee.id}"],
            )
        }
        EvenUpTextButton(
            text = "Delete fee",
            onClick = { onEvent(ReceiptReviewUiEvent.RemoveFeeClick(fee.id)) },
        )
    }
}

@Composable
private fun ReceiptReviewOriginalReceiptRow() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = EvenUpTheme.shapes.input,
        color = EvenUpTheme.colors.background,
        contentColor = EvenUpTheme.colors.textPrimary,
    ) {
        Row(
            modifier = Modifier.padding(vertical = EvenUpTheme.spacing.space12),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = EvenUpTheme.colors.textSecondary,
            )
            Text(
                text = "View original receipt",
                modifier = Modifier.weight(1f),
                style = EvenUpTheme.typography.bodyStrong,
                color = EvenUpTheme.colors.textPrimary,
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = EvenUpTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    detail: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = EvenUpTheme.typography.button,
            color = EvenUpTheme.colors.textPrimary,
        )
        Text(
            text = detail,
            style = EvenUpTheme.typography.caption,
            color = EvenUpTheme.colors.textSecondary,
            textAlign = TextAlign.End,
        )
    }
}
