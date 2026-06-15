package com.dps.evenup.core.designsystem.api

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class EvenUpColorScheme(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val primary: Color,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val divider: Color,
    val success: Color,
    val successContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val avatarPalette: List<Color>,
)

val EvenUpLightColorScheme = EvenUpColorScheme(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF7F7F5),
    surfaceElevated = Color(0xFFFFFFFF),
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF666666),
    textTertiary = Color(0xFF999999),
    border = Color(0xFFE6E6E2),
    divider = Color(0xFFEEEEEA),
    success = Color(0xFF1F7A4D),
    successContainer = Color(0xFFEAF7EF),
    error = Color(0xFFB42318),
    errorContainer = Color(0xFFFDECEC),
    warning = Color(0xFFA15C07),
    warningContainer = Color(0xFFFFF4E5),
    avatarPalette = listOf(
        Color(0xFF111111),
        Color(0xFF345C4F),
        Color(0xFF4F5D75),
        Color(0xFF7A5C32),
        Color(0xFF5A5A5A),
        Color(0xFF2F5E68),
    ),
)
