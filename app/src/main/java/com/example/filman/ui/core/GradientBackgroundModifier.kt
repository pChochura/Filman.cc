package com.example.filman.ui.core

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val gradientColors = listOf(Color.Transparent, Color.Black)

internal fun Modifier.gradientBackground() = drawWithCache {
    val brush = Brush.verticalGradient(
        colors = gradientColors,
        startY = 0f,
        endY = size.height,
    )
    onDrawWithContent {
        drawContent()
        drawRect(brush = brush)
    }
}
