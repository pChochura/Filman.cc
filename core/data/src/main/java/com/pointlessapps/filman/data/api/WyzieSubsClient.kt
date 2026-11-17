package com.pointlessapps.filman.data.api
import com.pointlessapps.filman.core.data.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class WyzieSubtitleResponse(
    val url: String,
    val display: String,
    val language: String,
    val format: String = "srt",
)

class WyzieSubsClient(
    private val client: OkHttpClient,
    private val tmdbClient: TmdbClient,
) {
    private val apiKey = BuildConfig.WYZIE_SUBS_API_KEY
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchSubtitles(
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        tmdbId: Int? = null,
        imdbId: String? = null,
    ): List<WyzieSubtitleResponse> =
        withContext(Dispatchers.IO) {
            try {
                val isTvShow = season != null && episode != null
                val resolvedId = imdbId?.takeIf { it.isNotBlank() }
                    ?: run {
                        val resolvedTmdbId = tmdbId?.takeIf { it > 0 }?.toString()
                            ?: tmdbClient.getTmdbId(title, year, isTvShow)
                        if (resolvedTmdbId != null) {
                            if (resolvedTmdbId.startsWith("tt")) {
                                resolvedTmdbId
                            } else {
                                tmdbClient.getImdbId(resolvedTmdbId, isTvShow) ?: resolvedTmdbId
                            }
                        } else {
                            null
                        }
                    }
                    ?: return@withContext emptyList()

                var searchUrl = "https://sub.wyzie.io/search?id=$resolvedId&key=$apiKey"
                if (isTvShow) {
                    searchUrl += "&s=$season&e=$episode"
                }

                val searchRequest =
                    Request
                        .Builder()
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
