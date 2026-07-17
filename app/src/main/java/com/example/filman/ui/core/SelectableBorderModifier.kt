package com.example.filman.ui.core

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.MaterialTheme
import com.example.filman.ui.theme.spacing

@Composable
internal fun Modifier.selectableBorder(
    isSelectedProvider: () -> Boolean,
    selectedBorderWidth: Dp = MaterialTheme.spacing.extraSmall,
    unselectedBorderWidth: Dp = 1.dp,
    selectedColor: Color = MaterialTheme.colorScheme.onSurface,
    unselectedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
): Modifier {
    val borderWidth by animateDpAsState(
        if (isSelectedProvider()) {
            selectedBorderWidth
        } else {
            unselectedBorderWidth
        },
    )

    return this.drawWithContent {
        drawContent()
        drawRoundRect(
            color = if (isSelectedProvider()) selectedColor else unselectedColor,
            cornerRadius = CornerRadius(12.dp.toPx()),
            style = Stroke(width = borderWidth.toPx()),
        )
    }
}

@Composable
internal fun focusedBorder(): Border {
    val color = MaterialTheme.colorScheme.onSurface
    val width = MaterialTheme.spacing.extraSmall
    val shape = MaterialTheme.shapes.medium

    return remember(color, width, shape) {
        Border(
            border = BorderStroke(width = width, color = color),
            shape = shape,
        )
    }
}

@Composable
internal fun border(): Border {
    val color = MaterialTheme.colorScheme.surfaceVariant
    val shape = MaterialTheme.shapes.medium

    return remember(color, shape) {
        Border(
            border = BorderStroke(width = 1.dp, color = color),
            shape = shape,
        )
    }
}
