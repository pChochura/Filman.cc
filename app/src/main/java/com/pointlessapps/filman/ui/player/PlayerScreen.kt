package com.pointlessapps.filman.ui.player

import android.webkit.WebView
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
import com.pointlessapps.filman.R
import com.pointlessapps.filman.Route
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.components.FilmanButton
import com.pointlessapps.filman.ui.components.FilmanFullscreenLoader
import com.pointlessapps.filman.ui.components.FilmanOverlayMenu
import com.pointlessapps.filman.ui.components.FilmanToast
import com.pointlessapps.filman.ui.core.CollectEffect
import com.pointlessapps.filman.ui.core.LocalIsPlaying
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.ui.login.getPlayerSeekScript
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun PlayerScreen(
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
                )
            } else {
                PlayerContent(
                    state = state,
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
) {
    val backFocusRequester = remember { FocusRequester() }

    LaunchedEffect(backFocusRequester) {
        backFocusRequester.requestFocus()
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

        FilmanButton(
            text = stringResource(R.string.overlay_menu_back),
            iconRes = R.drawable.ic_back,
            onClick = onBackClicked,
            modifier = Modifier.focusRequester(backFocusRequester),
        )
    }
}

@Composable
private fun PlayerContent(
    state: PlayerState,
    onEvent: (PlayerEvent) -> Unit,
    onBackClicked: () -> Unit,
) {
    var playerReference by remember { mutableStateOf<WeakReference<ExoPlayer>?>(null) }
    var webViewReference by remember { mutableStateOf<WeakReference<WebView>?>(null) }
    var isCaptchaShowing by remember { mutableStateOf(false) }
    val currentUrl = state.detailedMedia?.baseItem?.url
    val currentPositionMs by rememberUpdatedState(state.currentPositionMs)

    BackHandler(enabled = isCaptchaShowing) {
        onBackClicked()
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
                WebViewPlayer(
                    videoUrl = url,
                    isPlaying = state.isPlaying,
                    playbackSpeed = state.playbackSpeed,
                    aspectRatioMode = state.aspectRatioMode,
                    selectedSubtitleUrl = state.selectedSubtitleUrl,
                    onIsPlayingChanged = { onEvent(PlayerEvent.IsPlayingChanged(it)) },
                    onIsBufferingChanged = { onEvent(PlayerEvent.IsBufferingChanged(it)) },
                    onDurationProvided = { onEvent(PlayerEvent.DurationProvided(it)) },
                    onCurrentPositionChanged = { onEvent(PlayerEvent.CurrentPositionChanged(it)) },
                    onWebViewProvided = { webViewReference = it },
                    onPlayerError = { onEvent(PlayerEvent.PlayerError) },
                    onCaptchaStateChanged = { isCaptchaShowing = it },
                    onCloudflareCleared = { domain, cookies ->
                        onEvent(PlayerEvent.CloudflareCleared(domain, cookies))
                    },
                )
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

        if (!isCaptchaShowing) {
            PlayerControls(
                detailedMedia = state.detailedMedia,
                isPlayingProvider = { state.isPlaying },
                isBufferingProvider = { state.isBuffering },
                durationProvider = { state.duration },
                currentPositionProvider = { state.currentPositionMs },
                onPlayButtonClicked = { onEvent(PlayerEvent.IsPlayingChanged(!state.isPlaying)) },
                onSeekCommited = {
                    if (state.isWebView) {
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
                onControlsVisibilityChanged = { onEvent(PlayerEvent.ControlsVisibilityChanged(it)) },
                onNextEpisodePromptDismissed = { onEvent(PlayerEvent.NextEpisodePromptDismissed) },
                onCancelTimerRequested = { onEvent(PlayerEvent.CancelNextEpisodeTimer) },
            )
        }
    }
}
