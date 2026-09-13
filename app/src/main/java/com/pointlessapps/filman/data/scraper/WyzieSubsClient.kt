package com.pointlessapps.filman.data.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
internal data class WyzieSubtitleResponse(
    val url: String,
    val display: String,
    val language: String,
    val format: String = "srt",
)

internal class WyzieSubsClient(
    private val client: OkHttpClient,
    private val tmdbClient: TmdbClient,
) {
    private val apiKey = com.pointlessapps.filman.BuildConfig.WYZIE_SUBS_API_KEY
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchSubtitles(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
    ): List<WyzieSubtitleResponse> = withContext(Dispatchers.IO) {
        try {
            val isTvShow = season != null && episode != null
            val tmdbId =
                tmdbClient.getTmdbId(title, year, isTvShow) ?: return@withContext emptyList()

            var searchUrl = "https://sub.wyzie.io/search?id=$tmdbId&key=$apiKey"
            if (isTvShow) {
                searchUrl += "&s=$season&e=$episode"
            }

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .build()

            val response = client.newCall(searchRequest).execute()
            val body = response.body.string()
            if (!response.isSuccessful) return@withContext emptyList()

            json.decodeFromString<List<WyzieSubtitleResponse>>(body)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
