package com.pointlessapps.filman.data.scraper

import com.pointlessapps.filman.config.EkinoConfig
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.scraper.extractors.ExtractedVideo
import com.pointlessapps.filman.data.scraper.extractors.getExtractorForUrl
import com.pointlessapps.filman.data.scraper.extractors.resolveFilmanEmbedLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

internal class VideoUrlResolver(
    private val scraper: FilmanScraper,
    private val ekinoScraper: EkinoScraper,
    private val sessionManager: SessionManager,
    private val settingsManager: SettingsManager,
) {
    private val maxCacheSize = 10
    private val cacheTtlMs = 2 * 60 * 60 * 1000L // 2 hours
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class CacheEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val results: MutableStateFlow<List<ExtractedVideo>> = MutableStateFlow(emptyList()),
        val completedCount: AtomicInteger = AtomicInteger(0),
        val totalCount: AtomicInteger = AtomicInteger(-1),
        var job: Job? = null,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val accessOrder = mutableListOf<String>()

    private fun evictIfNeeded() {
        synchronized(accessOrder) {
            val now = System.currentTimeMillis()
            val expired = cache.entries.filter {
                now - it.value.timestamp > cacheTtlMs
            }.map { it.key }
            expired.forEach { key ->
                cache[key]?.job?.cancel()
                cache.remove(key)
                accessOrder.remove(key)
            }

            while (accessOrder.size > maxCacheSize) {
                val oldest = accessOrder.removeAt(0)
                cache[oldest]?.job?.cancel()
                cache.remove(oldest)
            }
        }
    }

    private fun markAccessed(url: String) {
        synchronized(accessOrder) {
            accessOrder.remove(url)
            accessOrder.add(url)
        }
    }

    fun prefetch(mediaUrl: String, detailedMedia: DetailedMedia? = null) {
        val now = System.currentTimeMillis()
        val entry = cache[mediaUrl]
        if (entry != null) {
            if (now - entry.timestamp <= cacheTtlMs) {
                val isCompleted = entry.job?.isCompleted == true
                if (isCompleted && entry.results.value.isEmpty()) {
                    entry.job?.cancel()
                    cache.remove(mediaUrl)
                } else {
                    markAccessed(mediaUrl)
                    return
                }
            } else {
                entry.job?.cancel()
            }
        }

        val newEntry = CacheEntry()
        cache[mediaUrl] = newEntry
        evictIfNeeded()
        markAccessed(mediaUrl)

        val job = scope.launch {
            val media = detailedMedia ?: scraper.getMediaDetails(mediaUrl) ?: return@launch

            val ekinoEmbeds = if (!mediaUrl.startsWith(EkinoConfig.BASE_URL)) {
                ekinoScraper.getEmbeds(
                    title = media.baseItem.titlePl,
                    year = media.metaInfo?.year?.toString(),
                )
            } else {
                emptyList()
            }

            val embeds = media.embeds + ekinoEmbeds

            if (embeds.isEmpty()) {
                newEntry.totalCount.set(0)
                newEntry.completedCount.set(0)
                return@launch
            }

            newEntry.totalCount.set(embeds.size)

            embeds.forEach { embed ->
                launch {
                    try {
                        val embedUrl = if (embed.url.startsWith("http")) {
                            embed.url
                        } else {
                            resolveFilmanEmbedLink(
                                cookie = sessionManager.getCookie().orEmpty(),
                                userAgent = sessionManager.getUserAgent(),
                                linkId = embed.url,
                                routeToken = media.baseItem.routeToken.orEmpty(),
                            )
                        } ?: return@launch

                        val extractor = getExtractorForUrl(embedUrl) ?: return@launch
                        val extractedList = extractor.extractVideo(embedUrl)
                        if (extractedList.isEmpty()) return@launch

                        extractedList.forEach { extracted ->
                            val latency = measureLatency(extracted.url)
                            val enrichedExtracted = extracted.copy(
                                serverName = embed.serverName.ifEmpty { extracted.serverName },
                                version = embed.version.ifEmpty { extracted.version },
                                quality = embed.quality.ifEmpty { extracted.quality },
                                latency = latency,
                            )

                            newEntry.results.update { current ->
                                if (current.any { it.url == enrichedExtracted.url }) {
                                    current
                                } else {
                                    current + enrichedExtracted
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        newEntry.completedCount.incrementAndGet()
                    }
                }
            }
        }
        newEntry.job = job
    }

    suspend fun getFastest(mediaUrl: String): ExtractedVideo? {
        var entry = cache[mediaUrl]
        if (entry == null || System.currentTimeMillis() - entry.timestamp > cacheTtlMs) {
            // Not cached or expired, trigger prefetch
            prefetch(mediaUrl)

            // Yield a bit or wait until entry is created
            while (cache[mediaUrl] == null) {
                delay(50.milliseconds)
            }
            entry = cache[mediaUrl]
        }

        if (entry == null) return null
        markAccessed(mediaUrl)

        return try {
            withTimeoutOrNull(2000.milliseconds) {
                entry.job?.join()
            }
            while (entry.results.value.isEmpty() && entry.job?.isActive == true) {
                delay(50.milliseconds)
            }
            entry.results.value.minByOrNull { it.latency }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun measureLatency(urlString: String): Long =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val host = url.host
                val port = if (url.port != -1) {
                    url.port
                } else if (url.protocol == "https") {
                    443
                } else {
                    80
                }
                val startTime = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 2000)
                }
                System.currentTimeMillis() - startTime
            } catch (e: Exception) {
                Long.MAX_VALUE
            }
        }

    fun getAlternativeUrls(mediaUrl: String): List<ExtractedVideo> {
        val entry = cache[mediaUrl] ?: return emptyList()
        if (System.currentTimeMillis() - entry.timestamp > cacheTtlMs) return emptyList()

        val priorityList = settingsManager.extractorsPriorityFlow.value.map { it.lowercase() }

        return entry.results.value.sortedBy { video ->
            val serverName = video.serverName.ifEmpty {
                runCatching { URL(video.url).host }.getOrNull().orEmpty()
            }.lowercase()

            val index = priorityList.indexOf(serverName)
            if (index != -1) index else Int.MAX_VALUE
        }
    }
}
