package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.core.data.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Serializable
data class OpenSubtitlesSearchResponse(
    val data: List<OpenSubtitlesData> = emptyList(),
)

@Serializable
data class OpenSubtitlesData(
    val id: String,
    val attributes: OpenSubtitlesAttributes,
)

@Serializable
data class OpenSubtitlesAttributes(
    @SerialName("subtitle_id") val subtitleId: String,
    val language: String,
    val files: List<OpenSubtitlesFile> = emptyList(),
    val release: String? = null,
    @SerialName("download_count") val downloadCount: Int? = null,
    val ratings: Double? = null,
    @SerialName("foreign_parts_only") val foreignPartsOnly: Boolean? = null,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean? = null,
)

@Serializable
data class OpenSubtitlesFile(
    @SerialName("file_id") val fileId: Int,
    @SerialName("file_name") val fileName: String,
)

@Serializable
data class OpenSubtitlesDownloadRequest(
    @SerialName("file_id") val fileId: Int,
)

@Serializable
data class OpenSubtitlesDownloadResponse(
    val link: String,
    @SerialName("file_name") val fileName: String,
)

data class OpenSubtitleCandidate(
    val fileId: Int,
    val fileName: String,
    val language: String,
    val release: String?,
    val downloadCount: Int,
    val rating: Double?,
    val foreignPartsOnly: Boolean,
    val hearingImpaired: Boolean,
)

class OpenSubtitlesClient(
    private val client: OkHttpClient = NetworkClient.okHttpClient,
) {
    private val apiKey = BuildConfig.OPEN_SUBTITLES_API_KEY
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(
        query: String,
        language: String,
        season: Int? = null,
        episode: Int? = null,
        tmdbId: Int? = null,
        videoFileName: String? = null,
    ): List<OpenSubtitleCandidate> =
        withContext(Dispatchers.IO) {
            val urlBuilder =
                HttpUrl
                    .Builder()
                    .scheme("https")
                    .host("api.opensubtitles.com")
                    .addPathSegment("api")
                    .addPathSegment("v1")
                    .addPathSegment("subtitles")
                    .addQueryParameter("languages", language)

            if (tmdbId != null && tmdbId > 0) {
                val idParameter = if (season != null && episode != null) "parent_tmdb_id" else "tmdb_id"
                urlBuilder.addQueryParameter(idParameter, tmdbId.toString())
            } else {
                urlBuilder.addQueryParameter("query", query)
            }

            if (season != null && episode != null) {
                urlBuilder.addQueryParameter("season_number", season.toString())
                urlBuilder.addQueryParameter("episode_number", episode.toString())
            }

            val searchUrl = urlBuilder.build()

            val searchRequest =
                Request
                    .Builder()
                    .url(searchUrl)
                    .header("Api-Key", apiKey)
                    .header("User-Agent", "filman")
                    .header("Accept", "application/json")
                    .build()

            val parsedSearch = client.newCall(searchRequest).execute().use { response ->
                if (!response.isSuccessful) throw IOException("OpenSubtitles search failed: HTTP ${response.code}")
                json.decodeFromString<OpenSubtitlesSearchResponse>(response.body.string())
            }
            val candidates = parsedSearch.data.flatMap { item ->
                item.attributes.files.map { file ->
                    OpenSubtitleCandidate(
                        fileId = file.fileId,
                        fileName = file.fileName,
                        language = item.attributes.language,
                        release = item.attributes.release,
                        downloadCount = item.attributes.downloadCount ?: 0,
                        rating = item.attributes.ratings,
                        foreignPartsOnly = item.attributes.foreignPartsOnly == true,
                        hearingImpaired = item.attributes.hearingImpaired == true,
                    )
                }
            }.distinctBy { it.fileId }
            rankOpenSubtitleCandidates(candidates, videoFileName)
        }

    suspend fun download(fileId: Int): OpenSubtitlesDownloadResponse =
        withContext(Dispatchers.IO) {
            val downloadUrl = "https://api.opensubtitles.com/api/v1/download"
            val downloadBody =
                json.encodeToString(OpenSubtitlesDownloadRequest(fileId))
            val downloadRequest =
                Request
                    .Builder()
                    .url(downloadUrl)
                    .post(downloadBody.toRequestBody("application/json".toMediaType()))
                    .header("Api-Key", apiKey)
                    .header("User-Agent", "filman")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .build()

            client.newCall(downloadRequest).execute().use { response ->
                if (!response.isSuccessful) throw IOException("OpenSubtitles download failed: HTTP ${response.code}")
                json.decodeFromString<OpenSubtitlesDownloadResponse>(response.body.string())
            }
        }
}

internal fun rankOpenSubtitleCandidates(
    candidates: List<OpenSubtitleCandidate>,
    videoFileName: String?,
): List<OpenSubtitleCandidate> {
    fun normalizedBaseName(name: String): String =
        name.substringAfterLast('/').replace(Regex("\\.(srt|vtt|mkv|mp4|avi|webm)$", RegexOption.IGNORE_CASE), "")
            .lowercase().replace(Regex("[^a-z0-9]+"), "")

    val videoBase = videoFileName?.let(::normalizedBaseName)?.takeIf { it.length >= 12 }
    return candidates.sortedWith(
        compareBy<OpenSubtitleCandidate> { it.foreignPartsOnly }
            .thenByDescending { candidate ->
                videoBase != null && listOfNotNull(candidate.release, candidate.fileName)
                    .any { normalizedBaseName(it) == videoBase }
            }
            .thenByDescending { it.downloadCount }
            .thenByDescending { it.rating ?: 0.0 },
    )
}
