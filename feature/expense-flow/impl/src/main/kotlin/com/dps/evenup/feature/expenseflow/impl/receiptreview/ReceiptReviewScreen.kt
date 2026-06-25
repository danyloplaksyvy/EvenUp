package com.dps.evenup.feature.expenseflow.impl.receiptreview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpBottomSheet
import com.dps.evenup.core.designsystem.api.EvenUpCard
import com.dps.evenup.core.designsystem.api.EvenUpPinnedTopBarScaffold
import com.dps.evenup.core.designsystem.api.EvenUpErrorState
import com.dps.evenup.core.designsystem.api.EvenUpIconButton
import com.dps.evenup.core.designsystem.api.EvenUpLoadingState
import com.dps.evenup.core.designsystem.api.EvenUpMoneyField
import com.dps.evenup.core.designsystem.api.EvenUpPrimaryButton
import com.dps.evenup.core.designsystem.api.EvenUpSecondaryButton
import com.dps.evenup.core.designsystem.api.EvenUpTextField
import com.dps.evenup.core.designsystem.api.EvenUpTextButton
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpValidationMessage
import com.dps.evenup.core.designsystem.api.EvenUpValidationSeverity
import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.feature.expenseflow.impl.receiptentry.CurrencySelector
import com.dps.evenup.feature.expenseflow.impl.receiptentry.ReceiptDatePickerField
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun ReceiptReviewScreen(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    EvenUpPinnedTopBarScaffold(
        title = "Receipt review",
        onNavigationClick = { onEvent(ReceiptReviewUiEvent.BackClick) },
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (!uiState.isLoading && !uiState.missingDraft) {
                ReceiptReviewBottomBar(uiState = uiState, onEvent = onEvent)
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> EvenUpLoadingState(
                message = "Loading receipt...",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            uiState.missingDraft -> EvenUpErrorState(
                title = "Receipt unavailable",
                message = uiState.submitError ?: "Start a new receipt to continue.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                retryText = "Go back",
                onRetryClick = { onEvent(ReceiptReviewUiEvent.BackClick) },
            )

            else -> ReceiptReviewContent(
                uiState = uiState,
                onEvent = onEvent,
                contentPadding = innerPadding,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReceiptReviewContent(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    val scrollState = rememberScrollState()
    val detailsRequester = remember { BringIntoViewRequester() }
    val itemsRequester = remember { BringIntoViewRequester() }
    val adjustmentsRequester = remember { BringIntoViewRequester() }
    val summaryRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(uiState.validationRequestId, uiState.firstBlockingSection) {
        when (uiState.firstBlockingSection) {
            ReceiptReviewSection.Details -> detailsRequester.bringIntoView()
            ReceiptReviewSection.Items -> itemsRequester.bringIntoView()
            ReceiptReviewSection.Adjustments -> adjustmentsRequester.bringIntoView()
            ReceiptReviewSection.Summary -> summaryRequester.bringIntoView()
            null -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .padding(horizontal = EvenUpTheme.spacing.space20)
            .padding(top = EvenUpTheme.spacing.space16, bottom = EvenUpTheme.spacing.space24),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space20),
    ) {
        ReceiptReviewHeader(uiState = uiState, onStatusClick = { onEvent(ReceiptReviewUiEvent.StatusClick) })
        ReceiptReviewDetailsCard(
            uiState = uiState,
            onEvent = onEvent,
            modifier = Modifier.bringIntoViewRequester(detailsRequester),
        )
        ReceiptReviewItemsCard(
            uiState = uiState,
            onEvent = onEvent,
            modifier = Modifier.bringIntoViewRequester(itemsRequester),
        )
        ReceiptReviewAdjustmentsCard(
            uiState = uiState,
            onEvent = onEvent,
            modifier = Modifier.bringIntoViewRequester(adjustmentsRequester),
        )
        ReceiptReviewTotalsCard(
            uiState = uiState,
            onEvent = onEvent,
            modifier = Modifier.bringIntoViewRequester(summaryRequester),
        )
        uiState.submitError?.let { error ->
            EvenUpValidationMessage(message = error)
        }
    }
    ReceiptReviewEditSheet(uiState = uiState, onEvent = onEvent)
    ReceiptReviewIssueNavigator(uiState = uiState, onEvent = onEvent)
}

@Composable
private fun ReceiptReviewBottomBar(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
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
                onClick = { onEvent(ReceiptReviewUiEvent.ContinueClick) },
                enabled = uiState.canContinue,
            )
        }
    }
}

@Composable
private fun ReceiptReviewHeader(
    uiState: ReceiptReviewUiState,
    onStatusClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        Text(
            text = uiState.scannedReceiptTotalLabel ?: uiState.summaryTotalLabel,
            style = EvenUpTheme.typography.displayLargeTotal,
            color = EvenUpTheme.colors.textPrimary,
        )
        Text(
            text = uiState.merchantName.ifBlank { "Unnamed merchant" },
            style = EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textSecondary,
        )
        ReceiptReviewStatusMessage(uiState = uiState, onClick = onStatusClick)
    }
}

@Composable
private fun ReceiptReviewStatusMessage(
    uiState: ReceiptReviewUiState,
    onClick: () -> Unit,
) {
    val isWarning = uiState.hasWarningStatus
    val containerColor = if (isWarning) EvenUpTheme.colors.warningContainer else EvenUpTheme.colors.successContainer
    val contentColor = if (isWarning) EvenUpTheme.colors.warning else EvenUpTheme.colors.success
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isWarning, onClick = onClick)
            .semantics {
                if (isWarning) role = Role.Button
                contentDescription = if (isWarning) {
                    "Warning: ${uiState.statusAccessibilityLabel}"
                } else {
                    uiState.statusLabel
                }
            },
        shape = EvenUpTheme.shapes.input,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = EvenUpTheme.spacing.space12,
                vertical = EvenUpTheme.spacing.space8,
            ),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReceiptReviewDetailsCard(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        SectionHeader(title = "Receipt details")
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
                value = uiState.dateDisplayLabel.ifBlank { "Not set" },
                isError = uiState.fieldErrors.containsKey("date"),
                supportingText = uiState.fieldErrors["date"],
                onClick = { onEvent(ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Date)) },
            )
            HorizontalDivider(color = EvenUpTheme.colors.divider)
            ReviewValueRow(
                label = "Currency",
                value = uiState.currencyCode,
                isError = uiState.fieldErrors.containsKey("currency") ||
                    uiState.issues.any { issue -> issue.kind == ReceiptReviewIssueKind.UncertainCurrency },
                supportingText = uiState.fieldErrors["currency"]
                    ?: uiState.issues.firstOrNull { issue -> issue.kind == ReceiptReviewIssueKind.UncertainCurrency }?.message,
                onClick = { onEvent(ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Currency)) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReceiptReviewItemsCard(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
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
            uiState.items.forEachIndexed { index, item ->
                val itemRequester = remember(item.id) { BringIntoViewRequester() }
                LaunchedEffect(uiState.validationRequestId, uiState.firstBlockingItemId) {
                    if (uiState.firstBlockingItemId == item.id) {
                        itemRequester.bringIntoView()
                    }
                }
                ReceiptReviewItemRow(
                    item = item,
                    currencyCode = uiState.currencyCode,
                    hasError = uiState.fieldErrors.containsKey("item_name_${item.id}") ||
                        uiState.fieldErrors.containsKey("item_quantity_${item.id}") ||
                        uiState.fieldErrors.containsKey("item_amount_${item.id}"),
                    modifier = Modifier.bringIntoViewRequester(itemRequester),
                    onClick = { onEvent(ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Item(item.id))) },
                )
                if (index != uiState.items.lastIndex) {
                    HorizontalDivider(color = EvenUpTheme.colors.divider)
                }
            }
            SecondaryListActionRow(
                text = "+ Add item",
                onClick = { onEvent(ReceiptReviewUiEvent.AddItemClick) },
            )
        }
    }
}

