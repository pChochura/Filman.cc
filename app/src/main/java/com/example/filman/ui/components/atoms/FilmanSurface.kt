package com.example.filman.ui.components.atoms

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.example.filman.ui.core.suppressKeyRepeat

enum class SurfaceStyle {
    Primary,
    DarkTransparent,
    Transparent,
    SurfaceVariant
}

enum class SurfaceShape {
    Circle,
    Rounded
}

@Composable
fun FilmanSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SurfaceStyle = SurfaceStyle.Primary,
    surfaceShape: SurfaceShape = SurfaceShape.Circle,
    content: @Composable () -> Unit,
) {
    val shape: Shape = when (surfaceShape) {
        SurfaceShape.Circle -> CircleShape
        SurfaceShape.Rounded -> RoundedCornerShape(8.dp)
    }

    val colors = when (style) {
        SurfaceStyle.Primary -> ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            focusedContentColor = Color.White,
        )

        SurfaceStyle.DarkTransparent -> ClickableSurfaceDefaults.colors(
            containerColor = Color.DarkGray.copy(alpha = 0.5f),
            focusedContainerColor = Color.DarkGray.copy(alpha = 0.8f),
        )

        SurfaceStyle.Transparent -> ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.2f),
        )

        SurfaceStyle.SurfaceVariant -> ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        )
    }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        modifier = modifier.suppressKeyRepeat(),
        colors = colors,
        content = { content() },
    )
}
