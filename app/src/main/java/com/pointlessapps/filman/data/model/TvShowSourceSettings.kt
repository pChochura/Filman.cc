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
)

fun MovieItem.getTvShowKey(): String? {
    if (!seriesUrl.isNullOrBlank()) {
        return seriesUrl.normalizeTvShowKey()
    }
    val isEpisode = seasonNumber != null ||
            episodeNumber != null ||
            !seasons.isNullOrEmpty() ||
            nextEpisodeUrl != null ||
            prevEpisodeUrl != null
    if (isEpisode) {
        val title = titlePl.substringBefore(" - ").trim().lowercase()
        if (title.isNotBlank()) {
            return "title:$title"
        }
        return url.normalizeTvShowKey()
    }
    return null
}

fun String?.normalizeTvShowKey(): String? {
    if (this == null) return null
    return this.replace(Regex("^https?://[^/]+"), "")
        .substringBefore("?")
        .substringBefore("#")
        .trimEnd('/')
}
