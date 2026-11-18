package com.pointlessapps.filman.data.api

import android.util.Log
import com.pointlessapps.filman.data.api.extractors.Subtitle
import com.pointlessapps.filman.data.api.tmdb.TmdbApiClient
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.SubtitleCandidate
import com.pointlessapps.filman.data.model.TvShowSourceSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

sealed interface PolishSubtitleSearchResult {
    data class Found(val subtitle: Subtitle) : PolishSubtitleSearchResult
    data class NotFound(val searchFailed: Boolean) : PolishSubtitleSearchResult
}

sealed interface RawSubtitleLoadResult {
    data class Success(val body: String) : RawSubtitleLoadResult
    data class Failed(val notifyDownloadError: Boolean) : RawSubtitleLoadResult
}

class SubtitleRepository(
    var openSubtitlesClient: OpenSubtitlesClient = OpenSubtitlesClient(),
    var wyzieSubsClient: WyzieSubsClient = WyzieSubsClient(
        NetworkClient.okHttpClient,
        TmdbApiClient(NetworkClient.okHttpClient),
    ),
    var openSubtitlesHasher: VideoHasher = OpenSubtitlesHasher,
    private val httpClient: OkHttpClient = NetworkClient.okHttpClient,
) {
    fun isPolishSubtitle(sub: Subtitle): Boolean {
        val lang = sub.language.lowercase()
        val label = sub.label.lowercase()
        return lang == "pl" || lang == "pol" || lang.contains("pol") ||
            label.contains("polsk") || label.contains("polish") ||
            label == "pl" || label.startsWith("pl ") || label.startsWith("pl-") ||
            label.endsWith("(pl)") || label.endsWith("pl")
    }

    fun findMatchingSubtitle(
        subtitles: List<Subtitle>,
        settings: TvShowSourceSettings,
    ): String? {
        if (!settings.subtitlesEnabled || subtitles.isEmpty()) return null

        val lang = settings.subtitleLanguage
        val label = settings.subtitleLabel

        if (!lang.isNullOrBlank() && !label.isNullOrBlank()) {
            val match = subtitles.find {
                it.language.equals(lang, ignoreCase = true) &&
                    it.label.equals(label, ignoreCase = true)
            }
            if (match != null) return match.url
        }

        if (!lang.isNullOrBlank()) {
            val match = subtitles.find { it.language.equals(lang, ignoreCase = true) }
            if (match != null) return match.url
        }

        if (!label.isNullOrBlank()) {
            val match = subtitles.find { it.label.equals(label, ignoreCase = true) }
            if (match != null) return match.url
        }

        return subtitles.firstOrNull()?.url
    }

    fun subtitleUrlWithFormat(url: String, format: String): String {
        val extension = when (format.lowercase()) {
            "srt", "vtt", "ttml" -> format.lowercase()
            else -> "srt"
        }
        return if (url.contains("?")) "$url&ext=.$extension" else "$url?ext=.$extension"
    }

    suspend fun searchCandidates(
        detailedMedia: DetailedMedia,
        language: String,
        videoUrl: String?,
        videoHeaders: Map<String, String>,
    ): Pair<List<SubtitleCandidate>, List<OpenSubtitleCandidate>> = coroutineScope {
        val title = detailedMedia.baseItem.titleEn ?: detailedMedia.baseItem.titlePl
        val year = detailedMedia.baseItem.year
        val season = detailedMedia.baseItem.seasonNumber
        val episode = detailedMedia.baseItem.episodeNumber
        val videoFileName = VideoSignatureExtractor.extract(videoUrl, videoHeaders)
        val movieHash = computeMovieHash(videoUrl, videoHeaders)

        val openSubsDeferred = async(Dispatchers.IO) {
            runCatching {
                openSubtitlesClient.search(
                    query = title,
                    language = language,
                    season = season,
                    episode = episode,
                    tmdbId = detailedMedia.tmdbId,
                    videoFileName = videoFileName,
                    movieHash = movieHash,
                )
            }.onFailure {
                if (it is CancellationException) throw it
                Log.w("Subtitles", "OpenSubtitles search failed in combined query", it)
            }.getOrNull().orEmpty()
        }

        val wyzieSubsDeferred = async(Dispatchers.IO) {
            runCatching {
                wyzieSubsClient.searchSubtitles(
                    title = title,
                    year = year,
                    season = season,
                    episode = episode,
                    tmdbId = detailedMedia.tmdbId,
                    imdbId = detailedMedia.imdbId,
                    language = language,
                )
            }.onFailure {
                if (it is CancellationException) throw it
                Log.w("Subtitles", "Wyzie search failed in combined query", it)
            }.getOrNull().orEmpty()
        }

        val openSubs = openSubsDeferred.await()
        val wyzieSubs = wyzieSubsDeferred.await().filter { it.language.equals(language, ignoreCase = true) }

        val openCandidates = openSubs.map { candidate ->
            SubtitleCandidate(
                id = "open_${candidate.fileId}",
                name = candidate.fileName,
                language = candidate.language,
                release = candidate.release,
                provider = "OpenSubtitles",
                downloadCount = candidate.downloadCount,
                rating = candidate.rating,
                foreignPartsOnly = candidate.foreignPartsOnly,
                hearingImpaired = candidate.hearingImpaired,
                openSubtitleFileId = candidate.fileId,
            )
        }

        val wyzieCandidates = wyzieSubs.mapIndexed { index, sub ->
            val name = when {
                !sub.fileName.isNullOrBlank() -> sub.fileName
                !sub.release.isNullOrBlank() -> sub.release
                sub.ai -> "${sub.display} (AI)"
                index > 0 -> "${sub.display} #${index + 1}"
                else -> sub.display
            }
            SubtitleCandidate(
                id = "wyzie_${sub.id ?: index}_${sub.url.hashCode()}",
                name = name,
                language = sub.language,
                release = sub.release,
                provider = "Wyzie",
                downloadCount = sub.downloadCount,
                hearingImpaired = sub.isHearingImpaired,
                directUrl = subtitleUrlWithFormat(sub.url, sub.format),
                format = sub.format,
            )
        }

        (openCandidates + wyzieCandidates) to openSubs
    }

    suspend fun findBestPolishSubtitle(
        detailedMedia: DetailedMedia,
        videoUrl: String?,
        videoHeaders: Map<String, String>,
        durationMs: Long,
        isPossiblyIncomplete: (String, Long) -> Boolean,
    ): PolishSubtitleSearchResult {
        val title = detailedMedia.baseItem.titleEn ?: detailedMedia.baseItem.titlePl
        val season = detailedMedia.baseItem.seasonNumber
        val episode = detailedMedia.baseItem.episodeNumber
        var searchFailed = false

        fun recordFailure(error: Throwable) {
            if (error is CancellationException) throw error
            searchFailed = true
            Log.w("Subtitles", "Automatic subtitle search failed", error)
        }

        val videoFileName = VideoSignatureExtractor.extract(videoUrl = videoUrl, headers = videoHeaders)
        val movieHash = computeMovieHash(videoUrl, videoHeaders)

        var candidates = runCatching {
            openSubtitlesClient.search(
                query = title,
                language = "pl",
                season = season,
                episode = episode,
                tmdbId = detailedMedia.tmdbId,
                videoFileName = videoFileName,
                movieHash = movieHash,
            )
        }.onFailure(::recordFailure).getOrNull().orEmpty()

        if (candidates.isEmpty() && (detailedMedia.tmdbId ?: 0) <= 0 &&
            detailedMedia.baseItem.titlePl != title
        ) {
            candidates = runCatching {
                openSubtitlesClient.search(
                    query = detailedMedia.baseItem.titlePl,
                    language = "pl",
                    season = season,
                    episode = episode,
                    videoFileName = videoFileName,
                    movieHash = movieHash,
                )
            }.onFailure(::recordFailure).getOrNull().orEmpty()
        }

        var srtUrl: String? = null
        var subLabel = "OpenSubtitles (pl)"

        val primaryCandidate = candidates.firstOrNull()
        if (primaryCandidate != null) {
            var chosenCandidate = primaryCandidate
            var chosenDownload = runCatching { openSubtitlesClient.download(primaryCandidate.fileId) }
                .onFailure(::recordFailure)
                .getOrNull()

            if (chosenDownload != null && candidates.size > 1 && durationMs > 0) {
                val primaryUrl = subtitleUrlWithFormat(
                    chosenDownload.link,
                    chosenDownload.fileName.substringAfterLast('.', "srt"),
                )
                val primaryBody = withContext(Dispatchers.IO) {
                    runCatching {
                        val req = Request.Builder().url(primaryUrl).build()
                        httpClient.newCall(req).execute().use { if (it.isSuccessful) it.body.string() else null }
                    }.getOrNull()
                }
                if (primaryBody != null && isPossiblyIncomplete(primaryBody, durationMs)) {
                    val secondCandidate = candidates[1]
                    val secondDownload = runCatching { openSubtitlesClient.download(secondCandidate.fileId) }.getOrNull()
                    if (secondDownload != null) {
                        chosenCandidate = secondCandidate
                        chosenDownload = secondDownload
                    }
                }
            }

            if (chosenDownload != null) {
                srtUrl = subtitleUrlWithFormat(
                    chosenDownload.link,
                    chosenDownload.fileName.substringAfterLast('.', "srt"),
                )
                subLabel = "OpenSubtitles (pl) ${chosenCandidate.fileName}".trim()
            }
        } else {
            val wyzieSubs = runCatching {
                wyzieSubsClient.searchSubtitles(
                    title = title,
                    year = detailedMedia.baseItem.year,
                    season = season,
                    episode = episode,
                    tmdbId = detailedMedia.tmdbId,
                    imdbId = detailedMedia.imdbId,
                    language = "pl",
                )
            }.getOrNull().orEmpty()
            val plSubs = wyzieSubs.filter { it.language.equals("pl", ignoreCase = true) }
            val plSub = if (videoFileName != null && plSubs.size > 1) {
                val videoTokens = VideoSignatureExtractor.tokenize(videoFileName)
                plSubs.maxByOrNull { sub ->
                    val subTokens = VideoSignatureExtractor.tokenize(sub.fileName ?: sub.release ?: sub.display)
                    VideoSignatureExtractor.jaccardSimilarity(videoTokens, subTokens)
                } ?: plSubs.firstOrNull()
            } else {
                plSubs.firstOrNull()
            }
            if (plSub != null) {
                srtUrl = subtitleUrlWithFormat(plSub.url, plSub.format)
                val labelText = plSub.fileName ?: plSub.release ?: if (plSub.ai) "${plSub.display} (AI)" else plSub.display
                subLabel = "Wyzie ($labelText)"
            }
        }

        return if (srtUrl != null) {
            PolishSubtitleSearchResult.Found(Subtitle(url = srtUrl, label = subLabel, language = "pl"))
        } else {
            PolishSubtitleSearchResult.NotFound(searchFailed = searchFailed)
        }
    }

    suspend fun downloadCandidate(candidate: SubtitleCandidate): Subtitle? {
        val fileId = candidate.openSubtitleFileId
        val directUrl = candidate.directUrl
        val (url, label) = when {
            fileId != null -> {
                val download = openSubtitlesClient.download(fileId)
                val link = subtitleUrlWithFormat(
                    download.link,
                    download.fileName.substringAfterLast('.', "srt"),
                )
                link to "${candidate.provider} (${candidate.language}) ${candidate.name}".trim()
            }
            directUrl != null -> directUrl to "${candidate.provider} (${candidate.name})".trim()
            else -> return null
        }
        return Subtitle(url = url, label = label, language = candidate.language)
    }

    suspend fun downloadOpenSubtitle(candidate: OpenSubtitleCandidate): Subtitle {
        val download = openSubtitlesClient.download(candidate.fileId)
        val link = subtitleUrlWithFormat(
            download.link,
            download.fileName.substringAfterLast('.', "srt"),
        )
        return Subtitle(
            url = link,
            label = "OpenSubtitles (${candidate.language}) ${candidate.fileName}",
            language = candidate.language,
        )
    }

    suspend fun fetchRawSubtitleContent(
        url: String,
        isDownloaded: Boolean,
        videoHeaders: Map<String, String>,
    ): RawSubtitleLoadResult = withContext(Dispatchers.IO) {
        try {
            if (url.startsWith("file://") || url.startsWith("/")) {
                val file = File(url.removePrefix("file://"))
                if (file.exists()) {
                    return@withContext RawSubtitleLoadResult.Success(file.readText())
                }
            }
            val requestBuilder = Request.Builder().url(url)
            if (!isDownloaded) {
                videoHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }
            }
            if (isDownloaded || videoHeaders.none { it.key.equals("User-Agent", ignoreCase = true) }) {
                requestBuilder.header("User-Agent", DEFAULT_SUBTITLE_USER_AGENT)
            }
            val subClient = if (isDownloaded) {
                httpClient.newBuilder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
            } else {
                httpClient
            }
            subClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    RawSubtitleLoadResult.Success(response.body.string())
                } else {
                    Log.w("Lektor", "Failed to download subtitle content, code=${response.code}")
                    RawSubtitleLoadResult.Failed(notifyDownloadError = isDownloaded)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("Lektor", "Error loading raw subtitle content", e)
            RawSubtitleLoadResult.Failed(notifyDownloadError = isDownloaded)
        }
    }

    private suspend fun computeMovieHash(
        videoUrl: String?,
        videoHeaders: Map<String, String>,
    ): String? {
        if (videoUrl == null ||
            videoUrl.contains(".m3u8", ignoreCase = true) ||
            videoUrl.contains(".mpd", ignoreCase = true)
        ) {
            return null
        }
        return openSubtitlesHasher.computeHash(videoUrl, videoHeaders)
    }

    private companion object {
        const val DEFAULT_SUBTITLE_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36"
    }
}
