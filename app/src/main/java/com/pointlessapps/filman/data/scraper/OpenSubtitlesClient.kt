package com.pointlessapps.filman.data.scraper

import com.pointlessapps.filman.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
internal data class OpenSubtitlesSearchResponse(
    val data: List<OpenSubtitlesData> = emptyList(),
)

@Serializable
internal data class OpenSubtitlesData(
    val id: String,
    val attributes: OpenSubtitlesAttributes,
)

@Serializable
internal data class OpenSubtitlesAttributes(
    @SerialName("subtitle_id") val subtitleId: String,
    val language: String,
    val files: List<OpenSubtitlesFile> = emptyList(),
)

@Serializable
internal data class OpenSubtitlesFile(
    @SerialName("file_id") val fileId: Int,
    @SerialName("file_name") val fileName: String,
)

@Serializable
internal data class OpenSubtitlesDownloadRequest(
    @SerialName("file_id") val fileId: Int,
)

@Serializable
internal data class OpenSubtitlesDownloadResponse(
    val link: String,
    @SerialName("file_name") val fileName: String,
)

internal class OpenSubtitlesClient {
    private val client = NetworkClient.okHttpClient
    private val apiKey = BuildConfig.OPEN_SUBTITLES_API_KEY
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchAndDownload(query: String, language: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val searchUrl = okhttp3.HttpUrl.Builder()
                    .scheme("https")
                    .host("api.opensubtitles.com")
                    .addPathSegment("api")
                    .addPathSegment("v1")
                    .addPathSegment("subtitles")
                    .addQueryParameter("query", query)
                    .addQueryParameter("languages", language)
                    .build()

                val searchRequest = Request.Builder()
                    .url(searchUrl)
                    .header("Api-Key", apiKey)
                    .header("User-Agent", "filman")
                    .header("Accept", "application/json")
                    .build()

                val searchResponse = client.newCall(searchRequest).execute()
                val searchBody = searchResponse.body.string()
                if (!searchResponse.isSuccessful) return@withContext null

                val parsedSearch = json.decodeFromString<OpenSubtitlesSearchResponse>(searchBody)
                val firstSubtitleFileId =
                    parsedSearch.data.firstOrNull()?.attributes?.files?.firstOrNull()?.fileId
                        ?: return@withContext null

                val downloadUrl = "https://api.opensubtitles.com/api/v1/download"
                val downloadBody =
                    json.encodeToString(OpenSubtitlesDownloadRequest(firstSubtitleFileId))
                val downloadRequest = Request.Builder()
                    .url(downloadUrl)
                    .post(downloadBody.toRequestBody("application/json".toMediaType()))
                    .header("Api-Key", apiKey)
                    .header("User-Agent", "filman")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .build()

                val downloadResponse = client.newCall(downloadRequest).execute()
                val downloadBodyStr = downloadResponse.body.string()
                if (!downloadResponse.isSuccessful) return@withContext null

                val parsedDownload =
                    json.decodeFromString<OpenSubtitlesDownloadResponse>(downloadBodyStr)
                parsedDownload.link
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
}
