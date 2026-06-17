package com.dps.evenup.feature.expenseflow.impl.receiptreview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpBottomActionBar
import com.dps.evenup.core.designsystem.api.EvenUpBottomSheet
import com.dps.evenup.core.designsystem.api.EvenUpCard
import com.dps.evenup.core.designsystem.api.EvenUpErrorState
import com.dps.evenup.core.designsystem.api.EvenUpLoadingState
import com.dps.evenup.core.designsystem.api.EvenUpMoneyField
import com.dps.evenup.core.designsystem.api.EvenUpPrimaryButton
import com.dps.evenup.core.designsystem.api.EvenUpTextButton
import com.dps.evenup.core.designsystem.api.EvenUpTextField
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpTopBar
import com.dps.evenup.core.designsystem.api.EvenUpValidationMessage
import com.dps.evenup.core.designsystem.api.EvenUpValidationSeverity
import com.dps.evenup.feature.expenseflow.impl.receiptentry.CurrencySelector
import com.dps.evenup.feature.expenseflow.impl.receiptentry.DeleteReceiptRowButton
import com.dps.evenup.feature.expenseflow.impl.receiptentry.ReceiptDatePickerField

private val ReceiptReviewFooterClearance = 132.dp

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
                .padding(top = EvenUpTheme.spacing.space16),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space20),
        ) {
            ReceiptReviewHeader(uiState = uiState)
            ReceiptReviewDetailsCard(uiState = uiState, onEvent = onEvent)
            ReceiptReviewItemsCard(uiState = uiState, onEvent = onEvent)
            ReceiptReviewAdjustmentsCard(uiState = uiState, onEvent = onEvent)
            ReceiptReviewTotalsCard(uiState = uiState, onEvent = onEvent)
            uiState.submitError?.let { error ->
                EvenUpValidationMessage(message = error)
            }
            Spacer(modifier = Modifier.height(ReceiptReviewFooterClearance))
        }
        EvenUpBottomActionBar(
            primaryText = if (uiState.isSaving) "Saving..." else "Continue",
            onPrimaryClick = { onEvent(ReceiptReviewUiEvent.ContinueClick) },
            primaryEnabled = !uiState.isSaving,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    ReceiptReviewEditSheet(uiState = uiState, onEvent = onEvent)
}

@Composable
private fun ReceiptReviewHeader(uiState: ReceiptReviewUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        Text(
            text = uiState.receiptTotalLabel,
            style = EvenUpTheme.typography.displayLargeTotal,
            color = EvenUpTheme.colors.textPrimary,
        )
        Text(
            text = uiState.merchantName.ifBlank { "Unnamed merchant" },
            style = EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textSecondary,
        )
        ReceiptReviewStatusMessage(uiState = uiState)
    }
}

