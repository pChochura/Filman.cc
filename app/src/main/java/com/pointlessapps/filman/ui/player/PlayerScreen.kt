package com.pointlessapps.filman.ui.player

import android.app.Activity
import android.view.WindowManager
import android.webkit.WebView
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.pointlessapps.filman.ui.core.CollectEffect
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

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is PlayerEffect.NavigateToAuth -> onNavigateTo(Route.Login())
        }
    }

    AnimatedContent(
        targetState = Triple(
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
        verticalArrangement = Arrangement.spacedBy(
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
    val currentPosition = remember { mutableLongStateOf(state.startPositionMs) }
    var playerReference by remember { mutableStateOf<WeakReference<ExoPlayer>?>(null) }
    var webViewReference by remember { mutableStateOf<WeakReference<WebView>?>(null) }

    val currentUrl = state.detailedMedia?.baseItem?.url
    DisposableEffect(currentUrl) {
        onDispose {
            if (currentUrl != null) {
                onEvent(PlayerEvent.SaveProgress(currentUrl, currentPosition.longValue))
            }
        }
    }

    val activityContext = LocalContext.current
    DisposableEffect(state.isPlaying) {
        val window = (activityContext as? Activity)?.window
        if (state.isPlaying) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(state.isPlaying, currentUrl) {
        if (!state.isPlaying || currentUrl == null) return@LaunchedEffect
        while (true) {
            delay(30.seconds)
            onEvent(PlayerEvent.SaveProgress(currentUrl, currentPosition.longValue))
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
                    onIsPlayingChanged = { onEvent(PlayerEvent.IsPlayingChanged(it)) },
                    onIsBufferingChanged = { onEvent(PlayerEvent.IsBufferingChanged(it)) },
                    onDurationProvided = { onEvent(PlayerEvent.DurationProvided(it)) },
                    onCurrentPositionChanged = { currentPosition.longValue = it },
                    onWebViewProvided = { webViewReference = it },
                    onPlayerError = { onEvent(PlayerEvent.PlayerError) },
                )
            } else {
                Player(
                    videoUrl = url,
                    audioUrl = state.alternativeSources.find { it.url == url }?.audioUrl,
                    headers = state.videoHeaders,
                    subtitles = state.subtitles,
                    selectedSubtitleUrl = state.selectedSubtitleUrl,
                    startPositionMs = state.startPositionMs,
                    playbackSpeed = state.playbackSpeed,
                    aspectRatioMode = state.aspectRatioMode,
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
            onSettingsClicked = {
                onEvent(PlayerEvent.OpenSettingsMenu(currentPosition.longValue, it))
            },
            onBackClicked = onBackClicked,
        )
    }
}
