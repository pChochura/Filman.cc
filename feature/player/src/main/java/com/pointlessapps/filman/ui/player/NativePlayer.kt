package com.pointlessapps.filman.ui.player
import com.pointlessapps.filman.ui.core.PlayerConstants

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
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
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.SubtitleStylePreferences
import com.pointlessapps.filman.data.api.extractors.Subtitle
import com.pointlessapps.filman.data.api.createOkHttpClient
import com.pointlessapps.filman.config.PLAYER_USER_AGENT
import kotlinx.coroutines.delay
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

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
    onPlayerProvided: (WeakReference<ExoPlayer>) -> Unit,
    onPlayerError: () -> Unit,
) {
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var mediaSession by remember { mutableStateOf<MediaSession?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var mySubtitleViewRef by remember { mutableStateOf<SubtitleView?>(null) }
    val context = LocalContext.current
    val currentOnAudioTracksChanged by rememberUpdatedState(onAudioTracksChanged)

    LaunchedEffect(playbackSpeed) {
        player?.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(aspectRatioMode, playerViewRef) {

        val view = playerViewRef ?: return@LaunchedEffect
        view.resizeMode =
            when (aspectRatioMode) {
                PlayerConstants.AspectRatio.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                PlayerConstants.AspectRatio.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                PlayerConstants.AspectRatio.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
    }

    LaunchedEffect(player, isPlaying) {
        val player = player ?: return@LaunchedEffect
        while (true) {
            if (player.duration != C.TIME_UNSET) {
                onDurationProvided(player.duration)
            }
            val pos = player.currentPosition
            onCurrentPositionChanged(pos)
            delay(100)
        }
    }

    LaunchedEffect(player, isPlaying) {
        val player = player ?: return@LaunchedEffect

        if (!isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    DisposableEffect(player, selectedSubtitleUrl, selectedAudioTrackId) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    val audioTracks = audioTrackReferences(tracks)
                    val extractedAudioTracks = audioTracks.mapIndexed { trackNumber, track ->
                        val format = track.group.getTrackFormat(track.trackIndex)
                        PlayerAudioTrack(
                            id = track.id,
                            label = getAudioTrackLabel(context, format, trackNumber),
                            language = format.language?.takeIf { it != "und" },
                            isSelected = track.group.isTrackSelected(track.trackIndex),
                        )
                    }
                    if (extractedAudioTracks.isNotEmpty()) {
                        currentOnAudioTracksChanged(extractedAudioTracks)
                    }

                    val trackParamsBuilder = player?.trackSelectionParameters?.buildUpon() ?: return

                    if (selectedSubtitleUrl == null) {
                        trackParamsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    } else {
                        val selectedSubtitleLanguage =
                            subtitles.find { it.url == selectedSubtitleUrl }?.language
                        val selectedOverride = subtitleTrackOverride(tracks, selectedSubtitleUrl, selectedSubtitleLanguage)

                        trackParamsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        trackParamsBuilder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        if (selectedOverride != null) {
                            trackParamsBuilder.addOverride(selectedOverride)
                        }
                    }

                    if (selectedAudioTrackId != null) {
                        val selectedTrack = audioTracks.find { it.id == selectedAudioTrackId }
                        val foundAudioOverride = selectedTrack?.let {
                            TrackSelectionOverride(it.group.mediaTrackGroup, it.trackIndex)
                        }

                        if (foundAudioOverride != null) {
                            trackParamsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            trackParamsBuilder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            trackParamsBuilder.addOverride(foundAudioOverride)
                        }
                    } else {
                        trackParamsBuilder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        trackParamsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    }

                    if (isLektorEnabled) {
                        trackParamsBuilder.setAudioOffloadPreferences(
                            TrackSelectionParameters.AudioOffloadPreferences.DEFAULT.buildUpon()
                                .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                                .build()
                        )
                    }

                    val newParams = trackParamsBuilder.build()
                    if (player?.trackSelectionParameters != newParams) {
                        player?.trackSelectionParameters = newParams
                    }
                }
            }

        player?.addListener(listener)
        player?.let { listener.onTracksChanged(it.currentTracks) }

        onDispose {
            player?.removeListener(listener)
        }
    }

    val dataSourceFactory =
        remember {
            OkHttpDataSource
                .Factory(createOkHttpClient())
                .setUserAgent(PLAYER_USER_AGENT)
        }

    val subtitleTextColor = MaterialTheme.colorScheme.background.toArgb()
    val subtitleShadowColor = MaterialTheme.colorScheme.onBackground.toArgb()

    val fontResolver = LocalFontFamilyResolver.current
    val fontFamily = MaterialTheme.typography.bodyLarge.fontFamily
    val typeface =
        remember(fontResolver, fontFamily) {
            fontResolver
                .resolve(
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                ).value as Typeface
        }

    LaunchedEffect(showSubtitles, mySubtitleViewRef) {
        mySubtitleViewRef?.visibility = if (showSubtitles) View.VISIBLE else View.GONE
    }

    LaunchedEffect(subtitleStylePreferences, mySubtitleViewRef) {
        val subtitleView = mySubtitleViewRef ?: return@LaunchedEffect
        subtitleView.setStyle(
            CaptionStyleCompat(
                subtitleStylePreferences.textColorArgb,
                subtitleStylePreferences.backgroundColorArgb,
                Color.TRANSPARENT,
                subtitleStylePreferences.edgeType,
                subtitleStylePreferences.edgeColorArgb,
                typeface,
            ),
        )
        subtitleView.setFractionalTextSize(
            subtitleStylePreferences.fontSizeFraction,
        )
        subtitleView.setBottomPaddingFraction(subtitleStylePreferences.verticalPaddingFraction)
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            PlayerView(context).apply {
                playerViewRef = this
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )

                useController = false
                setKeepContentOnPlayerReset(false)

                subtitleView?.visibility = View.GONE
                subtitleView?.alpha = 0f

                val mySubtitleView =
                    SubtitleView(context).apply {
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

                val newPlayer =
                    ExoPlayer
                        .Builder(context)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .setAudioAttributes(audioAttributes, true)
                        .build()
                        .apply {
                            addListener(
                                object : Player.Listener {
                                    override fun onCues(cueGroup: CueGroup) {
                                        val modifiedCues =
                                            cueGroup.cues.map { cue ->
                                                cue
                                                    .buildUpon()
                                                    .clearWindowColor()
                                                    .setPosition(Cue.DIMEN_UNSET)
                                                    .setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
                                                    .setSize(Cue.DIMEN_UNSET)
                                                    .build()
                                            }
                                        mySubtitleView.setCues(modifiedCues)
                                    }

                                    override fun onPlaybackStateChanged(playbackState: Int) {
                                        onIsBufferingChanged(playbackState == Player.STATE_BUFFERING)

                                        if (
                                            playbackState == Player.STATE_ENDED &&
                                            hasNextEpisode && autoPlayNextEpisode
                                        ) {
                                            onNextEpisodeRequested()
                                        }
                                    }

                                    override fun onPlayWhenReadyChanged(
                                        playWhenReady: Boolean,
                                        reason: Int,
                                    ) = onIsPlayingChanged(playWhenReady)

                                    override fun onPlayerError(error: PlaybackException) =
                                        onPlayerError()

                                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                                        requestLayout()
                                        invalidate()
                                    }
                                },
                            )

                            val mediaSource =
                                buildMediaSource(
                                    videoUrl = videoUrl,
                                    audioUrl = audioUrl,
                                    subtitles = subtitles,
                                    mediaSourceFactory = mediaSourceFactory,
                                )
                            setMediaSource(mediaSource)

                            prepare()
                            if (startPositionMs > 0) {
                                seekTo(startPositionMs)
                            }
                            setPlaybackSpeed(playbackSpeed)
                            // videoScalingMode is managed dynamically via aspectRatioMode
                            playWhenReady = true

                            onDurationProvided(duration)
                            onPlayerProvided(WeakReference(this))
                        }
                this.player = newPlayer
                player = newPlayer
                mediaSession = MediaSession.Builder(context, newPlayer).build()
            }
        },
        update = { view ->
            view.resizeMode =
                when (aspectRatioMode) {
                    PlayerConstants.AspectRatio.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    PlayerConstants.AspectRatio.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    PlayerConstants.AspectRatio.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }

            val currentPlayer = view.player as? ExoPlayer
            val currentUri =
                currentPlayer
                    ?.currentMediaItem
                    ?.localConfiguration
                    ?.uri
                    ?.toString()
            val currentSubtitles =
                currentPlayer
                    ?.currentMediaItem
                    ?.localConfiguration
                    ?.subtitleConfigurations
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
                val mediaSource =
                    buildMediaSource(
                        videoUrl = videoUrl,
                        audioUrl = audioUrl,
                        subtitles = subtitles,
                        mediaSourceFactory = mediaSourceFactory,
                    )
                currentPlayer.setMediaSource(mediaSource)
                currentPlayer.prepare()

                if (currentPosition > 0) {
                    currentPlayer.seekTo(currentPosition)
                }

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
