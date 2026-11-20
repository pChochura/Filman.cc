package com.pointlessapps.filman.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.ui.components.TiwiSeekBar
import com.pointlessapps.filman.ui.core.parseDuration
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun PlayerControlsQuickSeekOverlay(
    quickSeekOffset: Long,
    quickSeekDirection: Int,
    quickSeekClicks: Int,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        modifier =
            modifier.background(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                shape = CircleShape,
            ),
        targetState = quickSeekOffset,
        transitionSpec = {
            if (quickSeekDirection >= 0) {
                slideInHorizontally { it / 4 } + fadeIn() togetherWith
                    slideOutHorizontally { -it / 4 } + fadeOut()
            } else {
                slideInHorizontally { -it / 4 } + fadeIn() togetherWith
                    slideOutHorizontally { it / 4 } + fadeOut()
            }
        },
        contentAlignment = Alignment.Center,
    ) { seekOffset ->
        if (seekOffset != 0L || quickSeekClicks != 0) {
            Text(
                text = "${if (seekOffset > 0) "+" else "-"}${seekOffset.parseDuration()}",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier =
                    Modifier.padding(
                        horizontal = MaterialTheme.spacing.extraLarge,
                        vertical = MaterialTheme.spacing.medium,
                    ),
            )
        }
    }
}

@Composable
internal fun PlayerControlsQuickSeekHandler(
    quickSeekOffset: Long,
    durationProvider: () -> Long,
    currentPositionProvider: () -> Long,
    onSeekCommited: (Long) -> Unit,
    onClearQuickSeek: () -> Unit,
) {
    val currentSeekCommited by rememberUpdatedState(onSeekCommited)

    LaunchedEffect(quickSeekOffset) {
        if (quickSeekOffset != 0L) {
            delay(1.seconds)
            val currentPos = currentPositionProvider()
            val newPos =
                (currentPos + quickSeekOffset).coerceIn(
                    minimumValue = 0L,
                    maximumValue = durationProvider().coerceAtLeast(0),
                )
            currentSeekCommited(newPos)
            onClearQuickSeek()
        }
    }

    BackHandler(quickSeekOffset != 0L) {
        onClearQuickSeek()
    }
}

@Composable
internal fun PlayerControlsProgressBar(
    currentPositionProvider: () -> Long,
    durationProvider: () -> Long,
    isBufferingProvider: () -> Boolean,
    onSeekCommited: (Long) -> Unit,
    onSeekDiscarded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubPosition by remember { mutableStateOf<Long?>(null) }
    var seekTargetPosition by remember { mutableStateOf<Long?>(null) }
    var hasStartedBuffering by remember { mutableStateOf(false) }

    val isBuffering = isBufferingProvider()
    val currentPosition = currentPositionProvider()

    LaunchedEffect(isBuffering, currentPosition) {
        seekTargetPosition?.let { target ->
            if (isBuffering) {
                hasStartedBuffering = true
            } else {
                val reachedTarget = abs(currentPosition - target) < 2000L
                if (hasStartedBuffering || reachedTarget) {
                    seekTargetPosition = null
                    hasStartedBuffering = false
                }
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                space = MaterialTheme.spacing.small,
                alignment = Alignment.CenterVertically,
            ),
    ) {
        TiwiSeekBar(
            progressProvider = {
                val duration = durationProvider()
                if (duration > 0) {
                    (scrubPosition ?: currentPositionProvider()) / duration.toFloat()
                } else {
                    0f
                }
            },
            seekTargetProvider = {
                val duration = durationProvider()
                val target = seekTargetPosition
                if (target != null && duration > 0) {
                    target / duration.toFloat()
                } else {
                    null
                }
            },
            scrubOriginProvider = {
                val duration = durationProvider()
                if (scrubPosition != null && duration > 0) {
                    currentPositionProvider() / duration.toFloat()
                } else {
                    null
                }
            },
            isBufferingProvider = isBufferingProvider,
            durationProvider = durationProvider,
            onScrub = { offsetMs ->
                val duration = durationProvider()
                if (duration > 0) {
                    val current = scrubPosition ?: currentPositionProvider()
                    scrubPosition = (current + offsetMs).coerceIn(0L, duration)
                }
            },
            onSeekCommited = {
                scrubPosition?.let { pos ->
                    seekTargetPosition = pos
                    hasStartedBuffering = false
                    onSeekCommited(pos)
                    scrubPosition = null
                }
            },
            onFocusLost = {
                scrubPosition = null
            },
            onSeekDiscarded = {
                scrubPosition = null
                onSeekDiscarded()
            },
            modifier = Modifier.fillMaxWidth(),
        )

        PlayerControlsPositionRow(
            currentPositionProvider = { scrubPosition ?: currentPositionProvider() },
            durationProvider = durationProvider,
        )
    }
}

@Composable
internal fun PlayerControlsPositionRow(
    currentPositionProvider: () -> Long,
    durationProvider: () -> Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PlayerControlsPositionText(positionProvider = currentPositionProvider)
        PlayerControlsPositionText(positionProvider = durationProvider)
    }
}

@Composable
internal fun PlayerControlsPositionText(
    positionProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = stringResource(
            R.string.player_duration,
            positionProvider().parseDuration(),
        ),
        textAlign = TextAlign.Start,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
