package com.pointlessapps.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class TvShowSourceSettings(
    val serverName: String? = null,
    val version: String? = null,
    val quality: String? = null,
    val sourceWebsite: String? = null,
    val subtitlesEnabled: Boolean = false,
    val subtitleLanguage: String? = null,
    val subtitleLabel: String? = null,
    val playbackSpeed: Float? = null,
    val aspectRatioMode: Int? = null,
    val lastKnownEpisodeCount: Int? = null,
)

fun MovieItem.getTvShowKey(): String? {
    val explicitSeriesRef = MediaRef.parse(seriesUrl, isTvShowHint = true)
    if (explicitSeriesRef != null) {
        return explicitSeriesRef.parentRef.canonicalKey
    }
    val itemRef = MediaRef.parse(url, isTvShowHint = isTvShow || seasonNumber != null || episodeNumber != null || !seasons.isNullOrEmpty() || nextEpisodeUrl != null || prevEpisodeUrl != null)
    if (itemRef != null && itemRef.isTvShow) {
        return itemRef.parentRef.canonicalKey
    }
    return null
}

fun String?.normalizeTvShowKey(): String? {
    if (this == null) return null
    return MediaRef.parse(this, isTvShowHint = true)?.parentRef?.canonicalKey
}
