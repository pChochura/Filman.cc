package com.pointlessapps.filman.data.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
enum class MediaType {
    MOVIE,
    TV,
}

@Serializable
@Parcelize
@Immutable
data class MediaRef(
    val type: MediaType,
    val tmdbId: Int,
    val season: Int? = null,
    val episode: Int? = null,
) : Parcelable {
    val isTvShow: Boolean get() = type == MediaType.TV
    val isEpisode: Boolean get() = type == MediaType.TV && season != null && episode != null
    val parentRef: MediaRef get() = if (isEpisode) copy(season = null, episode = null) else this

    val canonicalKey: String
        get() = when {
            type == MediaType.MOVIE -> "movie_$tmdbId"
            season != null && episode != null -> "show_${tmdbId}_${season}_${episode}"
            else -> "show_$tmdbId"
        }

    companion object {
        fun parse(raw: String?, isTvShowHint: Boolean? = null): MediaRef? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.contains("/")) {
                return null
            }
            val explicitlyTv = when {
                trimmed.startsWith("show_") -> true
                trimmed.startsWith("movie_") -> false
                else -> null
            }
            val clean = trimmed
                .removePrefix("search_")
                .removePrefix("show_")
                .removePrefix("movie_")
            val parts = clean.split("_")
            val id = parts.firstOrNull()?.toIntOrNull()?.takeIf { it > 0 } ?: return null
            val season = parts.getOrNull(1)?.toIntOrNull()
            val episode = parts.getOrNull(2)?.toIntOrNull()
            val isTv = explicitlyTv ?: isTvShowHint ?: (season != null && episode != null)
            return if (isTv) {
                MediaRef(
                    type = MediaType.TV,
                    tmdbId = id,
                    season = season,
                    episode = episode,
                )
            } else {
                MediaRef(type = MediaType.MOVIE, tmdbId = id)
            }
        }
    }
}
