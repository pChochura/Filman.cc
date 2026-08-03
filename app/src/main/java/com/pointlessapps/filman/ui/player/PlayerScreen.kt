package com.pointlessapps.filman.ui.player

import android.app.Activity
import android.view.WindowManager
import android.webkit.WebView
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import com.pointlessapps.filman.Route
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.components.FilmanFullscreenLoader
import com.pointlessapps.filman.ui.components.FilmanOverlayMenu
import com.pointlessapps.filman.ui.core.CollectEffect
import com.pointlessapps.filman.ui.login.getPlayerSeekScript
import org.koin.androidx.compose.koinViewModel
import java.lang.ref.WeakReference

@Composable
internal fun PlayerScreen(
    url: String,
    onNavigateTo: (Route?) -> Unit,
    contentFocusRequester: FocusRequester,
    viewModel: PlayerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(url) {
        viewModel.onEvent(PlayerEvent.LoadDetails(url))
    }

    val activityContext = LocalContext.current
    DisposableEffect(Unit) {
        val window = (activityContext as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is PlayerEffect.NavigateToAuth -> onNavigateTo(Route.Login())
        }
    }

    AnimatedContent(
        targetState = state.isLoading,
        contentAlignment = Alignment.Center,
    ) { isLoading ->
        if (isLoading) {
            FilmanFullscreenLoader()
        } else {
            PlayerContent(
                state = state,
                onEvent = viewModel::onEvent,
                contentFocusRequester = contentFocusRequester,
                onBackClicked = { onNavigateTo(null) },
            )
        }
    }

    state.overlayMenuData?.let { data ->
        FilmanOverlayMenu(
            title = data.title,
            items = data.items,
            onDismissRequest = { viewModel.onEvent(BaseEvent.CloseContextMenu) },
        )
    }
}

@Composable
private fun PlayerContent(
    state: PlayerState,
    onEvent: (PlayerEvent) -> Unit,
    contentFocusRequester: FocusRequester,
    onBackClicked: () -> Unit,
) {
    val currentPosition = remember { mutableLongStateOf(state.startPositionMs) }
    var playerReference by remember { mutableStateOf<WeakReference<ExoPlayer>?>(null) }
    var webViewReference by remember { mutableStateOf<WeakReference<WebView>?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            onEvent(PlayerEvent.SaveProgress(currentPosition.longValue))
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
                    onIsPlayingChanged = { onEvent(PlayerEvent.IsPlayingChanged(it)) },
                    onIsBufferingChanged = { onEvent(PlayerEvent.IsBufferingChanged(it)) },
                    onDurationProvided = { onEvent(PlayerEvent.DurationProvided(it)) },
                    onCurrentPositionChanged = { currentPosition.longValue = it },
                    onWebViewProvided = { webViewReference = it },
                )
            } else {
                Player(
                    videoUrl = url,
                    audioUrl = state.alternativeSources.find { it.url == url }?.audioUrl,
                    headers = state.videoHeaders,
                    subtitles = state.subtitles,
                    selectedSubtitleUrl = state.selectedSubtitleUrl,
                    startPositionMs = state.startPositionMs,
                    isPlaying = state.isPlaying,
                    hasNextEpisode = state.detailedMedia?.baseItem?.nextEpisodeUrl != null,
                    onNextEpisodeRequested = { onEvent(PlayerEvent.NextEpisodeRequested) },
                    onIsPlayingChanged = { onEvent(PlayerEvent.IsPlayingChanged(it)) },
                    onIsBufferingChanged = { onEvent(PlayerEvent.IsBufferingChanged(it)) },
                    onDurationProvided = { onEvent(PlayerEvent.DurationProvided(it)) },
                    onCurrentPositionChanged = { currentPosition.longValue = it },
                    onPlayerProvided = { playerReference = it },
                    onPlayerError = { onEvent(PlayerEvent.PlayerError) },
                )
            }
        }

        PlayerControls(
            detailedMedia = state.detailedMedia,
            isPlayingProvider = { state.isPlaying },
            isBufferingProvider = { state.isBuffering },
            durationProvider = { state.duration },
            currentPositionProvider = { currentPosition.longValue },
            playButtonFocusRequester = contentFocusRequester,
            onPlayButtonClicked = { onEvent(PlayerEvent.IsPlayingChanged(!state.isPlaying)) },
            onSeekCommited = {
                if (state.isWebView) {
                    webViewReference?.get()
                        ?.evaluateJavascript(getPlayerSeekScript(it / 1000.0), null)
                } else {
                    playerReference?.get()?.seekTo(it)
                }
            },
            onNextEpisodeRequested = { onEvent(PlayerEvent.NextEpisodeRequested) },
            onNextEpisodeBoxAppeared = { onEvent(PlayerEvent.NextEpisodeBoxAppeared) },
            onSettingsClicked = { onEvent(PlayerEvent.OpenSettingsMenu(currentPosition.longValue)) },
            onBackClicked = onBackClicked,
        )
    }
}
