package com.example.filman.data.scraper

import com.example.filman.data.local.SessionManager
import com.example.filman.data.model.DetailedMedia
import com.example.filman.data.scraper.extractors.ExtractedVideo
import com.example.filman.data.scraper.extractors.getExtractorForUrl
import com.example.filman.data.scraper.extractors.resolveFilmanEmbedLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

internal class VideoUrlResolver(
    private val scraper: FilmanScraper,
    private val sessionManager: SessionManager,
) {
    private val maxCacheSize = 10
    private val cacheTtlMs = 2 * 60 * 60 * 1000L // 2 hours

    private data class CacheEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val results: MutableStateFlow<List<ExtractedVideo>> = MutableStateFlow(emptyList()),
        val completedCount: AtomicInteger = AtomicInteger(0),
        val totalCount: AtomicInteger = AtomicInteger(0),
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

    suspend fun prefetch(mediaUrl: String, detailedMedia: DetailedMedia? = null) {
        val now = System.currentTimeMillis()
        val entry = cache[mediaUrl]
        if (entry != null) {
            if (now - entry.timestamp <= cacheTtlMs) {
                markAccessed(mediaUrl)
                return
            }
            entry.job?.cancel()
        }

        val newEntry = CacheEntry()
        cache[mediaUrl] = newEntry
        evictIfNeeded()
        markAccessed(mediaUrl)

        coroutineScope {
            val job = launch(Dispatchers.IO) {
                val media = detailedMedia ?: scraper.getMediaDetails(mediaUrl) ?: return@launch
                val embeds = media.embeds

                if (embeds.isEmpty()) {
                    newEntry.totalCount.set(0)
                    newEntry.completedCount.set(0)
                    return@launch
                }

                newEntry.totalCount.set(embeds.size)

                embeds.forEach { embed ->
                    launch {
                        try {
                            val embedUrl = resolveFilmanEmbedLink(
                                cookie = sessionManager.getCookie().orEmpty(),
                                userAgent = sessionManager.getUserAgent(),
                                linkId = embed.url,
                                routeToken = media.baseItem.routeToken.orEmpty(),
                            ) ?: return@launch

                            val extractor = getExtractorForUrl(embedUrl) ?: return@launch
                            val extracted = extractor.extractVideo(embedUrl) ?: return@launch

                            newEntry.results.update { current ->
                                if (current.any { it.url == extracted.url }) current else current + extracted
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
    }

    suspend fun getFastest(mediaUrl: String): ExtractedVideo? {
        var entry = cache[mediaUrl]
        if (entry == null || System.currentTimeMillis() - entry.timestamp > cacheTtlMs) {
            // Not cached or expired, trigger prefetch
            coroutineScope {
                launch { prefetch(mediaUrl) }
            }
            // Yield a bit or wait until entry is created
            while (cache[mediaUrl] == null) {
                delay(50.milliseconds)
            }
            entry = cache[mediaUrl]
        }

        if (entry == null) return null
        markAccessed(mediaUrl)

        // Wait for at least one result, OR all tasks to complete/fail
        return try {
            val results = entry.results.first {
                it.isNotEmpty() || entry.completedCount.get() >= entry.totalCount.get()
            }
            results.firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getAlternativeUrls(mediaUrl: String): List<ExtractedVideo> {
        val entry = cache[mediaUrl] ?: return emptyList()
        if (System.currentTimeMillis() - entry.timestamp > cacheTtlMs) return emptyList()
        return entry.results.value
    }
}
