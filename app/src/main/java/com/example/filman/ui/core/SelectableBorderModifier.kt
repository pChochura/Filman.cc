package com.example.filman.ui.core

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
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

internal val focusedBorder: Border
    @Composable
    @ReadOnlyComposable
    get() = Border(
        border = BorderStroke(
            width = MaterialTheme.spacing.extraSmall,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        shape = MaterialTheme.shapes.medium,
    )

internal val border: Border
    @Composable
    @ReadOnlyComposable
    get() = Border(
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = MaterialTheme.shapes.medium,
    )
