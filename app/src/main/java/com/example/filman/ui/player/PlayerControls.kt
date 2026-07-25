package com.example.filman.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.data.model.DetailedMedia
import com.example.filman.ui.components.FilmanFullscreenLoader
import com.example.filman.ui.components.FilmanIconButton
import com.example.filman.ui.components.FilmanSeekBar
import com.example.filman.ui.core.gradientBackground
import com.example.filman.ui.core.parseDuration
import com.example.filman.ui.core.selectablePulse
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun PlayerControls(
    detailedMedia: DetailedMedia?,
    isPlayingProvider: () -> Boolean,
    isBufferingProvider: () -> Boolean,
    durationProvider: () -> Long,
    currentPositionProvider: () -> Long,
    playButtonFocusRequester: FocusRequester,
    onPlayButtonClicked: () -> Unit,
    onSeekCommited: (Long) -> Unit,
    onNextEpisodeRequested: () -> Unit,
    onNextEpisodeBoxAppeared: () -> Unit,
    onSettingsClicked: () -> Unit,
) {
    val nextEpisodeButtonFocusRequester = remember { FocusRequester() }
    var isNextEpisodeBoxVisible by remember { mutableStateOf(false) }
    var wasNextEpisodeBoxDismissed by remember { mutableStateOf(false) }
    var isNextEpisodeTimerRunning by remember { mutableStateOf(false) }
    var stopNextEpisodeTimer: (() -> Unit)? by remember { mutableStateOf(null) }
    var controlsVisibilityTimeoutFlag by remember { mutableStateOf(false) }
    var areControlsVisible by remember { mutableStateOf(true) }
    val animatedAlpha by animateFloatAsState(if (areControlsVisible) 1f else 0f)

    val toggleUiVisibility = { visible: Boolean ->
        areControlsVisible = visible
        controlsVisibilityTimeoutFlag = !controlsVisibilityTimeoutFlag
    }

    LaunchedEffect(controlsVisibilityTimeoutFlag) {
        snapshotFlow(isPlayingProvider).collectLatest { isPlaying ->
            if (isPlaying) {
                delay(CONTROLS_VISIBILITY_TIMEOUT)
                areControlsVisible = false
                playButtonFocusRequester.requestFocus()
            } else {
                areControlsVisible = true
            }
        }
    }

    PlayerControlsBackHandler(
        areControlsVisible = areControlsVisible,
        isPlayingProvider = isPlayingProvider,
        toggleUiVisibility = toggleUiVisibility,
    )

    BackHandler(isNextEpisodeBoxVisible) {
        isNextEpisodeBoxVisible = false
        wasNextEpisodeBoxDismissed = true
    }

    val currentDurationProvider by rememberUpdatedState(durationProvider)
    val currentPositionFlowProvider by rememberUpdatedState(currentPositionProvider)

    LaunchedEffect(detailedMedia) {
        isNextEpisodeBoxVisible = false
        wasNextEpisodeBoxDismissed = false
        if (detailedMedia?.baseItem?.nextEpisodeUrl == null) return@LaunchedEffect

        snapshotFlow { currentPositionFlowProvider() }.collectLatest {
            val duration = currentDurationProvider()
            if (duration > 0) {
                val timeLeft = duration - it
                if (timeLeft <= NEXT_EPISODE_BOX_TIME_LEFT_MS && !wasNextEpisodeBoxDismissed) {
                    isNextEpisodeBoxVisible = true
                } else if (timeLeft > NEXT_EPISODE_BOX_TIME_LEFT_MS) {
                    isNextEpisodeBoxVisible = false
                    wasNextEpisodeBoxDismissed = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && isNextEpisodeTimerRunning) {
                    isNextEpisodeTimerRunning = false
                    stopNextEpisodeTimer?.invoke()
                    if (it.key != Key.DirectionCenter && it.key != Key.Enter && it.key != Key.NumPadEnter) {
                        return@onPreviewKeyEvent true
                    }
                }

                if (it.key == Key.Back) return@onPreviewKeyEvent false

                if (!isNextEpisodeBoxVisible) {
                    val localAreControlsVisible = areControlsVisible
                    toggleUiVisibility(true)
                    return@onPreviewKeyEvent !localAreControlsVisible
                }

                return@onPreviewKeyEvent false
            },
        contentAlignment = Alignment.Center,
    ) {
        FilmanFullscreenLoader(isVisibleProvider = isBufferingProvider)

        Column(
            modifier = Modifier
                .graphicsLayer { alpha = animatedAlpha }
                .fillMaxSize()
                .gradientBackground()
                .padding(MaterialTheme.spacing.extraLarge)
                .focusGroup()
                .focusProperties {
                    onEnter = { playButtonFocusRequester.requestFocus() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                space = MaterialTheme.spacing.medium,
                alignment = Alignment.Bottom,
            ),
        ) {
            PlayerControlsMediaDetails(
                detailedMedia = detailedMedia,
            )

            Row(
                modifier = Modifier,
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                val settingsButtonFocusRequester = remember { FocusRequester() }

                PlayerControlsPlayPauseButton(
                    isPlayingProvider = isPlayingProvider,
                    onPlayButtonClicked = onPlayButtonClicked,
                    playButtonFocusRequester = playButtonFocusRequester,
                    modifier = Modifier.focusProperties {
                        down = settingsButtonFocusRequester
                    },
                )

                PlayerControlsProgressBar(
                    currentPositionProvider = currentPositionProvider,
                    durationProvider = durationProvider,
                    isBufferingProvider = isBufferingProvider,
                    onSeekCommited = {
                        onSeekCommited(it)
                        playButtonFocusRequester.requestFocus()
                    },
                    onSeekDiscarded = { playButtonFocusRequester.requestFocus() },
                    modifier = Modifier
                        .weight(1f)
                        .focusProperties {
                            down = settingsButtonFocusRequester
                        },
                )

                FilmanIconButton(
                    modifier = Modifier
                        .focusRequester(settingsButtonFocusRequester)
                        .focusProperties {
                            up = playButtonFocusRequester
                            down = playButtonFocusRequester
                            left = playButtonFocusRequester
                            right = playButtonFocusRequester
                        },
                    icon = R.drawable.ic_settings,
                    contentDescription = R.string.player_settings,
                    onClick = onSettingsClicked,
                    iconSize = 32.dp,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        PlayerControlsNextEpisodeBox(
            isVisible = isNextEpisodeBoxVisible,
            onNextEpisodeRequested = onNextEpisodeRequested,
            nextEpisodeButtonFocusRequester = nextEpisodeButtonFocusRequester,
            onBoxAppeared = {
                toggleUiVisibility(false)
                onNextEpisodeBoxAppeared()
            },
            onTimerStateChanged = { isRunning, stopFunc ->
                isNextEpisodeTimerRunning = isRunning
                stopNextEpisodeTimer = stopFunc
            },
        )
    }
}

@Composable
private fun PlayerControlsBackHandler(
    areControlsVisible: Boolean,
    isPlayingProvider: () -> Boolean,
    toggleUiVisibility: (Boolean) -> Unit,
) {
    var isPlaying by remember { mutableStateOf(isPlayingProvider()) }

    LaunchedEffect(isPlayingProvider) {
        snapshotFlow(isPlayingProvider).collectLatest {
            isPlaying = it
        }
    }

    BackHandler(areControlsVisible && isPlaying) {
        toggleUiVisibility(false)
    }
}

@Composable
private fun PlayerControlsMediaDetails(
    detailedMedia: DetailedMedia?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        val seasonNumber = detailedMedia?.baseItem?.seasonNumber
        val episodeNumber = detailedMedia?.baseItem?.episodeNumber

        if (seasonNumber != null && episodeNumber != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            ) {
                Text(
                    text = stringResource(R.string.details_season, seasonNumber),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.details_episode, episodeNumber),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = buildString {
                append(detailedMedia?.baseItem?.titlePl.orEmpty())
                detailedMedia?.baseItem?.episodeTitle?.let {
                    append(" - $it")
                }
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            modifier = Modifier
                .padding(top = MaterialTheme.spacing.extraSmall)
                .fillMaxWidth(0.4f),
            text = detailedMedia?.baseItem?.description.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlayerControlsPlayPauseButton(
    isPlayingProvider: () -> Boolean,
    onPlayButtonClicked: () -> Unit,
    playButtonFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    FilmanIconButton(
        modifier = modifier.focusRequester(playButtonFocusRequester),
        icon = if (isPlayingProvider()) {
            R.drawable.ic_pause
        } else {
            R.drawable.ic_play
        },
        contentDescription = null,
        onClick = onPlayButtonClicked,
        iconSize = 64.dp,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun PlayerControlsProgressBar(
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
        verticalArrangement = Arrangement.spacedBy(
            space = MaterialTheme.spacing.small,
            alignment = Alignment.CenterVertically,
        ),
    ) {
        FilmanSeekBar(
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
                onSeekDiscarded()
            },
            modifier = Modifier.fillMaxWidth(),
        )

        PlayerControlsPositionRow(
            currentPositionProvider = currentPositionProvider,
            durationProvider = durationProvider,
        )
    }
}

@Composable
private fun PlayerControlsPositionRow(
    currentPositionProvider: () -> Long,
    durationProvider: () -> Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PlayerControlsPositionText(
            positionProvider = currentPositionProvider,
        )

        PlayerControlsPositionText(
            positionProvider = durationProvider,
        )
    }
}

@Composable
private fun PlayerControlsPositionText(
    positionProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = stringResource(
            R.string.details_duration,
            positionProvider().parseDuration(),
        ),
        textAlign = TextAlign.Start,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BoxScope.PlayerControlsNextEpisodeBox(
    isVisible: Boolean,
    onNextEpisodeRequested: () -> Unit,
    nextEpisodeButtonFocusRequester: FocusRequester,
    onBoxAppeared: () -> Unit,
    onTimerStateChanged: (isRunning: Boolean, stopTimer: (() -> Unit)?) -> Unit,
) {
    val progress = remember { Animatable(0f) }

    var timerRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isVisible) {
        if (isVisible) {
            timerRunning = true
            onTimerStateChanged(true) {
                timerRunning = false
                scope.launch { progress.stop() }
            }
            onBoxAppeared()
            try {
                progress.snapTo(0f)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = NEXT_EPISODE_BOX_TIMEOUT_MS,
                        easing = LinearEasing,
                    ),
                )

                if (timerRunning) {
                    onNextEpisodeRequested()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ignore other exceptions
            }

            onTimerStateChanged(false, null)
        }
    }

    AnimatedVisibility(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(
                end = MaterialTheme.spacing.extraLarge,
                bottom = MaterialTheme.spacing.extraLarge * 2,
            ),
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val backgroundColor = MaterialTheme.colorScheme.surfaceVariant

        DisposableEffect(Unit) {
            nextEpisodeButtonFocusRequester.requestFocus()
            onDispose { }
        }

        Button(
            modifier = Modifier
                .selectablePulse()
                .drawWithCache {
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
                .focusRequester(nextEpisodeButtonFocusRequester),
            onClick = onNextEpisodeRequested,
            scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 0.9f),
            colors = ButtonDefaults.colors(
                focusedContainerColor = Color.Transparent,
                focusedContentColor = MaterialTheme.colorScheme.surface,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.surface,
            ),
            shape = ButtonDefaults.shape(CircleShape),
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(R.drawable.ic_play),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = stringResource(R.string.player_next_episode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private val CONTROLS_VISIBILITY_TIMEOUT = 5.seconds
private const val NEXT_EPISODE_BOX_TIME_LEFT_MS = 20000
private const val NEXT_EPISODE_BOX_TIMEOUT_MS = 10000
