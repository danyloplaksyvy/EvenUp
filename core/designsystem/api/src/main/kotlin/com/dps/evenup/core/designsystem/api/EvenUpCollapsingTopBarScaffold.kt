package com.dps.evenup.core.designsystem.api

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Use for screens with a Material top bar that should collapse while scrolling and keep the back
 * action reachable after the top bar collapses. Do not use for custom sticky business headers,
 * such as assignment headers, unless the screen only needs this generic top-bar behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvenUpCollapsingTopBarScaffold(
    title: String,
    onNavigationClick: () -> Unit,
    modifier: Modifier = Modifier,
    navigationContentDescription: String = "Navigate back",
    showStickyNavigationButton: Boolean = true,
    stickyNavigationThreshold: Float = 0.85f,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val coercedStickyNavigationThreshold = when {
        stickyNavigationThreshold.isNaN() -> 0.85f
        else -> stickyNavigationThreshold.coerceIn(0f, 1f)
    }
    val showStickyBackButton by remember(
        showStickyNavigationButton,
        scrollBehavior,
        coercedStickyNavigationThreshold,
    ) {
        derivedStateOf {
            showStickyNavigationButton &&
                scrollBehavior.state.collapsedFraction > coercedStickyNavigationThreshold
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = EvenUpTheme.colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
                        style = EvenUpTheme.typography.sectionTitle,
                        color = EvenUpTheme.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = navigationContentDescription,
                            tint = EvenUpTheme.colors.textPrimary,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EvenUpTheme.colors.background,
                    scrolledContainerColor = EvenUpTheme.colors.background,
                    navigationIconContentColor = EvenUpTheme.colors.textPrimary,
                    titleContentColor = EvenUpTheme.colors.textPrimary,
                ),
            )
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(innerPadding)
            AnimatedVisibility(
                visible = showStickyBackButton,
                modifier = Modifier.align(Alignment.TopStart),
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 180,
                        easing = FastOutSlowInEasing,
                    ),
                ) + scaleIn(
                    initialScale = 0.88f,
                    animationSpec = tween(
                        durationMillis = 180,
                        easing = FastOutSlowInEasing,
                    ),
                ) + slideInVertically(
                    initialOffsetY = { -it / 3 },
                    animationSpec = tween(
                        durationMillis = 180,
                        easing = FastOutSlowInEasing,
                    ),
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = 120,
                        easing = FastOutSlowInEasing,
                    ),
                ) + scaleOut(
                    targetScale = 0.88f,
                    animationSpec = tween(
                        durationMillis = 120,
                        easing = FastOutSlowInEasing,
                    ),
                ) + slideOutVertically(
                    targetOffsetY = { -it / 3 },
                    animationSpec = tween(
                        durationMillis = 120,
                        easing = FastOutSlowInEasing,
                    ),
                ),
            ) {
                EvenUpStickyBackButton(
                    onClick = onNavigationClick,
                    contentDescription = navigationContentDescription,
                )
            }
        }
    }
}
