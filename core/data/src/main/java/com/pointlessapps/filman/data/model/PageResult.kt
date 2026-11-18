package com.pointlessapps.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class PageResult(
    val featuredItems: List<MovieItem>,
    val movies: List<MovieItem>,
    val errorMessage: String? = null,
    val feed: TmdbFeed? = null,
    val page: Int = 1,
    val totalPages: Int = 1,
) {
    val hasMore: Boolean get() = page < totalPages && movies.isNotEmpty()
}
