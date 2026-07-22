package com.example.filman.data.source.obejrzyj.models

import kotlinx.serialization.Serializable

@Serializable
data class ObejrzyjDetailsResponse(
    val title: ObejrzyjTitleDetails? = null,
    val credits: ObejrzyjCredits? = null,
    val episodes: ObejrzyjEpisodesContainer? = null,
    val seasons: ObejrzyjSeasonsContainer? = null
)

@Serializable
data class ObejrzyjTitleDetails(
    val id: Int? = null,
    val name: String? = null,
    val description: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val runtime: Int? = null,
    val year: Int? = null,
    val views: Int? = null,
    val rating: Double? = null,
    val is_series: Boolean? = null,
    val seasons_count: Int? = null,
    val season_numbers_with_episodes: List<Int> = emptyList(),
    val genres: List<ObejrzyjGenre> = emptyList(),
    val production_countries: List<ObejrzyjCountry> = emptyList(),
    val keywords: List<ObejrzyjKeyword> = emptyList(),
    val videos: List<ObejrzyjVideo> = emptyList()
)

@Serializable
data class ObejrzyjGenre(
    val id: Int? = null,
    val name: String? = null,
    val display_name: String? = null
)

@Serializable
data class ObejrzyjCountry(
    val name: String? = null,
    val display_name: String? = null
)

@Serializable
data class ObejrzyjKeyword(
    val name: String? = null,
    val display_name: String? = null
)

@Serializable
data class ObejrzyjVideo(
    val id: Int? = null,
    val name: String? = null,
    val src: String? = null,
    val quality: String? = null,
    val language_type: String? = null
)

@Serializable
data class ObejrzyjCredits(
    val directing: List<ObejrzyjPerson> = emptyList(),
    val writing: List<ObejrzyjPerson> = emptyList(),
    val actors: List<ObejrzyjPerson> = emptyList()
)

@Serializable
data class ObejrzyjPerson(
    val id: Int? = null,
    val name: String? = null,
    val poster: String? = null
)

@Serializable
data class ObejrzyjRelatedResponse(
    val titles: List<ObejrzyjMovie> = emptyList()
)

@Serializable
data class ObejrzyjEpisodesContainer(
    val data: List<ObejrzyjEpisode> = emptyList()
)

@Serializable
data class ObejrzyjEpisode(
    val id: Int? = null,
    val name: String? = null,
    val season_number: Int? = null,
    val episode_number: Int? = null,
    val primary_video: ObejrzyjVideo? = null
)

@Serializable
data class ObejrzyjSeasonsContainer(
    val data: List<ObejrzyjSeason> = emptyList()
)

@Serializable
data class ObejrzyjSeason(
    val id: Int? = null,
    val number: Int? = null
)

@Serializable
data class ObejrzyjSeasonEpisodesResponse(
    val pagination: ObejrzyjEpisodesContainer? = null
)

@Serializable
data class ObejrzyjWatchResponse(
    val video: ObejrzyjVideo? = null,
    val alternative_videos: List<ObejrzyjVideo> = emptyList()
)
