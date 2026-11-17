package com.pointlessapps.filman.ui.player

import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.Route
import com.pointlessapps.filman.player.lektor.controller.LektorPlaybackController
import com.pointlessapps.filman.player.lektor.engine.LektorModelManager
import com.pointlessapps.filman.player.lektor.engine.ModelDownloadState
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.components.FilmanButton
import com.pointlessapps.filman.ui.components.FilmanFullscreenLoader
import com.pointlessapps.filman.ui.components.FilmanOverlayMenu
import com.pointlessapps.filman.ui.components.FilmanToast
import com.pointlessapps.filman.ui.core.CollectEffect
import com.pointlessapps.filman.ui.core.LocalIsPlaying
import com.pointlessapps.filman.ui.core.PlayerConstants
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.ui.core.getPlayerSeekScript
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.seconds

@Composable
fun PlayerScreen(
    url: String,
    onNavigateTo: (Route?) -> Unit,
    viewModel: PlayerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(url) {
        viewModel.onEvent(PlayerEvent.LoadDetails(url))
    }

    var toastMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is PlayerEffect.NavigateToAuth -> {
                onNavigateTo(Route.Login())
            }

            is PlayerEffect.ShowToast -> {
                toastMessage = effect.message.asString(context)
            }
        }
    }

    val isPlayingLocal = LocalIsPlaying.current
    DisposableEffect(Unit) {
        onDispose {
            isPlayingLocal.value = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState =
                Triple(
                    state.isLoading,
                    state.shared.errorMessage != null,
                    state.shared.errorMessage,
                ),
            contentAlignment = Alignment.Center,
        ) { (isLoading, hasError, errorMessage) ->
            if (isLoading) {
                FilmanFullscreenLoader()
            } else if (hasError && errorMessage != null) {
                PlayerErrorContent(
                    errorMessage = errorMessage,
                    onBackClicked = { onNavigateTo(null) },
                    onSwitchSource = if (state.alternativeSources.isNotEmpty()) {
                        {
                            viewModel.onEvent(
                                PlayerEvent.OpenSettingsMenu(
                                    state.currentPositionMs,
                                    PlayerConstants.MENU_SOURCES_ID,
                                ),
                            )
                        }
                    } else {
                        null
                    },
                )
            } else {
                PlayerContent(
                    state = state,
                    lektorModelManager = viewModel.lektorModelManager,
                    onEvent = viewModel::onEvent,
                    onBackClicked = { onNavigateTo(null) },
                )
            }
        }

        state.overlayMenuData?.let { data ->
            FilmanOverlayMenu(
                title = data.title,
                items = data.items,
                initialMenuId = data.initialMenuId,
                onDismissRequest = { viewModel.onEvent(BaseEvent.CloseContextMenu) },
            )
        }

        toastMessage?.let { message ->
            FilmanToast(
                message = message,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = MaterialTheme.spacing.large),
                onDismiss = { toastMessage = null },
            )
        }
    }
}

@Composable
private fun PlayerErrorContent(
    errorMessage: TextValue,
    onBackClicked: () -> Unit,
    onSwitchSource: (() -> Unit)?,
) {
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(initialFocusRequester, onSwitchSource != null) {
        initialFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                space = MaterialTheme.spacing.medium,
                alignment = Alignment.CenterVertically,
            ),
    ) {
        Text(
            text = errorMessage.asString(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleLarge,
        )

        if (onSwitchSource != null) {
            FilmanButton(
                text = stringResource(R.string.player_video_source),
                iconRes = null,
                onClick = onSwitchSource,
                modifier = Modifier.focusRequester(initialFocusRequester),
            )
        }

        FilmanButton(
            text = stringResource(R.string.overlay_menu_back),
            iconRes = R.drawable.ic_back,
            onClick = onBackClicked,
            modifier = if (onSwitchSource == null) Modifier.focusRequester(initialFocusRequester) else Modifier,
        )
    }
}

