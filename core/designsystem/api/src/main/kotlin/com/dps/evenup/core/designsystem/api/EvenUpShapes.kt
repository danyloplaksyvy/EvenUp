package com.dps.evenup.core.designsystem.api

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
data class EvenUpShapes(
    val screenCard: Shape = RoundedCornerShape(24.dp),
    val input: Shape = RoundedCornerShape(16.dp),
    val button: Shape = RoundedCornerShape(999.dp),
    val bottomSheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val avatar: Shape = CircleShape,
    val chip: Shape = RoundedCornerShape(999.dp),
)
