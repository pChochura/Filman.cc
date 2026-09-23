package com.pointlessapps.filman.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.ui.components.FilmanFullscreenLoader
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonUIState

@Composable
fun PlayerControls(
    detailedMedia: DetailedMedia?,
    isPlayingProvider: () -> Boolean,
    isBufferingProvider: () -> Boolean,
    durationProvider: () -> Long,
    currentPositionProvider: () -> Long,
    onPlayButtonClicked: () -> Unit,
    onSeekCommited: (Long) -> Unit,
    onNextEpisodeRequested: () -> Unit,
    onSettingsClicked: (String?) -> Unit,
    onBackClicked: () -> Unit,
    nextEpisodeButtonUIState: NextEpisodeButtonUIState,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    onNextEpisodePromptDismissed: () -> Unit,
    onCancelTimerRequested: () -> Unit,
) {
    val playButtonFocusRequester = remember { FocusRequester() }
    var controlsVisibilityTimeoutFlag by remember { mutableStateOf(false) }
    var areControlsVisible by remember { mutableStateOf(true) }
    val animatedAlpha by animateFloatAsState(if (areControlsVisible) 1f else 0f)

    val toggleUiVisibility = { visible: Boolean ->
        val wasVisible = areControlsVisible
        areControlsVisible = visible
        if (wasVisible != visible) {
            onControlsVisibilityChanged(visible)
        }
        if (!visible && !nextEpisodeButtonUIState.isVisible) {
            playButtonFocusRequester.requestFocus()
        }
        if (visible || wasVisible != visible) {
            controlsVisibilityTimeoutFlag = !controlsVisibilityTimeoutFlag
        }
        !wasVisible
    }

    LaunchedEffect(detailedMedia?.baseItem?.url) {
        toggleUiVisibility(true)
    }

    PlayerControlsVisibilityEffect(
        mediaUrl = detailedMedia?.baseItem?.url,
        isPlayingProvider = isPlayingProvider,
        playButtonFocusRequester = playButtonFocusRequester,
        onHideControls = { toggleUiVisibility(false) },
        onShowControls = { toggleUiVisibility(true) },
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
        modifier =
            Modifier
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

                        val step =
                            when {
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
                        playButtonFocusRequester = playButtonFocusRequester,
                        onNextEpisodeRequested = onNextEpisodeRequested,
                        uiState = nextEpisodeButtonUIState,
                        onDismissRequested = onNextEpisodePromptDismissed,
                        onCancelTimerRequested = onCancelTimerRequested,
                    )
                }
            },
        )
    }
}

