package com.pointlessapps.filman.ui.player

import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.pointlessapps.filman.config.PLAYER_USER_AGENT
import com.pointlessapps.filman.data.api.createOkHttpClient
import com.pointlessapps.filman.data.api.extractors.Subtitle
import com.pointlessapps.filman.data.model.SubtitleStylePreferences
import com.pointlessapps.filman.ui.core.PlayerConstants
import kotlinx.coroutines.delay
import java.lang.ref.WeakReference

@OptIn(UnstableApi::class)
@Composable
fun Player(
    videoUrl: String,
    audioUrl: String?,
    headers: Map<String, String>,
    subtitles: List<Subtitle>,
    selectedSubtitleUrl: String?,
    selectedAudioTrackId: String? = null,
    startPositionMs: Long,
    playbackSpeed: Float,
    aspectRatioMode: Int,
    subtitleStylePreferences: SubtitleStylePreferences,
    subtitleTimeline: com.pointlessapps.filman.player.lektor.model.SubtitleTimeline? = null,
    isLektorEnabled: Boolean = false,
    showSubtitles: Boolean = true,
    isPlaying: Boolean,
    hasNextEpisode: Boolean,
    autoPlayNextEpisode: Boolean,
    onNextEpisodeRequested: () -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    onIsBufferingChanged: (Boolean) -> Unit,
    onDurationProvided: (Long) -> Unit,
    onCurrentPositionChanged: (Long) -> Unit,
    onAudioTracksChanged: (List<PlayerAudioTrack>) -> Unit = {},
    onEmbeddedSubtitlesDiscovered: (List<Subtitle>) -> Unit = {},
    onPlayerProvided: (WeakReference<ExoPlayer>) -> Unit,
    onPlayerError: () -> Unit,
) {
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var mediaSession by remember { mutableStateOf<MediaSession?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var mySubtitleViewRef by remember { mutableStateOf<SubtitleView?>(null) }
    val context = LocalContext.current
    val currentOnAudioTracksChanged by rememberUpdatedState(onAudioTracksChanged)
    val currentOnEmbeddedSubtitlesDiscovered by rememberUpdatedState(onEmbeddedSubtitlesDiscovered)
    val currentSubtitleTimelineState = rememberUpdatedState(subtitleTimeline)
    val areSubtitlesVisible = selectedSubtitleUrl != null && showSubtitles
    val currentAreSubtitlesVisibleState = rememberUpdatedState(areSubtitlesVisible)

    LaunchedEffect(playbackSpeed) {
        player?.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(aspectRatioMode, playerViewRef) {
        val view = playerViewRef ?: return@LaunchedEffect
        view.resizeMode = aspectRatioToResizeMode(aspectRatioMode)
    }

    LaunchedEffect(player, isPlaying, subtitleTimeline, areSubtitlesVisible, mySubtitleViewRef) {
        val currentPlayer = player ?: return@LaunchedEffect
        var lastRenderedCues: List<com.pointlessapps.filman.player.lektor.model.SubtitleCue>? = null
        try {
            while (true) {
                if (currentPlayer.duration != C.TIME_UNSET) {
                    onDurationProvided(currentPlayer.duration)
                }
                val pos = currentPlayer.currentPosition
                onCurrentPositionChanged(pos)

                // Direct timeline rendering for external subtitles
                val view = mySubtitleViewRef
                if (view != null) {
                    if (subtitleTimeline != null && areSubtitlesVisible) {
                        val active = subtitleTimeline.activeCuesAt(pos)
                        if (active != lastRenderedCues) {
                            lastRenderedCues = active
                            val cues = active.map { cue ->
                                val text: CharSequence = if (cue.text.contains('<')) {
                                    android.text.Html.fromHtml(cue.text, android.text.Html.FROM_HTML_MODE_LEGACY)
                                } else {
                                    cue.text
                                }
                                androidx.media3.common.text.Cue.Builder()
                                    .setText(text)
                                    .build()
                            }
                            view.setCues(cues)
                        }
                    } else if (lastRenderedCues != null || !areSubtitlesVisible || subtitleTimeline == null) {
                        if (lastRenderedCues != null || !areSubtitlesVisible) {
                            lastRenderedCues = null
                            view.setCues(emptyList())
                        }
                    }
                }
                delay(50)
            }
        } finally {
            if (!areSubtitlesVisible || subtitleTimeline == null) {
                mySubtitleViewRef?.setCues(emptyList())
            }
        }
    }

    LaunchedEffect(player, isPlaying) {
        val currentPlayer = player ?: return@LaunchedEffect
        if (!isPlaying) currentPlayer.pause() else currentPlayer.play()
    }

    DisposableEffect(player, selectedSubtitleUrl, selectedAudioTrackId, subtitleTimeline != null) {
        val currentPlayer = player
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                currentPlayer?.applySelectedTracks(
                    tracks = tracks,
                    context = context,
                    subtitles = subtitles,
                    selectedSubtitleUrl = selectedSubtitleUrl,
                    selectedAudioTrackId = selectedAudioTrackId,
                    isLektorEnabled = isLektorEnabled,
                    hasActiveTimeline = subtitleTimeline != null,
                    onAudioTracksChanged = currentOnAudioTracksChanged,
                    onEmbeddedSubtitlesChanged = currentOnEmbeddedSubtitlesDiscovered,
                )
            }
        }

        currentPlayer?.addListener(listener)
        currentPlayer?.let { listener.onTracksChanged(it.currentTracks) }

        onDispose {
            currentPlayer?.removeListener(listener)
        }
    }

    val dataSourceFactory = remember {
        OkHttpDataSource.Factory(createOkHttpClient()).setUserAgent(PLAYER_USER_AGENT)
    }

    val subtitleTextColor = MaterialTheme.colorScheme.background.toArgb()
    val subtitleShadowColor = MaterialTheme.colorScheme.onBackground.toArgb()

    val fontResolver = LocalFontFamilyResolver.current
    val fontFamily = MaterialTheme.typography.bodyLarge.fontFamily
    val typeface = remember(fontResolver, fontFamily) {
        fontResolver.resolve(fontFamily = fontFamily, fontWeight = FontWeight.Bold).value as Typeface
    }

    LaunchedEffect(areSubtitlesVisible, mySubtitleViewRef) {
        val view = mySubtitleViewRef ?: return@LaunchedEffect
        view.visibility = if (areSubtitlesVisible) View.VISIBLE else View.GONE
        if (!areSubtitlesVisible) {
            view.setCues(emptyList())
        }
    }

    LaunchedEffect(subtitleStylePreferences, mySubtitleViewRef) {
        mySubtitleViewRef?.applySubtitleStylePreferences(subtitleStylePreferences, typeface)
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                playerViewRef = this
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = false
                setKeepContentOnPlayerReset(false)

                subtitleView?.visibility = View.GONE
                subtitleView?.alpha = 0f

                val mySubtitleView = SubtitleView(viewContext).apply {
                    visibility = if (areSubtitlesVisible) View.VISIBLE else View.GONE
                    setApplyEmbeddedStyles(false)
                    setApplyEmbeddedFontSizes(false)
                    setStyle(
                        CaptionStyleCompat(
                            subtitleTextColor,
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                            subtitleShadowColor,
                            typeface,
                        ),
                    )
                    setBottomPaddingFraction(0.05f)
                }
                mySubtitleViewRef = mySubtitleView
                addView(
                    mySubtitleView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )

                dataSourceFactory.setDefaultRequestProperties(headers)
                val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()

                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        30_000,
                        60_000,
                        2_500,
                        5_000,
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()

                val newPlayer = ExoPlayer.Builder(viewContext)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setLoadControl(loadControl)
                    .setAudioAttributes(audioAttributes, true)
                    .build()
                    .apply {
                        addListener(
                            object : Player.Listener {
                                override fun onCues(cueGroup: CueGroup) {
                                    // Only use ExoPlayer's built-in cues if we are not directly rendering a SubtitleTimeline
                                    if (currentSubtitleTimelineState.value == null) {
                                        if (currentAreSubtitlesVisibleState.value) {
                                            mySubtitleView.setCues(cueGroup.toCleanCues())
                                        } else {
                                            mySubtitleView.setCues(emptyList())
                                        }
                                    }
                                }

                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    onIsBufferingChanged(playbackState == Player.STATE_BUFFERING)
                                    if (playbackState == Player.STATE_ENDED &&
                                        hasNextEpisode && autoPlayNextEpisode
                                    ) {
                                        onNextEpisodeRequested()
                                    }
                                }

                                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) =
                                    onIsPlayingChanged(playWhenReady)

                                override fun onPlayerError(error: PlaybackException) = onPlayerError()

                                override fun onVideoSizeChanged(videoSize: VideoSize) {
                                    requestLayout()
                                    invalidate()
                                }
                            },
                        )

                        setMediaSource(
                            buildMediaSource(
                                videoUrl = videoUrl,
                                audioUrl = audioUrl,
                                subtitles = subtitles,
                                mediaSourceFactory = mediaSourceFactory,
                            ),
                        )
                        prepare()
                        if (startPositionMs > 0) seekTo(startPositionMs)
                        setPlaybackSpeed(playbackSpeed)
                        playWhenReady = true

                        onDurationProvided(duration)
                        onPlayerProvided(WeakReference(this))
                    }
                this.player = newPlayer
                player = newPlayer
                mediaSession = MediaSession.Builder(viewContext, newPlayer).build()
            }
        },
        update = { view ->
            view.resizeMode = aspectRatioToResizeMode(aspectRatioMode)

            val currentPlayer = view.player as? ExoPlayer
            val currentUri = currentPlayer?.currentMediaItem?.localConfiguration?.uri?.toString()
            val currentSubtitles = currentPlayer?.currentMediaItem?.localConfiguration?.subtitleConfigurations
                ?.map { it.uri.toString() } ?: emptyList()
            val newSubtitles = subtitles.map { it.url }

            if (currentPlayer != null && (currentUri != videoUrl || currentSubtitles != newSubtitles)) {
                val currentPosition =
                    if (currentUri == videoUrl) currentPlayer.currentPosition else startPositionMs
                val playWhenReady = currentPlayer.playWhenReady

                dataSourceFactory.setDefaultRequestProperties(headers)
                val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

                currentPlayer.stop()
                currentPlayer.clearMediaItems()
                currentPlayer.setMediaSource(
                    buildMediaSource(
                        videoUrl = videoUrl,
                        audioUrl = audioUrl,
                        subtitles = subtitles,
                        mediaSourceFactory = mediaSourceFactory,
                    ),
                )
                currentPlayer.prepare()
                if (currentPosition > 0) currentPlayer.seekTo(currentPosition)
                currentPlayer.setPlaybackSpeed(playbackSpeed)
                currentPlayer.playWhenReady = playWhenReady
            }
        },
        onRelease = { view ->
            mediaSession?.release()
            mediaSession = null
            view.player?.release()
            player = null
        },
    )
}

@OptIn(UnstableApi::class)
private fun aspectRatioToResizeMode(aspectRatioMode: Int): Int = when (aspectRatioMode) {
    PlayerConstants.AspectRatio.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    PlayerConstants.AspectRatio.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    PlayerConstants.AspectRatio.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}
