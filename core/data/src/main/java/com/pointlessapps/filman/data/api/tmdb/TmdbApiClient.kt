package com.pointlessapps.filman.data.api.tmdb

import android.util.Log
import com.pointlessapps.filman.core.data.BuildConfig
import com.pointlessapps.filman.data.api.dto.ActorDetail
import com.pointlessapps.filman.data.api.dto.Genre
import com.pointlessapps.filman.data.api.dto.MediaSummary
import com.pointlessapps.filman.data.api.dto.Movie
import com.pointlessapps.filman.data.api.dto.PaginatedResponse
import com.pointlessapps.filman.data.api.dto.Season
import com.pointlessapps.filman.data.api.dto.TvShow
import com.pointlessapps.filman.data.model.MediaRef
import com.pointlessapps.filman.data.model.TmdbFeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

class TmdbApiClient(
    private val client: OkHttpClient,
    private val localeProvider: TmdbLocaleProvider = TmdbLocaleProvider(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val imdbIdCache = ConcurrentHashMap<String, String>()
    private val sparseKidsCountries = ConcurrentHashMap.newKeySet<String>()

    private fun getApiKey(): String = BuildConfig.TMDB_API_KEY

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
            json.parseToJsonElement(response.body.string())
        }
    }

    suspend fun fetchFeed(feed: TmdbFeed, page: Int = 1): PaginatedResponse<MediaSummary> {
        val apiKey = getApiKey()
        val lang = localeProvider.languageTag()
        val country = localeProvider.countryCode()

        val kidsExtra = TmdbUrlBuilder.kidsExtraParams(feed)
        if (kidsExtra != null) {
            return fetchKidsMoviesWithFallback(apiKey, lang, page, kidsExtra)
        }

        val url = TmdbUrlBuilder.feedUrl(feed, apiKey, page, lang, country, localeProvider)
            ?: return PaginatedResponse(page, emptyList(), 1, 0)
        return executeGet(url).jsonObject.parsePagedSummaries(page, TmdbUrlBuilder.defaultMediaType(feed))
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

        val primaryUrl = TmdbUrlBuilder.kidsDiscoverUrl(apiKey, lang, page, extraParams, effectiveCountry, effectiveCert)
        val primaryResponse = executeGet(primaryUrl).jsonObject.parsePagedSummaries(page, "MOVIE")

        if (effectiveCountry != "US" && page == 1 && primaryResponse.results.size < 5) {
            sparseKidsCountries.add(certCountry)
            val fallbackUrl = TmdbUrlBuilder.kidsDiscoverUrl(apiKey, lang, page, extraParams, "US", "PG")
            val fallbackResponse = executeGet(fallbackUrl).jsonObject.parsePagedSummaries(page, "MOVIE")
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
        val url = TmdbUrlBuilder.searchUrl(getApiKey(), query, page, resolvedLanguage)
        return executeGet(url).jsonObject.parsePagedSummaries(page, null)
    }

    suspend fun findTmdbId(
        title: String,
        year: Int?,
        isTvShow: Boolean,
    ): Int? {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || title.isBlank()) return null
        val url = TmdbUrlBuilder.findTmdbIdUrl(apiKey, title, year, isTvShow)
        return executeGet(url).jsonObject.parseFirstResultId()
    }

    suspend fun getImdbId(ref: MediaRef): String? {
        val cacheKey = "${if (ref.isTvShow) "tv" else "movie"}:${ref.tmdbId}"
        imdbIdCache[cacheKey]?.let { return it }

        val apiKey = getApiKey()
        if (apiKey.isBlank()) return null
        val url = TmdbUrlBuilder.externalIdsUrl(apiKey, ref)
        return runCatching {
            val imdbId = executeGet(url).jsonObject.parseImdbId()
            if (imdbId != null) {
                imdbIdCache[cacheKey] = imdbId
            }
            imdbId
        }.getOrNull()
    }

    suspend fun getMovieGenres(language: String? = null): List<Genre> {
        val resolvedLanguage = language ?: localeProvider.languageTag()
        return fetchGenresSafely(TmdbUrlBuilder.movieGenresUrl(getApiKey(), resolvedLanguage), "movie")
    }

    suspend fun getTvGenres(language: String? = null): List<Genre> {
        val resolvedLanguage = language ?: localeProvider.languageTag()
        return fetchGenresSafely(TmdbUrlBuilder.tvGenresUrl(getApiKey(), resolvedLanguage), "tv")
    }

    suspend fun getCategories(language: String? = null): List<Genre> = coroutineScope {
        val movieGenresDeferred = async { getMovieGenres(language) }
        val tvGenresDeferred = async { getTvGenres(language) }
        (movieGenresDeferred.await() + tvGenresDeferred.await()).distinctBy { it.id }
    }

    private suspend fun fetchGenresSafely(url: String, kind: String): List<Genre> = try {
        executeGet(url).jsonObject.parseGenres()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("TmdbApiClient", "Failed to fetch $kind genres", e)
        emptyList()
    }

    suspend fun getMovieDetails(
        id: Int,
        language: String? = null,
    ): Movie {
        val resolvedLanguage = language ?: localeProvider.languageTag()
        val videoLanguages = preferredVideoLanguages()
        val url = TmdbUrlBuilder.movieDetailsUrl(getApiKey(), id, resolvedLanguage, videoLanguages)
        return executeGet(url).jsonObject.toFullMovie(localeProvider.languageCode())
    }

    suspend fun getTvShowDetails(
        id: Int,
        language: String? = null,
        onMetadata: (TvShow) -> Unit = {},
    ): TvShow = coroutineScope {
        val resolvedLanguage = language ?: localeProvider.languageTag()
        val apiKey = getApiKey()
        val url = TmdbUrlBuilder.tvShowDetailsUrl(apiKey, id, resolvedLanguage, preferredVideoLanguages())
        val root = executeGet(url).jsonObject
        val rawShow = root.toFullTvShow(localeProvider.languageCode())
        onMetadata(rawShow)

        val populatedSeasons = root.parseSeasonNumbers().map { sNum ->
            async {
                try {
                    val seasonUrl = TmdbUrlBuilder.seasonDetailsUrl(apiKey, id, sNum, resolvedLanguage)
                    executeGet(seasonUrl).jsonObject.parseSeason(sNum)
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
        val url = TmdbUrlBuilder.actorDetailsUrl(getApiKey(), id, resolvedLanguage)
        return executeGet(url).jsonObject.toActorDetail(id)
    }

    private fun preferredVideoLanguages(): String =
        listOf(localeProvider.languageCode(), "en", "null").distinct().joinToString(",")

    companion object {
        const val POSTER_BASE_URL = "https://image.tmdb.org/t/p/w500"
        const val BACKDROP_BASE_URL = "https://image.tmdb.org/t/p/w1280"
        const val PROFILE_BASE_URL = "https://image.tmdb.org/t/p/w342"
    }
}
