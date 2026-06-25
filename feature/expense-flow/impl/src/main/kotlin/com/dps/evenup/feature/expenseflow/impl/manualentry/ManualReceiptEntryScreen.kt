package com.dps.evenup.feature.expenseflow.impl.manualentry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpBottomSheet
import com.dps.evenup.core.designsystem.api.EvenUpCard
import com.dps.evenup.core.designsystem.api.EvenUpIconButton
import com.dps.evenup.core.designsystem.api.EvenUpMoneyField
import com.dps.evenup.core.designsystem.api.EvenUpPinnedTopBarScaffold
import com.dps.evenup.core.designsystem.api.EvenUpPrimaryButton
import com.dps.evenup.core.designsystem.api.EvenUpSecondaryButton
import com.dps.evenup.core.designsystem.api.EvenUpTextField
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpValidationMessage
import com.dps.evenup.core.designsystem.api.EvenUpValidationSeverity
import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.feature.expenseflow.impl.receiptentry.CurrencySelector
import com.dps.evenup.feature.expenseflow.impl.receiptentry.ReceiptDatePickerField

@Composable
fun ManualReceiptEntryScreen(
    uiState: ManualReceiptEntryUiState,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    EvenUpPinnedTopBarScaffold(
        title = "Manual entry",
        onNavigationClick = { onEvent(ManualReceiptEntryUiEvent.BackClick) },
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            ManualReceiptBottomBar(uiState = uiState, onEvent = onEvent)
        },
    ) { innerPadding ->
        ManualReceiptEntryContent(
            uiState = uiState,
            onEvent = onEvent,
            contentPadding = innerPadding,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManualReceiptEntryContent(
    uiState: ManualReceiptEntryUiState,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    val scrollState = rememberScrollState()
    val detailsRequester = remember { BringIntoViewRequester() }
    val itemsRequester = remember { BringIntoViewRequester() }
    val feesRequester = remember { BringIntoViewRequester() }
    val summaryRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(uiState.validationRequestId, uiState.firstBlockingSection) {
        when (uiState.firstBlockingSection) {
            ManualReceiptEntrySection.Details -> detailsRequester.bringIntoView()
            ManualReceiptEntrySection.Items -> itemsRequester.bringIntoView()
            ManualReceiptEntrySection.Fees -> feesRequester.bringIntoView()
            ManualReceiptEntrySection.Summary -> summaryRequester.bringIntoView()
            null -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = EvenUpTheme.spacing.space20)
                .padding(top = EvenUpTheme.spacing.space16, bottom = EvenUpTheme.spacing.space24),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space20),
        ) {
            ManualReceiptHeader(uiState = uiState)
            ManualReceiptDetailsCard(
                uiState = uiState,
                onEvent = onEvent,
                modifier = Modifier.bringIntoViewRequester(detailsRequester),
            )
            ManualReceiptItemsCard(
                uiState = uiState,
                onEvent = onEvent,
                modifier = Modifier.bringIntoViewRequester(itemsRequester),
            )
            ManualReceiptFeesCard(
                uiState = uiState,
                onEvent = onEvent,
                modifier = Modifier.bringIntoViewRequester(feesRequester),
            )
            ManualReceiptSummaryCard(
                uiState = uiState,
                modifier = Modifier.bringIntoViewRequester(summaryRequester),
            )
            uiState.submitError?.let { error ->
                EvenUpValidationMessage(message = error)
            }
        }
        ManualReceiptEditSheet(uiState = uiState, onEvent = onEvent)
    }
}

@Composable
private fun ManualReceiptBottomBar(
    uiState: ManualReceiptEntryUiState,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = EvenUpTheme.colors.background,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, EvenUpTheme.colors.divider),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(EvenUpTheme.spacing.space16),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
        ) {
            if (!uiState.isSaving && !uiState.canContinue) {
                uiState.continueBlockedMessage?.let { message ->
                    EvenUpValidationMessage(
                        message = message,
                        severity = EvenUpValidationSeverity.Warning,
                    )
                }
            }
            EvenUpPrimaryButton(
                text = if (uiState.isSaving) "Saving..." else "Continue",
                onClick = { onEvent(ManualReceiptEntryUiEvent.ContinueClick) },
                enabled = uiState.canContinue,
            )
        }
    }
}

