package com.dps.evenup.core.designsystem.api

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalEvenUpColors = staticCompositionLocalOf { EvenUpLightColorScheme }
private val LocalEvenUpTypography = staticCompositionLocalOf { EvenUpDefaultTypography }
private val LocalEvenUpShapes = staticCompositionLocalOf { EvenUpShapes() }
private val LocalEvenUpSpacing = staticCompositionLocalOf { EvenUpSpacing() }

@Composable
fun EvenUpTheme(
    colors: EvenUpColorScheme = EvenUpLightColorScheme,
    typography: EvenUpTypography = EvenUpDefaultTypography,
    shapes: EvenUpShapes = EvenUpShapes(),
    spacing: EvenUpSpacing = EvenUpSpacing(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalEvenUpColors provides colors,
        LocalEvenUpTypography provides typography,
        LocalEvenUpShapes provides shapes,
        LocalEvenUpSpacing provides spacing,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = typography.toMaterialTypography(),
            content = content,
        )
    }
}

object EvenUpTheme {
    val colors: EvenUpColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalEvenUpColors.current

    val typography: EvenUpTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalEvenUpTypography.current

    val shapes: EvenUpShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalEvenUpShapes.current

    val spacing: EvenUpSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalEvenUpSpacing.current
}

private fun EvenUpColorScheme.toMaterialColorScheme(): ColorScheme {
    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        secondary = textSecondary,
        onSecondary = onPrimary,
        background = background,
        onBackground = textPrimary,
        surface = surfaceElevated,
        onSurface = textPrimary,
        surfaceVariant = surface,
        onSurfaceVariant = textSecondary,
        outline = border,
        error = error,
        errorContainer = errorContainer,
        onError = onPrimary,
        onErrorContainer = error,
    )
}
