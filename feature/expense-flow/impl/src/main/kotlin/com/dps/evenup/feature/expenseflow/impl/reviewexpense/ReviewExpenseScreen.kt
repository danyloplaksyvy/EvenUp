package com.dps.evenup.feature.expenseflow.impl.reviewexpense

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpBottomActionBar
import com.dps.evenup.core.designsystem.api.EvenUpCard
import com.dps.evenup.core.designsystem.api.EvenUpErrorState
import com.dps.evenup.core.designsystem.api.EvenUpLoadingState
import com.dps.evenup.core.designsystem.api.EvenUpParticipantAvatar
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpTopBar
import com.dps.evenup.core.designsystem.api.EvenUpValidationMessage

@Composable
fun ReviewExpenseScreen(
    uiState: ReviewExpenseUiState,
    onEvent: (ReviewExpenseUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        EvenUpTopBar(
            title = "Review expense",
            onNavigationClick = { onEvent(ReviewExpenseUiEvent.BackClick) },
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
        )
        when {
            uiState.isLoading -> EvenUpLoadingState(
                message = "Calculating settlement...",
                modifier = Modifier.weight(1f),
            )
            uiState.missingDraft -> EvenUpErrorState(
                title = "Review unavailable",
                message = uiState.submitError ?: "Complete the previous steps before reviewing.",
                modifier = Modifier.weight(1f),
                retryText = "Go back",
                onRetryClick = { onEvent(ReviewExpenseUiEvent.BackClick) },
            )
            else -> ReviewExpenseContent(
                uiState = uiState,
                onEvent = onEvent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReviewExpenseContent(
    uiState: ReviewExpenseUiState,
    onEvent: (ReviewExpenseUiEvent) -> Unit,
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ReviewHeader(uiState = uiState)
            SettlementSummary(uiState = uiState)
            CalculationDetails(uiState = uiState, onEvent = onEvent)
            uiState.validationError?.let { error ->
                EvenUpValidationMessage(message = error)
            }
            uiState.submitError?.let { error ->
                EvenUpValidationMessage(message = error)
            }
        }
        EvenUpBottomActionBar(
            primaryText = if (uiState.isSaving) "Saving..." else "Save expense",
            onPrimaryClick = { onEvent(ReviewExpenseUiEvent.SaveClick) },
            primaryEnabled = uiState.canSave && !uiState.isSaving,
            modifier = Modifier.align(Alignment.BottomCenter),
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
        )
        Text(
            text = uiState.totalLabel,
            style = EvenUpTheme.typography.displayLargeTotal,
            color = EvenUpTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        EvenUpCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Paid by",
                    style = EvenUpTheme.typography.caption,
                    color = EvenUpTheme.colors.textSecondary,
                )
                EvenUpParticipantAvatar(
                    name = uiState.payerName,
                    colorIndex = uiState.payerColorIndex,
                    modifier = Modifier.padding(horizontal = EvenUpTheme.spacing.space8),
                )
                Text(
                    text = uiState.payerName,
                    style = EvenUpTheme.typography.caption,
                    color = EvenUpTheme.colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun CalculationDetails(
    uiState: ReviewExpenseUiState,
    onEvent: (ReviewExpenseUiEvent) -> Unit,
) {
    EvenUpCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEvent(ReviewExpenseUiEvent.CalculationDetailsToggled) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Calculate,
                    contentDescription = null,
                    tint = EvenUpTheme.colors.textSecondary,
                )
                Text(
                    text = "Calculation details",
                    style = EvenUpTheme.typography.bodyStrong,
                    color = EvenUpTheme.colors.textPrimary,
                )
            }
            Icon(
                imageVector = if (uiState.detailsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = EvenUpTheme.colors.textSecondary,
            )
        }
        if (uiState.detailsExpanded) {
            HorizontalDivider(color = EvenUpTheme.colors.divider)
            uiState.detailRows.forEach { detail ->
                ParticipantDetail(detail = detail)
            }
        }
    }
}

@Composable
private fun ParticipantDetail(detail: ParticipantCalculationDetailUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvenUpParticipantAvatar(
                name = detail.participantName,
                colorIndex = detail.participantColorIndex,
            )
            Text(
                text = detail.participantName,
                style = EvenUpTheme.typography.bodyStrong,
                color = EvenUpTheme.colors.textPrimary,
            )
        }
        DetailLine(label = "Items", value = detail.itemSubtotalLabel)
        DetailLine(label = "Fees", value = detail.feesLabel)
        DetailLine(label = "Total share", value = detail.totalShareLabel)
        DetailLine(label = "Amount paid", value = detail.amountPaidLabel)
        DetailLine(label = "Net balance", value = detail.netBalanceLabel)
        HorizontalDivider(color = EvenUpTheme.colors.divider)
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
            style = EvenUpTheme.typography.bodySmall,
            color = EvenUpTheme.colors.textSecondary,
        )
        Text(
            text = value,
            style = EvenUpTheme.typography.bodyStrong,
            color = EvenUpTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun SettlementSummary(uiState: ReviewExpenseUiState) {
    EvenUpCard {
        Text(
            text = "Settlement Summary",
            style = EvenUpTheme.typography.caption,
            color = EvenUpTheme.colors.textSecondary,
        )
        if (uiState.settlementRows.isEmpty()) {
            Text(
                text = "No one owes anything.",
                modifier = Modifier.fillMaxWidth(),
                style = EvenUpTheme.typography.body,
                color = EvenUpTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        } else {
            uiState.settlementRows.forEach { row ->
                SettlementRow(row = row)
            }
        }
        HorizontalDivider(color = EvenUpTheme.colors.divider)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${uiState.payerName}'s share",
                style = EvenUpTheme.typography.bodyStrong,
                color = EvenUpTheme.colors.textSecondary,
            )
            Text(
                text = uiState.payerShareLabel,
                style = EvenUpTheme.typography.moneyValue,
                color = EvenUpTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun SettlementRow(row: SettlementRowUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvenUpParticipantAvatar(
                name = row.fromParticipantName,
                colorIndex = row.fromParticipantColorIndex,
            )
            Column {
                Text(
                    text = row.fromParticipantName,
                    style = EvenUpTheme.typography.bodyStrong,
                    color = EvenUpTheme.colors.textPrimary,
                )
                Text(
                    text = "owes ${row.toParticipantName}",
                    style = EvenUpTheme.typography.caption,
                    color = EvenUpTheme.colors.textSecondary,
                )
            }
        }
        Text(
            text = row.amountLabel,
            style = EvenUpTheme.typography.moneyValue,
            color = EvenUpTheme.colors.textPrimary,
        )
    }
}
