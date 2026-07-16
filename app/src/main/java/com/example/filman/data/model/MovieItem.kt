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
