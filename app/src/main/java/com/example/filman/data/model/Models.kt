package com.example.filman.data.model

import androidx.compose.runtime.Immutable

@Immutable
open class MovieItem(
    open val url: String,
    open val titlePl: String,
    open val titleEn: String? = null,
    open val filmanRating: Float? = null,
    open val imdbRating: Float? = null,
    open val posterUrl: String,
    open val backgroundUrl: String? = null,
    open val description: String = "",
    open val routeToken: String? = null,
    open val embeds: List<EmbedLink> = emptyList(),
    open val seriesUrl: String? = null,
    open val prevEpisodeUrl: String? = null,
    open val nextEpisodeUrl: String? = null
)

@Immutable
data class TvShow(
    override val url: String,
    override val titlePl: String,
    override val titleEn: String? = null,
    override val filmanRating: Float? = null,
    override val imdbRating: Float? = null,
    override val posterUrl: String,
    override val backgroundUrl: String? = null,
    override val description: String = "",
    val seasons: List<Season>
) : MovieItem(url, titlePl, titleEn, filmanRating, imdbRating, posterUrl, backgroundUrl, description)

@Immutable
data class EmbedLink(
    val url: String, // Actually the linkId temporarily
    val serverName: String,
    val version: String = "",
    val quality: String = "",
)

@Immutable
data class EpisodeLink(
    val title: String,
    val url: String
)

@Immutable
data class Season(
    val name: String,
    val episodes: List<EpisodeLink>,
)

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

@Immutable
data class CategoryInfo(val name: String, val url: String)

@Immutable
data class TagInfo(val name: String, val url: String)

@Immutable
data class MediaMetadata(
    val year: Int?,
    val views: Int?,
    val duration: String?,
    val country: String?
)

enum class ActorRole {
    DIRECTOR,
    WRITER,
    ACTOR,
    UNKNOWN
}

@Immutable
data class ActorInfo(
    val role: ActorRole,
    val name: String,
    val avatarUrl: String?,
    val url: String?
)

@Immutable
data class SimilarMovie(
    val url: String,
    val name: String,
    val posterUrl: String
)

@Immutable
data class DetailedMedia(
    val baseItem: MovieItem,
    val categories: List<CategoryInfo> = emptyList(),
    val tags: List<TagInfo> = emptyList(),
    val metaInfo: MediaMetadata? = null,
    val actors: List<ActorInfo> = emptyList(),
    val similarMovies: List<SimilarMovie> = emptyList()
)

private val seasonEpisodeRegex = Regex("(?i)s(\\d+)e(\\d+)")
