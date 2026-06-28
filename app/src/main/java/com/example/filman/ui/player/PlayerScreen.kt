package com.example.filman.ui.player

import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
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
                            if (!isOverlayVisible) {
                                isOverlayVisible = true
                                scope.launch {
                                    playPauseFocusRequester.requestFocus()
                                }
                                true
                            } else {
                                false
                            }
                        }

                        Key.DirectionLeft -> {
                            if (!isOverlayVisible) {
                                exoPlayer?.let { player ->
                                    player.seekTo((player.currentPosition - 15000).coerceAtLeast(0))
                                }
                                true
                            } else {
                                false
                            }
                        }

                        Key.DirectionRight -> {
                            if (!isOverlayVisible) {
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
                            if (!isOverlayVisible) {
                                isOverlayVisible = true
                                scope.launch {
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
                                true
                            } else if (isOverlayVisible) {
                                isOverlayVisible = false
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
        if (state.isFetchingServers) {
            Text(
                text = stringResource(R.string.player_loading_servers),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (state.serverLoadError != null) {
            Text(
                text = state.serverLoadError,
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
                    text = state.errorMessage,
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f),
                            ),
                        ),
                    ),
            ) {
                // Top Start (Title)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(MaterialTheme.spacing.extraLarge),
                ) {
                    val seriesTitle = state.seriesTitle
                    val seasonName = state.getCurrentSeasonName()
                    if (seriesTitle != null && seasonName != null) {
                        Text(
                            text = "$seriesTitle - $seasonName",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.LightGray,
                        )
                    }
                    Text(
                        text = state.currentMediaTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                }

                // Middle (Playback Controls)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.hasPrevEpisode()) {
                        Surface(
                            onClick = { onEvent(PlayerEvent.PlayPrevEpisode) },
                            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                            modifier = Modifier.size(64.dp),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "⏮",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                        },
                        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                        modifier = Modifier
                            .size(80.dp)
                            .focusRequester(playPauseFocusRequester),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.DarkGray.copy(
                                alpha = 0.5f,
                            ),
                        ),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (isPlaying) "⏸" else "▶",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineLarge,
                            )
                        }
                    }

                    if (state.hasNextEpisode()) {
                        Surface(
                            onClick = { onEvent(PlayerEvent.PlayNextEpisode(true)) },
                            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                            modifier = Modifier.size(64.dp),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "⏭",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                            }
                        }
                    }
                }

                // Bottom Bar (Progress, Settings)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = MaterialTheme.spacing.extraLarge,
                            end = MaterialTheme.spacing.extraLarge,
                            bottom = MaterialTheme.spacing.extraLarge,
                        ),
                ) {
                    var isProgressBarFocused by remember { mutableStateOf(false) }
                    var isSeeking by remember { mutableStateOf(false) }
                    var seekPos by remember { mutableLongStateOf(0L) }

                    val displayPos = if (isSeeking) seekPos else currentPos
                    val progressRatio =
                        if (duration > 0) {
                            (displayPos.toFloat() / duration.toFloat()).coerceIn(
                                0f,
                                1f,
                            )
                        } else {
                            0f
                        }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .focusable()
                            .onFocusChanged { focusState ->
                                isProgressBarFocused = focusState.isFocused
                                if (!focusState.isFocused && isSeeking) {
                                    isSeeking = false
                                }
                            }
                            .onKeyEvent { event ->
                                lastInteractionTime = System.currentTimeMillis()
                                if (event.type == KeyEventType.KeyDown) {
                                    val repeatCount = event.nativeKeyEvent.repeatCount
                                    val step =
                                        if (repeatCount > 20) 60000L else if (repeatCount > 5) 30000L else 15000L

                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            if (!isSeeking) {
                                                isSeeking = true
                                                seekPos = currentPos
                                            }
                                            seekPos = (seekPos - step).coerceAtLeast(0)
                                            true
                                        }

                                        Key.DirectionRight -> {
                                            if (!isSeeking) {
                                                isSeeking = true
                                                seekPos = currentPos
                                            }
                                            seekPos = (seekPos + step).coerceAtMost(duration)
                                            true
                                        }

                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                            if (isSeeking) {
                                                exoPlayer?.seekTo(seekPos)
                                                isSeeking = false
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
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        val width = maxWidth
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(
                                    if (isProgressBarFocused) {
                                        Color.Gray
                                    } else {
                                        Color.DarkGray
                                    },
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .width(width * progressRatio)
                                .height(4.dp)
                                .background(Color.Red),
                        )
                        if (isProgressBarFocused) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(Color.Red, CircleShape)
                                    .offset(x = (width * progressRatio) - 8.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row {
                            Text(
                                text = formatTime(displayPos),
                                color = if (isSeeking) Color.Red else Color.White,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                            Text(" · ", color = Color.LightGray)
                            Text(
                                text = formatTime(duration),
                                color = Color.LightGray,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                        }

                        Surface(
                            onClick = { isSettingsVisible = true },
                            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.DarkGray.copy(
                                    alpha = 0.5f,
                                ),
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.player_settings),
                                modifier = Modifier.padding(12.dp),
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }

        // Next Episode Popup
        if (!nextEpisodeDismissed && duration > 0 && currentPos >= duration - 30_000 && state.hasNextEpisode()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 100.dp, end = MaterialTheme.spacing.extraLarge)
                    .background(
                        Color.DarkGray.copy(alpha = 0.9f),
                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    )
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
                        Button(onClick = { onEvent(PlayerEvent.PlayNextEpisode(true)) }) {
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
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(350.dp)
                    .background(Color.Black.copy(alpha = 0.9f))
                    .align(Alignment.CenterEnd)
                    .padding(MaterialTheme.spacing.extraLarge),
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.player_select_server),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                        items(state.servers) { server ->
                            Surface(
                                onClick = {
                                    onEvent(PlayerEvent.SelectServer(server))
                                    isSettingsVisible = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (state.selectedServer == server) {
                                        Color.DarkGray
                                    } else {
                                        Color.Transparent
                                    },
                                ),
                            ) {
                                Text(
                                    text = server.serverName,
                                    color = Color.White,
                                    modifier = Modifier.padding(MaterialTheme.spacing.medium),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
