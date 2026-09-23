package com.pointlessapps.filman.ui.player
import com.pointlessapps.filman.ui.core.PlayerConstants

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
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
import androidx.media3.common.AudioAttributes
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
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.SubtitleStylePreferences
import com.pointlessapps.filman.data.scraper.extractors.Subtitle
import com.pointlessapps.filman.data.scraper.getUnsafeOkHttpClient
import com.pointlessapps.filman.config.PLAYER_USER_AGENT
import kotlinx.coroutines.delay
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

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

internal fun getAudioTrackId(
    group: Tracks.Group,
    trackIndex: Int,
): String {
    val format = group.getTrackFormat(trackIndex)
    return format.id?.takeIf { it.isNotBlank() } ?: "${group.mediaTrackGroup.id}:$trackIndex"
}

internal fun getAudioTrackLabel(
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
