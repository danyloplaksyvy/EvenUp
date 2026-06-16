package com.dps.evenup.feature.expenseflow.impl.feesallocation

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
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
import com.dps.evenup.core.designsystem.api.EvenUpMoneyField
import com.dps.evenup.core.designsystem.api.EvenUpParticipantAvatar
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpTopBar
import com.dps.evenup.core.designsystem.api.EvenUpValidationMessage

@Composable
fun FeesAllocationScreen(
    uiState: FeesAllocationUiState,
    onEvent: (FeesAllocationUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        EvenUpTopBar(
            title = "Allocate fees",
            onNavigationClick = { onEvent(FeesAllocationUiEvent.BackClick) },
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
        )
        when {
            uiState.isLoading -> EvenUpLoadingState(
                message = "Loading fees...",
                modifier = Modifier.weight(1f),
            )
            uiState.missingDraft -> EvenUpErrorState(
                title = "Fees unavailable",
                message = uiState.submitError ?: "Complete the previous steps before allocating fees.",
                modifier = Modifier.weight(1f),
                retryText = "Go back",
                onRetryClick = { onEvent(FeesAllocationUiEvent.BackClick) },
            )
            else -> FeesAllocationContent(
                uiState = uiState,
                onEvent = onEvent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FeesAllocationContent(
    uiState: FeesAllocationUiState,
    onEvent: (FeesAllocationUiEvent) -> Unit,
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
            Header(uiState = uiState)
            FeeSummaryCards(uiState = uiState)
            ModeSelector(selectedMode = uiState.mode, onEvent = onEvent)
            Text(
                text = uiState.helperText,
                modifier = Modifier.fillMaxWidth(),
                style = EvenUpTheme.typography.bodySmall,
                color = EvenUpTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            ParticipantPreview(uiState = uiState)
            if (uiState.mode == FeesAllocationModeUiState.Custom) {
                CustomFeeCards(uiState = uiState, onEvent = onEvent)
            }
            uiState.fieldErrors["fees"]?.let { error ->
                EvenUpValidationMessage(message = error)
            }
            uiState.submitError?.let { error ->
                EvenUpValidationMessage(message = error)
            }
        }
        EvenUpBottomActionBar(
            primaryText = if (uiState.isSaving) "Saving..." else "Continue",
            onPrimaryClick = { onEvent(FeesAllocationUiEvent.ContinueClick) },
            primaryEnabled = uiState.canContinue && !uiState.isSaving,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun Header(uiState: FeesAllocationUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        Text(
            text = "Split tax & tip",
            style = EvenUpTheme.typography.screenTitle,
            color = EvenUpTheme.colors.textPrimary,
        )
        Text(
            text = "Review and adjust how extra fees are shared.",
            style = EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textSecondary,
        )
        if (uiState.merchantName.isNotBlank()) {
            Text(
                text = uiState.merchantName.uppercase(),
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun FeeSummaryCards(uiState: FeesAllocationUiState) {
    if (uiState.feeCards.isEmpty()) {
        EvenUpCard {
            Text(
                text = "No taxes, tips, or extra fees on this receipt.",
                style = EvenUpTheme.typography.body,
                color = EvenUpTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        ) {
            uiState.feeCards.take(2).forEach { fee ->
                FeeSummaryCard(
                    label = fee.label,
                    amountLabel = fee.amountLabel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (uiState.feeCards.size > 2) {
            EvenUpCard {
                uiState.feeCards.drop(2).forEach { fee ->
                    SummaryLine(label = fee.label, value = fee.amountLabel)
                }
            }
        }
    }
}

@Composable
private fun FeeSummaryCard(
    label: String,
    amountLabel: String,
    modifier: Modifier = Modifier,
) {
    EvenUpCard(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = EvenUpTheme.typography.caption,
            color = EvenUpTheme.colors.textSecondary,
        )
        Text(
            text = amountLabel,
            style = EvenUpTheme.typography.sectionTitle,
            color = EvenUpTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun ModeSelector(
    selectedMode: FeesAllocationModeUiState,
    onEvent: (FeesAllocationUiEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        ModeButton(
            text = "Equal",
            selected = selectedMode == FeesAllocationModeUiState.Equal,
            onClick = { onEvent(FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Equal)) },
            modifier = Modifier.weight(1f),
        )
        ModeButton(
            text = "Proportional",
            selected = selectedMode == FeesAllocationModeUiState.Proportional,
            onClick = { onEvent(FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Proportional)) },
            modifier = Modifier.weight(1f),
        )
        ModeButton(
            text = "Custom",
            selected = selectedMode == FeesAllocationModeUiState.Custom,
            onClick = { onEvent(FeesAllocationUiEvent.ModeSelected(FeesAllocationModeUiState.Custom)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = EvenUpTheme.shapes.chip,
        color = if (selected) EvenUpTheme.colors.primary else EvenUpTheme.colors.surfaceElevated,
        contentColor = if (selected) EvenUpTheme.colors.onPrimary else EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, if (selected) EvenUpTheme.colors.primary else EvenUpTheme.colors.border),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = EvenUpTheme.spacing.space12),
            style = EvenUpTheme.typography.button,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ParticipantPreview(uiState: FeesAllocationUiState) {
    EvenUpCard {
        SummaryLine(label = "Total fees", value = uiState.totalFeesLabel)
        uiState.participants.forEach { participant ->
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
                        name = participant.name,
                        colorIndex = participant.colorIndex,
                    )
                    Text(
                        text = participant.name,
                        style = EvenUpTheme.typography.body,
                        color = EvenUpTheme.colors.textPrimary,
                    )
                }
                Text(
                    text = participant.allocatedFeesLabel,
                    style = EvenUpTheme.typography.moneyValue,
                    color = EvenUpTheme.colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun CustomFeeCards(
    uiState: FeesAllocationUiState,
    onEvent: (FeesAllocationUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space16)) {
        uiState.feeCards.forEach { fee ->
            EvenUpCard {
                SummaryLine(label = fee.label, value = fee.amountLabel)
                fee.participantRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EvenUpParticipantAvatar(
                            name = row.name,
                            colorIndex = row.colorIndex,
                        )
                        Text(
                            text = row.name,
                            modifier = Modifier.weight(1f),
                            style = EvenUpTheme.typography.body,
                            color = EvenUpTheme.colors.textPrimary,
                        )
                        EvenUpMoneyField(
                            value = row.customAmount,
                            onValueChange = {
                                onEvent(
                                    FeesAllocationUiEvent.CustomAmountChanged(
                                        feeId = fee.id,
                                        participantId = row.participantId,
                                        value = it,
                                    ),
                                )
                            },
                            label = "Amount",
                            currencySymbol = "€",
                            isError = fee.error != null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                fee.error?.let { error ->
                    EvenUpValidationMessage(message = error)
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(
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
            style = EvenUpTheme.typography.moneyValue,
            color = EvenUpTheme.colors.textPrimary,
        )
    }
}