@Composable
private fun ManualReceiptHeader(uiState: ManualReceiptEntryUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        Text(
            text = uiState.displayTotalLabel,
            style = EvenUpTheme.typography.displayLargeTotal,
            color = EvenUpTheme.colors.textPrimary,
        )
        Text(
            text = uiState.displayMerchantLabel,
            style = EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ManualReceiptDetailsCard(
    uiState: ManualReceiptEntryUiState,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        SectionHeader(title = "Receipt details")
        EvenUpCard {
            ValueRow(
                label = "Merchant",
                value = uiState.displayMerchantLabel,
                isError = uiState.fieldErrors.containsKey("merchant"),
                supportingText = uiState.fieldErrors["merchant"],
                onClick = { onEvent(ManualReceiptEntryUiEvent.EditTargetSelected(ManualReceiptEditTarget.Merchant)) },
            )
            HorizontalDivider(color = EvenUpTheme.colors.divider)
            ValueRow(
                label = "Date",
                value = uiState.dateLabel.toManualReceiptDisplayDate(),
                isError = uiState.fieldErrors.containsKey("date"),
                supportingText = uiState.fieldErrors["date"],
                onClick = { onEvent(ManualReceiptEntryUiEvent.EditTargetSelected(ManualReceiptEditTarget.Date)) },
            )
            HorizontalDivider(color = EvenUpTheme.colors.divider)
            ValueRow(
                label = "Currency",
                value = uiState.currencyCode,
                isError = uiState.fieldErrors.containsKey("currency"),
                supportingText = uiState.fieldErrors["currency"],
                onClick = { onEvent(ManualReceiptEntryUiEvent.EditTargetSelected(ManualReceiptEditTarget.Currency)) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManualReceiptItemsCard(
    uiState: ManualReceiptEntryUiState,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        SectionHeader(title = "Items", detail = uiState.itemCountLabel)
        EvenUpCard {
            uiState.fieldErrors["items"]?.let { error ->
                EvenUpValidationMessage(message = error)
            }
            if (uiState.items.isEmpty()) {
                EmptyRowsState(
                    title = "No items yet",
                    message = "Add receipt items with quantity and amount.",
                    actionText = "Add item",
                    onActionClick = { onEvent(ManualReceiptEntryUiEvent.AddItemClick) },
                )
            } else {
                uiState.items.forEachIndexed { index, item ->
                    val itemRequester = remember(item.id) { BringIntoViewRequester() }
                    LaunchedEffect(uiState.validationRequestId, uiState.firstBlockingItemId) {
                        if (uiState.firstBlockingItemId == item.id) {
                            itemRequester.bringIntoView()
                        }
                    }
                    ManualReceiptItemRow(
                        item = item,
                        currencyCode = uiState.currencyCode,
                        hasError = uiState.fieldErrors.containsKey("item_name_${item.id}") ||
                            uiState.fieldErrors.containsKey("item_quantity_${item.id}") ||
                            uiState.fieldErrors.containsKey("item_amount_${item.id}"),
                        modifier = Modifier.bringIntoViewRequester(itemRequester),
                        onClick = {
                            onEvent(ManualReceiptEntryUiEvent.EditTargetSelected(ManualReceiptEditTarget.Item(item.id)))
                        },
                    )
                    if (index != uiState.items.lastIndex) {
                        HorizontalDivider(color = EvenUpTheme.colors.divider)
                    }
                }
                SecondaryListActionRow(
                    text = "Add item",
                    onClick = { onEvent(ManualReceiptEntryUiEvent.AddItemClick) },
                )
            }
        }
    }
}

@Composable
private fun ManualReceiptItemRow(
    item: ManualReceiptItemUiState,
    currencyCode: String,
    hasError: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Edit ${item.name.ifBlank { "item" }}, ${item.totalLabel(currencyCode)}"
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
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = EvenUpTheme.colors.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ManualReceiptFeesCard(
    uiState: ManualReceiptEntryUiState,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        SectionHeader(title = "Fees", detail = uiState.feeCountLabel)
        EvenUpCard {
            uiState.fieldErrors["fees"]?.let { error ->
                EvenUpValidationMessage(message = error)
            }
            if (uiState.fees.isEmpty()) {
                EmptyRowsState(
                    title = "No fees added",
                    message = "Add tax, tip, service charge, or other positive fees.",
                    actionText = "Add fee",
                    onActionClick = { onEvent(ManualReceiptEntryUiEvent.AddFeeClick) },
                )
            } else {
                uiState.fees.forEachIndexed { index, fee ->
                    ManualReceiptFeeRow(
                        fee = fee,
                        currencyCode = uiState.currencyCode,
                        hasError = uiState.fieldErrors.containsKey("fee_label_${fee.id}") ||
                            uiState.fieldErrors.containsKey("fee_amount_${fee.id}") ||
                            uiState.fieldErrors.containsKey("fee_type_${fee.id}"),
                        onClick = {
                            onEvent(ManualReceiptEntryUiEvent.EditTargetSelected(ManualReceiptEditTarget.Fee(fee.id)))
                        },
                    )
                    if (index != uiState.fees.lastIndex) {
                        HorizontalDivider(color = EvenUpTheme.colors.divider)
                    }
                }
                SecondaryListActionRow(
                    text = "Add fee",
                    onClick = { onEvent(ManualReceiptEntryUiEvent.AddFeeClick) },
                )
            }
        }
    }
}

@Composable
private fun ManualReceiptFeeRow(
    fee: ManualReceiptFeeUiState,
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
                contentDescription = "Edit ${fee.displayLabel}"
            }
            .padding(vertical = EvenUpTheme.spacing.space8),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = fee.displayLabel,
            modifier = Modifier.weight(1f),
            style = EvenUpTheme.typography.body,
            color = if (hasError) EvenUpTheme.colors.error else EvenUpTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = fee.amountLabel(currencyCode),
            style = EvenUpTheme.typography.moneyValue,
            color = if (hasError) EvenUpTheme.colors.error else EvenUpTheme.colors.textPrimary,
            textAlign = TextAlign.End,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = EvenUpTheme.colors.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ManualReceiptSummaryCard(
    uiState: ManualReceiptEntryUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        SectionHeader(title = "Summary")
        EvenUpCard {
            SummaryRow(label = "Subtotal", value = uiState.subtotalLabel)
            if (uiState.feeTotalMinor > 0L) {
                SummaryRow(label = "Fees", value = uiState.feesTotalLabel)
            }
            HorizontalDivider(color = EvenUpTheme.colors.divider)
            SummaryRow(label = "Total", value = uiState.calculatedTotalLabel, strong = true)
            uiState.fieldErrors["summary"]?.let { error ->
                EvenUpValidationMessage(message = error)
            }
        }
    }
}

@Composable
private fun ManualReceiptEditSheet(
    uiState: ManualReceiptEntryUiState,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
) {
    val editDraft = uiState.editDraft
    EvenUpBottomSheet(
        visible = editDraft != null,
        onDismissRequest = { onEvent(ManualReceiptEntryUiEvent.EditDismissed) },
        title = editSheetTitle(editDraft),
        headerAction = {
            EditSheetHeaderAction(uiState = uiState, editDraft = editDraft, onEvent = onEvent)
        },
    ) {
        when (editDraft) {
            is ManualReceiptEditDraft.Merchant -> MerchantEditContent(
                draft = editDraft,
                fieldErrors = uiState.fieldErrors,
                onEvent = onEvent,
            )
            is ManualReceiptEditDraft.Date -> DateEditContent(
                draft = editDraft,
                fieldErrors = uiState.fieldErrors,
                onEvent = onEvent,
            )
            is ManualReceiptEditDraft.Currency -> CurrencyEditContent(
                draft = editDraft,
                currencyChoices = uiState.currencyChoices,
                fieldErrors = uiState.fieldErrors,
                onEvent = onEvent,
            )
            is ManualReceiptEditDraft.Item -> ItemEditContent(
                draft = editDraft,
                currencyCode = uiState.currencyCode,
                fieldErrors = uiState.fieldErrors,
                onEvent = onEvent,
            )
            is ManualReceiptEditDraft.Fee -> FeeEditContent(
                draft = editDraft,
                currencyCode = uiState.currencyCode,
                fieldErrors = uiState.fieldErrors,
                onEvent = onEvent,
            )
            null -> Unit
        }
        EvenUpPrimaryButton(
            text = editDraft.primaryActionLabel(),
            onClick = { onEvent(ManualReceiptEntryUiEvent.EditCommitClick) },
        )
    }
}

@Composable
private fun EditSheetHeaderAction(
    uiState: ManualReceiptEntryUiState,
    editDraft: ManualReceiptEditDraft?,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
) {
    when (editDraft) {
        is ManualReceiptEditDraft.Item -> {
            val itemId = editDraft.itemId ?: return
            EvenUpIconButton(
                contentDescription = "Delete item",
                onClick = { onEvent(ManualReceiptEntryUiEvent.RemoveItemClick(itemId)) },
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = EvenUpTheme.colors.error,
                )
            }
        }
        is ManualReceiptEditDraft.Fee -> {
            val feeId = editDraft.feeId ?: return
            EvenUpIconButton(
                contentDescription = "Delete fee",
                onClick = { onEvent(ManualReceiptEntryUiEvent.RemoveFeeClick(feeId)) },
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = EvenUpTheme.colors.error,
                )
            }
        }
        else -> {
            if (uiState.isSaving) {
                Box(modifier = Modifier.size(44.dp))
            }
        }
    }
}

@Composable
private fun MerchantEditContent(
    draft: ManualReceiptEditDraft.Merchant,
    fieldErrors: Map<String, String>,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
) {
    EvenUpTextField(
        value = draft.value,
        onValueChange = { onEvent(ManualReceiptEntryUiEvent.MerchantNameChanged(it)) },
        label = "Merchant",
        placeholder = MANUAL_RECEIPT_FALLBACK_LABEL,
        isError = fieldErrors.containsKey("merchant"),
        supportingText = fieldErrors["merchant"],
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
}

@Composable
private fun DateEditContent(
    draft: ManualReceiptEditDraft.Date,
    fieldErrors: Map<String, String>,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
) {
    ReceiptDatePickerField(
        value = draft.value,
        onDateSelected = { onEvent(ManualReceiptEntryUiEvent.DateChanged(it)) },
    )
    fieldErrors["date"]?.let { error ->
        EvenUpValidationMessage(message = error)
    }
}

@Composable
private fun CurrencyEditContent(
    draft: ManualReceiptEditDraft.Currency,
    currencyChoices: List<String>,
    fieldErrors: Map<String, String>,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
) {
    CurrencySelector(
        selectedCurrencyCode = draft.value,
        onCurrencySelected = { onEvent(ManualReceiptEntryUiEvent.CurrencyChanged(it)) },
        currencyCodes = currencyChoices,
    )
    fieldErrors["currency"]?.let { error ->
        EvenUpValidationMessage(message = error)
    }
}

@Composable
private fun ItemEditContent(
    draft: ManualReceiptEditDraft.Item,
    currencyCode: String,
    fieldErrors: Map<String, String>,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
) {
    val fieldId = draft.itemId ?: "draft"
    val quantity = draft.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val showPriceEach = quantity > 1
    val currencySymbol = manualCurrencySymbol(currencyCode)
    val averagePriceNote = draft.averagePriceNote(currencyCode)
    EvenUpTextField(
        value = draft.name,
        onValueChange = { onEvent(ManualReceiptEntryUiEvent.ItemNameChanged(it)) },
        label = "Item name",
        isError = fieldErrors.containsKey("item_name_$fieldId"),
        supportingText = fieldErrors["item_name_$fieldId"],
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
    QuantityStepper(
        quantity = draft.quantity,
        onQuantityChange = { onEvent(ManualReceiptEntryUiEvent.ItemQuantityChanged(it)) },
        onDecrease = { onEvent(ManualReceiptEntryUiEvent.ItemQuantityStepped(-1)) },
        onIncrease = { onEvent(ManualReceiptEntryUiEvent.ItemQuantityStepped(1)) },
        canDecrease = quantity > 1,
        error = fieldErrors["item_quantity_$fieldId"],
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        AnimatedVisibility(visible = showPriceEach) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
                verticalAlignment = Alignment.Top,
            ) {
                EvenUpMoneyField(
                    value = draft.unitPrice,
                    onValueChange = { onEvent(ManualReceiptEntryUiEvent.ItemUnitPriceChanged(it)) },
                    label = "Price each",
                    currencySymbol = currencySymbol,
                    modifier = Modifier.weight(1f),
                )
                EvenUpMoneyField(
                    value = draft.lineTotal,
                    onValueChange = { onEvent(ManualReceiptEntryUiEvent.ItemLineTotalChanged(it)) },
                    label = "Item total",
                    currencySymbol = currencySymbol,
                    modifier = Modifier.weight(1f),
                    isError = fieldErrors.containsKey("item_amount_$fieldId"),
                )
            }
        }
        AnimatedVisibility(visible = !showPriceEach) {
            EvenUpMoneyField(
                value = draft.lineTotal,
                onValueChange = { onEvent(ManualReceiptEntryUiEvent.ItemLineTotalChanged(it)) },
                label = "Item total",
                currencySymbol = currencySymbol,
                isError = fieldErrors.containsKey("item_amount_$fieldId"),
            )
        }
        averagePriceNote?.let { note ->
            Text(
                text = note,
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textSecondary,
            )
        }
        fieldErrors["item_amount_$fieldId"]?.let { error ->
            Text(
                text = error,
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.error,
            )
        }
    }
}

@Composable
private fun FeeEditContent(
    draft: ManualReceiptEditDraft.Fee,
    currencyCode: String,
    fieldErrors: Map<String, String>,
    onEvent: (ManualReceiptEntryUiEvent) -> Unit,
) {
    val fieldId = draft.feeId ?: "draft"
    FeeTypeSelector(
        selectedType = draft.type,
        onTypeSelected = { onEvent(ManualReceiptEntryUiEvent.FeeTypeChanged(it)) },
    )
    if (draft.type == FeeType.Other) {
        EvenUpTextField(
            value = draft.label,
            onValueChange = { onEvent(ManualReceiptEntryUiEvent.FeeLabelChanged(it)) },
            label = "Fee name",
            isError = fieldErrors.containsKey("fee_label_$fieldId"),
            supportingText = fieldErrors["fee_label_$fieldId"],
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
    }
    EvenUpMoneyField(
        value = draft.amount,
        onValueChange = { onEvent(ManualReceiptEntryUiEvent.FeeAmountChanged(it)) },
        label = "Fee amount",
        currencySymbol = manualCurrencySymbol(currencyCode),
        isError = fieldErrors.containsKey("fee_amount_$fieldId"),
        supportingText = fieldErrors["fee_amount_$fieldId"],
    )
    fieldErrors["fee_type_$fieldId"]?.let { error ->
        EvenUpValidationMessage(message = error)
    }
}

@Composable
private fun FeeTypeSelector(
    selectedType: FeeType,
    onTypeSelected: (FeeType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        Text(
            text = "Fee type",
            style = EvenUpTheme.typography.bodySmall,
            color = EvenUpTheme.colors.textSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
        ) {
            listOf(FeeType.Tax, FeeType.Tip, FeeType.ServiceFee, FeeType.Other).forEach { type ->
                val selected = type == selectedType
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTypeSelected(type) }
                        .semantics {
                            role = Role.Button
                            contentDescription = manualFeeDisplayLabel(type)
                        },
                    shape = EvenUpTheme.shapes.chip,
                    color = if (selected) EvenUpTheme.colors.primary else EvenUpTheme.colors.surfaceElevated,
                    contentColor = if (selected) EvenUpTheme.colors.onPrimary else EvenUpTheme.colors.textPrimary,
                    border = BorderStroke(1.dp, if (selected) EvenUpTheme.colors.primary else EvenUpTheme.colors.border),
                ) {
                    Text(
                        text = manualFeeDisplayLabel(type),
                        modifier = Modifier.padding(vertical = EvenUpTheme.spacing.space12),
                        style = EvenUpTheme.typography.caption,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuantityStepper(
    quantity: String,
    onQuantityChange: (String) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    canDecrease: Boolean,
    error: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        Text(
            text = "Quantity",
            style = EvenUpTheme.typography.bodySmall,
            color = EvenUpTheme.colors.textSecondary,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = EvenUpTheme.shapes.input,
            color = EvenUpTheme.colors.surfaceElevated,
            contentColor = EvenUpTheme.colors.textPrimary,
            border = BorderStroke(1.dp, if (error == null) EvenUpTheme.colors.border else EvenUpTheme.colors.error),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = EvenUpTheme.spacing.space8, vertical = EvenUpTheme.spacing.space4),
                horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDecrease,
                    enabled = canDecrease,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = "Decrease quantity",
                    )
                }
                BasicTextField(
                    value = quantity,
                    onValueChange = onQuantityChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = EvenUpTheme.typography.body.copy(
                        color = EvenUpTheme.colors.textPrimary,
                        textAlign = TextAlign.Center,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    cursorBrush = SolidColor(EvenUpTheme.colors.primary),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Quantity" },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            innerTextField()
                        }
                    },
                )
                IconButton(
                    onClick = onIncrease,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Increase quantity",
                    )
                }
            }
        }
        error?.let {
            Text(
                text = it,
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.error,
            )
        }
    }
}

