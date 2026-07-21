package com.example.filman.ui.player

import android.app.Activity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.filman.Route
import com.example.filman.ui.base.BaseEvent
import com.example.filman.ui.components.FilmanFullscreenLoader
import com.example.filman.ui.components.FilmanOverlayMenu
import com.example.filman.ui.core.CollectEffect
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun PlayerScreen(
    url: String,
    onNavigateTo: (Route) -> Unit,
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
            is PlayerEffect.NavigateToAuth -> onNavigateTo(Route.Auth)
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
) {
    val currentPosition = remember { mutableLongStateOf(0) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        state.videoUrl?.let { url ->
            Player(
                videoUrl = url,
                headers = state.videoHeaders,
                isPlaying = state.isPlaying,
                onIsPlayingChanged = { onEvent(PlayerEvent.IsPlayingChanged(it)) },
                onDurationProvided = { onEvent(PlayerEvent.DurationProvided(it)) },
                onCurrentPositionChanged = { currentPosition.longValue = it },
            )
        }

        PlayerControls(
            detailedMedia = state.detailedMedia,
            duration = state.duration,
            currentPositionProvider = currentPosition::longValue,
            currentSeason = 1,
            currentEpisode = 3,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Player(
    videoUrl: String,
    headers: Map<String, String>,
    isPlaying: Boolean,
    onIsPlayingChanged: (Boolean) -> Unit,
    onDurationProvided: (Long) -> Unit,
    onCurrentPositionChanged: (Long) -> Unit,
) {
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(player, isPlaying) {
        player?.let { player ->
            while (true) {
                if (player.duration != C.TIME_UNSET) {
                    onDurationProvided(player.duration)
                }
                onCurrentPositionChanged(player.currentPosition)
                delay(1.seconds)
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            PlayerView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )

                useController = false

                val dataSourceFactory =
                    DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true)
                        .setDefaultRequestProperties(headers)

                val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

                player = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build()
                    .apply {
                        addListener(
                            object : Player.Listener {
                                override fun onIsPlayingChanged(isPlaying: Boolean) {
                                    onIsPlayingChanged(isPlaying)
                                }
                            },
                        )

                        setMediaItem(MediaItem.fromUri(videoUrl))
                        prepare()
                        playWhenReady = true
                        onDurationProvided(duration)
                    }

                this.player = player
            }
        },
        onRelease = { view -> view.player?.release() },
    )
}
