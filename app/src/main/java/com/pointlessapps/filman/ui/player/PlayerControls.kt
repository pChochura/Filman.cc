package com.pointlessapps.filman.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.R
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeInitialAppearance
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeSecondaryAppearance
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.ui.components.FilmanButton
import com.pointlessapps.filman.ui.components.FilmanFullscreenLoader
import com.pointlessapps.filman.ui.components.FilmanIconButton
import com.pointlessapps.filman.ui.components.FilmanSeekBar
import com.pointlessapps.filman.ui.components.TooltipPosition
import com.pointlessapps.filman.ui.core.gradientBackground
import com.pointlessapps.filman.ui.core.parseDuration
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun PlayerControls(
    detailedMedia: DetailedMedia?,
    isPlayingProvider: () -> Boolean,
    isBufferingProvider: () -> Boolean,
    durationProvider: () -> Long,
    currentPositionProvider: () -> Long,
    onPlayButtonClicked: () -> Unit,
    onSeekCommited: (Long) -> Unit,
    onNextEpisodeRequested: () -> Unit,
    onNextEpisodeBoxAppeared: () -> Unit,
    onSettingsClicked: (String?) -> Unit,
    onBackClicked: () -> Unit,
    initialAppearanceType: String,
    initialAppearanceOffset: Long,
    secondaryAppearanceType: String,
    secondaryAppearanceOffset: Long,
    secondaryTimerAmount: Long,
    initialAppearancePercentage: Long,
    secondaryAppearancePercentage: Long,
) {
    val playButtonFocusRequester = remember { FocusRequester() }
    var controlsVisibilityTimeoutFlag by remember { mutableStateOf(false) }
    var areControlsVisible by remember { mutableStateOf(true) }
    val animatedAlpha by animateFloatAsState(if (areControlsVisible) 1f else 0f)

    val toggleUiVisibility = { visible: Boolean ->
        val wasVisible = areControlsVisible
        areControlsVisible = visible
        if (!visible) {
            playButtonFocusRequester.requestFocus()
        }
        controlsVisibilityTimeoutFlag = !controlsVisibilityTimeoutFlag
        !wasVisible
    }

    PlayerControlsVisibilityEffect(
        isPlayingProvider = isPlayingProvider,
        playButtonFocusRequester = playButtonFocusRequester,
        onHideControls = { areControlsVisible = false },
        onShowControls = { areControlsVisible = true },
        visibilityTimeoutTrigger = controlsVisibilityTimeoutFlag,
    )

    PlayerControlsBackHandler(
        areControlsVisible = areControlsVisible,
        isPlayingProvider = isPlayingProvider,
        toggleUiVisibility = { toggleUiVisibility(it) },
    )

    var quickSeekOffset by remember { mutableLongStateOf(0L) }
    var quickSeekClicks by remember { mutableIntStateOf(0) }
    var quickSeekDirection by remember { mutableIntStateOf(0) }

    PlayerControlsQuickSeekHandler(
        quickSeekOffset = quickSeekOffset,
        durationProvider = durationProvider,
        currentPositionProvider = currentPositionProvider,
        onSeekCommited = onSeekCommited,
        onClearQuickSeek = {
            quickSeekOffset = 0L
            quickSeekClicks = 0
            quickSeekDirection = 0
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .playerControlsKeyEvent(
                areControlsVisible = areControlsVisible,
                onToggleUiVisibility = { toggleUiVisibility(true) },
                onQuickSeek = { direction ->
                    if (quickSeekDirection != 0 && quickSeekDirection != direction) {
                        quickSeekClicks = 0
                    }

                    quickSeekDirection = direction
                    quickSeekClicks++

                    val step = when {
                        quickSeekClicks >= 5 -> 30000L
                        quickSeekClicks >= 3 -> 20000L
                        else -> 10000L
                    }

                    quickSeekOffset += step * direction
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        FilmanFullscreenLoader(
            isVisibleProvider = isBufferingProvider,
            longLoadingContent = {
                PlayerControlsBufferingPrompt(
                    onSettingsClicked = onSettingsClicked,
                )
            },
        )

        PlayerControlsQuickSeekOverlay(
            quickSeekOffset = quickSeekOffset,
            quickSeekDirection = quickSeekDirection,
            quickSeekClicks = quickSeekClicks,
        )

        PlayerControlsTopBar(
            areControlsVisible = areControlsVisible,
            detailedMedia = detailedMedia,
            playButtonFocusRequester = playButtonFocusRequester,
            onBackClicked = onBackClicked,
            onNextEpisodeRequested = onNextEpisodeRequested,
        )

        PlayerControlsBottomBar(
            detailedMedia = detailedMedia,
            isPlayingProvider = isPlayingProvider,
            isBufferingProvider = isBufferingProvider,
            durationProvider = durationProvider,
            currentPositionProvider = currentPositionProvider,
            onPlayButtonClicked = onPlayButtonClicked,
            onSeekCommited = onSeekCommited,
            onSettingsClicked = onSettingsClicked,
            playButtonFocusRequester = playButtonFocusRequester,
            areControlsVisible = areControlsVisible,
            animatedAlpha = animatedAlpha,
            nextEpisodeBox = {
                if (detailedMedia?.baseItem?.nextEpisodeUrl != null) {
                    PlayerControlsNextEpisodeBox(
                        areControlsVisible = areControlsVisible,
                        durationProvider = durationProvider,
                        currentPositionProvider = currentPositionProvider,
                        playButtonFocusRequester = playButtonFocusRequester,
                        onNextEpisodeRequested = onNextEpisodeRequested,
                        onNextEpisodeBoxAppeared = onNextEpisodeBoxAppeared,
                        initialAppearanceType = initialAppearanceType,
                        initialAppearanceOffset = initialAppearanceOffset,
                        secondaryAppearanceType = secondaryAppearanceType,
                        secondaryAppearanceOffset = secondaryAppearanceOffset,
                        secondaryTimerAmount = secondaryTimerAmount,
                        initialAppearancePercentage = initialAppearancePercentage,
                        secondaryAppearancePercentage = secondaryAppearancePercentage,
                    )
                }
            },
        )
    }
}

@Composable
private fun PlayerControlsQuickSeekOverlay(
    quickSeekOffset: Long,
    quickSeekDirection: Int,
    quickSeekClicks: Int,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        modifier = modifier.background(
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
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.spacing.extraLarge,
                    vertical = MaterialTheme.spacing.medium,
                ),
            )
        }
    }
}

