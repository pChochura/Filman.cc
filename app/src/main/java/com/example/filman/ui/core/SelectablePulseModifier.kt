package com.example.filman.ui.core

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer

@Composable
internal fun Modifier.selectablePulse(): Modifier {
    var isSelected by remember { mutableStateOf(false) }
    val multiplierAnimatable = remember { Animatable(1f) }

    LaunchedEffect(isSelected) {
        if (isSelected) {
            multiplierAnimatable.animateTo(
                targetValue = 1.1f,
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
        .onFocusChanged { isSelected = it.isFocused }
        .graphicsLayer {
            scaleX = multiplierAnimatable.value
            scaleY = multiplierAnimatable.value
        }
}

private const val PULSE_DURATION = 600
