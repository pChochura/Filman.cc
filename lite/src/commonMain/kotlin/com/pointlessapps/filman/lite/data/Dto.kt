package com.pointlessapps.filman.lite.data

import kotlinx.serialization.Serializable

@Serializable
internal data class PaginatedResponse<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    val totalPages: Int = 1,
    val totalResults: Int = 0,
)

@Serializable
internal data class MediaSummaryDto(
    val id: Int,
    val title: String,
    val overview: String = "",
    val posterPath: String? = null,
    val mediaType: String? = null,
)

@Serializable
internal data class TvShowDto(
    val id: Int,
    val title: String,
    val seasons: List<SeasonDto> = emptyList(),
)

@Serializable
internal data class SeasonDto(
    val name: String = "",
    val seasonNumber: Int,
    val episodes: List<EpisodeDto> = emptyList(),
)

@Serializable
internal data class EpisodeDto(
    val id: Int = 0,
    val name: String = "",
    val episodeNumber: Int,
    val seasonNumber: Int,
)

@Serializable
internal data class LinksResponseDto(
    val status: String,
    val links: List<VideoLinkDto> = emptyList(),
    val message: String? = null,
    val requiresFilmanAuth: Boolean = false,
    val requiresCloudflare: Boolean = false,
    val pageRequest: PageRequestDto? = null,
)

@Serializable
internal data class PageRequestDto(val url: String = "")

@Serializable
internal data class VideoLinkDto(
    val url: String,
    val quality: String = "",
    val language: String = "",
    val sourceWebsite: String = "",
    val displayMode: String = "NATIVE",
    val headers: Map<String, String> = emptyMap(),
)
