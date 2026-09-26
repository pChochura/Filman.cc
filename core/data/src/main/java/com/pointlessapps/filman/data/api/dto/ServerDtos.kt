package com.pointlessapps.filman.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PaginatedResponse<T>(
    val page: Int,
    val results: List<T>,
    val totalPages: Int,
    val totalResults: Int
)

@Serializable
data class MediaSummary(
    val id: Int,
    val title: String,
    val overview: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val mediaType: String? = null, // "MOVIE" or "TV_SHOW"
    val voteAverage: Double? = null
)

@Serializable
data class Movie(
    val id: Int,
    val title: String,
    val overview: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val voteAverage: Double? = null
)

@Serializable
data class TvShow(
    val id: Int,
    val title: String,
    val overview: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val firstAirDate: String? = null,
    val voteAverage: Double? = null,
    val seasons: List<Season> = emptyList()
)

@Serializable
data class Season(
    val id: Int,
    val name: String,
    val seasonNumber: Int,
    val episodes: List<Episode> = emptyList()
)

@Serializable
data class Episode(
    val id: Int,
    val name: String,
    val overview: String,
    val stillPath: String? = null,
    val airDate: String? = null,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val voteAverage: Double? = null
)
