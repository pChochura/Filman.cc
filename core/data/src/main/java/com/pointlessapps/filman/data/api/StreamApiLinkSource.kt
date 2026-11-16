package com.pointlessapps.filman.data.api

import android.util.Log
import com.pointlessapps.filman.data.api.dto.LinksResponse
import com.pointlessapps.filman.data.api.dto.SubtitleTrack
import com.pointlessapps.filman.data.api.dto.VideoLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Serializable
internal data class StreamApiResponse(
    val success: Boolean,
    val streams: List<StreamApiStream>,
)

@Serializable
internal data class StreamApiStream(
    val url: String,
    val name: String = "",
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
) {
    private val client = client.newBuilder()
        .readTimeout(50, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = ConcurrentHashMap<String, LinksResponse>()
    private val timestamps = ConcurrentHashMap<String, Long>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun linkKey(tmdbId: Int, season: Int? = null, episode: Int? = null): String =
        if (season != null && episode != null) "show:$tmdbId:$season:$episode" else "movie:$tmdbId"

    fun getLinks(key: String): LinksResponse? {
        val timestamp = timestamps[key] ?: return null
        val ttl = if (cache[key]?.status == "ERROR") ERROR_CACHE_TTL_MS else CACHE_TTL_MS
        if (System.currentTimeMillis() - timestamp > ttl && key !in inFlight) {
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
        if (!inFlight.add(key)) return cache[key] ?: LinksResponse(status = "PENDING")
        val pending = LinksResponse(status = "PENDING", totalSources = 1)
        cache[key] = pending
        timestamps[key] = System.currentTimeMillis()
        scope.launch {
            val result = try {
                val base = baseUrl.trim().toHttpUrlOrNull()
                    ?: throw IllegalStateException("STREAM_API_BASE_URL is not configured or is invalid")
                require(base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null) {
                    "STREAM_API_BASE_URL must not contain credentials, query, or fragment"
                }
                val builder = base.newBuilder()
                    .addPathSegment("api")
                    .addPathSegment("streams")
                    .addPathSegment(if (isTvShow) "series" else "movie")
                    .addPathSegment(tmdbId.toString())
                if (isTvShow) {
                    builder.addQueryParameter("season", season.toString())
                    builder.addQueryParameter("episode", episode.toString())
                }
                val request = Request.Builder().url(builder.build()).build()
                val result = client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Stream API returned HTTP ${response.code}" }
                    json.decodeFromString<StreamApiResponse>(response.body.string())
                }
                check(result.success) { "Stream API reported an error" }
                val links = result.streams.map { stream ->
                    require(stream.url.toHttpUrlOrNull() != null) { "Stream API returned an invalid stream URL" }
                    VideoLink(
                        url = stream.url,
                        quality = stream.quality,
                        serverName = stream.name.ifBlank { stream.provider },
                        sourceWebsite = "stream_api",
                        headers = stream.headers,
                        subtitles = stream.subtitles.map { subtitle ->
                            require(subtitle.url.toHttpUrlOrNull() != null) { "Stream API returned an invalid subtitle URL" }
                            SubtitleTrack(
                                url = subtitle.url,
                                label = subtitle.label.ifBlank { subtitle.lang },
                                language = subtitle.lang,
                            )
                        },
                    )
                }.distinctBy { it.url }
                LinksResponse(
                    status = if (links.isEmpty()) "ERROR" else "READY",
                    links = links,
                    message = if (links.isEmpty()) "Stream API returned no streams" else null,
                    completedSources = 1,
                    totalSources = 1,
                )
            } catch (e: CancellationException) {
                synchronized(this@StreamApiLinkSource) {
                    cache.remove(key)
                    timestamps.remove(key)
                    inFlight.remove(key)
                }
                throw e
            } catch (e: Exception) {
                Log.e("StreamApiLinkSource", "Stream API request failed", e)
                LinksResponse(status = "ERROR", message = e.message, completedSources = 1, totalSources = 1)
            }
            synchronized(this@StreamApiLinkSource) {
                cache[key] = result
                timestamps[key] = System.currentTimeMillis()
                inFlight.remove(key)
            }
        }
        return pending
    }

    private companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L
        const val ERROR_CACHE_TTL_MS = 5 * 1000L
        const val MAX_CACHE_SIZE = 100
    }
}
