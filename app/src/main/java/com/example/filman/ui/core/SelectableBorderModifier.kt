package com.example.filman.ui.core

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.MaterialTheme
import com.example.filman.ui.theme.spacing

@Composable
internal fun Modifier.selectableBorder(
    isSelectedProvider: (() -> Boolean)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    selectedBorderWidth: Dp = MaterialTheme.spacing.extraSmall,
    unselectedBorderWidth: Dp = 1.dp,
    selectedColor: Color = MaterialTheme.colorScheme.onSurface,
    unselectedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    val isSelected = isSelectedProvider?.invoke() ?: isFocused

    val borderWidth by animateDpAsState(
        if (isSelected) {
            selectedBorderWidth
        } else {
            unselectedBorderWidth
        },
    )

    return this
        .then(
            if (isSelectedProvider == null) {
                Modifier.onFocusChanged { isFocused = it.isFocused }
            } else {
                Modifier
            },
        )
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            onDrawWithContent {
                drawContent()
                drawOutline(
                    outline = outline,
                    color = if (isSelected) selectedColor else unselectedColor,
                    style = Stroke(width = borderWidth.toPx()),
                )
            }
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
