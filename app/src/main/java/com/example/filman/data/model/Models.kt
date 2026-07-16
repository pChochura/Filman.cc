package com.example.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class MovieItem(
    val url: String,
    val titlePl: String,
    val titleEn: String? = null,
    val filmanRating: Rating? = null,
    val imdbRating: Rating? = null,
    val posterUrl: String,
    val backgroundUrl: String? = null,
    val description: String = "",
    val routeToken: String? = null,
    val seriesUrl: String? = null,
    val seasons: List<Season>? = null,
)

@Serializable
@Immutable
data class SearchResults(
    val movies: List<MovieItem> = emptyList(),
    val tvShows: List<MovieItem> = emptyList(),
)

@Serializable
@Immutable
data class EmbedLink(
    val url: String,
    val serverName: String,
    val version: String = "",
    val quality: String = "",
)

@Serializable
@Immutable
data class Rating(
    val score: Float,
    val maxValue: Float,
)

@Serializable
@Immutable
data class EpisodeLink(
    val title: String,
    val url: String,
)

@Serializable
@Immutable
data class Season(
    val name: String,
    val episodes: List<EpisodeLink>,
)

@Serializable
@Immutable
data class ProgressItem(
    val url: String,
    val titlePl: String,
    val posterUrl: String,
    val progressMs: Long,
    val durationMs: Long,
    val seriesTitle: String? = null,
    val seriesUrl: String? = null,
) {
    val progressPercentage: Float
        get() = if (durationMs > 0) (progressMs.toFloat() / durationMs.toFloat()) else 0f

    val seasonEpisode: String?
        get() = seasonEpisodeRegex.find(titlePl)?.let {
            "S${it.groupValues[1]}E${it.groupValues[2]}"
        }
}

@Serializable
@Immutable
data class CategoryInfo(val name: String, val url: String)

@Serializable
@Immutable
data class TagInfo(val name: String, val url: String)

@Serializable
@Immutable
data class MediaMetadata(
    val year: Int?,
    val views: Int?,
    val duration: kotlin.time.Duration?,
    val countries: List<String>,
)

enum class ActorRole {
    DIRECTOR,
    WRITER,
    ACTOR,
    UNKNOWN
}

@Serializable
@Immutable
data class ActorInfo(
    val role: ActorRole,
    val name: String,
    val avatarUrl: String?,
    val url: String?,
)

@Serializable
@Immutable
data class SimilarMovie(
    val url: String,
    val name: String,
    val posterUrl: String,
)

@Serializable
@Immutable
data class ActorDetails(
    val name: String,
    val birthDate: String?,
    val description: String,
    val filmwebRating: Rating?,
    val movies: List<MovieItem>,
)

@Serializable
@Immutable
data class DetailedMedia(
    val baseItem: MovieItem,
    val embeds: List<EmbedLink> = emptyList(),
    val prevEpisodeUrl: String? = null,
    val nextEpisodeUrl: String? = null,
    val categories: List<CategoryInfo> = emptyList(),
    val tags: List<TagInfo> = emptyList(),
    val metaInfo: MediaMetadata? = null,
    val actors: List<ActorInfo> = emptyList(),
    val similarMovies: List<SimilarMovie> = emptyList(),
)

private val seasonEpisodeRegex = Regex("(?i)s(\\d+)e(\\d+)")
