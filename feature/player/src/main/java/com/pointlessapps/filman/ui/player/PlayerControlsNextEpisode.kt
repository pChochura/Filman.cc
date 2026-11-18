package com.pointlessapps.filman.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.ui.components.FilmanButton
import com.pointlessapps.filman.ui.core.getComposeAnimationScale
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonUIState

@Composable
internal fun PlayerControlsNextEpisodeBox(
    areControlsVisible: Boolean,
    playButtonFocusRequester: FocusRequester,
    onNextEpisodeRequested: () -> Unit,
    uiState: NextEpisodeButtonUIState,
    onDismissRequested: () -> Unit,
    onCancelTimerRequested: () -> Unit,
) {
    BackHandler(uiState.isVisible) {
        onDismissRequested()
        playButtonFocusRequester.requestFocus()
    }

    val progress = remember { Animatable(0f) }
    var timerRunning by remember { mutableStateOf(false) }
    val animationScale = getComposeAnimationScale()

    LaunchedEffect(uiState.isVisible, uiState.isTimerRunning, uiState.timerDurationMs) {
        if (uiState.isVisible && uiState.isTimerRunning && uiState.timerDurationMs > 0) {
            timerRunning = true
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = (uiState.timerDurationMs / animationScale).toInt(),
                    easing = LinearEasing,
                ),
            )
            onNextEpisodeRequested()
        } else {
            timerRunning = false
            progress.snapTo(0f)
        }
    }

    PlayerControlsNextEpisodeUI(
        isVisible = uiState.isVisible,
        isSecondaryPhase = uiState.isSecondaryPhase,
        areControlsVisible = areControlsVisible,
        progress = progress,
        timerRunning = timerRunning,
        onNextEpisodeRequested = onNextEpisodeRequested,
        onStopTimer = onCancelTimerRequested,
    )
}

@Composable
internal fun PlayerControlsNextEpisodeUI(
    isVisible: Boolean,
    isSecondaryPhase: Boolean,
    areControlsVisible: Boolean,
    progress: Animatable<Float, *>,
    timerRunning: Boolean,
    onNextEpisodeRequested: () -> Unit,
    onStopTimer: () -> Unit,
) {
    val nextEpisodeButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible, areControlsVisible) {
        if (isVisible && !areControlsVisible) {
            nextEpisodeButtonFocusRequester.requestFocus()
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val backgroundColor = MaterialTheme.colorScheme.surfaceVariant

        FilmanButton(
            text = stringResource(R.string.player_next_episode),
            iconRes = R.drawable.ic_play,
            onClick = onNextEpisodeRequested,
            modifier =
                Modifier
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && timerRunning) {
                            onStopTimer()
                            if (it.key != Key.DirectionCenter &&
                                it.key != Key.Enter &&
                                it.key != Key.NumPadEnter
                            ) {
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    }
                    .graphicsLayer {
                        clip = false
                        alpha = if (isSecondaryPhase || areControlsVisible) 1f else 0.5f
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .then(
                        if (!areControlsVisible) {
                            Modifier.drawWithCache {
                                val outline = CircleShape.createOutline(size, layoutDirection, this)
                                val progressWidth = size.width * progress.value
                                onDrawWithContent {
                                    drawOutline(
                                        outline = outline,
                                        color = backgroundColor.copy(alpha = 0.5f),
                                    )
                                    clipRect(right = progressWidth) {
                                        drawOutline(
                                            outline = outline,
                                            color = backgroundColor,
                                        )
                                    }
                                    drawContent()
                                }
                            }
                        } else {
                            Modifier
                        },
                    )
                    .focusRequester(nextEpisodeButtonFocusRequester),
            containerColor =
                if (areControlsVisible) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
            focusedContainerColor =
                if (areControlsVisible) MaterialTheme.colorScheme.onSurface else Color.Transparent,
            contentColor =
                if (areControlsVisible) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            focusedContentColor =
                if (areControlsVisible) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}