@Composable
private fun BoxScope.PlayerControlsTopBar(
    areControlsVisible: Boolean,
    detailedMedia: DetailedMedia?,
    playButtonFocusRequester: FocusRequester,
    onBackClicked: () -> Unit,
    onNextEpisodeRequested: () -> Unit,
) {
    AnimatedVisibility(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(MaterialTheme.spacing.extraLarge),
        visible = areControlsVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .focusGroup()
                .focusProperties { down = playButtonFocusRequester },
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val backButtonFocusRequester = remember { FocusRequester() }
            FilmanIconButton(
                icon = R.drawable.ic_back,
                contentDescription = R.string.overlay_menu_back,
                onClick = onBackClicked,
                iconSize = 32.dp,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tooltipPosition = TooltipPosition.Below,
                showTooltip = areControlsVisible,
                modifier = Modifier
                    .focusRequester(backButtonFocusRequester)
                    .focusProperties {
                        up = backButtonFocusRequester
                        left = backButtonFocusRequester
                    },
            )

            if (detailedMedia?.baseItem?.nextEpisodeUrl != null) {
                val nextEpisodeButtonFocusRequester = remember { FocusRequester() }
                FilmanIconButton(
                    icon = R.drawable.ic_next,
                    contentDescription = R.string.player_next_episode,
                    onClick = {
                        onNextEpisodeRequested()
                        playButtonFocusRequester.requestFocus()
                    },
                    iconSize = 32.dp,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tooltipPosition = TooltipPosition.Below,
                    showTooltip = areControlsVisible,
                    modifier = Modifier
                        .focusRequester(nextEpisodeButtonFocusRequester)
                        .focusProperties { up = nextEpisodeButtonFocusRequester },
                )
            }
        }
    }
}

