package com.example.filman.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Movie(
    val url: String,
    val titlePl: String,
    val titleEn: String? = null,
    val year: Int? = null,
    val rating: Float? = null,
    val posterUrl: String,
    val description: String = "",
)

@Immutable
data class FeaturedItem(
    val url: String,
    val titlePl: String,
    val titleEn: String? = null,
    val year: Int? = null,
    val rating: Float? = null,
    val description: String,
    val posterUrl: String,
    val backgroundUrl: String? = null,
)

@Immutable
data class EmbedLink(
    val url: String, // Actually the linkId temporarily
    val serverName: String,
    val version: String = "",
    val quality: String = "",
)

@Immutable
data class Episode(
    val url: String,
    val titlePl: String,
)

@Immutable
data class Season(
    val name: String,
    val episodes: List<Episode>,
)

@Immutable
sealed class MediaDetails {
    abstract val titlePl: String
    abstract val titleEn: String?
    abstract val year: Int?
    abstract val rating: Float?
    abstract val posterUrl: String
    abstract val backgroundUrl: String?
    abstract val description: String

    @Immutable
    data class Series(
        override val titlePl: String,
        override val titleEn: String? = null,
        override val year: Int? = null,
        override val rating: Float? = null,
        override val posterUrl: String,
        override val backgroundUrl: String? = null,
        override val description: String,
        val seasons: List<Season>,
    ) : MediaDetails()

    @Immutable
    data class MovieOrEpisode(
        override val titlePl: String,
        override val titleEn: String? = null,
        override val year: Int? = null,
        override val rating: Float? = null,
        override val posterUrl: String,
        override val backgroundUrl: String? = null,
        override val description: String,
        val routeToken: String,
        val embeds: List<EmbedLink>,
        val seriesUrl: String? = null,
        val prevEpisodeUrl: String? = null,
        val nextEpisodeUrl: String? = null,
    ) : MediaDetails()
}

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

private val seasonEpisodeRegex = Regex("(?i)s(\\d+)e(\\d+)")
