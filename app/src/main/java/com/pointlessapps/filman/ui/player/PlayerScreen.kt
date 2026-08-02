package com.pointlessapps.filman.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
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
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.pointlessapps.filman.Route
import com.pointlessapps.filman.data.scraper.extractors.Subtitle
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.components.FilmanFullscreenLoader
import com.pointlessapps.filman.ui.components.FilmanOverlayMenu
import com.pointlessapps.filman.ui.core.CollectEffect
import com.pointlessapps.filman.ui.login.PLAYER_PAUSE_SCRIPT
import com.pointlessapps.filman.ui.login.PLAYER_PLAY_SCRIPT
import com.pointlessapps.filman.ui.login.PLAYER_USER_AGENT
import com.pointlessapps.filman.ui.login.getPlayerSeekScript
import com.pointlessapps.filman.ui.login.playerWebChromeClient
import com.pointlessapps.filman.ui.login.playerWebViewClient
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.seconds

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

@OptIn(UnstableApi::class)
@Composable
private fun Player(
    videoUrl: String,
    headers: Map<String, String>,
    subtitles: List<Subtitle>,
    selectedSubtitleUrl: String?,
    startPositionMs: Long,
    isPlaying: Boolean,
    hasNextEpisode: Boolean,
    onNextEpisodeRequested: () -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    onIsBufferingChanged: (Boolean) -> Unit,
    onDurationProvided: (Long) -> Unit,
    onCurrentPositionChanged: (Long) -> Unit,
    onPlayerProvided: (WeakReference<ExoPlayer>) -> Unit,
    onPlayerError: () -> Unit,
) {
    var isReady by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(player, isReady, isPlaying) {
        val player = player
        if (!isReady || player == null) return@LaunchedEffect

        if (!isPlaying) {
            player.pause()

            return@LaunchedEffect
        }

        player.play()
        while (true) {
            if (player.duration != C.TIME_UNSET) {
                onDurationProvided(player.duration)
            }
            onCurrentPositionChanged(player.currentPosition)
            delay(1.seconds)
        }
    }

    DisposableEffect(player, selectedSubtitleUrl) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val trackParamsBuilder = player?.trackSelectionParameters?.buildUpon()

                if (selectedSubtitleUrl == null) {
                    trackParamsBuilder?.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                } else {
                    var foundOverride: TrackSelectionOverride? = null
                    for (group in tracks.groups) {
                        if (group.type == C.TRACK_TYPE_TEXT) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                if (format.id == selectedSubtitleUrl) {
                                    foundOverride = TrackSelectionOverride(group.mediaTrackGroup, i)
                                    break
                                }
                            }
                        }
                        if (foundOverride != null) break
                    }

                    if (foundOverride != null) {
                        trackParamsBuilder?.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        trackParamsBuilder?.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        trackParamsBuilder?.addOverride(foundOverride)
                    } else {
                        trackParamsBuilder?.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    }
                }
                trackParamsBuilder?.build()?.let {
                    player?.trackSelectionParameters = it
                }
            }
        }

        player?.addListener(listener)
        player?.let { listener.onTracksChanged(it.currentTracks) }

        onDispose {
            player?.removeListener(listener)
        }
    }

    val dataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
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
                setKeepContentOnPlayerReset(false)

                dataSourceFactory.setDefaultRequestProperties(headers)
                val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

                val mediaItem = buildMediaItem(videoUrl, subtitles)

                val newPlayer = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build()
                    .apply {
                        addListener(
                            object : Player.Listener {
                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    isReady = playbackState == Player.STATE_READY
                                    onIsBufferingChanged(playbackState == Player.STATE_BUFFERING)

                                    if (playbackState == Player.STATE_ENDED && hasNextEpisode) {
                                        onNextEpisodeRequested()
                                    }
                                }

                                override fun onIsPlayingChanged(isPlaying: Boolean) {
                                    onIsPlayingChanged(isPlaying)
                                }

                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                    super.onPlayerError(error)
                                    onPlayerError()
                                }

                                override fun onVideoSizeChanged(videoSize: VideoSize) {
                                    super.onVideoSizeChanged(videoSize)
                                    requestLayout()
                                    invalidate()
                                }
                            },
                        )

                        setMediaItem(mediaItem)
                        prepare()
                        if (startPositionMs > 0) {
                            seekTo(startPositionMs)
                        }
                        playWhenReady = true

                        onDurationProvided(duration)
                        onPlayerProvided(WeakReference(this))
                    }
                this.player = newPlayer
                player = newPlayer
            }
        },
        update = { view ->
            val currentPlayer = view.player as? ExoPlayer
            val currentUri = currentPlayer?.currentMediaItem?.localConfiguration?.uri?.toString()

            if (currentPlayer != null && currentUri != videoUrl) {
                dataSourceFactory.setDefaultRequestProperties(headers)

                currentPlayer.stop()
                currentPlayer.clearMediaItems()
                currentPlayer.setMediaItem(buildMediaItem(videoUrl, subtitles))
                currentPlayer.prepare()

                if (startPositionMs > 0) {
                    currentPlayer.seekTo(startPositionMs)
                }

                currentPlayer.playWhenReady = true
            }
        },
        onRelease = { view ->
            view.player?.release()
            player = null
        },
    )
}

