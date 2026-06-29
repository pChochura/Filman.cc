package com.example.filman.ui.player

import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import com.example.filman.ui.components.organisms.PlayerControlsOverlay
import com.example.filman.ui.components.organisms.PlayerSettingsPanel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.ui.core.CollectEffect
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PlayerRoute(
    mediaUrl: String,
    viewModel: PlayerViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(mediaUrl) {
        viewModel.onEvent(PlayerEvent.LoadMedia(mediaUrl))
    }

    CollectEffect(viewModel.effect) {
        // No effects for now
    }

    PlayerScreen(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlayerState,
    onEvent: (PlayerEvent) -> Unit,
) {
    // UI state
    var isOverlayVisible by remember { mutableStateOf(true) }
    var isSettingsVisible by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val playPauseFocusRequester = remember { FocusRequester() }

    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var nextEpisodeDismissed by remember { mutableStateOf(false) }

    val showPopup =
        !nextEpisodeDismissed && duration > 0 && currentPos >= duration - 30_000 && state.hasNextEpisode()
    val popupFocusRequester = remember { FocusRequester() }
    var isPopupFocused by remember { mutableStateOf(false) }
    val settingsButtonFocusRequester = remember { FocusRequester() }

    // Reset dismiss state when media changes
    LaunchedEffect(state.currentMediaUrl) {
        nextEpisodeDismissed = false
    }

    // Format time function
    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val activityContext = LocalContext.current
    DisposableEffect(Unit) {
        val window = (activityContext as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto-hide overlay
    LaunchedEffect(isOverlayVisible, isSettingsVisible, isPlaying, lastInteractionTime) {
        if (isOverlayVisible && !isSettingsVisible && isPlaying) {
            delay(5000.milliseconds)
            isOverlayVisible = false
        }
    }

    LaunchedEffect(isOverlayVisible) {
        if (!isOverlayVisible) {
            focusRequester.requestFocus()
        }
    }

    BackHandler(isSettingsVisible) {
        isSettingsVisible = false
        isOverlayVisible = true
        scope.launch {
            delay(100.milliseconds)
            settingsButtonFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    lastInteractionTime = System.currentTimeMillis()
                    val key = event.key
                    when (key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (!isOverlayVisible && !showPopup && !isSettingsVisible) {
                                isOverlayVisible = true
                                exoPlayer?.pause()
                                scope.launch {
                                    delay(100.milliseconds)
                                    playPauseFocusRequester.requestFocus()
                                }
                                true
                            } else {
                                false
                            }
                        }

                        Key.DirectionLeft -> {
                            if (showPopup && !isSettingsVisible && !isPopupFocused) {
                                popupFocusRequester.requestFocus()
                                true
                            } else if (showPopup && !isSettingsVisible) {
                                false
                            } else if (!isOverlayVisible && !isSettingsVisible) {
                                exoPlayer?.let { player ->
                                    player.seekTo((player.currentPosition - 15000).coerceAtLeast(0))
                                }
                                true
                            } else {
                                false
                            }
                        }

                        Key.DirectionRight -> {
                            if (showPopup && !isSettingsVisible && !isPopupFocused) {
                                popupFocusRequester.requestFocus()
                                true
                            } else if (showPopup && !isSettingsVisible) {
                                false
                            } else if (!isOverlayVisible && !isSettingsVisible) {
                                exoPlayer?.let { player ->
                                    player.seekTo(
                                        (player.currentPosition + 15000).coerceAtMost(
                                            player.duration,
                                        ),
                                    )
                                }
                                true
                            } else {
                                false
                            }
                        }

                        Key.DirectionUp, Key.DirectionDown -> {
                            if (showPopup && !isSettingsVisible && !isPopupFocused) {
                                popupFocusRequester.requestFocus()
                                true
                            } else if (showPopup && !isSettingsVisible) {
                                false
                            } else if (!isOverlayVisible && !isSettingsVisible) {
                                isOverlayVisible = true
                                scope.launch {
                                    delay(100.milliseconds)
                                    playPauseFocusRequester.requestFocus()
                                }
                                true
                            } else {
                                false
                            }
                        }

                        Key.Back, Key.Escape -> {
                            if (isSettingsVisible) {
                                isSettingsVisible = false
                                isOverlayVisible = true
                                scope.launch {
                                    delay(100.milliseconds)
                                    settingsButtonFocusRequester.requestFocus()
                                }
                                true
                            } else if (isOverlayVisible) {
                                isOverlayVisible = false
                                scope.launch {
                                    delay(100.milliseconds)
                                    focusRequester.requestFocus()
                                }
                                true
                            } else {
                                false
                            }
                        }

                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusRequester(focusRequester),
    ) {
        // Player Layer
        if (state.videoUrl != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        // Disable default ExoPlayer controller because we are building our own
                        useController = false

                        val headers = mutableMapOf<String, String>()
                        headers["Referer"] = "https://voe.sx/"
                        headers.putAll(state.videoHeaders)

                        val dataSourceFactory =
                            DefaultHttpDataSource.Factory()
                                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                .setAllowCrossProtocolRedirects(true)
                                .setDefaultRequestProperties(headers)

                        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

                        val newPlayer = ExoPlayer.Builder(ctx)
                            .setMediaSourceFactory(mediaSourceFactory)
                            .build()
                            .apply {
                                setMediaItem(MediaItem.fromUri(state.videoUrl))
                                prepare()
                                // Seek to saved progress if available
                                if (state.initialProgressMs > 0) {
                                    seekTo(state.initialProgressMs)
                                }
                                playWhenReady = true
                            }

                        val forwardingPlayer =
                            object : ForwardingPlayer(newPlayer) {
                                override fun hasNextMediaItem(): Boolean = state.hasNextEpisode()
                                override fun hasPreviousMediaItem(): Boolean =
                                    state.hasPrevEpisode()

                                override fun seekToNextMediaItem() {
                                    if (state.hasNextEpisode()) {
                                        onEvent(PlayerEvent.PlayNextEpisode(false))
                                    }
                                }

                                override fun seekToPreviousMediaItem() {
                                    if (state.hasPrevEpisode()) {
                                        onEvent(PlayerEvent.PlayPrevEpisode)
                                    }
                                }
                            }

                        forwardingPlayer.addListener(
                            object : Player.Listener {
                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    if (playbackState == Player.STATE_ENDED) {
                                        forwardingPlayer.seekToNextMediaItem()
                                    }
                                }

                                override fun onIsPlayingChanged(playing: Boolean) {
                                    isPlaying = playing
                                }
                            },
                        )

                        player = forwardingPlayer
                        exoPlayer = newPlayer
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view ->
                    exoPlayer?.let { player ->
                        onEvent(PlayerEvent.SaveProgress(player.currentPosition, player.duration))
                    }
                    view.player?.release()
                    exoPlayer = null
                },
            )

            // Periodic save coroutine and position update
            LaunchedEffect(exoPlayer, isPlaying) {
                while (exoPlayer != null) {
                    currentPos = exoPlayer?.currentPosition ?: 0L
                    duration = exoPlayer?.duration ?: 0L

                    if (isPlaying && duration > 0) {
                        onEvent(PlayerEvent.SaveProgress(currentPos, duration))
                    }
                    delay(1000.milliseconds)
                }
            }
        }

        // Status Layer
        val errorStringRes: @Composable (PlayerError) -> String = { error ->
            when (error) {
                is PlayerError.NoServers -> stringResource(R.string.error_no_servers)
                is PlayerError.LoadServersFailed -> stringResource(R.string.error_load_servers, error.message)
                is PlayerError.ExtractFailed -> stringResource(R.string.error_extract_failed)
                is PlayerError.DecryptFailed -> stringResource(R.string.error_decrypt_failed)
                is PlayerError.UnsupportedServer -> stringResource(R.string.error_unsupported_server, error.url)
                is PlayerError.Generic -> stringResource(R.string.error_generic, error.message)
            }
        }

        if (state.isFetchingServers) {
            Text(
                text = stringResource(R.string.player_loading_servers),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (state.serverLoadError != null) {
            Text(
                text = errorStringRes(state.serverLoadError),
                color = Color.Red,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (state.isExtracting) {
            Text(
                text = stringResource(
                    R.string.player_extracting_video,
                    state.selectedServer?.serverName ?: "",
                ),
                color = Color.White, modifier = Modifier.align(Alignment.Center),
            )
        } else if (state.errorMessage != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = errorStringRes(state.errorMessage),
                    color = Color.Red,
                    modifier = Modifier.padding(MaterialTheme.spacing.medium),
                )
                Button(onClick = { isSettingsVisible = true }) {
                    Text(stringResource(R.string.player_select_another_server))
                }
            }
        }

        // Custom Controller Overlay
        if (isOverlayVisible && !isSettingsVisible) {
            PlayerControlsOverlay(
                title = state.currentMediaTitle,
                subtitle = if (state.seriesTitle != null && state.getCurrentSeasonName() != null) "${state.seriesTitle} - ${state.getCurrentSeasonName()}" else null,
                isPlaying = isPlaying,
                hasPrev = state.hasPrevEpisode(),
                hasNext = state.hasNextEpisode(),
                currentPos = currentPos,
                duration = duration,
                onPlayPauseToggle = { if (isPlaying) exoPlayer?.pause() else exoPlayer?.play() },
                onPrev = { onEvent(PlayerEvent.PlayPrevEpisode) },
                onNext = { onEvent(PlayerEvent.PlayNextEpisode(true)) },
                onSeek = { seekPos -> exoPlayer?.seekTo(seekPos) },
                onSettingsClick = { isSettingsVisible = true },
                onInteraction = { lastInteractionTime = System.currentTimeMillis() },
                playPauseFocusRequester = playPauseFocusRequester,
                settingsButtonFocusRequester = settingsButtonFocusRequester,
            )
        }

        // Next Episode Popup
        if (showPopup) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 100.dp, end = MaterialTheme.spacing.extraLarge)
                    .background(
                        Color.DarkGray.copy(alpha = 0.9f),
                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    )
                    .onFocusChanged { isPopupFocused = it.hasFocus }
                    .padding(MaterialTheme.spacing.medium),
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.player_next_episode),
                        color = Color.LightGray,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
                    Text(
                        text = stringResource(R.string.player_starting_soon),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                    Row {
                        Button(
                            onClick = { onEvent(PlayerEvent.PlayNextEpisode(true)) },
                            modifier = Modifier.focusRequester(popupFocusRequester),
                        ) {
                            Text(stringResource(R.string.player_play_next))
                        }
                        Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
                        Button(
                            onClick = { nextEpisodeDismissed = true },
                            colors = ButtonDefaults.colors(containerColor = Color.Gray),
                        ) {
                            Text(stringResource(R.string.player_dismiss))
                        }
                    }
                }
            }
        }

        // Settings Side Panel
        if (isSettingsVisible) {
            PlayerSettingsPanel(
                servers = state.servers,
                selectedServer = state.selectedServer,
                onServerSelected = { server ->
                    onEvent(PlayerEvent.SelectServer(server))
                    isSettingsVisible = false
                    isOverlayVisible = true
                    scope.launch {
                        kotlinx.coroutines.delay(100)
                        settingsButtonFocusRequester.requestFocus()
                    }
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