@Composable
private fun ReceiptReviewItemRow(
    item: ReceiptReviewItemUiState,
    currencyCode: String,
    hasError: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val rowShape = EvenUpTheme.shapes.input
    val suggestedNote = item.suggestedCorrectionNote(currencyCode)
    val isHighlighted = item.needsReview || item.suggestedCorrection != null
    val reviewDescription = suggestedNote?.let { note ->
        if (item.suggestedCorrection != null) {
            "Needs review: $note. "
        } else {
            "Needs review: $note. "
        }
    }.orEmpty()
    val quantityDetail = item.quantityDetail(currencyCode)
    val totalLabel = item.totalLabel(currencyCode)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isHighlighted) {
                    Modifier
                        .background(EvenUpTheme.colors.warningContainer, rowShape)
                        .border(BorderStroke(1.dp, EvenUpTheme.colors.warning.copy(alpha = 0.28f)), rowShape)
                        .padding(horizontal = EvenUpTheme.spacing.space12)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "${item.name.ifBlank { "Receipt item" }}, $totalLabel, $quantityDetail. ${reviewDescription}Edit item"
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
                color = when {
                    hasError -> EvenUpTheme.colors.error
                    else -> EvenUpTheme.colors.textPrimary
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = quantityDetail,
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isHighlighted && suggestedNote != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = EvenUpTheme.colors.warning,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = suggestedNote,
                        style = EvenUpTheme.typography.caption,
                        color = EvenUpTheme.colors.warning,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            text = totalLabel,
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
private fun ReceiptReviewAdjustmentsCard(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        SectionHeader(
            title = if (uiState.additiveFees.isEmpty() && uiState.discounts.isNotEmpty()) "Discounts" else "Fees",
            detail = if (uiState.additiveFees.isEmpty() && uiState.discounts.isNotEmpty()) {
                uiState.discountCountLabel
            } else {
                uiState.feeCountLabel
            },
        )
        EvenUpCard {
            uiState.fieldErrors["fees"]?.let { error ->
                EvenUpValidationMessage(message = error)
            }
            if (uiState.additiveFees.isEmpty() && uiState.discounts.isEmpty()) {
                Text(
                    text = "No fees or discounts",
                    style = EvenUpTheme.typography.bodySmall,
                    color = EvenUpTheme.colors.textSecondary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            uiState.additiveFees.forEachIndexed { index, fee ->
                ReceiptReviewFeeRow(
                    fee = fee,
                    currencyCode = uiState.currencyCode,
                    hasError = uiState.fieldErrors.containsKey("fee_label_${fee.id}") ||
                        uiState.fieldErrors.containsKey("fee_amount_${fee.id}"),
                    onClick = { onEvent(ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Fee(fee.id))) },
                )
                if (index != uiState.additiveFees.lastIndex || uiState.discounts.isNotEmpty()) {
                    HorizontalDivider(color = EvenUpTheme.colors.divider)
                }
            }
            if (uiState.discounts.isNotEmpty()) {
                Text(
                    text = "Discounts",
                    style = EvenUpTheme.typography.caption,
                    color = EvenUpTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = EvenUpTheme.spacing.space4),
                )
            }
            uiState.discounts.forEachIndexed { index, fee ->
                ReceiptReviewFeeRow(
                    fee = fee,
                    currencyCode = uiState.currencyCode,
                    hasError = uiState.fieldErrors.containsKey("fee_label_${fee.id}") ||
                        uiState.fieldErrors.containsKey("fee_amount_${fee.id}"),
                    onClick = { onEvent(ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.Fee(fee.id))) },
                )
                if (index != uiState.discounts.lastIndex) {
                    HorizontalDivider(color = EvenUpTheme.colors.divider)
                }
            }
            SecondaryListActionRow(
                text = "+ Add fee",
                onClick = { onEvent(ReceiptReviewUiEvent.AddFeeClick) },
            )
        }
    }
}

