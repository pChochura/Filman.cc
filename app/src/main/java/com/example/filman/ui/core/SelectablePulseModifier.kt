package com.example.filman.ui.core

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

@Composable
internal fun Modifier.selectablePulse(
    interactionSource: InteractionSource? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    borderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    borderWidth: Dp? = 2.dp,
    focusedScale: Float = 1f,
    pressedScale: Float = 0.9f,
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val isFocusedFinal = interactionSource?.collectIsFocusedAsState()?.value ?: isFocused
    val isPressedFinal = interactionSource?.collectIsPressedAsState()?.value ?: isPressed
    val multiplierAnimatable = remember { Animatable(1f) }

    val scale by animateFloatAsState(
        targetValue = when {
            isPressedFinal -> pressedScale
            isFocusedFinal -> focusedScale
            else -> 1f
        },
        label = "pulse_scale",
    )

    LaunchedEffect(isFocusedFinal) {
        if (isFocusedFinal) {
            multiplierAnimatable.animateTo(
                targetValue = 2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(PULSE_DURATION),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
        } else {
            multiplierAnimatable.animateTo(1f)
            multiplierAnimatable.stop()
        }
    }

    return this
        .then(
            if (interactionSource == null) {
                Modifier.onFocusChanged { isFocused = it.isFocused }
            } else {
                Modifier
            },
        )
        .onPreviewKeyEvent { event ->
            val isActionKey = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.NumPadEnter

            if (isActionKey) {
                when (event.type) {
                    KeyEventType.KeyDown -> isPressed = true
                    KeyEventType.KeyUp -> isPressed = false
                }
            }

            return@onPreviewKeyEvent false
        }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            onDrawWithContent {
                scale(scale, scale) {
                    this@onDrawWithContent.drawContent()
                    if (isFocusedFinal && borderWidth != null) {
                        drawOutline(
                            outline = outline,
                            color = borderColor,
                            style = Stroke(width = multiplierAnimatable.value * borderWidth.toPx()),
                        )
                    }
                }
            }
        }
}

private const val PULSE_DURATION = 800
