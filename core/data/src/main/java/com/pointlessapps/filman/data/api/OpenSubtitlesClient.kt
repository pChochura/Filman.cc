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
import kotlin.math.ln

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
    val votes: Int? = null,
    val fps: Double? = null,
    @SerialName("from_trusted") val fromTrusted: Boolean? = null,
    @SerialName("ai_translated") val aiTranslated: Boolean? = null,
    @SerialName("machine_translated") val machineTranslated: Boolean? = null,
    @SerialName("foreign_parts_only") val foreignPartsOnly: Boolean? = null,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean? = null,
    @SerialName("moviehash_match") val moviehashMatch: Boolean? = null,
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
    val votes: Int? = null,
    val fps: Double? = null,
    val fromTrusted: Boolean = false,
    val aiTranslated: Boolean = false,
    val foreignPartsOnly: Boolean = false,
    val hearingImpaired: Boolean = false,
    val moviehashMatch: Boolean = false,
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
        imdbId: String? = null,
        videoFileName: String? = null,
        movieHash: String? = null,
    ): List<OpenSubtitleCandidate> =
        withContext(Dispatchers.IO) {
            val candidates = mutableListOf<OpenSubtitleCandidate>()

            // 1. If movieHash is present, query with moviehash first
            if (!movieHash.isNullOrBlank()) {
                val hashUrl =
                    HttpUrl.Builder()
                        .scheme("https")
                        .host("api.opensubtitles.com")
                        .addPathSegment("api")
                        .addPathSegment("v1")
                        .addPathSegment("subtitles")
                        .addQueryParameter("languages", language)
                        .addQueryParameter("moviehash", movieHash)
                        .build()
                val hashCandidates = runCatching { executeSearch(hashUrl) }.getOrNull()
                if (!hashCandidates.isNullOrEmpty()) {
                    candidates.addAll(hashCandidates)
                }
            }

            // 2. Query by tmdbId/parent_tmdb_id or imdbId or query title
            val urlBuilder =
                HttpUrl.Builder()
                    .scheme("https")
                    .host("api.opensubtitles.com")
                    .addPathSegment("api")
                    .addPathSegment("v1")
                    .addPathSegment("subtitles")
                    .addQueryParameter("languages", language)

            if (tmdbId != null && tmdbId > 0) {
                val idParameter = if (season != null && episode != null) "parent_tmdb_id" else "tmdb_id"
                urlBuilder.addQueryParameter(idParameter, tmdbId.toString())
            } else if (!imdbId.isNullOrBlank()) {
                val numericImdb = imdbId.removePrefix("tt").toIntOrNull()
                if (numericImdb != null) {
                    val idParameter = if (season != null && episode != null) "parent_imdb_id" else "imdb_id"
                    urlBuilder.addQueryParameter(idParameter, numericImdb.toString())
                } else {
                    urlBuilder.addQueryParameter("query", query)
                }
            } else {
                urlBuilder.addQueryParameter("query", query)
            }

            if (season != null && episode != null) {
                urlBuilder.addQueryParameter("season_number", season.toString())
                urlBuilder.addQueryParameter("episode_number", episode.toString())
            }

            val idCandidates = executeSearch(urlBuilder.build())
            candidates.addAll(idCandidates)

            rankOpenSubtitleCandidates(candidates.distinctBy { it.fileId }, videoFileName)
        }

    private fun executeSearch(url: HttpUrl): List<OpenSubtitleCandidate> {
        val searchRequest =
            Request.Builder()
                .url(url)
                .header("Api-Key", apiKey)
                .header("User-Agent", "filman")
                .header("Accept", "application/json")
                .build()

        val parsedSearch = client.newCall(searchRequest).execute().use { response ->
            if (!response.isSuccessful) throw IOException("OpenSubtitles search failed: HTTP ${response.code}")
            json.decodeFromString<OpenSubtitlesSearchResponse>(response.body.string())
        }
        return parsedSearch.data.flatMap { item ->
            item.attributes.files.map { file ->
                OpenSubtitleCandidate(
                    fileId = file.fileId,
                    fileName = file.fileName,
                    language = item.attributes.language,
                    release = item.attributes.release,
                    downloadCount = item.attributes.downloadCount ?: 0,
                    rating = item.attributes.ratings,
                    votes = item.attributes.votes,
                    fps = item.attributes.fps,
                    fromTrusted = item.attributes.fromTrusted == true,
                    aiTranslated = item.attributes.aiTranslated == true || item.attributes.machineTranslated == true,
                    foreignPartsOnly = item.attributes.foreignPartsOnly == true,
                    hearingImpaired = item.attributes.hearingImpaired == true,
                    moviehashMatch = item.attributes.moviehashMatch == true,
                )
            }
        }.distinctBy { it.fileId }
    }

    suspend fun download(fileId: Int): OpenSubtitlesDownloadResponse =
        withContext(Dispatchers.IO) {
            val downloadUrl = "https://api.opensubtitles.com/api/v1/download"
            val downloadBody =
                json.encodeToString(OpenSubtitlesDownloadRequest(fileId))
            val downloadRequest =
                Request.Builder()
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

enum class RipSourceType {
    WEB, BLURAY, HDTV, DVDRIP, CAM
}

private val KNOWN_GROUPS = setOf(
    "flux", "yts", "yify", "cmrg", "evo", "sparks", "ion10", "ntb", "tepes",
    "glhf", "smurf", "kings", "kogi", "geckos", "rovers", "amiable", "fgt",
    "rarbg", "psa", "galaxyrg", "etrg", "megusta", "ctrlhd", "don", "ebp",
    "framestor", "qxr", "bwb", "as68", "tgx", "pahe", "vxt", "galaxy",
)

private val STOP_WORDS = setOf(
    "the", "a", "an", "and", "or", "in", "of", "to", "for", "with",
    "pl", "pol", "polish", "polski", "napisy", "srt", "sub", "vtt",
    "lektor", "dubbing", "audio", "video", "multisubs", "multi", "264", "265"
)

fun extractReleaseGroup(name: String): String? {
    val clean = name.substringBeforeLast('.').trim()
    val groupMatch = Regex("""-([a-zA-Z0-9]+)(?:\[.*?\])?$""").find(clean)
    val group = groupMatch?.groupValues?.get(1)?.lowercase()
    if (!group.isNullOrBlank() && group.length >= 2 && group !in setOf(
            "x264", "x265", "hevc", "h264", "h265", "aac", "ddp5", "1080p", "720p", "2160p", "4k", "dl", "rip", "web", "sub", "srt"
        )
    ) {
        return group
    }
    val tokens = clean.split(Regex("[^a-zA-Z0-9]+")).map { it.lowercase() }
    return tokens.lastOrNull { it in KNOWN_GROUPS }
}

fun detectSourceType(name: String): RipSourceType? {
    val lower = name.lowercase()
    return when {
        lower.contains(Regex("""\b(web-?dl|web-?rip|web)\b""")) -> RipSourceType.WEB
        lower.contains(Regex("""\b(bluray|blu-ray|bdrip|brrip|bd-rip)\b""")) -> RipSourceType.BLURAY
        lower.contains(Regex("""\b(hdtv|pdtv|dsr)\b""")) -> RipSourceType.HDTV
        lower.contains(Regex("""\b(dvdrip|dvd-rip|dvd)\b""")) -> RipSourceType.DVDRIP
        lower.contains(Regex("""\b(cam|camrip|telesync|hdts|ts|hdcam)\b""")) -> RipSourceType.CAM
        else -> null
    }
}

fun detectEffectiveSourceType(name: String?, videoUrl: String? = null): RipSourceType {
    if (!name.isNullOrBlank()) {
        val detected = detectSourceType(name)
        if (detected != null) return detected
    }
    if (!videoUrl.isNullOrBlank()) {
        val detected = detectSourceType(videoUrl)
        if (detected != null) return detected
        if (videoUrl.contains(".m3u8", ignoreCase = true) ||
            videoUrl.contains("/hls/", ignoreCase = true) ||
            videoUrl.contains(".mpd", ignoreCase = true)
        ) {
            return RipSourceType.WEB
        }
    }
    return RipSourceType.WEB
}

internal fun detectResolution(name: String): String? {
    val match = Regex("""\b(2160p|4k|1080p|720p|480p)\b""", RegexOption.IGNORE_CASE).find(name)
    return match?.value?.lowercase()?.replace("4k", "2160p")
}

internal fun detectCodec(name: String): String? {
    val lower = name.lowercase()
    return when {
        lower.contains(Regex("""\b(x264|h264|avc)\b""")) -> "h264"
        lower.contains(Regex("""\b(x265|h265|hevc)\b""")) -> "hevc"
        lower.contains(Regex("""\b(av1)\b""")) -> "av1"
        lower.contains(Regex("""\b(xvid)\b""")) -> "xvid"
        else -> null
    }
}

internal fun tokenize(name: String): Set<String> = VideoSignatureExtractor.tokenize(name)

internal fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double = VideoSignatureExtractor.jaccardSimilarity(a, b)

internal fun normalizedBase(name: String): String =
    name.substringAfterLast('/')
        .replace(Regex("\\.(srt|vtt|mkv|mp4|avi|webm)$", RegexOption.IGNORE_CASE), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "")

internal fun detectStreamingService(name: String): String? {
    val lower = name.lowercase()
    return when {
        lower.contains(Regex("""\b(amzn|amazon)\b""")) -> "amzn"
        lower.contains(Regex("""\b(nf|netflix)\b""")) -> "netflix"
        lower.contains(Regex("""\b(dsnp|disney|disney\+)\b""")) -> "disney"
        lower.contains(Regex("""\b(atvp|apple|apple\s*tv)\b""")) -> "appletv"
        lower.contains(Regex("""\b(hbo|max|hmax)\b""")) -> "max"
        lower.contains(Regex("""\b(hulu)\b""")) -> "hulu"
        lower.contains(Regex("""\b(pmtp|paramount|paramount\+)\b""")) -> "paramount"
        lower.contains(Regex("""\b(pcok|peacock)\b""")) -> "peacock"
        lower.contains(Regex("""\b(sky)\b""")) -> "sky"
        else -> null
    }
}

internal fun detectCutEdition(name: String): String? {
    val lower = name.lowercase()
    return when {
        lower.contains(Regex("""\b(extended|ext)\b""")) -> "extended"
        lower.contains(Regex("""\b(directors\.?cut|dc)\b""")) -> "directors_cut"
        lower.contains(Regex("""\b(unrated)\b""")) -> "unrated"
        lower.contains(Regex("""\b(theatrical)\b""")) -> "theatrical"
        lower.contains(Regex("""\b(imax)\b""")) -> "imax"
        else -> null
    }
}

internal fun calculateCandidateScore(
    candidate: OpenSubtitleCandidate,
    videoFileName: String?,
    preferredGroup: String? = null,
    preferredSource: String? = null,
    targetGroupToAvoid: String? = null,
): Double {
    var score = 0.0

    if (candidate.moviehashMatch) {
        score += 5000.0
    }

    if (candidate.foreignPartsOnly) {
        score -= 2000.0
    }

    if (candidate.aiTranslated) {
        score -= 100.0
    }

    if (candidate.fromTrusted) {
        score += 50.0
    }

    if (candidate.hearingImpaired) {
        score -= 35.0
    }

    score += ln(1.0 + candidate.downloadCount) * 1.5
    if (candidate.rating != null && candidate.rating > 0.0) {
        val voteWeight = if ((candidate.votes ?: 0) >= 5) 2.0 else 1.0
        score += candidate.rating * voteWeight
    }

    val candidateNames = listOfNotNull(candidate.release, candidate.fileName)

    if (!preferredGroup.isNullOrBlank()) {
        val matchesPrefGroup = candidateNames.any { name ->
            val candClean = VideoSignatureExtractor.cleanFilename(name).lowercase()
            val candGroup = extractReleaseGroup(candClean)
            (candGroup != null && candGroup.equals(preferredGroup, ignoreCase = true)) ||
                candClean.contains("-${preferredGroup.lowercase()}") ||
                candClean.endsWith(preferredGroup.lowercase())
        }
        if (matchesPrefGroup) {
            score += 250.0
        }
    }

    if (!preferredSource.isNullOrBlank()) {
        val candSource = candidateNames.firstNotNullOfOrNull { detectSourceType(it.lowercase()) }?.name
        if (candSource != null && candSource.equals(preferredSource, ignoreCase = true)) {
            score += 150.0
        } else if (candSource != null) {
            score -= 60.0
        }
    }

    if (!targetGroupToAvoid.isNullOrBlank()) {
        val targetLower = targetGroupToAvoid.lowercase()
        val matchesTargetGroup = candidateNames.any { name ->
            val candClean = VideoSignatureExtractor.cleanFilename(name).lowercase()
            val candGroup = extractReleaseGroup(candClean)
            (candGroup != null && candGroup == targetLower) ||
                candClean.contains("-$targetLower") ||
                candClean.endsWith(targetLower)
        }
        if (matchesTargetGroup) {
            score -= 300.0
        }
    }

    if (videoFileName.isNullOrBlank()) {
        return score
    }

    val videoClean = VideoSignatureExtractor.cleanFilename(videoFileName).lowercase()
    val videoBase = normalizedBase(videoFileName)

    val hasExactMatch = candidateNames.any { name ->
        val norm = normalizedBase(name)
        norm.length >= 8 && norm == videoBase
    }
    if (hasExactMatch) {
        score += 300.0
    }

    // Release group match
    val videoGroup = extractReleaseGroup(videoClean)
    if (!videoGroup.isNullOrBlank()) {
        val matchesGroup = candidateNames.any { name ->
            val candClean = VideoSignatureExtractor.cleanFilename(name).lowercase()
            val candGroup = extractReleaseGroup(candClean)
            (candGroup != null && candGroup == videoGroup) ||
                candClean.contains("-$videoGroup") ||
                candClean.endsWith(videoGroup)
        }
        if (matchesGroup) {
            score += 200.0
        }
    }

    // Streaming service match
    val videoService = detectStreamingService(videoClean)
    val candidateService = candidateNames.firstNotNullOfOrNull { detectStreamingService(it.lowercase()) }
    if (videoService != null && candidateService != null) {
        if (videoService == candidateService) {
            score += 150.0
        } else {
            score -= 50.0
        }
    }

    // Cut / Edition match
    val videoCut = detectCutEdition(videoClean)
    val candidateCut = candidateNames.firstNotNullOfOrNull { detectCutEdition(it.lowercase()) }
    if (videoCut != null || candidateCut != null) {
        if (videoCut != null && candidateCut != null && videoCut == candidateCut) {
            score += 200.0
        } else if (videoCut != candidateCut) {
            score -= 400.0 // Mismatched cuts have totally different timing!
        }
    }

    val videoSource = detectSourceType(videoClean)
    val candidateSource = candidateNames.firstNotNullOfOrNull { detectSourceType(it.lowercase()) }
    if (videoSource != null && candidateSource != null) {
        if (videoSource == candidateSource) {
            score += 120.0
        } else {
            score -= 60.0
        }
    }

    val videoRes = detectResolution(videoClean)
    val candidateRes = candidateNames.firstNotNullOfOrNull { detectResolution(it.lowercase()) }
    if (videoRes != null && candidateRes != null && videoRes == candidateRes) {
        score += 30.0
    }

    val videoCodec = detectCodec(videoClean)
    val candidateCodec = candidateNames.firstNotNullOfOrNull { detectCodec(it.lowercase()) }
    if (videoCodec != null && candidateCodec != null && videoCodec == candidateCodec) {
        score += 20.0
    }

    val videoTokens = tokenize(videoClean)
    if (videoTokens.isNotEmpty()) {
        val maxJaccard = candidateNames.maxOfOrNull { name ->
            val candTokens = tokenize(name.lowercase())
            jaccardSimilarity(videoTokens, candTokens)
        } ?: 0.0
        score += maxJaccard * 60.0
    }

    return score
}

fun rankOpenSubtitleCandidates(
    candidates: List<OpenSubtitleCandidate>,
    videoFileName: String?,
    preferredGroup: String? = null,
    preferredSource: String? = null,
    targetGroupToAvoid: String? = null,
): List<OpenSubtitleCandidate> {
    return candidates.sortedByDescending { candidate ->
        calculateCandidateScore(candidate, videoFileName, preferredGroup, preferredSource, targetGroupToAvoid)
    }
}
