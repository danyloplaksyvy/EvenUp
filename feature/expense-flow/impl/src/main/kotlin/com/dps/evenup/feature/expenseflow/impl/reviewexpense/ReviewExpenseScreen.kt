package com.dps.evenup.feature.expenseflow.impl.reviewexpense

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpBottomActionBar
import com.dps.evenup.core.designsystem.api.EvenUpBottomSheet
import com.dps.evenup.core.designsystem.api.EvenUpCard
import com.dps.evenup.core.designsystem.api.EvenUpCollapsingTopBarScaffold
import com.dps.evenup.core.designsystem.api.EvenUpErrorState
import com.dps.evenup.core.designsystem.api.EvenUpLoadingState
import com.dps.evenup.core.designsystem.api.EvenUpParticipantAvatar
import com.dps.evenup.core.designsystem.api.EvenUpPrimaryButton
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpValidationMessage

@Composable
fun ReviewExpenseScreen(
    uiState: ReviewExpenseUiState,
    onEvent: (ReviewExpenseUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    EvenUpCollapsingTopBarScaffold(
        title = "Review expense",
        onNavigationClick = { onEvent(ReviewExpenseUiEvent.BackClick) },
        modifier = modifier.fillMaxSize(),
        showStickyNavigationButton = false,
        bottomBar = {
            if (!uiState.isLoading && !uiState.missingDraft) {
                EvenUpBottomActionBar(
                    primaryText = if (uiState.isSaving) "Saving..." else "Save expense",
                    onPrimaryClick = { onEvent(ReviewExpenseUiEvent.SaveClick) },
                    primaryEnabled = uiState.canSave && !uiState.isSaving,
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> EvenUpLoadingState(
                message = "Calculating settlement...",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            uiState.missingDraft -> EvenUpErrorState(
                title = "Review unavailable",
                message = uiState.submitError ?: "Complete the previous steps before reviewing.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                retryText = "Go back",
                onRetryClick = { onEvent(ReviewExpenseUiEvent.BackClick) },
            )
            else -> ReviewExpenseContent(
                uiState = uiState,
                onEvent = onEvent,
                contentPadding = innerPadding,
            )
        }
    }
    CalculationDetailsSheet(uiState = uiState, onEvent = onEvent)
}

@Composable
private fun ReviewExpenseContent(
    uiState: ReviewExpenseUiState,
    onEvent: (ReviewExpenseUiEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = EvenUpTheme.spacing.space20)
            .padding(top = EvenUpTheme.spacing.space16, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space20),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ReviewHeader(uiState = uiState)
        SettlementSummary(uiState = uiState)
        PayerSummary(payerSummary = uiState.payerSummary)
        CalculationDetailsLauncher(uiState = uiState, onEvent = onEvent)
        uiState.validationError?.let { error ->
            EvenUpValidationMessage(message = error)
        }
        uiState.submitError?.let { error ->
            SaveErrorCard(
                message = error,
                onRetry = { onEvent(ReviewExpenseUiEvent.SaveRetryClick) },
                retryEnabled = uiState.canSave && !uiState.isSaving,
            )
        }
    }
}

@Composable
private fun SaveErrorCard(
    message: String,
    onRetry: () -> Unit,
    retryEnabled: Boolean,
) {
    EvenUpCard {
        Text(
            text = "Save failed",
            style = EvenUpTheme.typography.sectionTitle,
            color = EvenUpTheme.colors.textPrimary,
        )
        EvenUpValidationMessage(message = message)
        EvenUpPrimaryButton(
            text = "Try saving again",
            onClick = onRetry,
            enabled = retryEnabled,
        )
    }
}

@Composable
private fun ReviewHeader(uiState: ReviewExpenseUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
    ) {
        Text(
            text = uiState.merchantName,
            style = EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = uiState.totalLabel,
            modifier = Modifier.semantics {
                contentDescription = uiState.totalContentDescription
            },
            style = EvenUpTheme.typography.displayLargeTotal,
            color = EvenUpTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = EvenUpTheme.shapes.input,
            color = EvenUpTheme.colors.surface,
            contentColor = EvenUpTheme.colors.textPrimary,
            border = BorderStroke(1.dp, EvenUpTheme.colors.border),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = uiState.paidByContentDescription
                    }
                    .padding(
                        horizontal = EvenUpTheme.spacing.space16,
                        vertical = EvenUpTheme.spacing.space12,
                    ),
                horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EvenUpParticipantAvatar(
                    name = uiState.payerName,
                    colorIndex = uiState.payerColorIndex,
                )
                Text(
                    text = "${uiState.paidByLabel} · ${uiState.participantCountLabel}",
                    modifier = Modifier.weight(1f),
                    style = EvenUpTheme.typography.bodyStrong,
                    color = EvenUpTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CalculationDetailsLauncher(
    uiState: ReviewExpenseUiState,
    onEvent: (ReviewExpenseUiEvent) -> Unit,
) {
    EvenUpCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEvent(ReviewExpenseUiEvent.CalculationDetailsClick) }
                .semantics {
                    role = Role.Button
                    contentDescription = uiState.calculationDetailsContentDescription
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Calculate,
                    contentDescription = null,
                    tint = EvenUpTheme.colors.textPrimary,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
                ) {
                    Text(
                        text = "Calculation details",
                        style = EvenUpTheme.typography.bodyStrong,
                        color = EvenUpTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    uiState.balanceStatusLabel?.let { statusLabel ->
                        Row(
                            modifier = Modifier.semantics {
                                contentDescription = uiState.balanceStatusContentDescription ?: statusLabel
                            },
                            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = EvenUpTheme.colors.success,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = statusLabel,
                                style = EvenUpTheme.typography.caption,
                                color = EvenUpTheme.colors.success,
                            )
                        }
                    }
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = EvenUpTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun CalculationDetailsSheet(
    uiState: ReviewExpenseUiState,
    onEvent: (ReviewExpenseUiEvent) -> Unit,
) {
    EvenUpBottomSheet(
        visible = uiState.detailsSheetVisible,
        onDismissRequest = { onEvent(ReviewExpenseUiEvent.CalculationDetailsDismissed) },
        title = "Calculation details",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = EvenUpTheme.spacing.space24),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        ) {
            uiState.detailRows.forEach { detail ->
                ParticipantDetail(detail = detail)
            }
        }
    }
}

@Composable
private fun ParticipantDetail(detail: ParticipantCalculationDetailUiState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = detail.contentDescription
            },
        shape = EvenUpTheme.shapes.input,
        color = EvenUpTheme.colors.surface,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, EvenUpTheme.colors.border),
    ) {
        Column(
            modifier = Modifier.padding(EvenUpTheme.spacing.space12),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EvenUpParticipantAvatar(
                    name = detail.participantName,
                    colorIndex = detail.participantColorIndex,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = detail.participantName,
                    modifier = Modifier.weight(1f),
                    style = EvenUpTheme.typography.bodyStrong,
                    color = EvenUpTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DetailLine(label = "Items", value = detail.itemSubtotalLabel)
            DetailLine(label = "Fees", value = detail.feesLabel)
            detail.discountsLabel?.let { discountsLabel ->
                DetailLine(label = "Discounts", value = discountsLabel)
            }
            DetailLine(label = "Share", value = detail.totalShareLabel)
            DetailLine(label = "Paid", value = detail.amountPaidLabel)
            DetailLine(label = "Result", value = detail.resultLabel)
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = EvenUpTheme.typography.bodySmall,
            color = EvenUpTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = EvenUpTheme.typography.bodyStrong,
            color = EvenUpTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettlementSummary(uiState: ReviewExpenseUiState) {
    EvenUpCard {
        Text(
            text = "To settle up",
            style = EvenUpTheme.typography.caption,
            color = EvenUpTheme.colors.textSecondary,
        )
        if (uiState.settlementRows.isEmpty()) {
            Text(
                text = "No one needs to pay.",
                modifier = Modifier.fillMaxWidth(),
                style = EvenUpTheme.typography.body,
                color = EvenUpTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        } else {
            uiState.settlementRows.forEachIndexed { index, row ->
                SettlementRow(row = row)
                if (index < uiState.settlementRows.lastIndex) {
                    HorizontalDivider(color = EvenUpTheme.colors.divider)
                }
            }
        }
    }
}

@Composable
private fun SettlementRow(row: SettlementRowUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EvenUpTheme.spacing.space8)
            .semantics {
                contentDescription = row.contentDescription
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvenUpParticipantAvatar(
                name = row.fromParticipantName,
                colorIndex = row.fromParticipantColorIndex,
                modifier = Modifier.size(36.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.fromParticipantName,
                    style = EvenUpTheme.typography.bodyStrong,
                    color = EvenUpTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.actionLabel,
                    style = EvenUpTheme.typography.caption,
                    color = EvenUpTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = row.amountLabel,
            style = EvenUpTheme.typography.bodyStrong,
            color = EvenUpTheme.colors.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun PayerSummary(payerSummary: PayerSummaryUiState) {
    EvenUpCard(
        modifier = Modifier.semantics {
            contentDescription = payerSummary.contentDescription
        },
    ) {
        Text(
            text = "Payer summary",
            style = EvenUpTheme.typography.caption,
            color = EvenUpTheme.colors.textSecondary,
        )
        payerSummary.rows.forEach { row ->
            PayerSummaryRow(row = row)
        }
    }
}

@Composable
private fun PayerSummaryRow(row: PayerSummaryRowUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = row.contentDescription
            },
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.label,
            modifier = Modifier.weight(1f),
            style = if (row.emphasized) EvenUpTheme.typography.bodyStrong else EvenUpTheme.typography.bodySmall,
            color = if (row.emphasized) EvenUpTheme.colors.textPrimary else EvenUpTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = row.valueLabel,
            style = if (row.emphasized) EvenUpTheme.typography.bodyStrong else EvenUpTheme.typography.bodySmall,
            color = EvenUpTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}
