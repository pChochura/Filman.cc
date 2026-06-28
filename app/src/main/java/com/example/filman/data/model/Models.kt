package com.example.filman.data.model

data class Movie(
    val url: String,
    val title: String,
    val posterUrl: String,
    val description: String = ""
)

data class EmbedLink(
    val url: String, // Actually the linkId temporarily
    val serverName: String
)

data class Episode(
    val url: String,
    val title: String
)

data class Season(
    val name: String,
    val episodes: List<Episode>
)

sealed class MediaDetails {
    abstract val title: String
    abstract val posterUrl: String
    abstract val description: String

    data class Series(
        override val title: String, 
        override val posterUrl: String, 
        override val description: String,
        val seasons: List<Season>
    ) : MediaDetails()
    
    data class MovieOrEpisode(
        override val title: String, 
        override val posterUrl: String, 
        override val description: String,
        val routeToken: String, 
        val embeds: List<EmbedLink>,
        val seriesUrl: String? = null,
        val prevEpisodeUrl: String? = null,
        val nextEpisodeUrl: String? = null
    ) : MediaDetails()
}

data class ProgressItem(
    val url: String,
    val title: String,
    val posterUrl: String,
    val progressMs: Long,
    val durationMs: Long,
    val seriesTitle: String? = null
) {
    val progressPercentage: Float
        get() = if (durationMs > 0) (progressMs.toFloat() / durationMs.toFloat()) else 0f
}