@Composable
private fun PlayerContent(
    state: PlayerState,
    lektorModelManager: LektorModelManager,
    onEvent: (FilmanEvent) -> Unit,
    onBackClicked: () -> Unit,
) {
    val context = LocalContext.current
    var playerReference by remember { mutableStateOf<WeakReference<ExoPlayer>?>(null) }
    var webViewReference by remember { mutableStateOf<WeakReference<WebView>?>(null) }
    var isCaptchaShowing by remember { mutableStateOf(false) }
    var isWebViewLoading by remember(state.videoUrl) { mutableStateOf(state.isWebView) }
    val overlayButtonsFocusRequester = remember { FocusRequester() }
    val pointerFocusRequester = remember { FocusRequester() }
    val currentUrl = state.detailedMedia?.baseItem?.url
    val currentPositionMs by rememberUpdatedState(state.currentPositionMs)
    val isWebView by rememberUpdatedState(state.isWebView)

    val lektorController = remember(context, lektorModelManager) {
        LektorPlaybackController(
            context = context,
            modelManager = lektorModelManager,
            onDuckingVolumeChange = { duckedVolume ->
                playerReference?.get()?.volume = duckedVolume
                webViewReference?.get()?.evaluateJavascript(
                    "document.querySelectorAll('video').forEach(function(v) { v.volume = $duckedVolume; });",
                    null,
                )
            },
            nativePosition = { if (isWebView) null else playerReference?.get()?.currentPosition },
            nativeIsPlaying = { if (isWebView) null else playerReference?.get()?.isPlaying },
        )
    }

    DisposableEffect(lektorController) {
        onDispose {
            lektorController.release()
        }
    }

    DisposableEffect(playerReference, state.isWebView, lektorController) {
        val player = if (state.isWebView) null else playerReference?.get()
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK ||
                    reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    lektorController.onNativeSeek(newPosition.positionMs)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (player != null) lektorController.onNativePlaybackChanged(player.currentPosition, isPlaying)
            }
        }
        player?.addListener(listener)
        if (player != null) lektorController.onNativePlaybackChanged(player.currentPosition, player.isPlaying)
        onDispose { player?.removeListener(listener) }
    }

    LaunchedEffect(lektorController) {
        var lastToastPercent = -1
        lektorModelManager.downloadState.collect { downloadState ->
            when (downloadState) {
                is ModelDownloadState.Downloading -> {
                    val percent = (downloadState.progress * 100).toInt()
                    if (percent % 25 == 0 && percent != lastToastPercent) {
                        lastToastPercent = percent
                        Toast.makeText(context, "Pobieranie modelu lektora: $percent%", Toast.LENGTH_SHORT).show()
                    }
                }
                ModelDownloadState.Extracting -> {
                    Toast.makeText(context, "Wypakowywanie modelu lektora...", Toast.LENGTH_SHORT).show()
                }
                is ModelDownloadState.Error -> {
                    Toast.makeText(context, downloadState.message, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(state.isLektorEnabled) {
        lektorController.setEnabled(state.isLektorEnabled)
        if (!state.isLektorEnabled) {
            playerReference?.get()?.volume = 1.0f
            webViewReference?.get()?.evaluateJavascript(
                "document.querySelectorAll('video').forEach(function(v) { v.volume = 1.0; });",
                null,
            )
        }
    }

    LaunchedEffect(state.lektorVoiceId) {
        lektorController.setVoice(
            LektorVoice.fromId(state.lektorVoiceId),
            LektorVoice.systemVoiceName(state.lektorVoiceId),
        )
    }

    LaunchedEffect(state.lektorDuckingVolume) {
        lektorController.setDuckingVolume(state.lektorDuckingVolume)
    }

    LaunchedEffect(state.lektorTimingOffsetMs) {
        lektorController.setTimingOffsetMs(state.lektorTimingOffsetMs)
    }

    LaunchedEffect(state.lektorEdgeRate, state.lektorEdgePitch, state.lektorEdgeVolume) {
        lektorController.setEdgeVoiceOptions(
            rate = state.lektorEdgeRate,
            pitch = state.lektorEdgePitch,
            volume = state.lektorEdgeVolume,
        )
    }

    LaunchedEffect(state.rawSubtitleContent) {
        if (state.rawSubtitleContent != null) {
            lektorController.setSubtitles(state.rawSubtitleContent)
        } else {
            lektorController.clearSubtitles()
        }
    }

    LaunchedEffect(state.currentPositionMs, state.isPlaying, state.isBuffering) {
        lektorController.updatePlaybackState(state.currentPositionMs, state.isPlaying, state.isBuffering)
    }

    LaunchedEffect(state.videoUrl) {
        isCaptchaShowing = false
    }

    BackHandler(enabled = isCaptchaShowing) {
        if (state.overlayMenuData != null) {
            onEvent(BaseEvent.CloseContextMenu)
        } else {
            onBackClicked()
        }
    }

    DisposableEffect(currentUrl) {
        onDispose {
            if (currentUrl != null) {
                onEvent(PlayerEvent.SaveProgress(currentUrl, currentPositionMs))
            }
        }
    }


    val isPlayingLocal = LocalIsPlaying.current
    LaunchedEffect(state.isPlaying) {
        isPlayingLocal.value = state.isPlaying
    }

    LaunchedEffect(state.isPlaying, currentUrl) {
        if (currentUrl != null && !state.isPlaying) {
            onEvent(PlayerEvent.SaveProgress(currentUrl, currentPositionMs))
        }

        if (!state.isPlaying || currentUrl == null) return@LaunchedEffect
        while (true) {
            delay(30.seconds)
            onEvent(PlayerEvent.SaveProgress(currentUrl, currentPositionMs))
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        state.videoUrl?.let { url ->
            if (state.isWebView) {
                key(url) {
                    WebViewPlayer(
                        videoUrl = url,
                        startPositionMs = state.startPositionMs,
                        isPlaying = state.isPlaying,
                        playbackSpeed = state.playbackSpeed,
                        aspectRatioMode = state.aspectRatioMode,
                        selectedSubtitleUrl = state.selectedSubtitleUrl,
                        showSubtitles = state.showSubtitles,
                        onIsPlayingChanged = { onEvent(PlayerEvent.IsPlayingChanged(it)) },
                        onIsBufferingChanged = { onEvent(PlayerEvent.IsBufferingChanged(it)) },
                        onDurationProvided = { onEvent(PlayerEvent.DurationProvided(it)) },
                        onCurrentPositionChanged = { onEvent(PlayerEvent.CurrentPositionChanged(it)) },
                        onWebViewProvided = { webViewReference = it },
                        onPlayerError = { onEvent(PlayerEvent.PlayerError) },
                        onLoadingStateChanged = { isWebViewLoading = it },
                        onCaptchaStateChanged = { isCaptchaShowing = it },
                        onCancelCaptcha = onBackClicked,
                        overlayFocusRequester = overlayButtonsFocusRequester,
                        pointerFocusRequester = pointerFocusRequester,
                    )
                }
            } else {
                Player(
                    videoUrl = url,
                    audioUrl = state.alternativeSources.find { it.url == url }?.audioUrl,
                    headers = state.videoHeaders,
                    subtitles = state.subtitles + state.openSubtitles,
                    selectedSubtitleUrl = state.selectedSubtitleUrl,
                    selectedAudioTrackId = state.selectedAudioTrackId,
                    startPositionMs = state.startPositionMs,
                    playbackSpeed = state.playbackSpeed,
                    aspectRatioMode = state.aspectRatioMode,
                    subtitleStylePreferences = state.subtitleStylePreferences,
                    isLektorEnabled = state.isLektorEnabled,
                    showSubtitles = state.showSubtitles,

                    isPlaying = state.isPlaying,
                    hasNextEpisode = state.detailedMedia?.baseItem?.nextEpisodeUrl != null,
                    autoPlayNextEpisode = state.autoPlayNextEpisode,
                    onNextEpisodeRequested = { onEvent(PlayerEvent.NextEpisodeRequested) },
                    onIsPlayingChanged = { onEvent(PlayerEvent.IsPlayingChanged(it)) },
                    onIsBufferingChanged = { onEvent(PlayerEvent.IsBufferingChanged(it)) },
                    onDurationProvided = { onEvent(PlayerEvent.DurationProvided(it)) },
                    onCurrentPositionChanged = { onEvent(PlayerEvent.CurrentPositionChanged(it)) },
                    onAudioTracksChanged = { onEvent(PlayerEvent.AudioTracksChanged(it)) },
                    onPlayerProvided = { playerReference = it },
                    onPlayerError = { onEvent(PlayerEvent.PlayerError) },
                )
            }
        }

        if (isCaptchaShowing) {
            CloudflareVerificationOverlay(
                modifier = Modifier.align(Alignment.TopCenter),
                firstButtonFocusRequester = overlayButtonsFocusRequester,
                onNavigateDown = {
                    pointerFocusRequester.requestFocus()
                },
                onSwitchSource = {
                    onEvent(
                        PlayerEvent.OpenSettingsMenu(
                            state.currentPositionMs,
                            PlayerConstants.MENU_SOURCES_ID,
                        ),
                    )
                },
                onNextSource = {
                    onEvent(PlayerEvent.PlayerError)
                },
                onBackClicked = onBackClicked,
            )
        }

        if (!isCaptchaShowing) {
            PlayerControls(
                detailedMedia = state.detailedMedia,
                isPlayingProvider = { state.isPlaying },
                isBufferingProvider = {
                    playerIsBuffering(state.isBuffering, state.isWebView, isWebViewLoading)
                },
                durationProvider = { state.duration },
                currentPositionProvider = { state.currentPositionMs },
                onPlayButtonClicked = { onEvent(PlayerEvent.IsPlayingChanged(!state.isPlaying)) },
                onSeekCommited = {
                    if (state.isWebView) {
                        lektorController.onSeek(it)
                        webViewReference
                            ?.get()
                            ?.evaluateJavascript(getPlayerSeekScript(it / 1000.0), null)
                    } else {
                        playerReference?.get()?.seekTo(it)
                    }
                },
                onNextEpisodeRequested = { onEvent(PlayerEvent.NextEpisodeRequested) },
                onSettingsClicked = {
                    onEvent(PlayerEvent.OpenSettingsMenu(state.currentPositionMs, it))
                },
                onBackClicked = onBackClicked,
                nextEpisodeButtonUIState = state.nextEpisodeButtonUIState,
                isOverlayMenuOpen = state.overlayMenuData != null,
                onControlsVisibilityChanged = { onEvent(PlayerEvent.ControlsVisibilityChanged(it)) },
                onNextEpisodePromptDismissed = { onEvent(PlayerEvent.NextEpisodePromptDismissed) },
                onCancelTimerRequested = { onEvent(PlayerEvent.CancelNextEpisodeTimer) },
            )
        }
    }
}

internal fun playerIsBuffering(isBuffering: Boolean, isWebView: Boolean, isWebViewLoading: Boolean): Boolean =
    isBuffering || (isWebView && isWebViewLoading)
