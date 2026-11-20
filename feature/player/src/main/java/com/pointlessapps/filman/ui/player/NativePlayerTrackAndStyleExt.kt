package com.pointlessapps.filman.ui.player

import android.content.Context
import com.pointlessapps.filman.core.ui.R
import android.graphics.Color
import android.graphics.Typeface
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.pointlessapps.filman.data.api.extractors.Subtitle
import com.pointlessapps.filman.data.model.SubtitleStylePreferences

@OptIn(UnstableApi::class)
internal fun ExoPlayer.applySelectedTracks(
    tracks: Tracks,
    context: Context,
    subtitles: List<Subtitle>,
    selectedSubtitleUrl: String?,
    selectedAudioTrackId: String?,
    isLektorEnabled: Boolean,
    hasActiveTimeline: Boolean = false,
    onAudioTracksChanged: (List<PlayerAudioTrack>) -> Unit,
    onEmbeddedSubtitlesChanged: (List<Subtitle>) -> Unit = {},
) {
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
        onAudioTracksChanged(extractedAudioTracks)
    }

    // Extract embedded text tracks
    val embeddedSubs = mutableListOf<Subtitle>()
    tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEachIndexed { groupIdx, group ->
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            val id = format.id ?: "embedded_$groupIdx:$i"
            val lang = format.language?.takeIf { it != "und" } ?: "und"
            val label = format.label?.takeIf { it.isNotBlank() } ?: if (lang != "und") {
                context.getString(R.string.player_subtitle_embedded, lang)
            } else {
                context.getString(R.string.player_subtitle_embedded_unknown)
            }
            embeddedSubs.add(Subtitle(url = id, label = label, language = lang))
        }
    }
    if (embeddedSubs.isNotEmpty()) {
        onEmbeddedSubtitlesChanged(embeddedSubs)
    }

    val trackParamsBuilder = trackSelectionParameters.buildUpon()

    if (selectedSubtitleUrl == null || hasActiveTimeline) {
        trackParamsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        trackParamsBuilder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
    } else {
        val selectedSubtitleLanguage = subtitles.find { it.url == selectedSubtitleUrl }?.language
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
                .build(),
        )
    }

    val newParams = trackParamsBuilder.build()
    if (trackSelectionParameters != newParams) {
        trackSelectionParameters = newParams
    }
}

@OptIn(UnstableApi::class)
internal fun SubtitleView.applySubtitleStylePreferences(
    prefs: SubtitleStylePreferences,
    typeface: Typeface,
) {
    setStyle(
        CaptionStyleCompat(
            prefs.textColorArgb,
            prefs.backgroundColorArgb,
            Color.TRANSPARENT,
            prefs.edgeType,
            prefs.edgeColorArgb,
            typeface,
        ),
    )
    setFractionalTextSize(prefs.fontSizeFraction)
    setBottomPaddingFraction(prefs.verticalPaddingFraction)
}

@OptIn(UnstableApi::class)
internal fun CueGroup.toCleanCues(): List<Cue> = cues.map { cue ->
    cue
        .buildUpon()
        .clearWindowColor()
        .setPosition(Cue.DIMEN_UNSET)
        .setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
        .setSize(Cue.DIMEN_UNSET)
        .build()
}
