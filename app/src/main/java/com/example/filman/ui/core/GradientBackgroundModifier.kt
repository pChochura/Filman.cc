package com.example.filman.ui.core

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val gradientColors = listOf(Color.Transparent, Color.Black)

internal fun Modifier.gradientForeground() = drawWithCache {
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

internal fun Modifier.gradientBackground(invert: Boolean = false) = drawWithCache {
    val brush = Brush.verticalGradient(
        colors = gradientColors,
        startY = if (invert) size.height else 0f,
        endY = if (invert) 0f else size.height,
    )
    onDrawBehind {
        drawRect(brush = brush)
    }
}
