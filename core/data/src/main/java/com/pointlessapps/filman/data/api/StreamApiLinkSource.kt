package com.pointlessapps.filman.data.api

import android.util.Log
import com.pointlessapps.filman.data.api.dto.LinksResponse
import com.pointlessapps.filman.data.api.dto.SubtitleTrack
import com.pointlessapps.filman.data.api.dto.VideoLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.io.File

@Serializable
internal data class StreamApiProvider(
    val success: Boolean,
    val streams: List<StreamApiStream>,
)

@Serializable
internal data class StreamApiComplete(val success: Boolean)

@Serializable
internal data class StreamApiError(val error: String)

@Serializable
internal data class StreamApiStream(
    val url: String,
    val name: String = "",
    val title: String = "",
    val filename: String = "",
    val provider: String = "",
    val quality: String = "",
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<StreamApiSubtitle> = emptyList(),
)

@Serializable
internal data class StreamApiSubtitle(
    val url: String,
    val lang: String = "",
    val label: String = "",
)

class StreamApiLinkSource(
    client: OkHttpClient,
    private val baseUrl: String,
    cacheDirectory: File,
    private val apiKey: String = "",
    private val languageSupplier: () -> String = { "" },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val client = client.newBuilder()
        .readTimeout(50, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS)
        .build()
    private val wakeUpClient = client.newBuilder()
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = ConcurrentHashMap<String, LinksResponse>()
    private val timestamps = ConcurrentHashMap<String, Long>()
    private val inFlight = mutableMapOf<String, Long>()
    private val diskCache = StreamResponseDiskCache(cacheDirectory)
    private var generation = 0L

    fun wakeUp(): Job = scope.launch {
        try {
            val url = validatedBaseUrl().newBuilder()
                .addPathSegment("api")
                .addPathSegment("health")
                .build()
            val requestBuilder = Request.Builder().url(url)
            if (apiKey.isNotBlank()) {
                requestBuilder.header("X-Api-Key", apiKey)
            }
            wakeUpClient.newCall(requestBuilder.build()).execute().use { response ->
                check(response.isSuccessful) { "Stream API health check returned HTTP ${response.code}" }
            }
            Log.d("StreamApiLinkSource", "Stream API wake-up completed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            Log.w("StreamApiLinkSource", "Stream API wake-up failed", e)
        } catch (e: IllegalStateException) {
            Log.w("StreamApiLinkSource", "Stream API wake-up failed", e)
        } catch (e: IllegalArgumentException) {
            Log.w("StreamApiLinkSource", "Stream API wake-up failed", e)
        }
    }

    private fun validatedBaseUrl(): HttpUrl {
        val base = baseUrl.trim().toHttpUrlOrNull()
            ?: throw IllegalStateException("STREAM_API_BASE_URL is not configured or is invalid")
        require(base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null) {
            "STREAM_API_BASE_URL must not contain credentials, query, or fragment"
        }
        return base
    }

    fun linkKey(tmdbId: Int, season: Int? = null, episode: Int? = null): String {
        val base = if (season != null && episode != null) "show:$tmdbId:$season:$episode" else "movie:$tmdbId"
        val lang = languageSupplier().trim()
        return if (lang.isNotBlank()) "$base:$lang" else base
    }

    @Synchronized
    fun getLinks(key: String): LinksResponse? {
        val timestamp = timestamps[key] ?: return null
        val ttl = if (cache[key]?.status == "ERROR") ERROR_CACHE_TTL_MS else CACHE_TTL_MS
        if ((now() < timestamp || now() - timestamp > ttl) && key !in inFlight) {
            cache.remove(key)
            timestamps.remove(key)
            return null
        }
        return cache[key]
    }

    @Synchronized
    fun startExtraction(
        tmdbId: Int,
        isTvShow: Boolean = false,
        season: Int? = null,
        episode: Int? = null,
        force: Boolean = false,
    ): LinksResponse {
        require(tmdbId > 0 && if (isTvShow) {
            season != null && season >= 0 && episode != null && episode > 0
        } else {
            season == null && episode == null
        }) {
            "Invalid TMDB ID or episode"
        }
        val key = linkKey(tmdbId, season, episode)
        if (cache.size >= MAX_CACHE_SIZE) {
            timestamps.entries.filter { it.key !in inFlight }
                .minByOrNull { it.value }?.key?.let {
                    cache.remove(it)
                    timestamps.remove(it)
                }
        }
        val existing = getLinks(key)
        if (!force && existing != null) return existing
        if (key in inFlight) return cache[key] ?: LinksResponse(status = "PENDING")
        val requestGeneration = generation
        inFlight[key] = requestGeneration
        val pending = LinksResponse(status = "PENDING", totalSources = 1)
        cache[key] = pending
        timestamps[key] = now()
        scope.launch {
            val result = try {
                val cached = synchronized(this@StreamApiLinkSource) {
                    if (requestGeneration != generation) return@launch
                    if (force) {
                        diskCache.remove(baseUrl.trim(), key)
                        null
                    } else {
                        diskCache.read(baseUrl.trim(), key, now(), CACHE_TTL_MS)
                    }
                }
                if (cached != null) {
                    synchronized(this@StreamApiLinkSource) {
                        if (requestGeneration == generation) {
                            cache[key] = cached.response
                            timestamps[key] = cached.timestamp
                            inFlight.remove(key)
                        }
                    }
                    return@launch
                }
                val builder = validatedBaseUrl().newBuilder()
                    .addPathSegment("api")
                    .addPathSegment("streams")
                    .addPathSegment(if (isTvShow) "series" else "movie")
                    .addPathSegment(tmdbId.toString())
                if (isTvShow) {
                    builder.addQueryParameter("season", season.toString())
                    builder.addQueryParameter("episode", episode.toString())
                }
                builder.addQueryParameter("stream", "1")
                val lang = languageSupplier().trim()
                if (lang.isNotBlank()) {
                    builder.addQueryParameter("lang", lang)
                }
                val requestBuilder = Request.Builder().url(builder.build())
                    .header("Accept", "text/event-stream")
                if (apiKey.isNotBlank()) {
                    requestBuilder.header("X-Api-Key", apiKey)
                }
                val request = requestBuilder.build()
                val links = mutableListOf<VideoLink>()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Stream API returned HTTP ${response.code}" }
                    check(response.body.contentType()?.let {
                        it.type == "text" && it.subtype == "event-stream"
                    } == true) { "Stream API returned an invalid event stream content type" }
                    val source = response.body.source()
                    var event = ""
                    val data = mutableListOf<String>()
                    var complete = false
                    while (!complete) {
                        val line = source.readUtf8Line()
                            ?: throw IllegalStateException("Stream API connection ended before completion")
                        if (line.isEmpty()) {
                            if (data.isNotEmpty()) {
                                val payload = data.joinToString("\n")
                                when (event) {
                                    "provider" -> {
                                        val provider = json.decodeFromString<StreamApiProvider>(payload)
                                        if (provider.success) {
                                            links.addAll(provider.streams.mapNotNull(::toVideoLink))
                                            synchronized(this@StreamApiLinkSource) {
                                                if (inFlight[key] == requestGeneration && generation == requestGeneration) {
                                                    cache[key] = LinksResponse(status = "PARTIAL", links = links.toList())
                                                    timestamps[key] = now()
                                                }
                                            }
                                        }
                                    }
                                    "complete" -> {
                                        check(json.decodeFromString<StreamApiComplete>(payload).success) {
                                            "Stream API reported an error"
                                        }
                                        complete = true
                                    }
                                    "error" -> {
                                        val error = json.decodeFromString<StreamApiError>(payload)
                                        throw IllegalStateException("Stream API error: ${error.error}")
                                    }
                                }
                            }
                            event = ""
                            data.clear()
                        } else if (!line.startsWith(':')) {
                            val field = line.substringBefore(':')
                            val value = if (':' in line) line.substringAfter(':').removePrefix(" ") else ""
                            when (field) {
                                "event" -> event = value
                                "data" -> data.add(value)
                            }
                        }
                    }
                }
                LinksResponse(
                    status = if (links.isEmpty()) "ERROR" else "READY",
                    links = links,
                    message = if (links.isEmpty()) "Stream API returned no streams" else null,
                    completedSources = 1,
                    totalSources = 1,
                )
            } catch (e: CancellationException) {
                synchronized(this@StreamApiLinkSource) {
                    if (inFlight[key] == requestGeneration) {
                        cache.remove(key)
                        timestamps.remove(key)
                        inFlight.remove(key)
                    }
                }
                throw e
            } catch (e: Exception) {
                Log.e("StreamApiLinkSource", "Stream API request failed", e)
                val partialLinks = synchronized(this@StreamApiLinkSource) {
                    if (inFlight[key] == requestGeneration && generation == requestGeneration) {
                        cache[key]?.links.orEmpty()
                    } else {
                        emptyList()
                    }
                }
                LinksResponse(status = "ERROR", links = partialLinks, message = e.message, completedSources = 1, totalSources = 1)
            }
            synchronized(this@StreamApiLinkSource) {
                if (inFlight[key] == requestGeneration && generation == requestGeneration) {
                    val timestamp = now()
                    if (result.status == "READY") {
                        diskCache.write(baseUrl.trim(), key, CachedStreamResponse(timestamp, result))
                    }
                    cache[key] = result
                    timestamps[key] = timestamp
                    inFlight.remove(key)
                }
            }
        }
        return pending
    }

    private fun toVideoLink(stream: StreamApiStream): VideoLink? {
        if (stream.url.toHttpUrlOrNull() == null) {
            Log.w("StreamApiLinkSource", "Skipping stream with invalid URL")
            return null
        }
        val subtitles = stream.subtitles.mapNotNull { subtitle ->
            if (subtitle.url.toHttpUrlOrNull() == null) {
                Log.w("StreamApiLinkSource", "Skipping subtitle with invalid URL")
                null
            } else {
                SubtitleTrack(
                    url = subtitle.url,
                    label = subtitle.label.ifBlank { subtitle.lang },
                    language = subtitle.lang,
                )
            }
        }
        val resolvedFilename = when {
            stream.filename.isNotBlank() -> stream.filename.trim()
            stream.title.contains('\n') -> stream.title.substringAfter('\n').trim()
            stream.title.isNotBlank() -> stream.title.trim()
            else -> ""
        }
        val titleLower = stream.title.lowercase()
        val nameLower = stream.name.lowercase()
        val providerLower = stream.provider.lowercase()
        val detectedVersion = when {
            titleLower.contains("dubbing") || titleLower.contains("dub") || nameLower.contains("dub") -> "Dubbing"
            titleLower.contains("lektor") || titleLower.contains("lek") || nameLower.contains("lektor") -> "Lektor"
            titleLower.contains("napisy") || nameLower.contains("napisy") -> "Napisy"
            providerLower == "polish" || nameLower.contains("polish") || titleLower.contains("[pl") -> "PL"
            else -> ""
        }
        return VideoLink(
            url = stream.url,
            quality = stream.quality,
            language = detectedVersion,
            serverName = stream.name.ifBlank { stream.provider },
            filename = resolvedFilename,
            sourceWebsite = "stream_api",
            headers = stream.headers,
            subtitles = subtitles,
        )
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        synchronized(this@StreamApiLinkSource) {
            generation++
            cache.clear()
            timestamps.clear()
            inFlight.clear()
            diskCache.clear()
        }
    }

    private companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L
        const val ERROR_CACHE_TTL_MS = 5 * 1000L
        const val MAX_CACHE_SIZE = 100
    }
}
