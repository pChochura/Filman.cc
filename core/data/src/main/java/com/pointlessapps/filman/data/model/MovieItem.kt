package com.pointlessapps.filman.data.model

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
    val seriesUrl: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val prevEpisodeUrl: String? = null,
    val nextEpisodeUrl: String? = null,
    val seasons: List<Season>? = null,
    val year: Int? = null,
    val isTvShow: Boolean = false,
    @kotlinx.serialization.Transient
    val detailsRequest: DetailsRequest? = null,
) {
    val mediaRef: MediaRef?
        get() = MediaRef.parse(url, isTvShow || seasonNumber != null || seriesUrl != null)

    fun sanitizeToTmdb(): MovieItem? {
        val ref = mediaRef ?: return null
        val parentRef = ref.parentRef
        val canonicalUrl = ref.canonicalKey
        return copy(
            url = canonicalUrl,
            seriesUrl = if (ref.isEpisode) parentRef.canonicalKey else seriesUrl?.let { MediaRef.parse(it, true)?.parentRef?.canonicalKey },
            seasonNumber = seasonNumber ?: ref.season,
            episodeNumber = episodeNumber ?: ref.episode,
            isTvShow = ref.isTvShow,
            detailsRequest = DetailsRequest.Url(canonicalUrl, ref.isTvShow),
        )
    }
}