@Composable
private fun PlayerControlsBottomBar(
    detailedMedia: DetailedMedia?,
    isPlayingProvider: () -> Boolean,
    isBufferingProvider: () -> Boolean,
    durationProvider: () -> Long,
    currentPositionProvider: () -> Long,
    onPlayButtonClicked: () -> Unit,
    onSeekCommited: (Long) -> Unit,
    onSettingsClicked: (String?) -> Unit,
    playButtonFocusRequester: FocusRequester,
    areControlsVisible: Boolean,
    animatedAlpha: Float,
    modifier: Modifier = Modifier,
    nextEpisodeBox: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = animatedAlpha }
                .gradientBackground(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
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
            Box(modifier = Modifier.fillMaxWidth()) {
                PlayerControlsMediaDetails(
                    detailedMedia = detailedMedia,
                    modifier = Modifier.graphicsLayer { alpha = animatedAlpha },
                )

                Box(
                    modifier = Modifier.matchParentSize(),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    nextEpisodeBox()
                }
            }

            Row(
                modifier = Modifier.graphicsLayer { alpha = animatedAlpha },
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                val settingsButtonFocusRequester = remember { FocusRequester() }

                PlayerControlsPlayPauseButton(
                    isPlayingProvider = isPlayingProvider,
                    onPlayButtonClicked = onPlayButtonClicked,
                    playButtonFocusRequester = playButtonFocusRequester,
                    areControlsVisible = areControlsVisible,
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
                            down = playButtonFocusRequester
                            left = playButtonFocusRequester
                            right = settingsButtonFocusRequester
                        },
                    icon = R.drawable.ic_settings,
                    contentDescription = R.string.player_settings,
                    onClick = { onSettingsClicked(null) },
                    iconSize = 32.dp,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    showTooltip = areControlsVisible,
                )
            }
        }
    }
}

@Composable
private fun PlayerControlsVisibilityEffect(
    isPlayingProvider: () -> Boolean,
    playButtonFocusRequester: FocusRequester,
    onHideControls: () -> Unit,
    onShowControls: () -> Unit,
    visibilityTimeoutTrigger: Boolean,
) {
    LaunchedEffect(Unit) {
        delay(300.milliseconds)
        playButtonFocusRequester.requestFocus()
    }

    val currentIsPlayingProvider by rememberUpdatedState(isPlayingProvider)
    LaunchedEffect(visibilityTimeoutTrigger) {
        snapshotFlow { currentIsPlayingProvider() }.collectLatest { isPlaying ->
            if (isPlaying) {
                delay(CONTROLS_VISIBILITY_TIMEOUT)
                onHideControls()
                playButtonFocusRequester.requestFocus()
            } else {
                onShowControls()
            }
        }
    }
}

