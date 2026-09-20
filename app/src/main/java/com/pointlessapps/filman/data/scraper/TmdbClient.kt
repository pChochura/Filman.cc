package com.pointlessapps.filman.data.scraper

import com.pointlessapps.filman.BuildConfig
import com.pointlessapps.filman.data.model.EmbedLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale

internal class TmdbClient(
    private val client: OkHttpClient,
) {
    private val json: Json = Json { ignoreUnknownKeys = true }

    suspend fun getTmdbId(
        title: String,
        year: Int?,
        isTvShow: Boolean,
    ): String? =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.TMDB_API_KEY
            if (apiKey.isEmpty()) return@withContext null

            try {
                val type = if (isTvShow) "tv" else "movie"
                val encodedTitle = URLEncoder.encode(title, "UTF-8")
                var searchUrl =
                    "https://api.themoviedb.org/3/search/$type?query=$encodedTitle&api_key=$apiKey"
                if (year != null) {
                    searchUrl +=
                        if (isTvShow) {
                            "&first_air_date_year=$year"
                        } else {
                            "&year=$year"
                        }
                }

                val searchRequest = Request.Builder().url(searchUrl).build()
                val searchResponse = client.newCall(searchRequest).execute()
                if (!searchResponse.isSuccessful) return@withContext null

                val searchBody = searchResponse.body.string()
                val searchJson = json.parseToJsonElement(searchBody).jsonObject
                val results = searchJson["results"]?.jsonArray
                if (results.isNullOrEmpty()) return@withContext null

                results[0].jsonObject["id"]?.jsonPrimitive?.content
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    suspend fun getEmbeds(
        title: String,
        year: Int?,
        season: Int? = null,
        episode: Int? = null,
    ): List<EmbedLink> =
        withContext(Dispatchers.IO) {
            val isTvShow = season != null && episode != null
            val tmdbId = getTmdbId(title, year, isTvShow) ?: return@withContext emptyList()

            val videasyServers =
                listOf(
                    "Yoru" to "yoru",
                    "Breach" to "breach",
                    "Neon" to "neon",
                    "Cypher" to "cypher",
                    "Vyse" to "vyse",
                )

            if (isTvShow) {
                val list =
                    mutableListOf(
                        EmbedLink(
                            url = "https://vsembed.ru/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode",
                            serverName = "VidSrc",
                            quality = "1080p",
                            version = "",
                            sourceWebsite = "tmdb",
                        ),
                        EmbedLink(
                            url = "https://vidcore.io/tv/$tmdbId/$season/$episode?autoPlay=true",
                            serverName = "VidCore",
                            quality = "1080p",
                            version = "",
                            sourceWebsite = "tmdb",
                        ),
                        EmbedLink(
                            url = "https://vidfast.vc/tv/$tmdbId/$season/$episode?autoPlay=true",
                            serverName = "VidFast",
                            quality = "1080p",
                            version = "",
                            sourceWebsite = "tmdb",
                        ),
                        EmbedLink(
                            url = "https://vidnest.fun/tv/$tmdbId/$season/$episode",
                            serverName = "VidNest",
                            quality = "1080p",
                            version = "",
                            sourceWebsite = "tmdb",
                        ),
                    )
                videasyServers.forEach { (name, param) ->
                    list.add(
                        EmbedLink(
                            url = "https://player.videasy.to/tv/$tmdbId/$season/$episode?server=$param",
                            serverName = "Videasy ($name)",
                            quality = "1080p",
                            version = "",
                            sourceWebsite = "tmdb",
                        ),
                    )
                }
                list
            } else {
                val list =
                    mutableListOf(
                        EmbedLink(
                            url = "https://vsembed.ru/embed/movie?tmdb=$tmdbId",
                            serverName = "VidSrc",
                            quality = "1080p",
                            version = "",
                            sourceWebsite = "tmdb",
                        ),
                        EmbedLink(
                            url = "https://vidcore.io/movie/$tmdbId?autoPlay=true",
                            serverName = "VidCore",
                            quality = "1080p",
                            version = "",
                            sourceWebsite = "tmdb",
                        ),
                        EmbedLink(
                            url = "https://vidfast.vc/movie/$tmdbId?autoPlay=true",
                            serverName = "VidFast",
                            quality = "1080p",
                            version = "",
                            sourceWebsite = "tmdb",
                        ),
                        EmbedLink(
                            url = "https://vidnest.fun/movie/$tmdbId",
                            serverName = "VidNest",
                            quality = "1080p",
                            version = "",
                            sourceWebsite = "tmdb",
                        ),
                    )
                videasyServers.forEach { (name, param) ->
                    list.add(
                        EmbedLink(
                            url = "https://player.videasy.to/movie/$tmdbId?server=$param",
                            serverName = "Videasy ($name)",
                            quality = "1080p",
                            version = "",
                            sourceWebsite = "tmdb",
                        ),
                    )
                }
                list
            }
        }

    suspend fun getTrailerUrl(
        title: String,
        year: Int?,
        isTvShow: Boolean,
    ): String? =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.TMDB_API_KEY
            if (apiKey.isEmpty()) return@withContext null

            try {
                val tmdbId = getTmdbId(title, year, isTvShow) ?: return@withContext null
                val type = if (isTvShow) "tv" else "movie"

                val videosUrl = "https://api.themoviedb.org/3/$type/$tmdbId/videos?api_key=$apiKey"
                val videosRequest = Request.Builder().url(videosUrl).build()
                val videosResponse = client.newCall(videosRequest).execute()
                if (!videosResponse.isSuccessful) return@withContext null

                val videosBody = videosResponse.body.string()
                val videosJson = json.parseToJsonElement(videosBody).jsonObject
                val videoResults = videosJson["results"]?.jsonArray
                if (videoResults.isNullOrEmpty()) return@withContext null

                // Find a YouTube trailer
                val trailer =
                    videoResults.firstOrNull {
                        val site = it.jsonObject["site"]?.jsonPrimitive?.content
                        val videoType = it.jsonObject["type"]?.jsonPrimitive?.content
                        site == "YouTube" && videoType == "Trailer"
                    } ?: videoResults.firstOrNull {
                        it.jsonObject["site"]?.jsonPrimitive?.content == "YouTube"
                    }

                trailer?.jsonObject?.get("key")?.jsonPrimitive?.content?.let { key ->
                    "https://www.youtube.com/watch?v=$key"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    suspend fun getTrendingMovies(): List<TrendingMovie> =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.TMDB_API_KEY
            if (apiKey.isEmpty()) return@withContext emptyList()

            try {
                val language = Locale.getDefault().toLanguageTag()
                val searchUrl =
                    "https://api.themoviedb.org/3/trending/all/day?api_key=$apiKey&language=$language"
                val searchRequest = Request.Builder().url(searchUrl).build()
                val searchResponse = client.newCall(searchRequest).execute()
                if (!searchResponse.isSuccessful) return@withContext emptyList()

                val searchBody = searchResponse.body.string()
                val searchJson = json.parseToJsonElement(searchBody).jsonObject
                val results = searchJson["results"]?.jsonArray
                if (results.isNullOrEmpty()) return@withContext emptyList()

                results.mapNotNull {
                    val posterPath = it.jsonObject["backdrop_path"]?.jsonPrimitive?.content
                    val title = it.jsonObject["title"]?.jsonPrimitive?.content
                        ?: it.jsonObject["name"]?.jsonPrimitive?.content
                        ?: it.jsonObject["original_name"]?.jsonPrimitive?.content ?: ""
                    val overview = it.jsonObject["overview"]?.jsonPrimitive?.content ?: ""
                    val rating =
                        it.jsonObject["vote_average"]?.jsonPrimitive?.content?.toDoubleOrNull()
                            ?: 0.0
                    val releaseDate = it.jsonObject["release_date"]?.jsonPrimitive?.content
                        ?: it.jsonObject["first_air_date"]?.jsonPrimitive?.content
                    val releaseYear = releaseDate?.take(4)?.toIntOrNull()
                    val mediaType = it.jsonObject["media_type"]?.jsonPrimitive?.content

                    if (posterPath != null && (mediaType == "movie" || mediaType == "tv")) {
                        TrendingMovie(
                            title = title,
                            overview = overview,
                            rating = rating,
                            posterUrl = "https://image.tmdb.org/t/p/original$posterPath",
                            releaseYear = releaseYear,
                        )
                    } else null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    suspend fun getRecommendations(
        tmdbId: String,
        isTvShow: Boolean,
    ): List<TmdbMovie> =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.TMDB_API_KEY
            if (apiKey.isEmpty()) return@withContext emptyList()

            try {
                val type = if (isTvShow) "tv" else "movie"
                val language = Locale.getDefault().toLanguageTag()
                val url = "https://api.themoviedb.org/3/$type/$tmdbId/recommendations?api_key=$apiKey&language=$language"
                
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext emptyList()

                val body = response.body.string()
                val jsonBody = json.parseToJsonElement(body).jsonObject
                val results = jsonBody["results"]?.jsonArray
                if (results.isNullOrEmpty()) return@withContext emptyList()

                results.mapNotNull {
                    val id = it.jsonObject["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val posterPath = it.jsonObject["poster_path"]?.jsonPrimitive?.content
                    val title = it.jsonObject["title"]?.jsonPrimitive?.content
                        ?: it.jsonObject["name"]?.jsonPrimitive?.content
                        ?: it.jsonObject["original_name"]?.jsonPrimitive?.content ?: ""
                    val releaseDate = it.jsonObject["release_date"]?.jsonPrimitive?.content
                        ?: it.jsonObject["first_air_date"]?.jsonPrimitive?.content
                    val releaseYear = releaseDate?.take(4)?.toIntOrNull()

                    TmdbMovie(
                        id = id,
                        title = title,
                        posterUrl = if (posterPath != null) "https://image.tmdb.org/t/p/w500$posterPath" else "",
                        releaseYear = releaseYear,
                        isTvShow = isTvShow,
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
}

data class TrendingMovie(
    val title: String,
    val overview: String,
    val rating: Double,
    val posterUrl: String,
    val releaseYear: Int?,
)

data class TmdbMovie(
    val id: String,
    val title: String,
    val posterUrl: String,
    val releaseYear: Int?,
    val isTvShow: Boolean,
)