@Composable
private fun EmptyRowsState(
    title: String,
    message: String,
    actionText: String,
    onActionClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EvenUpTheme.spacing.space12),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
    ) {
        Text(
            text = title,
            style = EvenUpTheme.typography.bodyStrong,
            color = EvenUpTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = EvenUpTheme.typography.bodySmall,
            color = EvenUpTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        EvenUpSecondaryButton(
            text = actionText,
            onClick = onActionClick,
        )
    }
}

@Composable
private fun SecondaryListActionRow(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = text
            }
            .padding(vertical = EvenUpTheme.spacing.space12),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = EvenUpTheme.colors.textPrimary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = EvenUpTheme.typography.button,
            color = EvenUpTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun ValueRow(
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
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = EvenUpTheme.colors.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    strong: Boolean = false,
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
            style = if (strong) EvenUpTheme.typography.bodyStrong else EvenUpTheme.typography.bodySmall,
            color = if (strong) EvenUpTheme.colors.textPrimary else EvenUpTheme.colors.textSecondary,
        )
        Text(
            text = value,
            style = if (strong) EvenUpTheme.typography.moneyValue else EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textPrimary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    detail: String = "",
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
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textSecondary,
            )
        }
    }
}

private fun editSheetTitle(draft: ManualReceiptEditDraft?): String = when (draft) {
    is ManualReceiptEditDraft.Merchant -> "Edit merchant"
    is ManualReceiptEditDraft.Date -> "Edit date"
    is ManualReceiptEditDraft.Currency -> "Edit currency"
    is ManualReceiptEditDraft.Item -> if (draft.isNew) "Add item" else "Edit item"
    is ManualReceiptEditDraft.Fee -> if (draft.isNew) "Add fee" else "Edit fee"
    null -> ""
}

private fun ManualReceiptEditDraft?.primaryActionLabel(): String = when (this) {
    is ManualReceiptEditDraft.Item -> primaryActionLabel
    is ManualReceiptEditDraft.Fee -> if (isNew) "Add fee" else "Save changes"
    null -> "Done"
    else -> "Save changes"
}