@Composable
private fun ReceiptReviewFeeRow(
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
                contentDescription = "Edit ${fee.label.ifBlank { if (fee.isDiscount) "discount" else "fee" }}"
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
            text = text.removePrefix("+ "),
            style = EvenUpTheme.typography.button,
            color = EvenUpTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun ReceiptReviewTotalsCard(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        SectionHeader(title = "Summary", detail = uiState.summaryStatusLabel)
        EvenUpCard {
            SummaryRow(label = "Subtotal", value = uiState.derivedSubtotalLabel)
            if (uiState.feeTotalMinor > 0L) {
                SummaryRow(label = "Fees", value = uiState.feesTotalLabel)
            }
            if (uiState.discountTotalMinor > 0L) {
                SummaryRow(label = "Discounts", value = uiState.discountsTotalLabel)
            }
            SummaryRow(label = "Calculated total", value = uiState.calculatedTotalLabel)
            HorizontalDivider(color = EvenUpTheme.colors.divider)
            SummaryActionRow(
                label = "Receipt total",
                value = uiState.scannedReceiptTotalLabel ?: "Not detected",
                showChevron = uiState.reconciliation.isIssue,
                isError = uiState.fieldErrors.containsKey("summary"),
                supportingText = uiState.fieldErrors["summary"],
                onClick = {
                    when (uiState.reconciliation.type) {
                        ReceiptReviewReconciliationType.ReviewItems -> {
                            onEvent(ReceiptReviewUiEvent.ReviewHighlightedItemsClick)
                        }
                        ReceiptReviewReconciliationType.Mismatch -> {
                            if (uiState.suspectedCorrectionCount > 0) {
                                onEvent(ReceiptReviewUiEvent.ReviewHighlightedItemsClick)
                            } else {
                                onEvent(ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.TotalCheck))
                            }
                        }
                        ReceiptReviewReconciliationType.MissingScannedTotal -> {
                            onEvent(ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.TotalCheck))
                        }
                        ReceiptReviewReconciliationType.Matched -> Unit
                    }
                },
            )
            ReceiptReviewSummaryStatus(uiState = uiState, onEvent = onEvent)
        }
    }
}

