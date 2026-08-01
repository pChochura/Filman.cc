package com.pointlessapps.filman.data.scraper

import com.pointlessapps.filman.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

internal class TmdbClient(
    private val client: OkHttpClient,
) {
    private val json: Json = Json { ignoreUnknownKeys = true }

    suspend fun getTrailerUrl(title: String, year: Int?, isTvShow: Boolean): String? =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.TMDB_API_KEY
            if (apiKey.isEmpty()) return@withContext null

            try {
                val type = if (isTvShow) "tv" else "movie"
                val encodedTitle = URLEncoder.encode(title, "UTF-8")
                var searchUrl =
                    "https://api.themoviedb.org/3/search/$type?query=$encodedTitle&api_key=$apiKey"
                if (year != null) {
                    searchUrl += if (isTvShow) {
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

                val tmdbId =
                    results[0].jsonObject["id"]?.jsonPrimitive?.content ?: return@withContext null

                val videosUrl = "https://api.themoviedb.org/3/$type/$tmdbId/videos?api_key=$apiKey"
                val videosRequest = Request.Builder().url(videosUrl).build()
                val videosResponse = client.newCall(videosRequest).execute()
                if (!videosResponse.isSuccessful) return@withContext null

                val videosBody = videosResponse.body.string()
                val videosJson = json.parseToJsonElement(videosBody).jsonObject
                val videoResults = videosJson["results"]?.jsonArray
                if (videoResults.isNullOrEmpty()) return@withContext null

                // Find a YouTube trailer
                val trailer = videoResults.firstOrNull {
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
}
