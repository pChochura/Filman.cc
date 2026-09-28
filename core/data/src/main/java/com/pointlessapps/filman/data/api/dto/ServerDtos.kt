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
    val voteAverage: Double? = null,
    val cast: List<Actor> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val countries: List<String> = emptyList(),
    val duration: Int? = null
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
    val seasons: List<Season> = emptyList(),
    val cast: List<Actor> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val countries: List<String> = emptyList(),
    val duration: Int? = null
)

@Serializable
data class Season(
    val name: String,
    val seasonNumber: Int,
    val episodes: List<Episode> = emptyList()
)

@Serializable
data class Episode(
    val id: Int,
    val name: String,
    val episodeNumber: Int,
    val seasonNumber: Int
)


@Serializable
data class Actor(
    val id: Int,
    val name: String,
    val profilePath: String? = null
)

@Serializable
data class Genre(
    val id: Int,
    val name: String
)

@Serializable
data class SubtitleTrack(
    val url: String,
    val label: String,
    val language: String = "",
)

@Serializable
data class VideoLink(
    val url: String,
    val quality: String = "",
    val language: String = "",
    val subtitles: List<SubtitleTrack> = emptyList(),
    val sourceWebsite: String = "",
    val displayMode: String = "NATIVE",
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class LinksResponse(
    val status: String, // "PENDING", "PARTIAL", "READY", "ERROR"
    val links: List<VideoLink> = emptyList()
)

@Serializable
data class ActorDetail(
    val id: Int,
    val name: String,
    val biography: String? = null,
    val profilePath: String? = null,
    val knownForDepartment: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    val placeOfBirth: String? = null
)
