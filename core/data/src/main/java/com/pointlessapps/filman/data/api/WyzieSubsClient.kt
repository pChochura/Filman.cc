package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.core.data.BuildConfig
import com.pointlessapps.filman.data.api.tmdb.TmdbApiClient
import com.pointlessapps.filman.data.model.MediaRef
import com.pointlessapps.filman.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
data class WyzieSubtitleResponse(
    val id: String? = null,
    val url: String,
    val display: String,
    val language: String,
    val format: String = "srt",
    val media: String? = null,
    val release: String? = null,
    val fileName: String? = null,
    val downloadCount: Int? = null,
    val isHearingImpaired: Boolean = false,
    val ai: Boolean = false,
)

class WyzieSubsClient(
    client: OkHttpClient,
    private val tmdbApiClient: TmdbApiClient,
) {
    private val client = client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val apiKey = BuildConfig.WYZIE_SUBS_API_KEY
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchSubtitles(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        tmdbId: Int? = null,
        imdbId: String? = null,
        language: String? = null,
    ): List<WyzieSubtitleResponse> =
        withContext(Dispatchers.IO) {
            try {
                val isTvShow = season != null && episode != null
                val resolvedId = imdbId?.takeIf { it.isNotBlank() }
                    ?: run {
                        val resolvedTmdbId = tmdbId?.takeIf { it > 0 }
                            ?: tmdbApiClient.findTmdbId(title, year, isTvShow)
                        if (resolvedTmdbId != null) {
                            val ref = MediaRef(
                                type = if (isTvShow) MediaType.TV else MediaType.MOVIE,
                                tmdbId = resolvedTmdbId,
                            )
                            tmdbApiClient.getImdbId(ref) ?: resolvedTmdbId.toString()
                        } else {
                            null
                        }
                    }
                    ?: return@withContext emptyList()

                var searchUrl = "https://sub.wyzie.io/search?id=$resolvedId&key=$apiKey"
                if (isTvShow) {
                    searchUrl += "&season=$season&episode=$episode"
                }
                if (!language.isNullOrBlank()) {
                    searchUrl += "&language=${language.lowercase()}"
                }

                val searchRequest =
                    Request
                        .Builder()
                        .url(searchUrl)
                        .build()

                client.newCall(searchRequest).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) return@withContext emptyList()
                    json.decodeFromString<List<WyzieSubtitleResponse>>(body)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
}
