package com.example.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class SearchResults(
    val movies: List<MovieItem> = emptyList(),
    val tvShows: List<MovieItem> = emptyList(),
)
