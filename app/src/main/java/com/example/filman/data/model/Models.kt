package com.example.filman.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Movie(
    val url: String,
    val title: String,
    val posterUrl: String,
    val description: String = "",
)

@Immutable
data class EmbedLink(
    val url: String, // Actually the linkId temporarily
    val serverName: String,
)

@Immutable
data class Episode(
    val url: String,
    val title: String,
)

@Immutable
data class Season(
    val name: String,
    val episodes: List<Episode>,
)

@Immutable
sealed class MediaDetails {
    abstract val title: String
    abstract val posterUrl: String
    abstract val description: String

    @Immutable
    data class Series(
        override val title: String,
        override val posterUrl: String,
        override val description: String,
        val seasons: List<Season>,
    ) : MediaDetails()

    @Immutable
    data class MovieOrEpisode(
        override val title: String,
        override val posterUrl: String,
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
    val title: String,
    val posterUrl: String,
    val progressMs: Long,
    val durationMs: Long,
    val seriesTitle: String? = null,
) {
    val progressPercentage: Float
        get() = if (durationMs > 0) (progressMs.toFloat() / durationMs.toFloat()) else 0f
}