@Composable
private fun PlayerControlsQuickSeekHandler(
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
            val newPos = (currentPos + quickSeekOffset).coerceIn(
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

private fun Modifier.playerControlsKeyEvent(
    areControlsVisible: Boolean,
    onToggleUiVisibility: () -> Boolean,
    onQuickSeek: (direction: Int) -> Unit,
) = onPreviewKeyEvent {
    if (it.key == Key.Back) return@onPreviewKeyEvent false

    if (!areControlsVisible) {
        if (it.key == Key.DirectionRight || it.key == Key.DirectionLeft) {
            if (it.type == KeyEventType.KeyDown) {
                onQuickSeek(if (it.key == Key.DirectionRight) 1 else -1)
            }
            return@onPreviewKeyEvent true
        }
    }

    onToggleUiVisibility()
}

@Composable
private fun PlayerControlsBufferingPrompt(
    onSettingsClicked: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = CircleShape,
            )
            .padding(MaterialTheme.spacing.medium),
    ) {
        Text(
            text = stringResource(R.string.player_still_waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FilmanButton(
            modifier = Modifier.focusRequester(focusRequester),
            text = stringResource(R.string.player_video_source),
            iconRes = R.drawable.ic_settings,
            onClick = { onSettingsClicked(PlayerConstants.MENU_SOURCES_ID) },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun PlayerControlsBackHandler(
    areControlsVisible: Boolean,
    isPlayingProvider: () -> Boolean,
    toggleUiVisibility: (Boolean) -> Unit,
) {
    val currentIsPlayingProvider by rememberUpdatedState(isPlayingProvider)
    var isPlaying by remember { mutableStateOf(currentIsPlayingProvider()) }

    LaunchedEffect(Unit) {
        snapshotFlow { currentIsPlayingProvider() }.collectLatest {
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
    areControlsVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    FilmanIconButton(
        modifier = modifier
            .focusRequester(playButtonFocusRequester)
            .focusProperties { left = playButtonFocusRequester },
        icon = if (isPlayingProvider()) {
            R.drawable.ic_pause
        } else {
            R.drawable.ic_play
        },
        contentDescription = if (isPlayingProvider()) {
            R.string.player_pause
        } else {
            R.string.player_play
        },
        onClick = onPlayButtonClicked,
        iconSize = 64.dp,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        showTooltip = areControlsVisible,
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
            currentPositionProvider = { scrubPosition ?: currentPositionProvider() },
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
private fun PlayerControlsNextEpisodeBox(
    areControlsVisible: Boolean,
    durationProvider: () -> Long,
    currentPositionProvider: () -> Long,
    playButtonFocusRequester: FocusRequester,
    onNextEpisodeRequested: () -> Unit,
    onNextEpisodeBoxAppeared: () -> Unit,
    initialAppearanceType: String,
    initialAppearanceOffset: Long,
    secondaryAppearanceType: String,
    secondaryAppearanceOffset: Long,
    secondaryTimerAmount: Long,
    initialAppearancePercentage: Long,
    secondaryAppearancePercentage: Long,
) {
    var isVisible by remember { mutableStateOf(false) }
    var isTimerCancelled by remember { mutableStateOf(false) }
    var isHardPrompt by remember { mutableStateOf(false) }
    var isPastSoftOffset by remember { mutableStateOf(false) }
    var wasSoftPromptDismissed by remember { mutableStateOf(false) }
    var wasHardPromptDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(isHardPrompt) {
        if (!isHardPrompt) isTimerCancelled = false
    }

    LaunchedEffect(areControlsVisible) {
        if (areControlsVisible && isHardPrompt) {
            isTimerCancelled = true
        }
    }

    PlayerControlsNextEpisodeVisibilityEffect(
        areControlsVisible = areControlsVisible,
        wasSoftPromptDismissed = wasSoftPromptDismissed,
        wasHardPromptDismissed = wasHardPromptDismissed,
        durationProvider = durationProvider,
        currentPositionProvider = currentPositionProvider,
        initialAppearanceType = initialAppearanceType,
        initialAppearanceOffset = initialAppearanceOffset,
        secondaryAppearanceType = secondaryAppearanceType,
        secondaryAppearanceOffset = secondaryAppearanceOffset,
        initialAppearancePercentage = initialAppearancePercentage,
        secondaryAppearancePercentage = secondaryAppearancePercentage,
        onShowPrompt = { visible, hard, pastSoftOffset ->
            isVisible = visible
            isHardPrompt = hard
            isPastSoftOffset = pastSoftOffset
        },
        onResetDismissed = {
            wasSoftPromptDismissed = false
            wasHardPromptDismissed = false
            isTimerCancelled = false
        },
    )

    val progress = remember { Animatable(0f) }
    var timerRunning by remember { mutableStateOf(false) }
    val nextEpisodeButtonFocusRequester = remember { FocusRequester() }

    val shouldShow = isVisible || (areControlsVisible && isPastSoftOffset)

    BackHandler(isVisible) {
        isVisible = false
        if (isHardPrompt) {
            wasHardPromptDismissed = true
            isTimerCancelled = true
        } else {
            wasSoftPromptDismissed = true
        }
        playButtonFocusRequester.requestFocus()
    }

    PlayerControlsNextEpisodeTimerEffect(
        isVisible = shouldShow,
        isHardPrompt = isHardPrompt,
        isTimerEnabled = !areControlsVisible &&
                !isTimerCancelled &&
                secondaryAppearanceType == NextEpisodeSecondaryAppearance.SHOW_WITH_TIMER,
        timerAmountMs = secondaryTimerAmount * 1000,
        progress = progress,
        onTimerStatusChanged = { timerRunning = it },
        onNextEpisodeRequested = onNextEpisodeRequested,
    )

    LaunchedEffect(shouldShow) {
        if (shouldShow) onNextEpisodeBoxAppeared()
    }

    PlayerControlsNextEpisodeUI(
        isVisible = shouldShow,
        isHardPrompt = isHardPrompt,
        areControlsVisible = areControlsVisible,
        progress = progress,
        timerRunning = timerRunning,
        onNextEpisodeRequested = onNextEpisodeRequested,
        onStopTimer = { isTimerCancelled = true },
        nextEpisodeButtonFocusRequester = nextEpisodeButtonFocusRequester,
    )
}

@Composable
private fun PlayerControlsNextEpisodeVisibilityEffect(
    areControlsVisible: Boolean,
    wasSoftPromptDismissed: Boolean,
    wasHardPromptDismissed: Boolean,
    durationProvider: () -> Long,
    currentPositionProvider: () -> Long,
    initialAppearanceType: String,
    initialAppearanceOffset: Long,
    secondaryAppearanceType: String,
    secondaryAppearanceOffset: Long,
    initialAppearancePercentage: Long,
    secondaryAppearancePercentage: Long,
    onShowPrompt: (visible: Boolean, hard: Boolean, pastSoftOffset: Boolean) -> Unit,
    onResetDismissed: () -> Unit,
) {
    val currentDurationProvider by rememberUpdatedState(durationProvider)
    val currentPositionFlowProvider by rememberUpdatedState(currentPositionProvider)
    val currentAreControlsVisible by rememberUpdatedState(areControlsVisible)
    val currentWasSoftPromptDismissed by rememberUpdatedState(wasSoftPromptDismissed)
    val currentWasHardPromptDismissed by rememberUpdatedState(wasHardPromptDismissed)
    val currentOnShowPrompt by rememberUpdatedState(onShowPrompt)
    val currentOnResetDismissed by rememberUpdatedState(onResetDismissed)
    val currentInitialType by rememberUpdatedState(initialAppearanceType)
    val currentSecondaryType by rememberUpdatedState(secondaryAppearanceType)
    val currentInitialOffset by rememberUpdatedState(initialAppearanceOffset)
    val currentSecondaryOffset by rememberUpdatedState(secondaryAppearanceOffset)

    LaunchedEffect(Unit) {
        snapshotFlow { currentPositionFlowProvider() }.collectLatest {
            val duration = currentDurationProvider()
            if (duration > 0) {
                val timeLeft = duration - it
                val softPromptTimeLeft = (duration * (initialAppearancePercentage / 100f))
                    .toLong().coerceAtMost(currentInitialOffset * 1000)
                val hardPromptTimeLeft = (duration * (secondaryAppearancePercentage / 100f))
                    .toLong().coerceAtMost(currentSecondaryOffset * 1000)

                if (timeLeft <= hardPromptTimeLeft) {
                    val visible = when (currentSecondaryType) {
                        NextEpisodeSecondaryAppearance.SHOW_WITH_TIMER,
                        NextEpisodeSecondaryAppearance.JUST_SHOW,
                            -> !currentWasHardPromptDismissed && !currentAreControlsVisible

                        NextEpisodeSecondaryAppearance.SHOW_IN_OVERLAY ->
                            !currentWasHardPromptDismissed && currentAreControlsVisible

                        else -> false
                    }
                    val pastSoftOffset =
                        currentSecondaryType != NextEpisodeSecondaryAppearance.DONT_SHOW ||
                                currentInitialType != NextEpisodeInitialAppearance.DONT_SHOW
                    currentOnShowPrompt(visible, true, pastSoftOffset)
                } else if (timeLeft <= softPromptTimeLeft) {
                    val visible = when (currentInitialType) {
                        NextEpisodeInitialAppearance.SHOW ->
                            !currentWasSoftPromptDismissed && !currentAreControlsVisible

                        NextEpisodeInitialAppearance.SHOW_IN_OVERLAY ->
                            !currentWasSoftPromptDismissed && currentAreControlsVisible

                        else -> false
                    }
                    val pastSoftOffset =
                        currentInitialType != NextEpisodeInitialAppearance.DONT_SHOW
                    currentOnShowPrompt(visible, false, pastSoftOffset)
                } else {
                    currentOnShowPrompt(false, false, false)
                    currentOnResetDismissed()
                }
            }
        }
    }
}

@Composable
private fun PlayerControlsNextEpisodeTimerEffect(
    isVisible: Boolean,
    isHardPrompt: Boolean,
    isTimerEnabled: Boolean,
    timerAmountMs: Long,
    progress: Animatable<Float, *>,
    onTimerStatusChanged: (Boolean) -> Unit,
    onNextEpisodeRequested: () -> Unit,
) {
    LaunchedEffect(isVisible, isHardPrompt, isTimerEnabled, timerAmountMs) {
        if (isVisible && isHardPrompt && isTimerEnabled) {
            onTimerStatusChanged(true)
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = timerAmountMs.toInt(),
                    easing = LinearEasing,
                ),
            )
            onNextEpisodeRequested()
        } else {
            onTimerStatusChanged(false)
            progress.snapTo(0f)
        }
    }
}

@Composable
private fun PlayerControlsNextEpisodeUI(
    isVisible: Boolean,
    isHardPrompt: Boolean,
    areControlsVisible: Boolean,
    progress: Animatable<Float, *>,
    timerRunning: Boolean,
    onNextEpisodeRequested: () -> Unit,
    onStopTimer: () -> Unit,
    nextEpisodeButtonFocusRequester: FocusRequester,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val backgroundColor = MaterialTheme.colorScheme.surfaceVariant

        LaunchedEffect(Unit) {
            if (!areControlsVisible) {
                nextEpisodeButtonFocusRequester.requestFocus()
            }
        }

        FilmanButton(
            text = stringResource(R.string.player_next_episode),
            iconRes = R.drawable.ic_play,
            onClick = onNextEpisodeRequested,
            modifier = Modifier
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && timerRunning) {
                        onStopTimer()
                        if (it.key != Key.DirectionCenter && it.key != Key.Enter && it.key != Key.NumPadEnter) {
                            return@onPreviewKeyEvent true
                        }
                    }
                    false
                }
                .graphicsLayer {
                    clip = false
                    alpha = if (isHardPrompt || areControlsVisible) 1f else 0.5f
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
            containerColor = if (areControlsVisible) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                Color.Transparent
            },
            focusedContainerColor = if (areControlsVisible) {
                MaterialTheme.colorScheme.onSurface
            } else {
                Color.Transparent
            },
            contentColor = if (areControlsVisible) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            focusedContentColor = if (areControlsVisible) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

private val CONTROLS_VISIBILITY_TIMEOUT = 5.seconds
private const val NEXT_EPISODE_BOX_SOFT_PERCENTAGE_OFFSET = 0.03f
private const val NEXT_EPISODE_BOX_HARD_PERCENTAGE_OFFSET = 0.05f
