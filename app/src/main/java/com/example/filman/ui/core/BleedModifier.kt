package com.example.filman.ui.core

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

internal fun Modifier.horizontalBleed(padding: Dp) = layout { measurable, constraints ->
    val paddingPx = padding.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            maxWidth = constraints.maxWidth + paddingPx * 2
        )
    )
    // Report the original constrained width so the parent doesn't clip/coerce us
    layout(if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width, placeable.height) {
        placeable.place(-paddingPx, 0)
    }
}
