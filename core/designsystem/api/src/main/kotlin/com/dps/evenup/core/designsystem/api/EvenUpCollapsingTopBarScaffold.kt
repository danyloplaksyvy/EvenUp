package com.dps.evenup.core.designsystem.api

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Use for expense-flow screens with a pinned, layout-aware navigation bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvenUpPinnedTopBarScaffold(
    title: String,
    onNavigationClick: () -> Unit,
    modifier: Modifier = Modifier,
    navigationContentDescription: String = "Navigate back",
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val isTopBarScrolled by remember(scrollBehavior) {
        derivedStateOf { scrollBehavior.state.overlappedFraction > 0.01f }
    }
    val dividerColor by animateColorAsState(
        targetValue = if (isTopBarScrolled) {
            EvenUpTheme.colors.divider
        } else {
            EvenUpTheme.colors.divider.copy(alpha = 0f)
        },
        label = "PinnedTopBarDividerColor",
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = EvenUpTheme.colors.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = EvenUpTheme.typography.sectionTitle,
                            color = EvenUpTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        EvenUpIconButton(
                            contentDescription = navigationContentDescription,
                            onClick = onNavigationClick,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = EvenUpTheme.colors.textPrimary,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = EvenUpTheme.colors.background.copy(alpha = 0.92f),
                        scrolledContainerColor = EvenUpTheme.colors.background,
                        navigationIconContentColor = EvenUpTheme.colors.textPrimary,
                        titleContentColor = EvenUpTheme.colors.textPrimary,
                    ),
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = dividerColor,
                )
            }
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        content(innerPadding)
    }
}