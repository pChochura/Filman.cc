package com.pointlessapps.filman.ui.player

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks

internal fun subtitleTrackOverride(
    tracks: Tracks,
    subtitleUrl: String,
    language: String?,
): TrackSelectionOverride? {
    var languageFallback: TrackSelectionOverride? = null
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        for (index in 0 until group.length) {
            val format = group.getTrackFormat(index)
            if (format.id == subtitleUrl) return TrackSelectionOverride(group.mediaTrackGroup, index)
            if (languageFallback == null && language != null && format.language == language) {
                languageFallback = TrackSelectionOverride(group.mediaTrackGroup, index)
            }
        }
    }
    return languageFallback
}
