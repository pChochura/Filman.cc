package com.pointlessapps.filman.data.api.tmdb

import android.util.Log
import com.pointlessapps.filman.core.data.BuildConfig
import com.pointlessapps.filman.data.api.dto.*
import com.pointlessapps.filman.data.model.ActorRole
import com.pointlessapps.filman.data.model.MediaRef
import com.pointlessapps.filman.data.model.MediaType
import com.pointlessapps.filman.data.model.TmdbFeed
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class TmdbApiClient(
    private val client: OkHttpClient,
    private val localeProvider: TmdbLocaleProvider = TmdbLocaleProvider(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "https://api.themoviedb.org/3"
    private val imdbIdCache = ConcurrentHashMap<String, String>()
    private val sparseKidsCountries = ConcurrentHashMap.newKeySet<String>()

    private fun getApiKey(): String = BuildConfig.TMDB_API_KEY

    private fun todayIsoDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    private suspend fun executeGet(url: String): JsonElement = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body.string()
                throw IllegalStateException("TMDB request failed with code ${response.code}: $body")
            }
            val bodyString = response.body.string()
            json.parseToJsonElement(bodyString)
        }
    }

    suspend fun fetchFeed(feed: TmdbFeed, page: Int = 1): PaginatedResponse<MediaSummary> {
        val apiKey = getApiKey()
        val lang = localeProvider.languageTag()
        val country = localeProvider.countryCode()

        return when (feed) {
            is TmdbFeed.TrendingAll -> {
                val url = "$baseUrl/trending/all/${feed.window.apiValue}?api_key=$apiKey&page=$page&language=$lang&include_adult=false"
                parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = null)
            }

            is TmdbFeed.TrendingMovies -> {
                val url = "$baseUrl/trending/movie/${feed.window.apiValue}?api_key=$apiKey&page=$page&language=$lang&include_adult=false"
                parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = "MOVIE")
            }

            is TmdbFeed.TrendingTv -> {
                val url = "$baseUrl/trending/tv/${feed.window.apiValue}?api_key=$apiKey&page=$page&language=$lang&include_adult=false"
                parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = "TV_SHOW")
            }

            TmdbFeed.MoviesNowPlaying -> {
                val url = "$baseUrl/movie/now_playing?api_key=$apiKey&page=$page&language=$lang&region=$country"
                parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = "MOVIE")
            }

            TmdbFeed.MoviesPopular -> {
                val url = "$baseUrl/movie/popular?api_key=$apiKey&page=$page&language=$lang&region=$country"
                parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = "MOVIE")
            }

            TmdbFeed.MoviesTopRated -> {
                val url = "$baseUrl/movie/top_rated?api_key=$apiKey&page=$page&language=$lang&region=$country"
                parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = "MOVIE")
            }

            TmdbFeed.MoviesUpcoming -> {
                val url = "$baseUrl/movie/upcoming?api_key=$apiKey&page=$page&language=$lang&region=$country"
                parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = "MOVIE")
            }

            TmdbFeed.MoviesNewReleases -> {
                val today = todayIsoDate()
                val url = "$baseUrl/discover/movie?api_key=$apiKey&page=$page&language=$lang&region=$country&include_adult=false&sort_by=primary_release_date.desc&vote_count.gte=30&primary_release_date.lte=$today"
                parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = "MOVIE")
            }

            TmdbFeed.TvOnTheAir -> {
                val url = "$baseUrl/tv/on_the_air?api_key=$apiKey&page=$page&language=$lang"
                parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = "TV_SHOW")
            }

            TmdbFeed.TvAiringToday -> {
                val url = "$baseUrl/tv/airing_today?api_key=$apiKey&page=$page&language=$lang"
                parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = "TV_SHOW")
            }

            TmdbFeed.TvPopular -> {
                val url = "$baseUrl/tv/popular?api_key=$apiKey&page=$page&language=$lang"
                parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = "TV_SHOW")
            }

            TmdbFeed.TvTopRated -> {
                val url = "$baseUrl/tv/top_rated?api_key=$apiKey&page=$page&language=$lang"
                parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = "TV_SHOW")
            }

            TmdbFeed.TvNewReleases -> {
                val today = todayIsoDate()
                val url = "$baseUrl/discover/tv?api_key=$apiKey&page=$page&language=$lang&include_adult=false&sort_by=first_air_date.desc&vote_count.gte=20&first_air_date.lte=$today"
                parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = "TV_SHOW")
            }

            TmdbFeed.KidsPopularMovies -> {
                fetchKidsMoviesWithFallback(
                    apiKey = apiKey,
                    lang = lang,
                    page = page,
                    extraParams = "with_genres=16|10751&sort_by=popularity.desc",
                )
            }

            TmdbFeed.KidsTvShows -> {
                val url = "$baseUrl/discover/tv?api_key=$apiKey&page=$page&language=$lang&include_adult=false&with_genres=10762&sort_by=popularity.desc"
                parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = "TV_SHOW")
            }

            TmdbFeed.KidsTopRatedFamily -> {
                fetchKidsMoviesWithFallback(
                    apiKey = apiKey,
                    lang = lang,
                    page = page,
                    extraParams = "with_genres=10751&sort_by=vote_average.desc&vote_count.gte=300",
                )
            }

            TmdbFeed.KidsDisneyPixar -> {
                fetchKidsMoviesWithFallback(
                    apiKey = apiKey,
                    lang = lang,
                    page = page,
                    extraParams = "with_companies=2|3&sort_by=popularity.desc",
                )
            }

            TmdbFeed.KidsDreamWorksGhibli -> {
                fetchKidsMoviesWithFallback(
                    apiKey = apiKey,
                    lang = lang,
                    page = page,
                    extraParams = "with_companies=521|10342&sort_by=popularity.desc",
                )
            }

            is TmdbFeed.DiscoverByGenre -> {
                if (feed.mediaType == MediaType.TV) {
                    val tvGenre = localeProvider.mapTvGenreIds(feed.genreId)
                    val url = "$baseUrl/discover/tv?api_key=$apiKey&with_genres=$tvGenre&page=$page&language=$lang&include_adult=false&sort_by=popularity.desc"
                    parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = "TV_SHOW")
                } else {
                    val movieGenre = localeProvider.mapMovieGenreIds(feed.genreId)
                    val url = "$baseUrl/discover/movie?api_key=$apiKey&with_genres=$movieGenre&page=$page&language=$lang&include_adult=false&sort_by=popularity.desc"
                    parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = "MOVIE")
                }
            }

            is TmdbFeed.Recommendations -> {
                val type = if (feed.ref.isTvShow) "tv" else "movie"
                val defaultType = if (feed.ref.isTvShow) "TV_SHOW" else "MOVIE"
                val url = "$baseUrl/$type/${feed.ref.tmdbId}/recommendations?api_key=$apiKey&page=$page&language=$lang"
                parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = defaultType)
            }

            TmdbFeed.PersonalizedForYou -> PaginatedResponse(page, emptyList(), 1, 0)
        }
    }

    private suspend fun fetchKidsMoviesWithFallback(
        apiKey: String,
        lang: String,
        page: Int,
        extraParams: String,
    ): PaginatedResponse<MediaSummary> {
        val (certCountry, certLte) = localeProvider.kidsMovieCertification()
        val effectiveCountry = if (certCountry in sparseKidsCountries) "US" else certCountry
        val effectiveCert = if (effectiveCountry == "US") "PG" else certLte

        val primaryUrl = "$baseUrl/discover/movie?api_key=$apiKey&page=$page&language=$lang&include_adult=false&$extraParams&certification_country=$effectiveCountry&certification.lte=${URLEncoder.encode(effectiveCert, "UTF-8")}"
        val primaryResponse = parsePagedSummaries(executeGet(primaryUrl).jsonObject, page, defaultMediaType = "MOVIE")

        if (effectiveCountry != "US" && page == 1 && primaryResponse.results.size < 5) {
            sparseKidsCountries.add(certCountry)
            val fallbackUrl = "$baseUrl/discover/movie?api_key=$apiKey&page=$page&language=$lang&include_adult=false&$extraParams&certification_country=US&certification.lte=PG"
            val fallbackResponse = parsePagedSummaries(executeGet(fallbackUrl).jsonObject, page, defaultMediaType = "MOVIE")
            if (fallbackResponse.results.size > primaryResponse.results.size) {
                return fallbackResponse
            }
        }
        return primaryResponse
    }

    suspend fun search(
        query: String = "",
        page: Int = 1,
        language: String? = null,
    ): PaginatedResponse<MediaSummary> {
        if (query.isBlank()) return PaginatedResponse(page, emptyList(), 0, 0)
        val resolvedLanguage = language ?: localeProvider.languageTag()
        val apiKey = getApiKey()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/search/multi?api_key=$apiKey&query=$encoded&page=$page&language=$resolvedLanguage&include_adult=false"
        return parsePagedSummaries(executeGet(url).jsonObject, page, defaultMediaType = null)
    }

    suspend fun findTmdbId(
        title: String,
        year: Int?,
        isTvShow: Boolean,
    ): Int? {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || title.isBlank()) return null
        val type = if (isTvShow) "tv" else "movie"
        val encoded = URLEncoder.encode(title, "UTF-8")
        var url = "$baseUrl/search/$type?api_key=$apiKey&query=$encoded&include_adult=false"
        if (year != null) {
            url += if (isTvShow) "&first_air_date_year=$year" else "&year=$year"
        }
        val root = executeGet(url).jsonObject
        val results = root["results"]?.jsonArray ?: return null
        return results.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content?.toIntOrNull()
    }

    suspend fun getImdbId(ref: MediaRef): String? {
        val cacheKey = "${if (ref.isTvShow) "tv" else "movie"}:${ref.tmdbId}"
        imdbIdCache[cacheKey]?.let { return it }

        val apiKey = getApiKey()
        if (apiKey.isBlank()) return null
        val type = if (ref.isTvShow) "tv" else "movie"
        val url = "$baseUrl/$type/${ref.tmdbId}/external_ids?api_key=$apiKey"
        return runCatching {
            val root = executeGet(url).jsonObject
            val raw = root["imdb_id"]
            val imdbId = if (raw != null && raw !is JsonNull) {
                raw.jsonPrimitive.content.takeIf { it.isNotBlank() }
            } else {
                null
            }
            if (imdbId != null) {
                imdbIdCache[cacheKey] = imdbId
            }
            imdbId
        }.getOrNull()
    }

    suspend fun getCategories(language: String? = null): List<Genre> = coroutineScope {
        val resolvedLanguage = language ?: localeProvider.languageTag()
        val apiKey = getApiKey()
        val movieGenresDeferred = async {
            try {
                val url = "$baseUrl/genre/movie/list?api_key=$apiKey&language=$resolvedLanguage"
                val root = executeGet(url).jsonObject
                root["genres"]?.jsonArray?.mapNotNull { it.toGenre() } ?: emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TmdbApiClient", "Failed to fetch movie genres", e)
                emptyList()
            }
        }
        val tvGenresDeferred = async {
            try {
                val url = "$baseUrl/genre/tv/list?api_key=$apiKey&language=$resolvedLanguage"
                val root = executeGet(url).jsonObject
                root["genres"]?.jsonArray?.mapNotNull { it.toGenre() } ?: emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TmdbApiClient", "Failed to fetch tv genres", e)
                emptyList()
            }
        }

        (movieGenresDeferred.await() + tvGenresDeferred.await()).distinctBy { it.id }
    }

    suspend fun getMovieDetails(
        id: Int,
        language: String? = null,
    ): Movie {
        val resolvedLanguage = language ?: localeProvider.languageTag()
        val apiKey = getApiKey()
        val videoLanguages = listOf(localeProvider.languageCode(), "en", "null").distinct().joinToString(",")
        val url = "$baseUrl/movie/$id?api_key=$apiKey&language=$resolvedLanguage&append_to_response=credits,recommendations,similar,videos,external_ids&include_video_language=$videoLanguages"
        val root = executeGet(url).jsonObject
        return root.toFullMovie()
    }

    suspend fun getTvShowDetails(
        id: Int,
        language: String? = null,
        onMetadata: (TvShow) -> Unit = {},
    ): TvShow = coroutineScope {
        val resolvedLanguage = language ?: localeProvider.languageTag()
        val apiKey = getApiKey()
        val videoLanguages = listOf(localeProvider.languageCode(), "en", "null").distinct().joinToString(",")
        val url = "$baseUrl/tv/$id?api_key=$apiKey&language=$resolvedLanguage&append_to_response=credits,recommendations,similar,videos,external_ids&include_video_language=$videoLanguages"
        val root = executeGet(url).jsonObject
        val rawShow = root.toFullTvShow()
        onMetadata(rawShow)

        val seasonNumbers = root["seasons"]?.jsonArray?.mapNotNull { s ->
            s.jsonObject["season_number"]?.jsonPrimitive?.content?.toIntOrNull()
        }?.filter { it > 0 } ?: emptyList()

        val populatedSeasons = seasonNumbers.map { sNum ->
            async {
                try {
                    val seasonUrl = "$baseUrl/tv/$id/season/$sNum?api_key=$apiKey&language=$resolvedLanguage"
                    val seasonRoot = executeGet(seasonUrl).jsonObject
                    val seasonName = seasonRoot["name"]?.jsonPrimitive?.content ?: "Season $sNum"
                    val episodesArray = seasonRoot["episodes"]?.jsonArray ?: JsonArray(emptyList())
                    val episodes = episodesArray.mapNotNull { epJson ->
                        val epObj = epJson.jsonObject
                        val epId = epObj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                        val epName = epObj["name"]?.jsonPrimitive?.content ?: "Episode"
                        val epNum = epObj["episode_number"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                        Episode(id = epId, name = epName, episodeNumber = epNum, seasonNumber = sNum)
                    }
                    Season(name = seasonName, seasonNumber = sNum, episodes = episodes)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("TmdbApiClient", "Failed to fetch season $sNum for show $id", e)
                    Season(name = "Season $sNum", seasonNumber = sNum, episodes = emptyList())
                }
            }
        }.awaitAll().sortedBy { it.seasonNumber }

        rawShow.copy(seasons = populatedSeasons)
    }

    suspend fun getActorDetails(
        id: Int,
        language: String? = null,
    ): ActorDetail {
        val resolvedLanguage = language ?: localeProvider.languageTag()
        val apiKey = getApiKey()
        val url = "$baseUrl/person/$id?api_key=$apiKey&language=$resolvedLanguage&append_to_response=combined_credits"
        val root = executeGet(url).jsonObject

        val creditsObj = root["combined_credits"]?.jsonObject
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
            id = root["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: id,
            name = root["name"]?.jsonPrimitive?.content ?: "",
            biography = root["biography"]?.jsonPrimitive?.contentOrNull,
            profilePath = root["profile_path"]?.jsonPrimitive?.contentOrNull,
            knownForDepartment = root["known_for_department"]?.jsonPrimitive?.contentOrNull,
            birthday = root["birthday"]?.jsonPrimitive?.contentOrNull,
            deathday = root["deathday"]?.jsonPrimitive?.contentOrNull,
            placeOfBirth = root["place_of_birth"]?.jsonPrimitive?.contentOrNull,
            castCredits = castCredits,
            directorCredits = directorCredits,
            writerCredits = writerCredits,
        )
    }

    private fun parsePagedSummaries(
        root: JsonObject,
        page: Int,
        defaultMediaType: String?,
    ): PaginatedResponse<MediaSummary> {
        val resultsArray = root["results"]?.jsonArray ?: JsonArray(emptyList())
        val items = resultsArray.mapNotNull { it.toMediaSummary(defaultMediaType) }
        val totalPages = root["total_pages"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        val totalResults = root["total_results"]?.jsonPrimitive?.content?.toIntOrNull() ?: items.size
        return PaginatedResponse(
            page = page,
            results = items,
            totalPages = totalPages,
            totalResults = totalResults,
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
        val voteAverage = obj["vote_average"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val releaseDate = obj["release_date"]?.jsonPrimitive?.contentOrNull
            ?: obj["first_air_date"]?.jsonPrimitive?.contentOrNull

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

    private fun JsonObject.extractTrailerUrl(): String? {
        val videos = this["videos"]?.jsonObject?.get("results")?.jsonArray ?: return null
        val preferredLanguage = localeProvider.languageCode()
        val youtubeVideos = videos.mapNotNull { element ->
            val obj = element.jsonObject
            val site = obj["site"]?.jsonPrimitive?.contentOrNull
            val key = obj["key"]?.jsonPrimitive?.contentOrNull
            if (site == "YouTube" && !key.isNullOrBlank()) obj else null
        }
        val best = youtubeVideos.firstOrNull {
            it["type"]?.jsonPrimitive?.contentOrNull == "Trailer" &&
                it["iso_639_1"]?.jsonPrimitive?.contentOrNull == preferredLanguage
        } ?: youtubeVideos.firstOrNull {
            it["type"]?.jsonPrimitive?.contentOrNull == "Trailer"
        } ?: youtubeVideos.firstOrNull()

        val key = best?.get("key")?.jsonPrimitive?.contentOrNull ?: return null
        return "https://www.youtube.com/watch?v=$key"
    }

    private fun JsonObject.toFullMovie(): Movie {
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
        val recommendationsTotalPages = recommendationsObj?.get("total_pages")?.jsonPrimitive?.content?.toIntOrNull() ?: 1

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
            trailerUrl = extractTrailerUrl(),
        )
    }

    private fun JsonObject.toFullTvShow(): TvShow {
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
        val recommendationsTotalPages = recommendationsObj?.get("total_pages")?.jsonPrimitive?.content?.toIntOrNull() ?: 1

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
            trailerUrl = extractTrailerUrl(),
        )
    }

    private val JsonPrimitive.contentOrNull: String?
        get() = if (this is JsonNull) null else content

    companion object {
        const val POSTER_BASE_URL = "https://image.tmdb.org/t/p/w500"
        const val BACKDROP_BASE_URL = "https://image.tmdb.org/t/p/w1280"
        const val PROFILE_BASE_URL = "https://image.tmdb.org/t/p/w342"
        private val WRITER_JOBS = setOf("Writer", "Screenplay", "Story", "Creator", "Novel")
    }
}
