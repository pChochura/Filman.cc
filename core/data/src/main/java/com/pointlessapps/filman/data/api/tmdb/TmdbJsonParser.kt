package com.pointlessapps.filman.data.api.tmdb

import com.pointlessapps.filman.data.api.dto.Actor
import com.pointlessapps.filman.data.api.dto.ActorDetail
import com.pointlessapps.filman.data.api.dto.Episode
import com.pointlessapps.filman.data.api.dto.Genre
import com.pointlessapps.filman.data.api.dto.MediaSummary
import com.pointlessapps.filman.data.api.dto.Movie
import com.pointlessapps.filman.data.api.dto.PaginatedResponse
import com.pointlessapps.filman.data.api.dto.Season
import com.pointlessapps.filman.data.api.dto.TvShow
import com.pointlessapps.filman.data.model.ActorRole
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val WRITER_JOBS = setOf("Writer", "Screenplay", "Story", "Creator", "Novel")

private val JsonPrimitive.contentOrNull: String?
    get() = if (this is JsonNull) null else content

internal fun JsonObject.parsePagedSummaries(
    page: Int,
    defaultMediaType: String?,
): PaginatedResponse<MediaSummary> {
    val resultsArray = this["results"]?.jsonArray ?: JsonArray(emptyList())
    val items = resultsArray.mapNotNull { it.toMediaSummary(defaultMediaType) }
    val totalPages = this["total_pages"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
    val totalResults = this["total_results"]?.jsonPrimitive?.content?.toIntOrNull() ?: items.size
    return PaginatedResponse(
        page = page,
        results = items,
        totalPages = totalPages,
        totalResults = totalResults,
    )
}

internal fun JsonObject.parseFirstResultId(): Int? =
    this["results"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content?.toIntOrNull()

internal fun JsonObject.parseImdbId(): String? {
    val raw = this["imdb_id"] ?: return null
    return if (raw !is JsonNull) {
        raw.jsonPrimitive.content.takeIf { it.isNotBlank() }
    } else {
        null
    }
}

internal fun JsonObject.parseGenres(): List<Genre> =
    this["genres"]?.jsonArray?.mapNotNull { it.toGenre() } ?: emptyList()

internal fun JsonObject.parseSeasonNumbers(): List<Int> =
    this["seasons"]?.jsonArray?.mapNotNull { s ->
        s.jsonObject["season_number"]?.jsonPrimitive?.content?.toIntOrNull()
    }?.filter { it > 0 } ?: emptyList()

internal fun JsonObject.parseSeason(seasonNumber: Int): Season {
    val seasonName = this["name"]?.jsonPrimitive?.content ?: "Season $seasonNumber"
    val episodesArray = this["episodes"]?.jsonArray ?: JsonArray(emptyList())
    val episodes = episodesArray.mapNotNull { epJson ->
        val epObj = epJson.jsonObject
        val epId = epObj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
        val epName = epObj["name"]?.jsonPrimitive?.content ?: "Episode"
        val epNum = epObj["episode_number"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        Episode(id = epId, name = epName, episodeNumber = epNum, seasonNumber = seasonNumber)
    }
    return Season(name = seasonName, seasonNumber = seasonNumber, episodes = episodes)
}

internal fun JsonObject.toFullMovie(preferredLanguageCode: String): Movie {
    val id = this["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
    val title = this["title"]?.jsonPrimitive?.contentOrNull
        ?: this["original_title"]?.jsonPrimitive?.contentOrNull ?: ""
    val originalTitle = this["original_title"]?.jsonPrimitive?.contentOrNull
    val overview = this["overview"]?.jsonPrimitive?.contentOrNull ?: ""
    val posterPath = this["poster_path"]?.jsonPrimitive?.contentOrNull
    val backdropPath = this["backdrop_path"]?.jsonPrimitive?.contentOrNull
    val releaseDate = this["release_date"]?.jsonPrimitive?.contentOrNull
    val voteAverage = this["vote_average"]?.jsonPrimitive?.content?.toDoubleOrNull()
    val duration = this["runtime"]?.jsonPrimitive?.content?.toIntOrNull()

    val genres = this["genres"]?.jsonArray?.mapNotNull { it.toGenre() } ?: emptyList()
    val countries = this["production_countries"]?.jsonArray?.mapNotNull {
        it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
    } ?: emptyList()

    val rawMovieImdb = this["imdb_id"] ?: this["external_ids"]?.jsonObject?.get("imdb_id")
    val imdbId = if (rawMovieImdb != null && rawMovieImdb !is JsonNull) {
        rawMovieImdb.jsonPrimitive.content.takeIf { it.isNotBlank() }
    } else {
        null
    }

    val recommendationsObj = this["recommendations"]?.jsonObject
    val recommendations = recommendationsObj?.get("results")?.jsonArray
        ?.mapNotNull { it.toMediaSummary(defaultMediaType = "MOVIE") }
        ?: emptyList()
    val recommendationsTotalPages =
        recommendationsObj?.get("total_pages")?.jsonPrimitive?.content?.toIntOrNull() ?: 1

    val similar = this["similar"]?.jsonObject?.get("results")?.jsonArray
        ?.mapNotNull { it.toMediaSummary(defaultMediaType = "MOVIE") }
        ?: emptyList()

    return Movie(
        id = id,
        title = title,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate,
        voteAverage = voteAverage,
        cast = extractActors(includeCreators = false),
        genres = genres,
        countries = countries,
        duration = duration,
        originalTitle = originalTitle,
        imdbId = imdbId,
        recommendations = recommendations,
        recommendationsTotalPages = recommendationsTotalPages,
        similar = similar,
        trailerUrl = extractTrailerUrl(preferredLanguageCode),
    )
}

internal fun JsonObject.toFullTvShow(preferredLanguageCode: String): TvShow {
    val id = this["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
    val title = this["name"]?.jsonPrimitive?.contentOrNull
        ?: this["original_name"]?.jsonPrimitive?.contentOrNull ?: ""
    val originalTitle = this["original_name"]?.jsonPrimitive?.contentOrNull
    val overview = this["overview"]?.jsonPrimitive?.contentOrNull ?: ""
    val posterPath = this["poster_path"]?.jsonPrimitive?.contentOrNull
    val backdropPath = this["backdrop_path"]?.jsonPrimitive?.contentOrNull
    val firstAirDate = this["first_air_date"]?.jsonPrimitive?.contentOrNull
    val voteAverage = this["vote_average"]?.jsonPrimitive?.content?.toDoubleOrNull()
    val duration = this["episode_run_time"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content?.toIntOrNull()
        ?: this["last_episode_to_air"]?.jsonObject?.get("runtime")?.jsonPrimitive?.content?.toIntOrNull()

    val rawTvImdb = this["external_ids"]?.jsonObject?.get("imdb_id")
    val imdbId = if (rawTvImdb != null && rawTvImdb !is JsonNull) {
        rawTvImdb.jsonPrimitive.content.takeIf { it.isNotBlank() }
    } else {
        null
    }

    val genres = this["genres"]?.jsonArray?.mapNotNull { it.toGenre() } ?: emptyList()
    val countries = this["origin_country"]?.jsonArray?.mapNotNull {
        it.jsonPrimitive.contentOrNull
    } ?: emptyList()

    val recommendationsObj = this["recommendations"]?.jsonObject
    val recommendations = recommendationsObj?.get("results")?.jsonArray
        ?.mapNotNull { it.toMediaSummary(defaultMediaType = "TV_SHOW") }
        ?: emptyList()
    val recommendationsTotalPages =
        recommendationsObj?.get("total_pages")?.jsonPrimitive?.content?.toIntOrNull() ?: 1

    val similar = this["similar"]?.jsonObject?.get("results")?.jsonArray
        ?.mapNotNull { it.toMediaSummary(defaultMediaType = "TV_SHOW") }
        ?: emptyList()

    return TvShow(
        id = id,
        title = title,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        firstAirDate = firstAirDate,
        voteAverage = voteAverage,
        seasons = emptyList(),
        cast = extractActors(includeCreators = true),
        genres = genres,
        countries = countries,
        duration = duration,
        originalTitle = originalTitle,
        imdbId = imdbId,
        recommendations = recommendations,
        recommendationsTotalPages = recommendationsTotalPages,
        similar = similar,
        trailerUrl = extractTrailerUrl(preferredLanguageCode),
    )
}

internal fun JsonObject.toActorDetail(defaultId: Int): ActorDetail {
    val creditsObj = this["combined_credits"]?.jsonObject
    val castCredits = creditsObj?.get("cast")?.jsonArray
        ?.mapNotNull { it.toMediaSummary(defaultMediaType = null) }
        ?.distinctBy { "${it.mediaType}_${it.id}" }
        .orEmpty()

    val crewArray = creditsObj?.get("crew")?.jsonArray ?: JsonArray(emptyList())
    val directorCredits = crewArray
        .filter { it.jsonObject["job"]?.jsonPrimitive?.content == "Director" }
        .mapNotNull { it.toMediaSummary(defaultMediaType = null) }
        .distinctBy { "${it.mediaType}_${it.id}" }

    val writerCredits = crewArray
        .filter { item ->
            val obj = item.jsonObject
            val dept = obj["department"]?.jsonPrimitive?.content
            val job = obj["job"]?.jsonPrimitive?.content
            dept == "Writing" || job in WRITER_JOBS
        }
        .mapNotNull { it.toMediaSummary(defaultMediaType = null) }
        .distinctBy { "${it.mediaType}_${it.id}" }

    return ActorDetail(
        id = this["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: defaultId,
        name = this["name"]?.jsonPrimitive?.content ?: "",
        biography = this["biography"]?.jsonPrimitive?.contentOrNull,
        profilePath = this["profile_path"]?.jsonPrimitive?.contentOrNull,
        knownForDepartment = this["known_for_department"]?.jsonPrimitive?.contentOrNull,
        birthday = this["birthday"]?.jsonPrimitive?.contentOrNull,
        deathday = this["deathday"]?.jsonPrimitive?.contentOrNull,
        placeOfBirth = this["place_of_birth"]?.jsonPrimitive?.contentOrNull,
        castCredits = castCredits,
        directorCredits = directorCredits,
        writerCredits = writerCredits,
    )
}

private fun JsonElement.toMediaSummary(defaultMediaType: String? = null): MediaSummary? {
    val obj = this.jsonObject
    val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
    val mediaTypeRaw = obj["media_type"]?.jsonPrimitive?.contentOrNull
    val mediaType = when (mediaTypeRaw) {
        "tv" -> "TV_SHOW"
        "movie" -> "MOVIE"
        "person" -> "PERSON"
        else -> defaultMediaType ?: if (obj.containsKey("first_air_date") || obj.containsKey("name")) "TV_SHOW" else "MOVIE"
    }

    val title = obj["title"]?.jsonPrimitive?.contentOrNull
        ?: obj["name"]?.jsonPrimitive?.contentOrNull
        ?: obj["original_title"]?.jsonPrimitive?.contentOrNull
        ?: obj["original_name"]?.jsonPrimitive?.contentOrNull
        ?: return null

    val originalTitle = obj["original_title"]?.jsonPrimitive?.contentOrNull
        ?: obj["original_name"]?.jsonPrimitive?.contentOrNull
    val overview = obj["overview"]?.jsonPrimitive?.contentOrNull ?: ""
    val posterPath = obj["poster_path"]?.jsonPrimitive?.contentOrNull
    val backdropPath = obj["backdrop_path"]?.jsonPrimitive?.contentOrNull
    val voteAverage = obj["vote_average"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
    val releaseDate = obj["release_date"]?.jsonPrimitive?.contentOrNull
        ?: obj["first_air_date"]?.jsonPrimitive?.contentOrNull
    val genreIds = obj["genre_ids"]?.jsonArray
        ?.mapNotNull { it.jsonPrimitive.contentOrNull?.toIntOrNull() }
        .orEmpty()
    val voteCount = obj["vote_count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    return MediaSummary(
        id = id,
        title = title,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        mediaType = mediaType,
        voteAverage = voteAverage,
        releaseDate = releaseDate,
        originalTitle = originalTitle,
        genreIds = genreIds,
        voteCount = voteCount,
    )
}

private fun JsonElement.toGenre(): Genre? {
    val obj = this.jsonObject
    val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
    return Genre(id = id, name = name)
}

private fun JsonObject.extractActors(includeCreators: Boolean = false): List<Actor> {
    val creditsObj = this["credits"]?.jsonObject
    val result = mutableListOf<Actor>()

    if (includeCreators) {
        this["created_by"]?.jsonArray?.forEach { item ->
            val obj = item.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@forEach
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            result.add(
                Actor(
                    id = id,
                    name = name,
                    profilePath = obj["profile_path"]?.jsonPrimitive?.contentOrNull,
                    role = ActorRole.WRITER,
                ),
            )
        }
    }

    creditsObj?.get("crew")?.jsonArray?.forEach { item ->
        val obj = item.jsonObject
        val job = obj["job"]?.jsonPrimitive?.contentOrNull
        val dept = obj["department"]?.jsonPrimitive?.contentOrNull
        val role = when {
            job == "Director" -> ActorRole.DIRECTOR
            dept == "Writing" || job in WRITER_JOBS -> ActorRole.WRITER
            else -> null
        } ?: return@forEach
        val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@forEach
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
        result.add(
            Actor(
                id = id,
                name = name,
                profilePath = obj["profile_path"]?.jsonPrimitive?.contentOrNull,
                role = role,
            ),
        )
    }

    creditsObj?.get("cast")?.jsonArray?.forEach { item ->
        val obj = item.jsonObject
        val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@forEach
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
        result.add(
            Actor(
                id = id,
                name = name,
                profilePath = obj["profile_path"]?.jsonPrimitive?.contentOrNull,
                role = ActorRole.ACTOR,
            ),
        )
    }

    return result.distinctBy { "${it.role}_${it.id}" }
}

private fun JsonObject.extractTrailerUrl(preferredLanguageCode: String): String? {
    val videos = this["videos"]?.jsonObject?.get("results")?.jsonArray ?: return null
    val youtubeVideos = videos.mapNotNull { element ->
        val obj = element.jsonObject
        val site = obj["site"]?.jsonPrimitive?.contentOrNull
        val key = obj["key"]?.jsonPrimitive?.contentOrNull
        if (site == "YouTube" && !key.isNullOrBlank()) obj else null
    }
    val best = youtubeVideos.firstOrNull {
        it["type"]?.jsonPrimitive?.contentOrNull == "Trailer" &&
            it["iso_639_1"]?.jsonPrimitive?.contentOrNull == preferredLanguageCode
    } ?: youtubeVideos.firstOrNull {
        it["type"]?.jsonPrimitive?.contentOrNull == "Trailer"
    } ?: youtubeVideos.firstOrNull()

    val key = best?.get("key")?.jsonPrimitive?.contentOrNull ?: return null
    return "https://www.youtube.com/watch?v=$key"
}
