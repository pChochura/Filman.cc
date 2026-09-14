package com.pointlessapps.filman.ui.player

import android.content.Context
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
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.pointlessapps.filman.R
import com.pointlessapps.filman.data.scraper.extractors.Subtitle
import com.pointlessapps.filman.getUnsafeOkHttpClient
import com.pointlessapps.filman.ui.login.PLAYER_USER_AGENT
import kotlinx.coroutines.delay
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@OptIn(UnstableApi::class)
@Composable
internal fun Player(
    videoUrl: String,
    audioUrl: String?,
    headers: Map<String, String>,
    subtitles: List<Subtitle>,
    selectedSubtitleUrl: String?,
    selectedAudioTrackId: String? = null,
    startPositionMs: Long,
    playbackSpeed: Float,
    aspectRatioMode: Int,
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
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
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

    LaunchedEffect(player) {
        val player = player ?: return@LaunchedEffect
        while (true) {
            if (player.duration != C.TIME_UNSET) {
                onDurationProvided(player.duration)
            }
            onCurrentPositionChanged(player.currentPosition)
            delay(1.seconds)
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
                    val extractedAudioTracks = mutableListOf<PlayerAudioTrack>()
                    var trackNumber = 0
                    for (group in tracks.groups) {
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val trackId = getAudioTrackId(group, i)
                                val label = getAudioTrackLabel(context, format, trackNumber)
                                val isSelected = group.isTrackSelected(i)
                                extractedAudioTracks.add(
                                    PlayerAudioTrack(
                                        id = trackId,
                                        label = label,
                                        language = format.language?.takeIf { it != "und" },
                                        isSelected = isSelected,
                                    ),
                                )
                                trackNumber++
                            }
                        }
                    }
                    if (extractedAudioTracks.isNotEmpty()) {
                        currentOnAudioTracksChanged(extractedAudioTracks)
                    }

                    val trackParamsBuilder = player?.trackSelectionParameters?.buildUpon() ?: return

                    if (selectedSubtitleUrl == null) {
                        trackParamsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    } else {
                        var foundOverride: TrackSelectionOverride? = null
                        val selectedSubtitleLanguage =
                            subtitles.find { it.url == selectedSubtitleUrl }?.language

                        for (group in tracks.groups) {
                            if (group.type == C.TRACK_TYPE_TEXT) {
                                for (i in 0 until group.length) {
                                    val format = group.getTrackFormat(i)
                                    if (format.id == selectedSubtitleUrl || format.language == selectedSubtitleLanguage) {
                                        foundOverride = TrackSelectionOverride(group.mediaTrackGroup, i)
                                        break
                                    }
                                }
                            }
                            if (foundOverride != null) break
                        }

                        trackParamsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        trackParamsBuilder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        if (foundOverride != null) {
                            trackParamsBuilder.addOverride(foundOverride)
                        }
                    }

                    if (selectedAudioTrackId != null) {
                        var foundAudioOverride: TrackSelectionOverride? = null
                        for (group in tracks.groups) {
                            if (group.type == C.TRACK_TYPE_AUDIO) {
                                for (i in 0 until group.length) {
                                    val trackId = getAudioTrackId(group, i)
                                    if (trackId == selectedAudioTrackId) {
                                        foundAudioOverride = TrackSelectionOverride(group.mediaTrackGroup, i)
                                        break
                                    }
                                }
                            }
                            if (foundAudioOverride != null) break
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
                .Factory(getUnsafeOkHttpClient())
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
                addView(
                    mySubtitleView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )

                dataSourceFactory.setDefaultRequestProperties(headers)
                val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

                val newPlayer =
                    ExoPlayer
                        .Builder(context)
                        .setMediaSourceFactory(mediaSourceFactory)
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

                                        if (playbackState == Player.STATE_ENDED && hasNextEpisode && autoPlayNextEpisode) {
                                            onNextEpisodeRequested()
                                        }
                                    }

                                    override fun onPlayWhenReadyChanged(
                                        playWhenReady: Boolean,
                                        reason: Int,
                                    ) = onIsPlayingChanged(playWhenReady)

                                    override fun onPlayerError(error: PlaybackException) = onPlayerError()

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
                val currentPosition = if (currentUri == videoUrl) currentPlayer.currentPosition else startPositionMs
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
            view.player?.release()
            player = null
        },
    )
}

@OptIn(UnstableApi::class)
internal fun buildMediaSource(
    videoUrl: String,
    audioUrl: String?,
    subtitles: List<Subtitle>,
    mediaSourceFactory: DefaultMediaSourceFactory,
): MediaSource {
    val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl)
    if (subtitles.isNotEmpty()) {
        mediaItemBuilder.setSubtitleConfigurations(
            subtitles.map { subtitle ->
                MediaItem.SubtitleConfiguration
                    .Builder(subtitle.url.toUri())
                    .setId(subtitle.url)
                    .setMimeType(getMimeType(subtitle.url))
                    .setLanguage(subtitle.language)
                    .setLabel(subtitle.label)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            },
        )
    }

    if (audioUrl == null) {
        return mediaSourceFactory.createMediaSource(mediaItemBuilder.build())
    }

    val sources = mutableListOf<MediaSource>()
    sources.add(mediaSourceFactory.createMediaSource(mediaItemBuilder.build()))
    sources.add(mediaSourceFactory.createMediaSource(MediaItem.fromUri(audioUrl)))

    return MergingMediaSource(true, *sources.toTypedArray())
}

internal fun getMimeType(url: String) =
    when {
        url.contains("fmt=ttml") || url.contains(".ttml") || url.contains(".xml") -> MimeTypes.APPLICATION_TTML
        url.contains("fmt=vtt") || url.contains(".vtt") -> MimeTypes.TEXT_VTT
        url.contains(".srt") -> MimeTypes.APPLICATION_SUBRIP
        else -> MimeTypes.TEXT_VTT
    }

private fun getAudioTrackId(
    group: Tracks.Group,
    trackIndex: Int,
): String {
    val format = group.getTrackFormat(trackIndex)
    return format.id?.takeIf { it.isNotBlank() } ?: "${group.mediaTrackGroup.id}:$trackIndex"
}

private fun getAudioTrackLabel(
    context: Context,
    format: Format,
    trackIndex: Int,
): String {
    val rawLabel = format.label?.trim()
    if (!rawLabel.isNullOrBlank()) {
        return rawLabel
    }

    val lang = format.language?.trim()
    if (!lang.isNullOrBlank() && lang != "und") {
        val locale = Locale.forLanguageTag(lang)
        val displayLang = locale.getDisplayLanguage(Locale.getDefault())
        if (displayLang.isNotBlank()) {
            val capitalized =
                displayLang.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            val channelInfo =
                when (format.channelCount) {
                    6 -> " (5.1)"
                    8 -> " (7.1)"
                    2 -> " (Stereo)"
                    else -> ""
                }
            return "$capitalized$channelInfo"
        }
    }

    return context.getString(R.string.player_audio_track_default, trackIndex + 1)
}
