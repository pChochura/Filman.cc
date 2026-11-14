package com.pointlessapps.filman.data.api.tmdb

import android.util.Log
import com.pointlessapps.filman.core.data.BuildConfig
import com.pointlessapps.filman.data.api.dto.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class TmdbApiClient(
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "https://api.themoviedb.org/3"

    private fun getApiKey(): String = BuildConfig.TMDB_API_KEY

    private suspend fun executeGet(url: String): JsonElement = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val body = response.body.string()
            throw IllegalStateException("TMDB request failed with code ${response.code}: $body")
        }
        val bodyString = response.body.string()
        json.parseToJsonElement(bodyString)
    }

    suspend fun getFeaturedAll(page: Int = 1, language: String = "pl-PL"): PaginatedResponse<MediaSummary> {
        val apiKey = getApiKey()
        val url = "$baseUrl/trending/all/week?api_key=$apiKey&page=$page&language=$language"
        val root = executeGet(url).jsonObject
        val resultsArray = root["results"]?.jsonArray ?: JsonArray(emptyList())

        val items = resultsArray.mapNotNull { it.toMediaSummary() }
        val totalPages = root["total_pages"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        val totalResults = root["total_results"]?.jsonPrimitive?.content?.toIntOrNull() ?: items.size
        return PaginatedResponse(page = page, results = items, totalPages = totalPages, totalResults = totalResults)
    }

    suspend fun getFeaturedMovies(page: Int = 1, language: String = "pl-PL"): PaginatedResponse<Movie> {
        val apiKey = getApiKey()
        val url = "$baseUrl/trending/movie/week?api_key=$apiKey&page=$page&language=$language"
        val root = executeGet(url).jsonObject
        val resultsArray = root["results"]?.jsonArray ?: JsonArray(emptyList())

        val items = resultsArray.mapNotNull { it.toMovie() }
        val totalPages = root["total_pages"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        val totalResults = root["total_results"]?.jsonPrimitive?.content?.toIntOrNull() ?: items.size
        return PaginatedResponse(page = page, results = items, totalPages = totalPages, totalResults = totalResults)
    }

    suspend fun getFeaturedShows(page: Int = 1, language: String = "pl-PL"): PaginatedResponse<TvShow> {
        val apiKey = getApiKey()
        val url = "$baseUrl/trending/tv/week?api_key=$apiKey&page=$page&language=$language"
        val root = executeGet(url).jsonObject
        val resultsArray = root["results"]?.jsonArray ?: JsonArray(emptyList())

        val items = resultsArray.mapNotNull { it.toTvShow() }
        val totalPages = root["total_pages"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        val totalResults = root["total_results"]?.jsonPrimitive?.content?.toIntOrNull() ?: items.size
        return PaginatedResponse(page = page, results = items, totalPages = totalPages, totalResults = totalResults)
    }

    suspend fun search(
        query: String = "",
        genreId: Int? = null,
        page: Int = 1,
        language: String = "pl-PL"
    ): PaginatedResponse<MediaSummary> {
        val apiKey = getApiKey()
        val url = if (query.isNotBlank()) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            "$baseUrl/search/multi?api_key=$apiKey&query=$encoded&page=$page&language=$language"
        } else if (genreId != null) {
            "$baseUrl/discover/movie?api_key=$apiKey&with_genres=$genreId&page=$page&language=$language&sort_by=popularity.desc"
        } else {
            return PaginatedResponse(page, emptyList(), 0, 0)
        }

        val root = executeGet(url).jsonObject
        val resultsArray = root["results"]?.jsonArray ?: JsonArray(emptyList())
        val items = resultsArray.mapNotNull { it.toMediaSummary() }
        val totalPages = root["total_pages"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        val totalResults = root["total_results"]?.jsonPrimitive?.content?.toIntOrNull() ?: items.size
        return PaginatedResponse(page = page, results = items, totalPages = totalPages, totalResults = totalResults)
    }

    suspend fun getCategories(language: String = "pl-PL"): List<Genre> = coroutineScope {
        val apiKey = getApiKey()
        val movieGenresDeferred = async {
            try {
                val url = "$baseUrl/genre/movie/list?api_key=$apiKey&language=$language"
                val root = executeGet(url).jsonObject
                root["genres"]?.jsonArray?.mapNotNull { it.toGenre() } ?: emptyList()
            } catch (e: Exception) {
                Log.e("TmdbApiClient", "Failed to fetch movie genres", e)
                emptyList()
            }
        }
        val tvGenresDeferred = async {
            try {
                val url = "$baseUrl/genre/tv/list?api_key=$apiKey&language=$language"
                val root = executeGet(url).jsonObject
                root["genres"]?.jsonArray?.mapNotNull { it.toGenre() } ?: emptyList()
            } catch (e: Exception) {
                Log.e("TmdbApiClient", "Failed to fetch tv genres", e)
                emptyList()
            }
        }

        (movieGenresDeferred.await() + tvGenresDeferred.await()).distinctBy { it.id }
    }

    suspend fun getMovieDetails(id: Int, language: String = "pl-PL"): Movie {
        val apiKey = getApiKey()
        val url = "$baseUrl/movie/$id?api_key=$apiKey&language=$language&append_to_response=credits,recommendations"
        val root = executeGet(url).jsonObject
        return root.toFullMovie()
    }

    suspend fun getTvShowDetails(
        id: Int,
        language: String = "pl-PL",
        onMetadata: (TvShow) -> Unit = {},
    ): TvShow = coroutineScope {
        val apiKey = getApiKey()
        val url = "$baseUrl/tv/$id?api_key=$apiKey&language=$language&append_to_response=credits,recommendations"
        val root = executeGet(url).jsonObject
        val rawShow = root.toFullTvShow()
        onMetadata(rawShow)

        val seasonNumbers = root["seasons"]?.jsonArray?.mapNotNull { s ->
            s.jsonObject["season_number"]?.jsonPrimitive?.content?.toIntOrNull()
        }?.filter { it > 0 } ?: emptyList()

        val populatedSeasons = seasonNumbers.map { sNum ->
            async {
                try {
                    val seasonUrl = "$baseUrl/tv/$id/season/$sNum?api_key=$apiKey&language=$language"
                    val seasonRoot = executeGet(seasonUrl).jsonObject
                    val seasonName = seasonRoot["name"]?.jsonPrimitive?.content ?: "Sezon $sNum"
                    val episodesArray = seasonRoot["episodes"]?.jsonArray ?: JsonArray(emptyList())
                    val episodes = episodesArray.mapNotNull { epJson ->
                        val epObj = epJson.jsonObject
                        val epId = epObj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                        val epName = epObj["name"]?.jsonPrimitive?.content ?: "Odcinek"
                        val epNum = epObj["episode_number"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                        Episode(id = epId, name = epName, episodeNumber = epNum, seasonNumber = sNum)
                    }
                    Season(name = seasonName, seasonNumber = sNum, episodes = episodes)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("TmdbApiClient", "Failed to fetch season $sNum for show $id", e)
                    Season(name = "Sezon $sNum", seasonNumber = sNum, episodes = emptyList())
                }
            }
        }.awaitAll().sortedBy { it.seasonNumber }

        rawShow.copy(seasons = populatedSeasons)
    }

    suspend fun getActorDetails(id: Int, language: String = "pl-PL"): ActorDetail {
        val apiKey = getApiKey()
        val url = "$baseUrl/person/$id?api_key=$apiKey&language=$language"
        val root = executeGet(url).jsonObject
        return ActorDetail(
            id = root["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: id,
            name = root["name"]?.jsonPrimitive?.content ?: "",
            biography = root["biography"]?.jsonPrimitive?.content,
            profilePath = root["profile_path"]?.jsonPrimitive?.content,
            knownForDepartment = root["known_for_department"]?.jsonPrimitive?.content,
            birthday = root["birthday"]?.jsonPrimitive?.content,
            deathday = root["deathday"]?.jsonPrimitive?.content,
            placeOfBirth = root["place_of_birth"]?.jsonPrimitive?.content,
        )
    }

    suspend fun getActorMovies(id: Int, language: String = "pl-PL"): List<Movie> {
        val apiKey = getApiKey()
        val url = "$baseUrl/person/$id/combined_credits?api_key=$apiKey&language=$language"
        val root = executeGet(url).jsonObject
        val castArray = root["cast"]?.jsonArray ?: return emptyList()

        return castArray.mapNotNull { item ->
            val obj = item.jsonObject
            val itemId = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
            val mediaType = obj["media_type"]?.jsonPrimitive?.content
            val title = obj["title"]?.jsonPrimitive?.content
                ?: obj["name"]?.jsonPrimitive?.content
                ?: return@mapNotNull null
            val originalTitle = obj["original_title"]?.jsonPrimitive?.content
                ?: obj["original_name"]?.jsonPrimitive?.content
            val overview = obj["overview"]?.jsonPrimitive?.content ?: ""
            val posterPath = obj["poster_path"]?.jsonPrimitive?.content
            val backdropPath = obj["backdrop_path"]?.jsonPrimitive?.content
            val releaseDate = obj["release_date"]?.jsonPrimitive?.content
                ?: obj["first_air_date"]?.jsonPrimitive?.content
            val voteAverage = obj["vote_average"]?.jsonPrimitive?.content?.toDoubleOrNull()

            Movie(
                id = itemId,
                title = title,
                overview = overview,
                posterPath = posterPath,
                backdropPath = backdropPath,
                releaseDate = releaseDate,
                voteAverage = voteAverage,
                originalTitle = originalTitle,
            )
        }
    }

    private fun JsonElement.toMediaSummary(): MediaSummary? {
        val obj = this.jsonObject
        val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val mediaTypeRaw = obj["media_type"]?.jsonPrimitive?.content
        val isTv = mediaTypeRaw == "tv"
        val mediaType = if (isTv) "TV_SHOW" else "MOVIE"

        val title = obj["title"]?.jsonPrimitive?.content
            ?: obj["name"]?.jsonPrimitive?.content
            ?: obj["original_title"]?.jsonPrimitive?.content
            ?: obj["original_name"]?.jsonPrimitive?.content
            ?: return null

        val overview = obj["overview"]?.jsonPrimitive?.content ?: ""
        val posterPath = obj["poster_path"]?.jsonPrimitive?.content
        val backdropPath = obj["backdrop_path"]?.jsonPrimitive?.content
        val voteAverage = obj["vote_average"]?.jsonPrimitive?.content?.toDoubleOrNull()

        return MediaSummary(
            id = id,
            title = title,
            overview = overview,
            posterPath = posterPath,
            backdropPath = backdropPath,
            mediaType = mediaType,
            voteAverage = voteAverage
        )
    }

    private fun JsonElement.toMovie(): Movie? {
        val obj = this.jsonObject
        val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val title = obj["title"]?.jsonPrimitive?.content
            ?: obj["original_title"]?.jsonPrimitive?.content
            ?: return null
        val originalTitle = obj["original_title"]?.jsonPrimitive?.content
        val overview = obj["overview"]?.jsonPrimitive?.content ?: ""
        val posterPath = obj["poster_path"]?.jsonPrimitive?.content
        val backdropPath = obj["backdrop_path"]?.jsonPrimitive?.content
        val releaseDate = obj["release_date"]?.jsonPrimitive?.content
        val voteAverage = obj["vote_average"]?.jsonPrimitive?.content?.toDoubleOrNull()

        return Movie(
            id = id,
            title = title,
            overview = overview,
            posterPath = posterPath,
            backdropPath = backdropPath,
            releaseDate = releaseDate,
            voteAverage = voteAverage,
            originalTitle = originalTitle
        )
    }

    private fun JsonElement.toTvShow(): TvShow? {
        val obj = this.jsonObject
        val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val title = obj["name"]?.jsonPrimitive?.content
            ?: obj["original_name"]?.jsonPrimitive?.content
            ?: return null
        val originalTitle = obj["original_name"]?.jsonPrimitive?.content
        val overview = obj["overview"]?.jsonPrimitive?.content ?: ""
        val posterPath = obj["poster_path"]?.jsonPrimitive?.content
        val backdropPath = obj["backdrop_path"]?.jsonPrimitive?.content
        val firstAirDate = obj["first_air_date"]?.jsonPrimitive?.content
        val voteAverage = obj["vote_average"]?.jsonPrimitive?.content?.toDoubleOrNull()

        return TvShow(
            id = id,
            title = title,
            overview = overview,
            posterPath = posterPath,
            backdropPath = backdropPath,
            firstAirDate = firstAirDate,
            voteAverage = voteAverage,
            originalTitle = originalTitle
        )
    }

    private fun JsonElement.toGenre(): Genre? {
        val obj = this.jsonObject
        val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val name = obj["name"]?.jsonPrimitive?.content ?: return null
        return Genre(id = id, name = name)
    }

    private fun JsonObject.toFullMovie(): Movie {
        val id = this["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val title = this["title"]?.jsonPrimitive?.content
            ?: this["original_title"]?.jsonPrimitive?.content ?: ""
        val originalTitle = this["original_title"]?.jsonPrimitive?.content
        val overview = this["overview"]?.jsonPrimitive?.content ?: ""
        val posterPath = this["poster_path"]?.jsonPrimitive?.content
        val backdropPath = this["backdrop_path"]?.jsonPrimitive?.content
        val releaseDate = this["release_date"]?.jsonPrimitive?.content
        val voteAverage = this["vote_average"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val duration = this["runtime"]?.jsonPrimitive?.content?.toIntOrNull()

        val genres = this["genres"]?.jsonArray?.mapNotNull { it.toGenre() } ?: emptyList()
        val countries = this["production_countries"]?.jsonArray?.mapNotNull {
            it.jsonObject["name"]?.jsonPrimitive?.content
        } ?: emptyList()

        val cast = this["credits"]?.jsonObject?.get("cast")?.jsonArray?.mapNotNull { item ->
            val obj = item.jsonObject
            val castId = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val profilePath = obj["profile_path"]?.jsonPrimitive?.content
            Actor(id = castId, name = name, profilePath = profilePath)
        } ?: emptyList()

        return Movie(
            id = id,
            title = title,
            overview = overview,
            posterPath = posterPath,
            backdropPath = backdropPath,
            releaseDate = releaseDate,
            voteAverage = voteAverage,
            cast = cast,
            genres = genres,
            countries = countries,
            duration = duration,
            originalTitle = originalTitle
        )
    }

    private fun JsonObject.toFullTvShow(): TvShow {
        val id = this["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val title = this["name"]?.jsonPrimitive?.content
            ?: this["original_name"]?.jsonPrimitive?.content ?: ""
        val originalTitle = this["original_name"]?.jsonPrimitive?.content
        val overview = this["overview"]?.jsonPrimitive?.content ?: ""
        val posterPath = this["poster_path"]?.jsonPrimitive?.content
        val backdropPath = this["backdrop_path"]?.jsonPrimitive?.content
        val firstAirDate = this["first_air_date"]?.jsonPrimitive?.content
        val voteAverage = this["vote_average"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val duration = this["episode_run_time"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content?.toIntOrNull()

        val genres = this["genres"]?.jsonArray?.mapNotNull { it.toGenre() } ?: emptyList()
        val countries = this["origin_country"]?.jsonArray?.mapNotNull {
            it.jsonPrimitive.content
        } ?: emptyList()

        val cast = this["credits"]?.jsonObject?.get("cast")?.jsonArray?.mapNotNull { item ->
            val obj = item.jsonObject
            val castId = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val profilePath = obj["profile_path"]?.jsonPrimitive?.content
            Actor(id = castId, name = name, profilePath = profilePath)
        } ?: emptyList()

        return TvShow(
            id = id,
            title = title,
            overview = overview,
            posterPath = posterPath,
            backdropPath = backdropPath,
            firstAirDate = firstAirDate,
            voteAverage = voteAverage,
            seasons = emptyList(),
            cast = cast,
            genres = genres,
            countries = countries,
            duration = duration,
            originalTitle = originalTitle
        )
    }
}
