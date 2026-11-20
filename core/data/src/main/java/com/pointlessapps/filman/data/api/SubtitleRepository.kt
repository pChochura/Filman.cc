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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.ln

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
    private val referenceSubtitleCache = ConcurrentHashMap<String, String>()

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
        serverName: String? = null,
        quality: String? = null,
        filename: String? = null,
    ): Pair<List<SubtitleCandidate>, List<OpenSubtitleCandidate>> = coroutineScope {
        val title = detailedMedia.baseItem.titleEn ?: detailedMedia.baseItem.titlePl
        val year = detailedMedia.baseItem.year
        val season = detailedMedia.baseItem.seasonNumber
        val episode = detailedMedia.baseItem.episodeNumber
        val probe = probeVideoDetails(videoUrl, videoHeaders)
        val videoFileName = VideoSignatureExtractor.extract(
            videoUrl = probe?.finalUrl ?: videoUrl,
            headers = if (probe?.contentDisposition != null) {
                videoHeaders + ("Content-Disposition" to probe.contentDisposition)
            } else {
                videoHeaders
            },
            serverName = serverName,
            quality = quality,
            filename = filename,
        )
        val movieHash = probe?.movieHash

        val openSubsDeferred = async(Dispatchers.IO) {
            runCatching {
                openSubtitlesClient.search(
                    query = title,
                    language = language,
                    season = season,
                    episode = episode,
                    tmdbId = detailedMedia.tmdbId,
                    imdbId = detailedMedia.imdbId,
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
        val wyzieSubs = rankWyziePolishCandidates(
            candidates = wyzieSubsDeferred.await().filter { it.language.equals(language, ignoreCase = true) },
            videoFileName = videoFileName,
        )

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
        serverName: String? = null,
        quality: String? = null,
        filename: String? = null,
        preferredGroup: String? = null,
        preferredSource: String? = null,
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

        val probe = probeVideoDetails(videoUrl, videoHeaders)
        val videoFileName = VideoSignatureExtractor.extract(
            videoUrl = probe?.finalUrl ?: videoUrl,
            headers = if (probe?.contentDisposition != null) {
                videoHeaders + ("Content-Disposition" to probe.contentDisposition)
            } else {
                videoHeaders
            },
            serverName = serverName,
            quality = quality,
            filename = filename,
        )
        val movieHash = probe?.movieHash
        val candidates = searchOpenSubtitlesPolish(
            detailedMedia = detailedMedia,
            title = title,
            season = season,
            episode = episode,
            videoFileName = videoFileName,
            movieHash = movieHash,
            onError = ::recordFailure,
        )

        val rankedCandidates = if (!preferredGroup.isNullOrBlank() || !preferredSource.isNullOrBlank()) {
            rankOpenSubtitleCandidates(candidates, videoFileName, preferredGroup, preferredSource)
        } else {
            candidates
        }

        val primaryCandidate = rankedCandidates.firstOrNull()
        val found = if (primaryCandidate != null) {
            resolveOpenSubtitlesPolish(
                candidates = rankedCandidates,
                primaryCandidate = primaryCandidate,
                durationMs = durationMs,
                isPossiblyIncomplete = isPossiblyIncomplete,
                onError = ::recordFailure,
            )
        } else {
            resolveWyziePolish(
                title = title,
                detailedMedia = detailedMedia,
                season = season,
                episode = episode,
                videoFileName = videoFileName,
                durationMs = durationMs,
                isPossiblyIncomplete = isPossiblyIncomplete,
            )
        }

        return if (found != null) {
            PolishSubtitleSearchResult.Found(found)
        } else {
            PolishSubtitleSearchResult.NotFound(searchFailed = searchFailed)
        }
    }

    suspend fun findReferenceSubtitleForAlignment(
        detailedMedia: DetailedMedia,
        videoUrl: String?,
        videoHeaders: Map<String, String>,
        serverName: String? = null,
        quality: String? = null,
        filename: String? = null,
        durationMs: Long = 0L,
    ): String? = withContext(Dispatchers.IO) {
        val title = detailedMedia.baseItem.titleEn ?: detailedMedia.baseItem.titlePl
        val season = detailedMedia.baseItem.seasonNumber
        val episode = detailedMedia.baseItem.episodeNumber

        val probe = probeVideoDetails(videoUrl, videoHeaders)
        val videoFileName = VideoSignatureExtractor.extract(
            videoUrl = probe?.finalUrl ?: videoUrl,
            headers = if (probe?.contentDisposition != null) {
                videoHeaders + ("Content-Disposition" to probe.contentDisposition)
            } else {
                videoHeaders
            },
            serverName = serverName,
            quality = quality,
            filename = filename,
        )
        val cacheKey = "${detailedMedia.tmdbId ?: 0}_${season ?: 0}_${episode ?: 0}_${videoFileName ?: title}"
        val cached = referenceSubtitleCache[cacheKey]
        if (cached != null) return@withContext cached

        val movieHash = probe?.movieHash

        val enCandidates = runCatching {
            openSubtitlesClient.search(
                query = title,
                language = "en",
                season = season,
                episode = episode,
                tmdbId = detailedMedia.tmdbId,
                imdbId = detailedMedia.imdbId,
                videoFileName = videoFileName,
                movieHash = movieHash,
            )
        }.getOrNull().orEmpty()

        val videoCut = VideoSignatureExtractor.cleanFilename(videoFileName.orEmpty()).lowercase().let { detectCutEdition(it) }
        val filtered = enCandidates.filter { candidate ->
            if (candidate.foreignPartsOnly) return@filter false
            val candNames = listOfNotNull(candidate.release, candidate.fileName)
            val candCut = candNames.firstNotNullOfOrNull { detectCutEdition(it.lowercase()) }
            if (videoCut != null && candCut != null && videoCut != candCut) return@filter false
            true
        }.sortedWith(
            compareByDescending<OpenSubtitleCandidate> { !it.hearingImpaired }
                .thenByDescending { it.fromTrusted }
                .thenByDescending { it.downloadCount },
        )

        for (candidate in filtered.take(3)) {
            val download = runCatching { openSubtitlesClient.download(candidate.fileId) }.getOrNull() ?: continue
            val srtUrl = subtitleUrlWithFormat(
                download.link,
                download.fileName.substringAfterLast('.', "srt"),
            )
            val body = fetchDecodedBody(srtUrl)
            if (body != null && body.contains("-->")) {
                if (durationMs > 10 * 60_000L) {
                    val lastEnd = parseLastTimestampMs(body)
                    if (lastEnd > 0L && kotlin.math.abs(lastEnd - durationMs) > 20 * 60_000L) {
                        continue
                    }
                }
                referenceSubtitleCache[cacheKey] = body
                return@withContext body
            }
        }
        null
    }

    private fun parseLastTimestampMs(body: String): Long {
        val matches = Regex("""(?:(\d{1,2}):)?(\d{2}):(\d{2})[,.](\d{3})""").findAll(body)
        val last = matches.lastOrNull() ?: return 0L
        val hours = last.groupValues[1].takeIf { it.isNotEmpty() }?.toLongOrNull() ?: 0L
        val minutes = last.groupValues[2].toLongOrNull() ?: 0L
        val seconds = last.groupValues[3].toLongOrNull() ?: 0L
        val millis = last.groupValues[4].toLongOrNull() ?: 0L
        return (hours * 3600 + minutes * 60 + seconds) * 1000 + millis
    }

    private suspend fun searchOpenSubtitlesPolish(
        detailedMedia: DetailedMedia,
        title: String,
        season: Int?,
        episode: Int?,
        videoFileName: String?,
        movieHash: String?,
        onError: (Throwable) -> Unit,
    ): List<OpenSubtitleCandidate> {
        var candidates = runCatching {
            openSubtitlesClient.search(
                query = title,
                language = "pl",
                season = season,
                episode = episode,
                tmdbId = detailedMedia.tmdbId,
                imdbId = detailedMedia.imdbId,
                videoFileName = videoFileName,
                movieHash = movieHash,
            )
        }.onFailure(onError).getOrNull().orEmpty()

        if (candidates.isEmpty() &&
            (detailedMedia.tmdbId ?: 0) <= 0 &&
            detailedMedia.baseItem.titlePl != title
        ) {
            candidates = runCatching {
                openSubtitlesClient.search(
                    query = detailedMedia.baseItem.titlePl,
                    language = "pl",
                    season = season,
                    episode = episode,
                    imdbId = detailedMedia.imdbId,
                    videoFileName = videoFileName,
                    movieHash = movieHash,
                )
            }.onFailure(onError).getOrNull().orEmpty()
        }
        return candidates
    }

    private suspend fun resolveOpenSubtitlesPolish(
        candidates: List<OpenSubtitleCandidate>,
        primaryCandidate: OpenSubtitleCandidate,
        durationMs: Long,
        isPossiblyIncomplete: (String, Long) -> Boolean,
        onError: (Throwable) -> Unit,
    ): Subtitle? {
        var chosenCandidate = primaryCandidate
        var chosenDownload = runCatching { openSubtitlesClient.download(primaryCandidate.fileId) }
            .onFailure(onError)
            .getOrNull() ?: return null

        if (candidates.size > 1) {
            val primaryUrl = subtitleUrlWithFormat(
                chosenDownload.link,
                chosenDownload.fileName.substringAfterLast('.', "srt"),
            )
            val primaryBody = fetchDecodedBody(primaryUrl)
            if (primaryBody != null && isPossiblyIncomplete(primaryBody, durationMs)) {
                val secondCandidate = candidates[1]
                val secondDownload = runCatching {
                    openSubtitlesClient.download(secondCandidate.fileId)
                }.onFailure { if (it is CancellationException) throw it }.getOrNull()
                if (secondDownload != null) {
                    chosenCandidate = secondCandidate
                    chosenDownload = secondDownload
                }
            }
        }

        val srtUrl = subtitleUrlWithFormat(
            chosenDownload.link,
            chosenDownload.fileName.substringAfterLast('.', "srt"),
        )
        val subLabel = "OpenSubtitles (pl) ${chosenCandidate.fileName}".trim()
        return Subtitle(url = srtUrl, label = subLabel, language = "pl")
    }

    private suspend fun resolveWyziePolish(
        title: String,
        detailedMedia: DetailedMedia,
        season: Int?,
        episode: Int?,
        videoFileName: String?,
        durationMs: Long,
        isPossiblyIncomplete: (String, Long) -> Boolean,
    ): Subtitle? {
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
        }.onFailure { if (it is CancellationException) throw it }.getOrNull().orEmpty()
        val plSubs = wyzieSubs.filter { it.language.equals("pl", ignoreCase = true) }
        val ranked = rankWyziePolishCandidates(plSubs, videoFileName)
        if (ranked.isEmpty()) return null

        val chosen = if (ranked.size > 1) {
            ranked.take(3).firstOrNull { candidate ->
                val url = subtitleUrlWithFormat(candidate.url, candidate.format)
                val body = fetchDecodedBody(url)
                body != null && !isPossiblyIncomplete(body, durationMs)
            } ?: ranked.first()
        } else {
            ranked.first()
        }

        val srtUrl = subtitleUrlWithFormat(chosen.url, chosen.format)
        val labelText = chosen.fileName
            ?: chosen.release
            ?: if (chosen.ai) "${chosen.display} (AI)" else chosen.display
        return Subtitle(url = srtUrl, label = "Wyzie ($labelText)", language = "pl")
    }

    private suspend fun fetchDecodedBody(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    SubtitleTextDecoder.decode(
                        bytes = response.body.bytes(),
                        declaredCharset = response.body.contentType()?.charset(),
                    )
                } else {
                    null
                }
            }
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
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
                    return@withContext RawSubtitleLoadResult.Success(
                        SubtitleTextDecoder.decode(file.readBytes()),
                    )
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
                    val decoded = SubtitleTextDecoder.decode(
                        bytes = response.body.bytes(),
                        declaredCharset = response.body.contentType()?.charset(),
                    )
                    RawSubtitleLoadResult.Success(decoded)
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

    private suspend fun probeVideoDetails(
        videoUrl: String?,
        videoHeaders: Map<String, String>,
    ): VideoProbeResult? {
        if (videoUrl == null ||
            videoUrl.contains(".m3u8", ignoreCase = true) ||
            videoUrl.contains(".mpd", ignoreCase = true)
        ) {
            return null
        }
        return if (openSubtitlesHasher is OpenSubtitlesHasher) {
            OpenSubtitlesHasher.probeVideo(videoUrl, videoHeaders)
        } else {
            val hash = openSubtitlesHasher.computeHash(videoUrl, videoHeaders)
            VideoProbeResult(movieHash = hash, fileSize = null, finalUrl = null, contentDisposition = null)
        }
    }

    private companion object {
        const val DEFAULT_SUBTITLE_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36"
    }
}

internal fun rankWyziePolishCandidates(
    candidates: List<WyzieSubtitleResponse>,
    videoFileName: String?,
): List<WyzieSubtitleResponse> {
    if (candidates.size <= 1) return candidates
    val videoTokens = videoFileName
        ?.takeIf { it.isNotBlank() }
        ?.let { VideoSignatureExtractor.tokenize(VideoSignatureExtractor.cleanFilename(it)) }
        ?: emptySet()

    return candidates.sortedByDescending { sub ->
        var score = 0.0
        if (sub.ai) score -= 100.0
        if (sub.isHearingImpaired) score -= 35.0
        sub.downloadCount?.takeIf { it > 0 }?.let { count ->
            score += ln(1.0 + count) * 1.5
        }
        if (videoTokens.isNotEmpty()) {
            val candidateName = sub.fileName ?: sub.release ?: sub.display
            val subTokens = VideoSignatureExtractor.tokenize(candidateName)
            score += VideoSignatureExtractor.jaccardSimilarity(videoTokens, subTokens) * 60.0
        }
        score
    }
}
