package com.example.filman.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.ui.core.parseDuration
import com.example.filman.ui.theme.spacing

@Composable
internal fun FilmanSeekBar(
    progressProvider: () -> Float,
    seekTargetProvider: () -> Float?,
    scrubOriginProvider: () -> Float?,
    isBufferingProvider: () -> Boolean,
    durationProvider: () -> Long,
    onScrub: (offsetMs: Long) -> Unit,
    onSeekCommited: () -> Unit,
    onFocusLost: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = PROGRESS_BAR_HEIGHT,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    shape: Shape = CircleShape,
) {
    var isFocused by remember { mutableStateOf(false) }
    var scrubStartTime by remember { mutableLongStateOf(0L) }
    var lastScrubDirection by remember { mutableStateOf<Boolean?>(null) }

    fun handleScrub(forward: Boolean) {
        val now = System.currentTimeMillis()
        if (lastScrubDirection != forward) {
            scrubStartTime = now
            lastScrubDirection = forward
        }

        val holdDuration = now - scrubStartTime
        val stepMs = when {
            holdDuration > 5000L -> 300_000L
            holdDuration > 2500L -> 60_000L
            holdDuration > 1000L -> 30_000L
            else -> 10_000L
        }
        onScrub(if (forward) stepMs else -stepMs)
    }

    BackHandler(isFocused) {
        onFocusLost()
    }

    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .onFocusChanged {
                isFocused = it.isFocused
                if (!isFocused) onFocusLost()
            }
            .focusable()
            .onKeyEvent {
                when (it.key) {
                    Key.DirectionLeft -> {
                        if (it.type == KeyEventType.KeyDown) {
                            handleScrub(false)
                        } else if (it.type == KeyEventType.KeyUp) {
                            lastScrubDirection = null
                        }
                        true
                    }

                    Key.DirectionRight -> {
                        if (it.type == KeyEventType.KeyDown) {
                            handleScrub(true)
                        } else if (it.type == KeyEventType.KeyUp) {
                            lastScrubDirection = null
                        }
                        true
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (it.type == KeyEventType.KeyUp) {
                            onSeekCommited()
                        }
                        true
                    }

                    else -> false
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val maxWidthPx = constraints.maxWidth.toFloat()

        FilmanSeekBarTrack(
            progressProvider = progressProvider,
            seekTargetProvider = seekTargetProvider,
            scrubOriginProvider = scrubOriginProvider,
            isBufferingProvider = isBufferingProvider,
            isFocused = isFocused,
            height = height,
            trackColor = trackColor,
            progressColor = progressColor,
            shape = shape,
        )

        if (isFocused) {
            FilmanSeekBarPopup(
                progressProvider = progressProvider,
                durationProvider = durationProvider,
                maxWidthPx = maxWidthPx,
            )
        }
    }
}

@Composable
private fun FilmanSeekBarTrack(
    progressProvider: () -> Float,
    seekTargetProvider: () -> Float?,
    scrubOriginProvider: () -> Float?,
    isBufferingProvider: () -> Boolean,
    isFocused: Boolean,
    height: Dp,
    trackColor: Color,
    progressColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                color = trackColor,
                shape = shape,
            )
            .drawWithContent {
                drawContent()
                val progress = progressProvider()
                val seekTarget = seekTargetProvider()
                val scrubOrigin = scrubOriginProvider()
                val isBuffering = isBufferingProvider()

                val solidEnd = if (seekTarget != null) minOf(progress, seekTarget) else progress

                drawRoundRect(
                    color = progressColor,
                    cornerRadius = CornerRadius(height.toPx() / 2),
                    size = size.copy(width = size.width * solidEnd),
                )

                if (seekTarget != null) {
                    val start = minOf(progress, seekTarget)
                    val end = maxOf(progress, seekTarget)
                    val sectionWidth = (end - start) * size.width
                    val sectionStart = start * size.width

                    val alpha = if (isBuffering) pulseAlpha else 0.5f

                    drawRoundRect(
                        color = progressColor.copy(alpha = alpha),
                        topLeft = Offset(sectionStart, 0f),
                        size = size.copy(width = sectionWidth),
                        cornerRadius = CornerRadius(height.toPx() / 2, height.toPx() / 2),
                    )
                }

                if (scrubOrigin != null) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.7f),
                        start = Offset(
                            size.width * scrubOrigin,
                            size.height / 2 - height.toPx() * 1.5f,
                        ),
                        end = Offset(
                            size.width * scrubOrigin,
                            size.height / 2 + height.toPx() * 1.5f,
                        ),
                        strokeWidth = 2.dp.toPx(),
                    )
                }

                if (isFocused) {
                    drawCircle(
                        color = progressColor,
                        radius = height.toPx() * 2f,
                        center = Offset(size.width * progress, size.height / 2),
                    )
                }
            },
    )
}

@Composable
private fun FilmanSeekBarPopup(
    progressProvider: () -> Float,
    durationProvider: () -> Long,
    maxWidthPx: Float,
    modifier: Modifier = Modifier,
) {
    val duration = durationProvider()
    val progress = progressProvider()
    val timestamp = (progress * duration).toLong().parseDuration()

    Layout(
        modifier = modifier,
        content = {
            Text(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small,
                    )
                    .padding(
                        vertical = MaterialTheme.spacing.small,
                        horizontal = MaterialTheme.spacing.medium,
                    ),
                text = timestamp,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) { measurables, _ ->
        val placeable = measurables.first().measure(emptyConstraints)
        layout(0, 0) {
            val popupWidth = placeable.width
            val popupHeight = placeable.height
            val currentProgress = progressProvider()

            val translationX = (maxWidthPx * currentProgress - popupWidth / 2f)
                .coerceIn(0f, maxWidthPx - popupWidth)
            val translationY = -(popupHeight + 16.dp.roundToPx())

            placeable.place(x = translationX.toInt(), y = translationY)
        }
    }
}

private val emptyConstraints = Constraints()
private val PROGRESS_BAR_HEIGHT = 4.dp