@Composable
private fun ReceiptReviewStatusMessage(uiState: ReceiptReviewUiState) {
    val isWarning = uiState.hasWarningStatus
    val containerColor = if (isWarning) EvenUpTheme.colors.warningContainer else EvenUpTheme.colors.successContainer
    val contentColor = if (isWarning) EvenUpTheme.colors.warning else EvenUpTheme.colors.success
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = uiState.statusLabel
            },
        shape = EvenUpTheme.shapes.input,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(EvenUpTheme.spacing.space12),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isWarning) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = uiState.statusLabel,
                style = EvenUpTheme.typography.bodySmall,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun ReceiptReviewDetailsCard(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        SectionHeader(title = "Receipt details", detail = "Tap to edit")
        EvenUpCard {
            ReviewValueRow(
                label = "Merchant",
                value = uiState.merchantName.ifBlank { "Required" },
                isError = uiState.fieldErrors.containsKey("merchant"),
                supportingText = uiState.fieldErrors["merchant"],
                onClick = { onEvent(ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Merchant)) },
            )
            HorizontalDivider(color = EvenUpTheme.colors.divider)
            ReviewValueRow(
                label = "Date",
                value = uiState.dateLabel.ifBlank { "Not set" },
                onClick = { onEvent(ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Date)) },
            )
            HorizontalDivider(color = EvenUpTheme.colors.divider)
            ReviewValueRow(
                label = "Currency",
                value = uiState.currencyCode,
                isError = uiState.fieldErrors.containsKey("currency"),
                supportingText = uiState.fieldErrors["currency"],
                onClick = { onEvent(ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Currency)) },
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
                    currencyCode = uiState.currencyCode,
                    hasError = uiState.fieldErrors.containsKey("item_name_${item.id}") ||
                        uiState.fieldErrors.containsKey("item_quantity_${item.id}") ||
                        uiState.fieldErrors.containsKey("item_amount_${item.id}"),
                    onClick = { onEvent(ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item(item.id))) },
                )
                if (index != uiState.items.lastIndex) {
                    HorizontalDivider(color = EvenUpTheme.colors.divider)
                }
            }
            EvenUpTextButton(
                text = "+ Add item",
                onClick = { onEvent(ReceiptReviewUiEvent.AddItemClick) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReceiptReviewItemRow(
    item: ReceiptReviewItemUiState,
    currencyCode: String,
    hasError: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Edit ${item.name.ifBlank { "receipt item" }}"
            }
            .padding(vertical = EvenUpTheme.spacing.space8),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
        ) {
            Text(
                text = item.name.ifBlank { "Unnamed item" },
                style = EvenUpTheme.typography.body,
                color = if (hasError) EvenUpTheme.colors.error else EvenUpTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.quantityDetail(currencyCode),
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = item.totalLabel(currencyCode),
            style = EvenUpTheme.typography.moneyValue,
            color = if (hasError) EvenUpTheme.colors.error else EvenUpTheme.colors.textPrimary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ReceiptReviewAdjustmentsCard(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        SectionHeader(title = "Adjustments", detail = uiState.adjustmentCountLabel)
        EvenUpCard {
            uiState.fieldErrors["fees"]?.let { error ->
                EvenUpValidationMessage(message = error)
            }
            if (uiState.fees.isEmpty()) {
                Text(
                    text = "No adjustments",
                    style = EvenUpTheme.typography.bodySmall,
                    color = EvenUpTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            uiState.fees.forEachIndexed { index, fee ->
                ReceiptReviewAdjustmentRow(
                    fee = fee,
                    currencyCode = uiState.currencyCode,
                    hasError = uiState.fieldErrors.containsKey("fee_label_${fee.id}") ||
                        uiState.fieldErrors.containsKey("fee_amount_${fee.id}"),
                    onClick = { onEvent(ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Fee(fee.id))) },
                )
                if (index != uiState.fees.lastIndex) {
                    HorizontalDivider(color = EvenUpTheme.colors.divider)
                }
            }
            EvenUpTextButton(
                text = "+ Add adjustment",
                onClick = { onEvent(ReceiptReviewUiEvent.AddFeeClick) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReceiptReviewAdjustmentRow(
    fee: ReceiptReviewFeeUiState,
    currencyCode: String,
    hasError: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Edit ${fee.label.ifBlank { "adjustment" }}"
            }
            .padding(vertical = EvenUpTheme.spacing.space8),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = fee.label.ifBlank { "Adjustment" },
            modifier = Modifier.weight(1f),
            style = EvenUpTheme.typography.body,
            color = if (hasError) EvenUpTheme.colors.error else EvenUpTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatCurrency(fee.amount, currencyCode),
            style = EvenUpTheme.typography.moneyValue,
            color = if (hasError) EvenUpTheme.colors.error else EvenUpTheme.colors.textPrimary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ReceiptReviewTotalsCard(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        SectionHeader(title = "Summary", detail = "Reconciled")
        EvenUpCard {
            SummaryRow(label = "Subtotal", value = uiState.derivedSubtotalLabel)
            SummaryRow(label = "Adjustments", value = uiState.adjustmentsTotalLabel)
            HorizontalDivider(color = EvenUpTheme.colors.divider)
            ReviewValueRow(
                label = "Receipt total override",
                value = uiState.receiptTotalLabel,
                supportingText = "Tap to edit receipt total",
                isError = uiState.reconciliation.isMismatch || uiState.fieldErrors.containsKey("total"),
                onClick = { onEvent(ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.ReceiptTotal)) },
            )
            EvenUpValidationMessage(
                message = uiState.reconciliation.message,
                severity = if (uiState.reconciliation.isMismatch) {
                    EvenUpValidationSeverity.Warning
                } else {
                    EvenUpValidationSeverity.Success
                },
            )
            uiState.fieldErrors["total"]?.let { error ->
                EvenUpValidationMessage(message = error)
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EvenUpTheme.spacing.space4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = EvenUpTheme.typography.bodySmall,
            color = EvenUpTheme.colors.textSecondary,
        )
        Text(
            text = value,
            style = EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textPrimary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ReviewValueRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Edit $label, $value"
            }
            .padding(vertical = EvenUpTheme.spacing.space8),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
        ) {
            Text(
                text = label,
                style = EvenUpTheme.typography.bodySmall,
                color = EvenUpTheme.colors.textSecondary,
            )
            supportingText?.let {
                Text(
                    text = it,
                    style = EvenUpTheme.typography.caption,
                    color = if (isError) EvenUpTheme.colors.error else EvenUpTheme.colors.textSecondary,
                )
            }
        }
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = EvenUpTheme.typography.body,
            color = if (isError) EvenUpTheme.colors.error else EvenUpTheme.colors.textPrimary,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReceiptReviewEditSheet(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    EvenUpBottomSheet(
        visible = uiState.editTarget != null,
        onDismissRequest = { onEvent(ReceiptReviewUiEvent.EditDismissed) },
        title = editSheetTitle(uiState.editTarget),
    ) {
        when (val target = uiState.editTarget) {
            ReceiptReviewEditTarget.Merchant -> MerchantEditContent(uiState = uiState, onEvent = onEvent)
            ReceiptReviewEditTarget.Date -> DateEditContent(uiState = uiState, onEvent = onEvent)
            ReceiptReviewEditTarget.Currency -> CurrencyEditContent(uiState = uiState, onEvent = onEvent)
            ReceiptReviewEditTarget.ReceiptTotal -> ReceiptTotalEditContent(uiState = uiState, onEvent = onEvent)
            is ReceiptReviewEditTarget.Item -> {
                uiState.items.firstOrNull { item -> item.id == target.itemId }?.let { item ->
                    ItemEditContent(
                        item = item,
                        fieldErrors = uiState.fieldErrors,
                        canRemove = uiState.items.size > 1,
                        onEvent = onEvent,
                    )
                }
            }
            is ReceiptReviewEditTarget.Fee -> {
                uiState.fees.firstOrNull { fee -> fee.id == target.feeId }?.let { fee ->
                    FeeEditContent(
                        fee = fee,
                        fieldErrors = uiState.fieldErrors,
                        onEvent = onEvent,
                    )
                }
            }
            null -> Unit
        }
        EvenUpPrimaryButton(
            text = "Done",
            onClick = { onEvent(ReceiptReviewUiEvent.EditDismissed) },
        )
    }
}

private fun editSheetTitle(target: ReceiptReviewEditTarget?): String = when (target) {
    ReceiptReviewEditTarget.Merchant -> "Edit merchant"
    ReceiptReviewEditTarget.Date -> "Edit date"
    ReceiptReviewEditTarget.Currency -> "Edit currency"
    ReceiptReviewEditTarget.ReceiptTotal -> "Receipt total override"
    is ReceiptReviewEditTarget.Item -> "Edit item"
    is ReceiptReviewEditTarget.Fee -> "Edit adjustment"
    null -> ""
}

@Composable
private fun MerchantEditContent(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    EvenUpTextField(
        value = uiState.merchantName,
        onValueChange = { onEvent(ReceiptReviewUiEvent.MerchantNameChanged(it)) },
        label = "Merchant",
        isError = uiState.fieldErrors.containsKey("merchant"),
        supportingText = uiState.fieldErrors["merchant"],
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
}

@Composable
private fun DateEditContent(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    ReceiptDatePickerField(
        value = uiState.dateLabel,
        onDateSelected = { onEvent(ReceiptReviewUiEvent.DateChanged(it)) },
    )
}

@Composable
private fun CurrencyEditContent(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    CurrencySelector(
        selectedCurrencyCode = uiState.currencyCode,
        onCurrencySelected = { onEvent(ReceiptReviewUiEvent.CurrencyChanged(it)) },
    )
    uiState.fieldErrors["currency"]?.let { error ->
        EvenUpValidationMessage(message = error)
    }
}

@Composable
private fun ReceiptTotalEditContent(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    EvenUpMoneyField(
        value = uiState.totalAmount,
        onValueChange = { onEvent(ReceiptReviewUiEvent.TotalChanged(it)) },
        label = "Receipt total override",
        isError = uiState.fieldErrors.containsKey("total") || uiState.reconciliation.isMismatch,
        supportingText = uiState.fieldErrors["total"],
    )
    EvenUpValidationMessage(
        message = uiState.reconciliation.message,
        severity = if (uiState.reconciliation.isMismatch) {
            EvenUpValidationSeverity.Warning
        } else {
            EvenUpValidationSeverity.Success
        },
    )
}

@Composable
private fun ItemEditContent(
    item: ReceiptReviewItemUiState,
    fieldErrors: Map<String, String>,
    canRemove: Boolean,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    EvenUpTextField(
        value = item.name,
        onValueChange = { onEvent(ReceiptReviewUiEvent.ItemNameChanged(item.id, it)) },
        label = "Item",
        isError = fieldErrors.containsKey("item_name_${item.id}"),
        supportingText = fieldErrors["item_name_${item.id}"],
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        verticalAlignment = Alignment.Top,
    ) {
        EvenUpTextField(
            value = item.quantity,
            onValueChange = { onEvent(ReceiptReviewUiEvent.ItemQuantityChanged(item.id, it)) },
            label = "Qty",
            modifier = Modifier.weight(0.44f),
            isError = fieldErrors.containsKey("item_quantity_${item.id}"),
            supportingText = fieldErrors["item_quantity_${item.id}"],
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        EvenUpMoneyField(
            value = item.amount,
            onValueChange = { onEvent(ReceiptReviewUiEvent.ItemAmountChanged(item.id, it)) },
            label = "Line total",
            modifier = Modifier.weight(1f),
            isError = fieldErrors.containsKey("item_amount_${item.id}"),
            supportingText = fieldErrors["item_amount_${item.id}"],
        )
        DeleteReceiptRowButton(
            contentDescription = "Delete item",
            onClick = { onEvent(ReceiptReviewUiEvent.RemoveItemClick(item.id)) },
            enabled = canRemove,
            modifier = Modifier.padding(top = EvenUpTheme.spacing.space12),
        )
    }
}

@Composable
private fun FeeEditContent(
    fee: ReceiptReviewFeeUiState,
    fieldErrors: Map<String, String>,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    EvenUpTextField(
        value = fee.label,
        onValueChange = { onEvent(ReceiptReviewUiEvent.FeeLabelChanged(fee.id, it)) },
        label = "Adjustment",
        isError = fieldErrors.containsKey("fee_label_${fee.id}"),
        supportingText = fieldErrors["fee_label_${fee.id}"],
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        verticalAlignment = Alignment.Top,
    ) {
        EvenUpMoneyField(
            value = fee.amount,
            onValueChange = { onEvent(ReceiptReviewUiEvent.FeeAmountChanged(fee.id, it)) },
            label = "Amount",
            modifier = Modifier.weight(1f),
            isError = fieldErrors.containsKey("fee_amount_${fee.id}"),
            supportingText = fieldErrors["fee_amount_${fee.id}"],
        )
        DeleteReceiptRowButton(
            contentDescription = "Delete adjustment",
            onClick = { onEvent(ReceiptReviewUiEvent.RemoveFeeClick(fee.id)) },
            modifier = Modifier.padding(top = EvenUpTheme.spacing.space12),
        )
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
