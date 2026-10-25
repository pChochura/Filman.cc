package com.pointlessapps.filman.data.scraper

import android.util.Log
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.data.api.AuthException
import com.pointlessapps.filman.data.api.CloudflareChallengeDetected
import com.pointlessapps.filman.data.api.dto.LinksResponse
import com.pointlessapps.filman.data.api.dto.SubtitleTrack
import com.pointlessapps.filman.data.api.dto.VideoLink
import com.pointlessapps.filman.data.api.extractors.getExtractorForUrl
import com.pointlessapps.filman.data.local.MediaAssociationManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.ZaluknijSessionManager
import com.pointlessapps.filman.data.model.EmbedLink
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class LocalLinkExtractor(
    private val filmanScraper: FilmanScraper,
    private val zaluknijScraper: ZaluknijScraper,
    private val ekinoScraper: EkinoScraper,
    private val tmdbEmbedSource: TmdbEmbedSource,
    private val sessionManager: SessionManager,
    private val zaluknijSessionManager: ZaluknijSessionManager,
    private val mediaAssociationManager: MediaAssociationManager,
) {
    private val tag = "LocalLinkExtractor"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val CACHE_TTL_MS = 2 * 60 * 60 * 1000L // 2 hours
        private const val MAX_CACHE_SIZE = 100
        const val TOTAL_SOURCES = 4
    }

    private val cache = ConcurrentHashMap<String, LinksResponse>()
    private val timestamps = ConcurrentHashMap<String, Long>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun linkKey(tmdbId: Int, season: Int? = null, episode: Int? = null): String =
        if (season != null && episode != null) "show:$tmdbId:$season:$episode" else "movie:$tmdbId"

    fun shouldRetryForAuth(key: String): Boolean {
        val existing = cache[key] ?: return false
        return (existing.requiresCloudflare && zaluknijSessionManager.hasCookie()) ||
            (existing.requiresFilmanAuth && sessionManager.hasCookie())
    }

    fun getLinks(key: String): LinksResponse? {
        val timestamp = timestamps[key]
        if (timestamp != null && System.currentTimeMillis() - timestamp > CACHE_TTL_MS && !inFlight.contains(key)) {
            cache.remove(key)
            timestamps.remove(key)
            return null
        }
        return cache[key]
    }

    fun startExtraction(
        tmdbId: Int,
        title: String,
        originalTitle: String? = null,
        year: Int? = null,
        isTvShow: Boolean = false,
        season: Int? = null,
        episode: Int? = null,
        force: Boolean = false,
    ): LinksResponse {
        val key = linkKey(tmdbId, season, episode)
        cleanupCache()

        val existing = cache[key]
        val shouldForce = force || shouldRetryForAuth(key)

        if (existing != null && !shouldForce) {
            val isError = existing.status == "ERROR"
            val isAuth = existing.status == "NEEDS_AUTHORIZATION"
            val isSettled = existing.status == "READY"
            if (!isError && !isAuth && isSettled) {
                return existing
            }
        }

        if (!shouldForce && !inFlight.add(key)) {
            return cache[key] ?: LinksResponse(status = "PENDING", totalSources = TOTAL_SOURCES)
        }
        inFlight.add(key)

        val pending = LinksResponse(
            status = "PENDING",
            links = existing?.links ?: emptyList(),
            completedSources = 0,
            totalSources = TOTAL_SOURCES
        )
        cache[key] = pending
        timestamps[key] = System.currentTimeMillis()

        scope.launch {
            processExtraction(key, tmdbId, title, originalTitle, year, isTvShow, season, episode, shouldForce, existing?.links)
        }

        return pending
    }

    private suspend fun processExtraction(
        key: String,
        tmdbId: Int,
        title: String,
        originalTitle: String?,
        year: Int?,
        isTvShow: Boolean,
        season: Int?,
        episode: Int?,
        force: Boolean = false,
        initialLinks: List<VideoLink>? = null,
    ) = supervisorScope {
        val allLinks = CopyOnWriteArrayList<VideoLink>(initialLinks ?: emptyList())
        var completedSources = 0
        var filmanAuthRequired = false
        var cloudflareChallengeRequired = false

        Log.d(tag, "Starting link extraction for tmdbId=$tmdbId (\"$title\", S${season}E${episode})")

        fun updateProgress(status: String) {
            val distinctLinks = allLinks.distinctBy { it.url }
            val effectiveStatus = if (status == "READY" || completedSources >= TOTAL_SOURCES) {
                if (distinctLinks.isNotEmpty()) "READY"
                else if (filmanAuthRequired || cloudflareChallengeRequired) "NEEDS_AUTHORIZATION"
                else "ERROR"
            } else {
                status
            }
            cache[key] = LinksResponse(
                status = effectiveStatus,
                links = distinctLinks,
                completedSources = completedSources,
                totalSources = TOTAL_SOURCES,
                requiresFilmanAuth = filmanAuthRequired,
                requiresCloudflare = cloudflareChallengeRequired,
            )
            timestamps[key] = System.currentTimeMillis()
        }

        // Source 1: TMDB direct embeds (instant)
        val tmdbJob = async {
            try {
                val embeds = tmdbEmbedSource.getEmbeds(tmdbId, season, episode)
                val resolved = coroutineScope {
                    embeds.map { embed ->
                        async { resolveEmbed(embed) }
                    }.awaitAll().flatten()
                }
                allLinks.addAll(resolved)
                completedSources++
                Log.d(tag, "TMDB embeds resolved: ${resolved.size} links")
                updateProgress(if (completedSources < TOTAL_SOURCES) "PARTIAL" else "READY")
            } catch (e: Exception) {
                completedSources++
                Log.e(tag, "TMDB embeds failed", e)
                updateProgress(if (completedSources < TOTAL_SOURCES) "PARTIAL" else "READY")
            }
        }

        // Source 2: Ekino
        val ekinoJob = async {
            try {
                val directUrl = mediaAssociationManager.getProviderUrl(tmdbId, isTvShow, "ekino")
                val directEpUrl = if (season != null && episode != null) {
                    mediaAssociationManager.getEpisodeUrl(tmdbId, season, episode, "ekino")
                } else null

                val result = ekinoScraper.getEmbeds(
                    title = title,
                    year = year,
                    originalTitle = originalTitle,
                    season = season,
                    episode = episode,
                    directUrl = directUrl,
                    directEpisodeUrl = directEpUrl,
                )
                if (result.mediaUrl != null && directUrl == null) {
                    mediaAssociationManager.saveProviderUrl(tmdbId, isTvShow, "ekino", result.mediaUrl)
                }

                if (result.episodeUrl != null && directEpUrl == null && season != null && episode != null) {
                    mediaAssociationManager.saveEpisodeUrl(tmdbId, season, episode, "ekino", result.episodeUrl)
                }

                val resolved = coroutineScope {
                    result.embeds.map { embed ->
                        async { resolveEmbed(embed) }
                    }.awaitAll().flatten()
                }
                allLinks.addAll(resolved)
                Log.d(tag, "Ekino resolved: ${resolved.size} links")
            } catch (e: Exception) {
                Log.e(tag, "Ekino scraper error", e)
            } finally {
                completedSources++
                updateProgress(if (completedSources < TOTAL_SOURCES) "PARTIAL" else "READY")
            }
        }

        // Source 3: Filman
        val filmanJob = async {
            try {
                val directUrl = mediaAssociationManager.getProviderUrl(tmdbId, isTvShow, "filman")
                val directEpUrl = if (season != null && episode != null) {
                    mediaAssociationManager.getEpisodeUrl(tmdbId, season, episode, "filman")
                } else null

                val cookie = sessionManager.getCookie()
                if (cookie.isNullOrBlank()) {
                    Log.d(tag, "Filman: no session cookie, skipping without marking not-found")
                    filmanAuthRequired = true
                } else {
                    val ua = sessionManager.getUserAgent()

                    val result = filmanScraper.getEmbeds(
                        title = title,
                        year = year,
                        originalTitle = originalTitle,
                        season = season,
                        episode = episode,
                        cookie = cookie,
                        userAgent = ua,
                        directUrl = directUrl,
                        directEpisodeUrl = directEpUrl,
                    )

                    if (result.mediaUrl != null && directUrl == null) {
                        mediaAssociationManager.saveProviderUrl(tmdbId, isTvShow, "filman", result.mediaUrl)
                    }

                    if (result.episodeUrl != null && directEpUrl == null && season != null && episode != null) {
                        mediaAssociationManager.saveEpisodeUrl(tmdbId, season, episode, "filman", result.episodeUrl)
                    }

                    val resolved = coroutineScope {
                        result.embeds.map { embed ->
                            async { resolveEmbed(embed) }
                        }.awaitAll().flatten()
                    }
                    allLinks.addAll(resolved)
                    Log.d(tag, "Filman resolved: ${resolved.size} links")
                }
            } catch (e: AuthException) {
                Log.w(tag, "Filman requires authorization: ${e.message}")
                filmanAuthRequired = true
                sessionManager.saveCookie("")
            } catch (e: Exception) {
                Log.e(tag, "Filman scraper error", e)
            } finally {
                completedSources++
                updateProgress(if (completedSources < TOTAL_SOURCES) "PARTIAL" else "READY")
            }
        }

        // Source 4: Zaluknij
        val zaluknijJob = async {
            try {
                val directUrl = mediaAssociationManager.getProviderUrl(tmdbId, isTvShow, "zaluknij")
                val directEpUrl = if (season != null && episode != null) {
                    mediaAssociationManager.getEpisodeUrl(tmdbId, season, episode, "zaluknij")
                } else null

                val cookie = zaluknijSessionManager.getCookie()
                val ua = sessionManager.getUserAgent()

                val result = zaluknijScraper.getEmbeds(
                    title = title,
                    year = year,
                    originalTitle = originalTitle,
                    season = season,
                    episode = episode,
                    cookie = cookie,
                    userAgent = ua,
                    directUrl = directUrl,
                    directEpisodeUrl = directEpUrl,
                )

                if (result.mediaUrl != null && directUrl == null) {
                    mediaAssociationManager.saveProviderUrl(tmdbId, isTvShow, "zaluknij", result.mediaUrl)
                }

                if (result.episodeUrl != null && directEpUrl == null && season != null && episode != null) {
                    mediaAssociationManager.saveEpisodeUrl(tmdbId, season, episode, "zaluknij", result.episodeUrl)
                }

                val resolved = coroutineScope {
                    result.embeds.map { embed ->
                        async { resolveEmbed(embed) }
                    }.awaitAll().flatten()
                }
                allLinks.addAll(resolved)
                Log.d(tag, "Zaluknij resolved: ${resolved.size} links")
            } catch (e: CloudflareChallengeDetected) {
                Log.w(tag, "Zaluknij Cloudflare challenge detected")
                cloudflareChallengeRequired = true
                zaluknijSessionManager.requestChallenge()
            } catch (e: Exception) {
                Log.e(tag, "Zaluknij scraper error", e)
            } finally {
                completedSources++
                updateProgress(if (completedSources < TOTAL_SOURCES) "PARTIAL" else "READY")
            }
        }

        awaitAll(tmdbJob, ekinoJob, filmanJob, zaluknijJob)

        val distinctLinks = allLinks.distinctBy { it.url }
        val finalStatus = when {
            distinctLinks.isNotEmpty() -> "READY"
            filmanAuthRequired || cloudflareChallengeRequired -> "NEEDS_AUTHORIZATION"
            else -> "ERROR"
        }

        cache[key] = LinksResponse(
            status = finalStatus,
            links = distinctLinks,
            completedSources = TOTAL_SOURCES,
            totalSources = TOTAL_SOURCES,
            requiresFilmanAuth = filmanAuthRequired,
            requiresCloudflare = cloudflareChallengeRequired,
        )
        timestamps[key] = System.currentTimeMillis()
        inFlight.remove(key)
        Log.d(tag, "Extraction complete for $key: ${distinctLinks.size} links, status=$finalStatus")
    }

    private suspend fun resolveEmbed(embed: EmbedLink): List<VideoLink> {
        val extractor = getExtractorForUrl(embed.url, embed.serverName)
        if (extractor == null) {
            return listOf(
                VideoLink(
                    url = embed.url,
                    quality = embed.quality,
                    language = embed.version,
                    subtitles = embed.subtitles.map { SubtitleTrack(url = it.url, label = it.label, language = it.language) },
                    sourceWebsite = embed.sourceWebsite,
                    serverName = embed.serverName,
                    displayMode = if (embed.isWebView) "WEBVIEW" else "NATIVE",
                    headers = embed.headers
                )
            )
        }

        return try {
            val extractedVideos = extractor.extractVideo(embed.url)
            if (extractedVideos.isEmpty()) {
                listOf(
                    VideoLink(
                        url = embed.url,
                        quality = embed.quality,
                        language = embed.version,
                        subtitles = embed.subtitles.map { SubtitleTrack(url = it.url, label = it.label, language = it.language) },
                        sourceWebsite = embed.sourceWebsite,
                        serverName = embed.serverName,
                        displayMode = "WEBVIEW",
                        headers = embed.headers
                    )
                )
            } else {
                extractedVideos.map { extracted ->
                    VideoLink(
                        url = extracted.url,
                        quality = extracted.quality.ifEmpty { embed.quality },
                        language = embed.version,
                        subtitles = (embed.subtitles + extracted.subtitles).distinctBy { it.url }.map {
                            SubtitleTrack(url = it.url, label = it.label, language = it.language)
                        },
                        sourceWebsite = embed.sourceWebsite,
                        serverName = extracted.serverName.ifEmpty { embed.serverName },
                        displayMode = if (extracted.isWebView || embed.isWebView) "WEBVIEW" else "NATIVE",
                        headers = embed.headers + extracted.headers
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed extracting embed ${embed.url}", e)
            emptyList()
        }
    }

    private fun cleanupCache() {
        val now = System.currentTimeMillis()
        val expiredThreshold = now - CACHE_TTL_MS
        for ((key, timestamp) in timestamps) {
            if (timestamp < expiredThreshold && !inFlight.contains(key)) {
                cache.remove(key)
                timestamps.remove(key)
            }
        }
        if (cache.size > MAX_CACHE_SIZE) {
            val sortedByOldest = timestamps.entries
                .filterNot { inFlight.contains(it.key) }
                .sortedBy { it.value }
            for (entry in sortedByOldest) {
                if (cache.size <= MAX_CACHE_SIZE) break
                cache.remove(entry.key)
                timestamps.remove(entry.key)
            }
        }
    }
}
