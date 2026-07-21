package com.example.filman.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

@Composable
internal fun FilmanProgressBar(
    progressProvider: () -> Float,
    modifier: Modifier = Modifier,
    height: Dp = PROGRESS_BAR_HEIGHT,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    shape: Shape = CircleShape,
) {
    Box(
        modifier = modifier
            .height(height)
            .background(
                color = trackColor,
                shape = shape,
            )
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = progressColor,
                    cornerRadius = CornerRadius(
                        x = height.toPx() / 2,
                        y = height.toPx() / 2,
                    ),
                    size = size.copy(
                        width = size.width * progressProvider(),
                    ),
                )
            },
    )
}

private val PROGRESS_BAR_HEIGHT = 4.dp