@Composable
private fun ReceiptReviewSummaryStatus(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    val clickAction = when (uiState.reconciliation.type) {
        ReceiptReviewReconciliationType.ReviewItems -> ({ onEvent(ReceiptReviewUiEvent.ReviewHighlightedItemsClick) })
        ReceiptReviewReconciliationType.Mismatch -> {
            if (uiState.suspectedCorrectionCount > 0) {
                ({ onEvent(ReceiptReviewUiEvent.ReviewHighlightedItemsClick) })
            } else {
                ({ onEvent(ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.TotalCheck)) })
            }
        }
        ReceiptReviewReconciliationType.MissingScannedTotal -> {
            ({ onEvent(ReceiptReviewUiEvent.EditTargetSelected(ReceiptReviewEditTarget.TotalCheck)) })
        }
        ReceiptReviewReconciliationType.Matched -> null
    }
    val modifier = if (clickAction == null) {
        Modifier
    } else {
        Modifier
            .clickable(onClick = clickAction)
            .semantics { role = Role.Button }
    }
    EvenUpValidationMessage(
        message = uiState.reconciliation.message,
        modifier = modifier.semantics {
            contentDescription = if (uiState.reconciliation.isIssue) {
                "Warning: ${uiState.reconciliation.message}"
            } else {
                uiState.reconciliation.message
            }
        },
        severity = if (uiState.reconciliation.isIssue) {
            EvenUpValidationSeverity.Warning
        } else {
            EvenUpValidationSeverity.Success
        },
    )
    uiState.reconciliation.suggestedCorrectionActionLabel?.let { label ->
        EvenUpTextButton(
            text = label,
            onClick = { onEvent(ReceiptReviewUiEvent.ReviewHighlightedItemsClick) },
            modifier = Modifier.semantics {
                role = Role.Button
                contentDescription = label
            },
        )
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
private fun SummaryActionRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    supportingText: String? = null,
    isError: Boolean = false,
    showChevron: Boolean = true,
) {
    val rowModifier = if (showChevron) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "$label, $value"
            }
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier
            .padding(vertical = EvenUpTheme.spacing.space4),
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
            style = EvenUpTheme.typography.moneyValue,
            color = if (isError) EvenUpTheme.colors.error else EvenUpTheme.colors.textPrimary,
            textAlign = TextAlign.End,
        )
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = EvenUpTheme.colors.textTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
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
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = EvenUpTheme.colors.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ReceiptReviewEditSheet(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    val editDraft = uiState.editDraft
    EvenUpBottomSheet(
        visible = editDraft != null,
        onDismissRequest = { onEvent(ReceiptReviewUiEvent.EditDismissed) },
        title = editSheetTitle(editDraft),
        headerAction = {
            EditSheetHeaderAction(uiState = uiState, editDraft = editDraft, onEvent = onEvent)
        },
    ) {
        when (editDraft) {
            is ReceiptReviewEditDraft.Merchant -> MerchantEditContent(
                draft = editDraft,
                fieldErrors = uiState.fieldErrors,
                onEvent = onEvent,
            )
            is ReceiptReviewEditDraft.Date -> DateEditContent(
                draft = editDraft,
                fieldErrors = uiState.fieldErrors,
                onEvent = onEvent,
            )
            is ReceiptReviewEditDraft.Currency -> CurrencyEditContent(
                draft = editDraft,
                fieldErrors = uiState.fieldErrors,
                onEvent = onEvent,
            )
            is ReceiptReviewEditDraft.ReceiptTotal -> ReceiptTotalEditContent(
                draft = editDraft,
                currencyCode = uiState.currencyCode,
                fieldErrors = uiState.fieldErrors,
                onEvent = onEvent,
            )
            ReceiptReviewEditDraft.TotalCheck -> TotalCheckContent(uiState = uiState, onEvent = onEvent)
            is ReceiptReviewEditDraft.Item -> ItemEditContent(
                draft = editDraft,
                currencyCode = uiState.currencyCode,
                fieldErrors = uiState.fieldErrors,
                onEvent = onEvent,
            )
            is ReceiptReviewEditDraft.Fee -> FeeEditContent(
                draft = editDraft,
                fieldErrors = uiState.fieldErrors,
                onEvent = onEvent,
            )
            null -> Unit
        }
        EvenUpPrimaryButton(
            text = editDraft.primaryActionLabel(),
            onClick = { onEvent(ReceiptReviewUiEvent.EditCommitClick) },
            modifier = Modifier.semantics {
                contentDescription = editDraft.primaryActionAccessibilityLabel()
            },
        )
    }
}

