package com.example.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

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
    val similarMovies: List<MovieItem> = emptyList(),
) {
    val seasonsNumber: Int?
        get() = baseItem.seasons?.size
}
