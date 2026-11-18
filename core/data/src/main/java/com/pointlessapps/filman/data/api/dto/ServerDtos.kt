package com.pointlessapps.filman.data.api.dto

import com.pointlessapps.filman.data.model.ActorRole
import kotlinx.serialization.Serializable

@Serializable
data class PaginatedResponse<T>(
    val page: Int,
    val results: List<T>,
    val totalPages: Int,
    val totalResults: Int,
)

@Serializable
data class MediaSummary(
    val id: Int,
    val title: String,
    val overview: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val mediaType: String? = null, // "MOVIE", "TV_SHOW", or "PERSON"
    val voteAverage: Double? = null,
    val releaseDate: String? = null,
    val originalTitle: String? = null,
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
    val duration: Int? = null,
    val originalTitle: String? = null,
    val imdbId: String? = null,
    val recommendations: List<MediaSummary> = emptyList(),
    val recommendationsTotalPages: Int = 1,
    val similar: List<MediaSummary> = emptyList(),
    val trailerUrl: String? = null,
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
    val duration: Int? = null,
    val originalTitle: String? = null,
    val imdbId: String? = null,
    val recommendations: List<MediaSummary> = emptyList(),
    val recommendationsTotalPages: Int = 1,
    val similar: List<MediaSummary> = emptyList(),
    val trailerUrl: String? = null,
)

@Serializable
data class Season(
    val name: String,
    val seasonNumber: Int,
    val episodes: List<Episode> = emptyList(),
)

@Serializable
data class Episode(
    val id: Int,
    val name: String,
    val episodeNumber: Int,
    val seasonNumber: Int,
)

@Serializable
data class Actor(
    val id: Int,
    val name: String,
    val profilePath: String? = null,
    val role: ActorRole = ActorRole.ACTOR,
)

@Serializable
data class Genre(
    val id: Int,
    val name: String,
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
    val serverName: String = "",
)

@Serializable
data class LinksResponse(
    val status: String, // "PENDING", "PARTIAL", "READY", "ERROR"
    val links: List<VideoLink> = emptyList(),
    val message: String? = null,
    val completedSources: Int = 0,
    val totalSources: Int = 0,
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
    val placeOfBirth: String? = null,
    val castCredits: List<MediaSummary> = emptyList(),
    val directorCredits: List<MediaSummary> = emptyList(),
    val writerCredits: List<MediaSummary> = emptyList(),
)