private fun ReceiptReviewEditDraft?.primaryActionLabel(): String = when (this) {
    is ReceiptReviewEditDraft.Item -> primaryActionLabel
    is ReceiptReviewEditDraft.Fee -> if (isNew) "Add fee" else "Save changes"
    null -> "Done"
    else -> "Save changes"
}

private fun ReceiptReviewEditDraft?.primaryActionAccessibilityLabel(): String = when (this) {
    is ReceiptReviewEditDraft.Item -> when (primaryActionLabel) {
        "Confirm amount" -> "Confirm amount for ${name.ifBlank { "item" }}"
        "Add item" -> "Add item"
        else -> "Save changes for ${name.ifBlank { "item" }}"
    }
    else -> primaryActionLabel()
}

@Composable
private fun ReceiptReviewIssueNavigator(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    EvenUpBottomSheet(
        visible = uiState.issueNavigatorVisible,
        onDismissRequest = { onEvent(ReceiptReviewUiEvent.IssueNavigatorDismissed) },
        title = "Review issues",
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
        ) {
            uiState.issues.forEach { issue ->
                IssueNavigatorRow(
                    issue = issue,
                    onClick = { onEvent(ReceiptReviewUiEvent.IssueSelected(issue.id)) },
                )
            }
        }
    }
}

@Composable
private fun IssueNavigatorRow(
    issue: ReceiptReviewIssueUiState,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "${issue.accessibilityLabel} ${issue.actionLabel}"
            },
        shape = EvenUpTheme.shapes.input,
        color = EvenUpTheme.colors.surfaceElevated,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, EvenUpTheme.colors.border),
    ) {
        Row(
            modifier = Modifier.padding(EvenUpTheme.spacing.space12),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = EvenUpTheme.colors.warning,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
            ) {
                Text(
                    text = issue.title,
                    style = EvenUpTheme.typography.body,
                    color = EvenUpTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = issue.message,
                    style = EvenUpTheme.typography.caption,
                    color = EvenUpTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = issue.actionLabel,
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textPrimary,
                textAlign = TextAlign.End,
            )
        }
    }
}

private fun editSheetTitle(draft: ReceiptReviewEditDraft?): String = when (draft) {
    is ReceiptReviewEditDraft.Merchant -> "Edit merchant"
    is ReceiptReviewEditDraft.Date -> "Edit date"
    is ReceiptReviewEditDraft.Currency -> "Edit currency"
    is ReceiptReviewEditDraft.ReceiptTotal -> "Edit scanned total"
    ReceiptReviewEditDraft.TotalCheck -> "Total check"
    is ReceiptReviewEditDraft.Item -> if (draft.isNew) "Add item" else "Edit item"
    is ReceiptReviewEditDraft.Fee -> when {
        draft.isNew -> "Add fee"
        draft.type == FeeType.Discount -> "Edit discount"
        else -> "Edit fee"
    }
    null -> ""
}