@OptIn(UnstableApi::class)
private fun buildMediaItem(videoUrl: String, subtitles: List<Subtitle>): MediaItem {
    val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl)
    if (subtitles.isNotEmpty()) {
        val subtitleConfigurations = subtitles.map { subtitle ->
            val mimeType = when {
                subtitle.url.contains("fmt=ttml") || subtitle.url.contains(".ttml") -> MimeTypes.APPLICATION_TTML
                subtitle.url.contains("fmt=vtt") || subtitle.url.contains(".vtt") -> MimeTypes.TEXT_VTT
                subtitle.url.contains(".srt") -> MimeTypes.APPLICATION_SUBRIP
                subtitle.url.contains(".xml") -> MimeTypes.APPLICATION_TTML
                else -> MimeTypes.TEXT_VTT
            }
            MediaItem.SubtitleConfiguration.Builder(subtitle.url.toUri())
                .setId(subtitle.url)
                .setMimeType(mimeType)
                .setLanguage(subtitle.label)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }
        mediaItemBuilder.setSubtitleConfigurations(subtitleConfigurations)
    }
    return mediaItemBuilder.build()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewPlayer(
    videoUrl: String,
    isPlaying: Boolean,
    onIsPlayingChanged: (Boolean) -> Unit,
    onIsBufferingChanged: (Boolean) -> Unit,
    onDurationProvided: (Long) -> Unit,
    onCurrentPositionChanged: (Long) -> Unit,
    onWebViewProvided: (WeakReference<WebView>) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(isPlaying, webView) {
        val webView = webView ?: return@LaunchedEffect
        if (isPlaying) {
            webView.evaluateJavascript(PLAYER_PLAY_SCRIPT, null)
        } else {
            webView.evaluateJavascript(PLAYER_PAUSE_SCRIPT, null)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                isFocusable = false
                isFocusableInTouchMode = false
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.userAgentString = PLAYER_USER_AGENT
                setBackgroundColor(Color.BLACK)

                addJavascriptInterface(
                    object {
                        @Suppress("Unused")
                        @JavascriptInterface
                        fun onTimeUpdate(currentTime: Double, duration: Double) {
                            onIsBufferingChanged(false)
                            onCurrentPositionChanged((currentTime * 1000).toLong())
                            if (!duration.isNaN()) {
                                onDurationProvided((duration * 1000).toLong())
                            }
                        }

                        @Suppress("Unused")
                        @JavascriptInterface
                        fun onPlayStateChanged(playing: Boolean) {
                            onIsPlayingChanged(playing)
                        }

                        @Suppress("Unused")
                        @JavascriptInterface
                        fun onBufferingChanged(buffering: Boolean) {
                            onIsBufferingChanged(buffering)
                        }
                    },
                    "AndroidBridge",
                )

                webChromeClient = playerWebChromeClient()
                webViewClient = playerWebViewClient(videoUrl)

                loadUrl(videoUrl)
                webView = this
                onWebViewProvided(WeakReference(this))
            }
        },
        update = { view ->
            if (view.url != videoUrl) {
                view.loadUrl(videoUrl)
            }
        },
        onRelease = { view ->
            view.destroy()
            webView = null
        },
    )
}
