package com.pointlessapps.filman.data.scraper

import android.util.Log
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.data.api.AuthException
import com.pointlessapps.filman.data.api.CloudflareChallengeDetected
import com.pointlessapps.filman.data.api.dto.LinksResponse
import com.pointlessapps.filman.data.api.dto.SubtitleTrack
import com.pointlessapps.filman.data.api.dto.VideoLink
import com.pointlessapps.filman.data.api.extractors.getExtractorForUrl
import com.pointlessapps.filman.data.local.CdaHdSessionManager
import com.pointlessapps.filman.data.local.MediaAssociationManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.ZaluknijSessionManager
import com.pointlessapps.filman.data.model.EmbedLink
import com.pointlessapps.filman.data.model.SourcePriorityConfig
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class LocalLinkExtractor(
    private val filmanScraper: FilmanScraper,
    private val zaluknijScraper: ZaluknijScraper,
    private val ekinoScraper: EkinoScraper,
    private val cdaHdScraper: CdaHdScraper,
    private val tmdbEmbedSource: TmdbEmbedSource,
    private val sessionManager: SessionManager,
    private val zaluknijSessionManager: ZaluknijSessionManager,
    private val cdaHdSessionManager: CdaHdSessionManager,
    private val settingsManager: SettingsManager,
    private val mediaAssociationManager: MediaAssociationManager,
) {
    private val tag = "LocalLinkExtractor"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val CACHE_TTL_MS = 2 * 60 * 60 * 1000L // 2 hours
        private const val MAX_CACHE_SIZE = 100
        const val TOTAL_SOURCES = 5
    }

    private val cache = ConcurrentHashMap<String, LinksResponse>()
    private val timestamps = ConcurrentHashMap<String, Long>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val attemptGenerations = ConcurrentHashMap<String, Map<String, Long>>()
    private val attemptedFilman = ConcurrentHashMap<String, Boolean>()

    private fun isFilmanEnabled(sources: List<SourcePriorityConfig> = settingsManager.sourcesPriorityFlow.value): Boolean =
        sources.find { it.name == "filman" }?.isEnabled ?: false

    fun shouldRetryForSettings(key: String): Boolean =
        isFilmanEnabled() && attemptedFilman[key] == false

    private fun visibleLinks(response: LinksResponse): LinksResponse {
        if (isFilmanEnabled()) return response
        val links = response.links.filterNot { it.sourceWebsite.contains("filman", ignoreCase = true) }
        val challenges = response.challengeSources.filterNot { it == "filman" }
        val status = when {
            response.status !in setOf("READY", "ERROR", "NEEDS_AUTHORIZATION") -> response.status
            links.isNotEmpty() -> "READY"
            challenges.isNotEmpty() -> "NEEDS_AUTHORIZATION"
            else -> "ERROR"
        }
        return response.copy(
            status = status,
            links = links,
            requiresFilmanAuth = false,
            requiresCloudflare = challenges.isNotEmpty(),
            challengeSources = challenges,
        )
    }

    private fun generations(): Map<String, Long> = mapOf(
        "zaluknij" to zaluknijSessionManager.verificationGeneration.value,
        "cda_hd" to cdaHdSessionManager.verificationGeneration.value,
        "filman" to sessionManager.verificationGeneration.value,
    )

    private fun verifiedSources(key: String, existing: LinksResponse): Set<String> {
        val attempted = attemptGenerations[key] ?: return emptySet()
        val current = generations()
        return existing.challengeSources.filterTo(mutableSetOf()) { source ->
            (current[source] ?: 0L) > (attempted[source] ?: 0L)
        }
    }

    fun linkKey(tmdbId: Int, season: Int? = null, episode: Int? = null): String =
        if (season != null && episode != null) "show:$tmdbId:$season:$episode" else "movie:$tmdbId"

    fun shouldRetryForAuth(key: String): Boolean {
        val existing = cache[key]?.let(::visibleLinks) ?: return false
        return verifiedSources(key, existing).isNotEmpty() ||
            (existing.requiresFilmanAuth && sessionManager.hasCookie())
    }

    fun getLinks(key: String): LinksResponse? {
        val timestamp = timestamps[key]
        if (timestamp != null && System.currentTimeMillis() - timestamp > CACHE_TTL_MS && !inFlight.contains(key)) {
            cache.remove(key)
            timestamps.remove(key)
            attemptGenerations.remove(key)
            attemptedFilman.remove(key)
            return null
        }
        return cache[key]?.let(::visibleLinks)
    }

    @Synchronized
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

        val existing = cache[key]?.let(::visibleLinks)
        val retryFilman = shouldRetryForSettings(key)
        val shouldForce = force || shouldRetryForAuth(key) || retryFilman

        if (existing != null && !shouldForce) {
            val isError = existing.status == "ERROR"
            val isAuth = existing.status == "NEEDS_AUTHORIZATION"
            val isSettled = existing.status == "READY"
            if (!isError && !isAuth && isSettled) {
                return existing
            }
        }

        if (!inFlight.add(key)) {
            val current = cache[key]?.let(::visibleLinks) ?: LinksResponse(status = "PENDING", totalSources = TOTAL_SOURCES)
            return if (shouldForce) current.copy(status = "PENDING") else current
        }
        val retrySources = if (!force && existing != null) {
            verifiedSources(key, existing) +
                if (retryFilman || (existing.requiresFilmanAuth && sessionManager.hasCookie())) setOf("filman") else emptySet()
        } else {
            emptySet()
        }
        val targetedSources = retrySources.takeIf { it.isNotEmpty() }
        val sourcesConfig = settingsManager.sourcesPriorityFlow.value
        if (targetedSources == null || "filman" in targetedSources || !isFilmanEnabled(sourcesConfig)) {
            attemptedFilman[key] = isFilmanEnabled(sourcesConfig)
        }
        val currentGenerations = generations()
        attemptGenerations[key] = if (targetedSources == null) currentGenerations else {
            attemptGenerations[key].orEmpty() + currentGenerations.filterKeys { it in targetedSources }
        }

        val pending = LinksResponse(
            status = "PENDING",
            links = existing?.links ?: emptyList(),
            completedSources = 0,
            totalSources = TOTAL_SOURCES
        )
        cache[key] = pending
        timestamps[key] = System.currentTimeMillis()

        scope.launch {
            try {
                processExtraction(key, tmdbId, title, originalTitle, year, isTvShow, season, episode, targetedSources, existing, sourcesConfig)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Link extraction failed for $key", e)
                cache[key] = (cache[key] ?: pending).copy(status = "ERROR")
            } finally {
                inFlight.remove(key)
            }
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
        retrySources: Set<String>? = null,
        previous: LinksResponse? = null,
        sourcesConfig: List<SourcePriorityConfig>,
    ) = supervisorScope {
        val allLinks = CopyOnWriteArrayList<VideoLink>(previous?.links ?: emptyList())
        var completedSources = 0
        val progressLock = Any()
        val filmanAuthRequired = AtomicBoolean(previous?.requiresFilmanAuth == true && retrySources != null && "filman" !in retrySources)
        val challengeSources = ConcurrentHashMap.newKeySet<String>()
        if (retrySources != null) challengeSources.addAll(previous?.challengeSources.orEmpty().filterNot { it in retrySources })

        fun enabled(name: String, default: Boolean) =
            (retrySources == null || name in retrySources) && (sourcesConfig.find { it.name == name }?.isEnabled ?: default)
        val isFilmanEnabled = enabled("filman", false)
        val isZaluknijEnabled = enabled("zaluknij", true)
        val isEkinoEnabled = enabled("ekino", true)
        val isCdaHdEnabled = enabled("cda_hd", true)
        val isTmdbEnabled = enabled("tmdb", true)

        val activeSourceCount = (if (isTmdbEnabled) 1 else 0) +
            (if (isEkinoEnabled) 1 else 0) +
            (if (isCdaHdEnabled) 1 else 0) +
            (if (isZaluknijEnabled) 1 else 0) +
            (if (isFilmanEnabled) 1 else 0)
        val totalExpected = if (activeSourceCount > 0) activeSourceCount else 1

        Log.d(tag, "Starting link extraction for tmdbId=$tmdbId (\"$title\", S${season}E${episode}) with $totalExpected sources")

        fun updateProgress(status: String) {
            val distinctLinks = allLinks.distinctBy { it.url }
            val effectiveStatus = if (status == "READY" || completedSources >= totalExpected) {
                if (distinctLinks.isNotEmpty()) "READY"
                else if (filmanAuthRequired.get() || challengeSources.isNotEmpty()) "NEEDS_AUTHORIZATION"
                else "ERROR"
            } else {
                status
            }
            cache[key] = LinksResponse(
                status = effectiveStatus,
                links = distinctLinks,
                completedSources = completedSources,
                totalSources = totalExpected,
                requiresFilmanAuth = filmanAuthRequired.get(),
                requiresCloudflare = challengeSources.isNotEmpty(),
                challengeSources = challengeSources.toList(),
            )
            timestamps[key] = System.currentTimeMillis()
        }

        fun finishSource() = synchronized(progressLock) {
            completedSources++
            updateProgress(if (completedSources < totalExpected) "PARTIAL" else "READY")
        }

        // Source 1: TMDB direct embeds (instant)
        val tmdbJob = if (!isTmdbEnabled) null else async {
            try {
                val embeds = tmdbEmbedSource.getEmbeds(tmdbId, season, episode)
                val resolved = coroutineScope {
                    embeds.map { embed ->
                        async { resolveEmbed(embed) }
                    }.awaitAll().flatten()
                }
                allLinks.addAll(resolved)
                Log.d(tag, "TMDB embeds resolved: ${resolved.size} links")
            } catch (e: Exception) {
                Log.e(tag, "TMDB embeds failed", e)
            } finally {
                finishSource()
            }
        }

        // Source 2: Ekino
        val ekinoJob = if (!isEkinoEnabled) null else async {
            try {
                val directUrl = mediaAssociationManager.getProviderUrl(tmdbId, isTvShow, "ekino")?.takeIf { it.isNotBlank() }
                val directEpUrl = if (season != null && episode != null) {
                    mediaAssociationManager.getEpisodeUrl(tmdbId, season, episode, "ekino")?.takeIf { it.isNotBlank() }
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
                finishSource()
            }
        }

        // Source 3: CDA-HD
        val cdaHdJob = if (!isCdaHdEnabled) null else async {
            try {
                val directUrl = mediaAssociationManager.getProviderUrl(tmdbId, isTvShow, "cda_hd")?.takeIf { it.isNotBlank() }
                val directEpUrl = if (season != null && episode != null) {
                    mediaAssociationManager.getEpisodeUrl(tmdbId, season, episode, "cda_hd")?.takeIf { it.isNotBlank() }
                } else null

                val embeds = cdaHdScraper.getEmbeds(
                    title = title,
                    year = year?.toString(),
                    season = season,
                    episode = episode,
                )

                if (cdaHdSessionManager.isChallengeActive.value) {
                    challengeSources.add("cda_hd")
                }

                val resolved = coroutineScope {
                    embeds.map { embed ->
                        async { resolveEmbed(embed) }
                    }.awaitAll().flatten()
                }
                allLinks.addAll(resolved)
                Log.d(tag, "CDA-HD resolved: ${resolved.size} links")
            } catch (e: CloudflareChallengeDetected) {
                Log.w(tag, "CDA-HD Cloudflare challenge detected")
                challengeSources.add("cda_hd")
                cdaHdSessionManager.requestChallenge(e.request)
            } catch (e: Exception) {
                Log.e(tag, "CDA-HD scraper error", e)
            } finally {
                finishSource()
            }
        }

        // Source 4: Zaluknij
        val zaluknijJob = if (!isZaluknijEnabled) null else async {
            try {
                val directUrl = mediaAssociationManager.getProviderUrl(tmdbId, isTvShow, "zaluknij")?.takeIf { it.isNotBlank() }
                val directEpUrl = if (season != null && episode != null) {
                    mediaAssociationManager.getEpisodeUrl(tmdbId, season, episode, "zaluknij")?.takeIf { it.isNotBlank() }
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
                challengeSources.add("zaluknij")
                zaluknijSessionManager.requestChallenge(e.request)
            } catch (e: Exception) {
                Log.e(tag, "Zaluknij scraper error", e)
            } finally {
                finishSource()
            }
        }

        // Source 5: Filman
        val filmanJob = if (!isFilmanEnabled) null else async {
            try {
                val directUrl = mediaAssociationManager.getProviderUrl(tmdbId, isTvShow, "filman")?.takeIf { it.isNotBlank() }
                val directEpUrl = if (season != null && episode != null) {
                    mediaAssociationManager.getEpisodeUrl(tmdbId, season, episode, "filman")?.takeIf { it.isNotBlank() }
                } else null

                val cookie = sessionManager.getCookie()
                if (cookie.isNullOrBlank()) {
                    Log.d(tag, "Filman: no session cookie, skipping without marking not-found")
                    filmanAuthRequired.set(true)
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
            } catch (e: CloudflareChallengeDetected) {
                Log.w(tag, "Filman Cloudflare challenge detected")
                challengeSources.add("filman")
                if (isFilmanEnabled()) sessionManager.requestChallenge(e.request)
            } catch (e: AuthException) {
                Log.w(tag, "Filman requires authorization: ${e.message}")
                filmanAuthRequired.set(true)
                sessionManager.saveCookie("")
            } catch (e: Exception) {
                Log.e(tag, "Filman scraper error", e)
            } finally {
                finishSource()
            }
        }

        listOfNotNull(tmdbJob, ekinoJob, cdaHdJob, zaluknijJob, filmanJob).awaitAll()

        val distinctLinks = allLinks.distinctBy { it.url }
        val finalStatus = when {
            distinctLinks.isNotEmpty() -> "READY"
            filmanAuthRequired.get() || challengeSources.isNotEmpty() -> "NEEDS_AUTHORIZATION"
            else -> "ERROR"
        }

        cache[key] = LinksResponse(
            status = finalStatus,
            links = distinctLinks,
            completedSources = totalExpected,
            totalSources = totalExpected,
            requiresFilmanAuth = filmanAuthRequired.get(),
            requiresCloudflare = challengeSources.isNotEmpty(),
            challengeSources = challengeSources.toList(),
        )
        timestamps[key] = System.currentTimeMillis()
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
        if (cache.size > MAX_CACHE_SIZE) {
            val oldest = timestamps.entries
                .filter { !inFlight.contains(it.key) }
                .sortedBy { it.value }
                .take(cache.size - MAX_CACHE_SIZE + 20)
            for (entry in oldest) {
                cache.remove(entry.key)
                timestamps.remove(entry.key)
                attemptGenerations.remove(entry.key)
                attemptedFilman.remove(entry.key)
            }
        }
    }
}