@Composable
private fun EditSheetHeaderAction(
    uiState: ReceiptReviewUiState,
    editDraft: ReceiptReviewEditDraft?,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    when (editDraft) {
        is ReceiptReviewEditDraft.Item -> {
            val itemId = editDraft.itemId ?: return
            if (editDraft.isNew || uiState.items.size <= 1) return
            EvenUpIconButton(
                contentDescription = "Delete item",
                onClick = { onEvent(ReceiptReviewUiEvent.RemoveItemClick(itemId)) },
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = EvenUpTheme.colors.error,
                )
            }
        }
        is ReceiptReviewEditDraft.Fee -> {
            val feeId = editDraft.feeId ?: return
            if (editDraft.isNew) return
            EvenUpIconButton(
                contentDescription = if (editDraft.type == FeeType.Discount) "Delete discount" else "Delete fee",
                onClick = { onEvent(ReceiptReviewUiEvent.RemoveFeeClick(feeId)) },
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = EvenUpTheme.colors.error,
                )
            }
        }
        else -> Unit
    }
}

@Composable
private fun MerchantEditContent(
    draft: ReceiptReviewEditDraft.Merchant,
    fieldErrors: Map<String, String>,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    EvenUpTextField(
        value = draft.value,
        onValueChange = { onEvent(ReceiptReviewUiEvent.MerchantNameChanged(it)) },
        label = "Merchant",
        isError = fieldErrors.containsKey("merchant"),
        supportingText = fieldErrors["merchant"],
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
}

@Composable
private fun DateEditContent(
    draft: ReceiptReviewEditDraft.Date,
    fieldErrors: Map<String, String>,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    ReceiptDatePickerField(
        value = draft.value,
        onDateSelected = { onEvent(ReceiptReviewUiEvent.DateChanged(it)) },
    )
    fieldErrors["date"]?.let { error ->
        EvenUpValidationMessage(message = error)
    }
}

@Composable
private fun CurrencyEditContent(
    draft: ReceiptReviewEditDraft.Currency,
    fieldErrors: Map<String, String>,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    CurrencySelector(
        selectedCurrencyCode = draft.value,
        onCurrencySelected = { onEvent(ReceiptReviewUiEvent.CurrencyChanged(it)) },
    )
    fieldErrors["currency"]?.let { error ->
        EvenUpValidationMessage(message = error)
    }
}

@Composable
private fun ReceiptTotalEditContent(
    draft: ReceiptReviewEditDraft.ReceiptTotal,
    currencyCode: String,
    fieldErrors: Map<String, String>,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    EvenUpMoneyField(
        value = draft.value,
        onValueChange = { onEvent(ReceiptReviewUiEvent.ReceiptTotalChanged(it)) },
        label = "Scanned receipt total",
        currencySymbol = currencySymbol(currencyCode),
        isError = fieldErrors.containsKey("summary"),
        supportingText = fieldErrors["summary"],
    )
}

@Composable
private fun TotalCheckContent(
    uiState: ReceiptReviewUiState,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
    ) {
        SummaryRow(label = "Calculated from items", value = uiState.summaryTotalLabel)
        SummaryRow(
            label = "Scanned receipt total",
            value = uiState.scannedReceiptTotalLabel ?: "Not detected",
        )
        uiState.reconciliation.differenceLabel?.let { difference ->
            SummaryRow(label = "Difference", value = difference)
        }
        uiState.reconciliation.reason?.let { reason ->
            Text(
                text = reason,
                style = EvenUpTheme.typography.bodySmall,
                color = EvenUpTheme.colors.textSecondary,
            )
        }
        EvenUpValidationMessage(
            message = uiState.reconciliation.message,
            severity = if (uiState.reconciliation.isIssue) {
                EvenUpValidationSeverity.Warning
            } else {
                EvenUpValidationSeverity.Success
            },
        )
        uiState.suggestedCorrectionActionLabel?.let { label ->
            EvenUpSecondaryButton(
                text = label,
                onClick = { onEvent(ReceiptReviewUiEvent.ReviewHighlightedItemsClick) },
            )
        }
        if (uiState.unresolvedReviewItemCount > 0) {
            EvenUpSecondaryButton(
                text = "Review highlighted items",
                onClick = { onEvent(ReceiptReviewUiEvent.ReviewHighlightedItemsClick) },
            )
        }
        if (uiState.reconciliation.type == ReceiptReviewReconciliationType.Mismatch ||
            uiState.reconciliation.type == ReceiptReviewReconciliationType.MissingScannedTotal
        ) {
            EvenUpSecondaryButton(
                text = "Use receipt total",
                onClick = { onEvent(ReceiptReviewUiEvent.UseReceiptTotalClick) },
                modifier = Modifier.semantics {
                    contentDescription = "Use receipt total and reconcile safe differences"
                },
            )
            EvenUpSecondaryButton(
                text = "Keep calculated total",
                onClick = { onEvent(ReceiptReviewUiEvent.KeepCalculatedTotalClick) },
                modifier = Modifier.semantics {
                    contentDescription = "Keep calculated total as the confirmed receipt total"
                },
            )
            EvenUpSecondaryButton(
                text = if (uiState.scannedReceiptTotalLabel == null) "Enter manually" else "Edit manually",
                onClick = { onEvent(ReceiptReviewUiEvent.EditReceiptTotalClick) },
                modifier = Modifier.semantics {
                    contentDescription = "Edit receipt total manually"
                },
            )
        }
    }
}

