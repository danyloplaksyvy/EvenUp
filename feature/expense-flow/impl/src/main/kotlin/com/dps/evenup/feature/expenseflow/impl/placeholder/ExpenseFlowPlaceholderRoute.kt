package com.dps.evenup.feature.expenseflow.impl.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpTopBar

@Composable
internal fun ExpenseFlowPlaceholderRoute(
    title: String,
    message: String,
    onBack: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = EvenUpTheme.colors.background,
        contentColor = EvenUpTheme.colors.textPrimary,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EvenUpTopBar(
                title = title,
                onNavigationClick = { onBack() },
                navigationContentDescription = "Navigate back",
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = EvenUpTheme.spacing.space20)
                    .padding(top = EvenUpTheme.spacing.space24),
                verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
            ) {
                Text(
                    text = title,
                    style = EvenUpTheme.typography.screenTitle,
                    color = EvenUpTheme.colors.textPrimary,
                )
                Text(
                    text = message,
                    style = EvenUpTheme.typography.body,
                    color = EvenUpTheme.colors.textSecondary,
                )
            }
        }
    }
}
