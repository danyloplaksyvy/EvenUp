package com.dps.evenup.feature.expenseflow.impl.newexpense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpTopBar

@Composable
fun NewExpenseScreen(
    uiState: NewExpenseUiState,
    onEvent: (NewExpenseUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    closeEnabled: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = EvenUpTheme.colors.background,
        contentColor = EvenUpTheme.colors.textPrimary,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EvenUpTopBar(
                title = "EvenUp",
                onNavigationClick = if (closeEnabled) {
                    { onEvent(NewExpenseUiEvent.CloseClick) }
                } else {
                    null
                },
                navigationContentDescription = "Close new expense",
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = EvenUpTheme.spacing.space20)
                    .padding(top = EvenUpTheme.spacing.space16, bottom = EvenUpTheme.spacing.space24)
                    .widthIn(max = 520.dp)
                    .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space24),
            ) {
                NewExpenseHero(uiState = uiState)
                Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12)) {
                    NewExpenseActionCard(
                        title = "Scan receipt",
                        description = "Snap a photo to auto-fill details",
                        icon = ScanReceiptIcon,
                        onClick = { onEvent(NewExpenseUiEvent.ScanReceiptClick) },
                    )
                    NewExpenseActionCard(
                        title = "Enter manually",
                        description = "Fill in the details yourself",
                        icon = EnterManuallyIcon,
                        onClick = { onEvent(NewExpenseUiEvent.EnterManuallyClick) },
                    )
                }
                Spacer(modifier = Modifier.height(EvenUpTheme.spacing.space40))
                Text(
                    text = "Transparent item-level splitting.",
                    modifier = Modifier.fillMaxWidth(),
                    style = EvenUpTheme.typography.caption,
                    color = EvenUpTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