@Composable
private fun ItemEditContent(
    draft: ReceiptReviewEditDraft.Item,
    currencyCode: String,
    fieldErrors: Map<String, String>,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    val fieldId = draft.itemId ?: "draft"
    val quantity = draft.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val showPriceEach = quantity > 1
    val currencySymbol = currencySymbol(currencyCode)
    val averagePriceNote = draft.averagePriceNote(currencyCode)
    draft.suggestedCorrection?.let { correction ->
        SuggestedItemCorrectionContent(
            draft = draft,
            correction = correction,
            currencyCode = currencyCode,
            onUseClick = { onEvent(ReceiptReviewUiEvent.UseSuggestedItemCorrectionClick) },
            onKeepClick = { onEvent(ReceiptReviewUiEvent.EditDismissed) },
        )
    }
    draft.reviewNote?.let { note ->
        EvenUpValidationMessage(
            message = note,
            severity = EvenUpValidationSeverity.Warning,
        )
    }
    EvenUpTextField(
        value = draft.name,
        onValueChange = { onEvent(ReceiptReviewUiEvent.ItemNameChanged(it)) },
        label = "Item name",
        isError = fieldErrors.containsKey("item_name_$fieldId"),
        supportingText = fieldErrors["item_name_$fieldId"],
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
    QuantityStepper(
        quantity = draft.quantity,
        onQuantityChange = { onEvent(ReceiptReviewUiEvent.ItemQuantityChanged(it)) },
        onDecrease = { onEvent(ReceiptReviewUiEvent.ItemQuantityStepped(-1)) },
        onIncrease = { onEvent(ReceiptReviewUiEvent.ItemQuantityStepped(1)) },
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
                    onValueChange = { onEvent(ReceiptReviewUiEvent.ItemUnitPriceChanged(it)) },
                    label = "Price each",
                    currencySymbol = currencySymbol,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = "Price each for ${draft.name.ifBlank { "item" }} in $currencyCode"
                        },
                )
                EvenUpMoneyField(
                    value = draft.lineTotal,
                    onValueChange = { onEvent(ReceiptReviewUiEvent.ItemLineTotalChanged(it)) },
                    label = "Item total",
                    currencySymbol = currencySymbol,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = "Item total for ${draft.name.ifBlank { "item" }} in $currencyCode"
                        },
                    isError = fieldErrors.containsKey("item_amount_$fieldId"),
                )
            }
        }
        AnimatedVisibility(visible = !showPriceEach) {
            EvenUpMoneyField(
                value = draft.lineTotal,
                onValueChange = { onEvent(ReceiptReviewUiEvent.ItemLineTotalChanged(it)) },
                label = "Item total",
                currencySymbol = currencySymbol,
                modifier = Modifier.semantics {
                    contentDescription = "Item total for ${draft.name.ifBlank { "item" }} in $currencyCode"
                },
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
private fun SuggestedItemCorrectionContent(
    draft: ReceiptReviewEditDraft.Item,
    correction: SuspectedItemCorrection,
    currencyCode: String,
    onUseClick: () -> Unit,
    onKeepClick: () -> Unit,
) {
    val quantity = draft.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val currentTotal = formatCurrency(formatMoneyInput(correction.currentAmountMinor), currencyCode)
    val suggestedTotal = formatCurrency(formatMoneyInput(correction.suggestedAmountMinor), currencyCode)
    val currentLabel = if (quantity > 1) {
        val currentEach = formatCurrency(formatAverageAmount(correction.currentAmountMinor, quantity), currencyCode)
        "$currentTotal · $currentEach each"
    } else {
        currentTotal
    }
    val suggestedLabel = if (quantity > 1) {
        val suggestedEach = formatCurrency(formatAverageAmount(correction.suggestedAmountMinor, quantity), currencyCode)
        "$suggestedTotal · $suggestedEach each"
    } else {
        suggestedTotal
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Suggested correction for ${draft.name.ifBlank { "item" }}. Current $currentLabel. Suggested $suggestedLabel."
            },
        shape = EvenUpTheme.shapes.input,
        color = EvenUpTheme.colors.warningContainer,
        contentColor = EvenUpTheme.colors.warning,
        border = BorderStroke(1.dp, EvenUpTheme.colors.warning.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier.padding(EvenUpTheme.spacing.space12),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
        ) {
            Text(
                text = "This correction helps match the receipt total",
                style = EvenUpTheme.typography.bodySmall,
                color = EvenUpTheme.colors.warning,
            )
            SummaryRow(label = "Current item total", value = currentLabel)
            SummaryRow(label = "Suggested from receipt", value = suggestedLabel)
            EvenUpSecondaryButton(
                text = "Use $suggestedTotal",
                onClick = onUseClick,
                modifier = Modifier.semantics {
                    contentDescription = "Use suggested amount $suggestedTotal"
                },
            )
            EvenUpTextButton(
                text = "Keep $currentTotal",
                onClick = onKeepClick,
                modifier = Modifier.semantics {
                    contentDescription = "Keep current amount $currentTotal"
                },
            )
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
private fun FeeEditContent(
    draft: ReceiptReviewEditDraft.Fee,
    fieldErrors: Map<String, String>,
    onEvent: (ReceiptReviewUiEvent) -> Unit,
) {
    val fieldId = draft.feeId ?: "draft"
    FeeTypeSelector(
        selectedType = draft.type,
        includeDiscount = draft.type == FeeType.Discount,
        onTypeSelected = { onEvent(ReceiptReviewUiEvent.FeeTypeChanged(it)) },
    )
    if (draft.type == FeeType.Other || draft.type == FeeType.Discount) {
        EvenUpTextField(
            value = draft.label,
            onValueChange = { onEvent(ReceiptReviewUiEvent.FeeLabelChanged(it)) },
            label = if (draft.type == FeeType.Discount) "Discount name" else "Fee name",
            isError = fieldErrors.containsKey("fee_label_$fieldId"),
            supportingText = fieldErrors["fee_label_$fieldId"],
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        verticalAlignment = Alignment.Top,
    ) {
        EvenUpMoneyField(
            value = draft.amount,
            onValueChange = { onEvent(ReceiptReviewUiEvent.FeeAmountChanged(it)) },
            label = if (draft.type == FeeType.Discount) "Discount amount" else "Fee amount",
            modifier = Modifier.weight(1f),
            isError = fieldErrors.containsKey("fee_amount_$fieldId"),
            supportingText = fieldErrors["fee_amount_$fieldId"],
        )
    }
}

@Composable
private fun FeeTypeSelector(
    selectedType: FeeType,
    includeDiscount: Boolean,
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
            val types = buildList {
                add(FeeType.Tax)
                add(FeeType.Tip)
                add(FeeType.ServiceFee)
                add(FeeType.Other)
                if (includeDiscount) add(FeeType.Discount)
            }
            types.forEach { type ->
                val selected = type == selectedType
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTypeSelected(type) }
                        .semantics {
                            role = Role.Button
                            contentDescription = feeDisplayLabel(type)
                        },
                    shape = EvenUpTheme.shapes.chip,
                    color = if (selected) EvenUpTheme.colors.primary else EvenUpTheme.colors.surfaceElevated,
                    contentColor = if (selected) EvenUpTheme.colors.onPrimary else EvenUpTheme.colors.textPrimary,
                    border = BorderStroke(1.dp, if (selected) EvenUpTheme.colors.primary else EvenUpTheme.colors.border),
                ) {
                    Text(
                        text = feeDisplayLabel(type),
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

private fun currencySymbol(currencyCode: String): String = when (currencyCode.uppercase()) {
    "EUR" -> "€"
    "USD" -> "$"
    "GBP" -> "£"
    else -> currencyCode.uppercase()
}

private fun formatAverageAmount(
    totalMinor: Long,
    quantity: Int,
): String {
    return formatMoneyInput(BigDecimal(totalMinor).divide(BigDecimal(quantity.coerceAtLeast(1)), 2, RoundingMode.HALF_UP))
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
        Text(
            text = detail,
            style = EvenUpTheme.typography.caption,
            color = EvenUpTheme.colors.textSecondary,
            textAlign = TextAlign.End,
        )
    }
}
