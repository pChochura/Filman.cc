package com.pointlessapps.filman.data.scraper.extractors

import com.pointlessapps.filman.data.scraper.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI

internal object VideasyExtractor : EmbedExtractor {

    private const val SPEEDRACELIGHT_URL = "https://api.speedracelight.com"
    private const val DEC_API_URL = "https://enc-dec.app/api/dec-videasy"
    private const val VIDEASY_REFERER = "https://player.videasy.net/"

    internal enum class VideasyServer(
        val serverName: String,
        val endpoint: String,
    ) {
        NEON("Videasy (Neon)", "vsrc"),
        BREACH("Videasy (Breach)", "m4uhd"),
        CYPHER("Videasy (Cypher)", "downloader2"),
        YORU("Videasy (Yoru)", "cdn"),
        VYSE("Videasy (Vyse)", "hdmovie");

        companion object {
            fun from(serverParam: String?): VideasyServer {
                if (serverParam.isNullOrBlank()) return YORU
                val clean = serverParam.trim().lowercase()
                return entries.find {
                    it.name.equals(clean, ignoreCase = true) ||
                    it.endpoint.equals(clean, ignoreCase = true) ||
                    it.serverName.contains(clean, ignoreCase = true)
                } ?: YORU
            }
        }
    }

    internal data class VideasyParams(
        val tmdbId: String,
        val mediaType: String,
        val season: Int? = null,
        val episode: Int? = null,
        val server: VideasyServer = VideasyServer.YORU,
    )

    internal fun parseVideasyUrl(url: String): VideasyParams? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val query = uri.query.orEmpty()
        val serverParam = when {
            query.contains("server=") -> query.substringAfter("server=").substringBefore("&")
            query.contains("endpoint=") -> query.substringAfter("endpoint=").substringBefore("&")
            url.contains("server=") -> url.substringAfter("server=").substringBefore("&")
            url.contains("endpoint=") -> url.substringAfter("endpoint=").substringBefore("&")
            url.contains("/vsrc/") -> "neon"
            url.contains("/m4uhd/") -> "breach"
            url.contains("/downloader2/") -> "cypher"
            url.contains("/hdmovie/") -> "vyse"
            url.contains("/cdn/") -> "yoru"
            else -> null
        }
        val server = VideasyServer.from(serverParam)
        val pathSegments = uri.path.split("/").filter { it.isNotEmpty() }

        return when {
            pathSegments.contains("movie") -> {
                val index = pathSegments.indexOf("movie")
                val id = pathSegments.getOrNull(index + 1) ?: return null
                VideasyParams(tmdbId = id, mediaType = "movie", server = server)
            }

            pathSegments.contains("tv") -> {
                val index = pathSegments.indexOf("tv")
                val id = pathSegments.getOrNull(index + 1) ?: return null
                val season = pathSegments.getOrNull(index + 2)?.toIntOrNull()
                val episode = pathSegments.getOrNull(index + 3)?.toIntOrNull()
                VideasyParams(
                    tmdbId = id,
                    mediaType = "tv",
                    season = season,
                    episode = episode,
                    server = server,
                )
            }

            url.contains("tmdbId=") -> {
                val tmdbId = url.substringAfter("tmdbId=").substringBefore("&")
                val mediaType = if (url.contains("mediaType=tv")) "tv" else "movie"
                val season = url.substringAfter("seasonId=", "").substringBefore("&").toIntOrNull()
                val episode = url.substringAfter("episodeId=", "").substringBefore("&").toIntOrNull()
                VideasyParams(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    season = season,
                    episode = episode,
                    server = server,
                )
            }

            else -> null
        }
    }

    override suspend fun extractVideo(embedUrl: String): List<ExtractedVideo> =
        withContext(Dispatchers.IO) {
            val params = parseVideasyUrl(embedUrl)
            val serverName = if (embedUrl.contains("server=") || embedUrl.contains("endpoint=")) {
                params?.server?.serverName ?: "Videasy"
            } else {
                "Videasy"
            }

            try {
                if (params == null) {
                    return@withContext listOf(
                        ExtractedVideo(
                            url = embedUrl,
                            serverName = serverName,
                            isWebView = true,
                        ),
                    )
                }

                val endpoint = params.server.endpoint
                val apiUrl = if (params.mediaType == "movie") {
                    "$SPEEDRACELIGHT_URL/$endpoint/sources-with-title?tmdbId=${params.tmdbId}&mediaType=movie"
                } else {
                    val s = params.season ?: 1
                    val e = params.episode ?: 1
                    "$SPEEDRACELIGHT_URL/$endpoint/sources-with-title?tmdbId=${params.tmdbId}&mediaType=tv&seasonId=$s&episodeId=$e"
                }

                val userAgent =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"

                // 1. Get encrypted payload from speedracelight
                val req = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", userAgent)
                    .header("Referer", VIDEASY_REFERER)
                    .header("Origin", "https://player.videasy.net")
                    .build()

                val resp = NetworkClient.okHttpClient.newCall(req).execute()
                val encData = resp.body.string()

                if (encData.isBlank() || !resp.isSuccessful) {
                    return@withContext listOf(
                        ExtractedVideo(
                            url = embedUrl,
                            serverName = serverName,
                            isWebView = true,
                        ),
                    )
                }

                // 2. Post to decryption API
                val postPayload = JSONObject().apply {
                    put("text", encData)
                    put("id", params.tmdbId)
                }

                val decReq = Request.Builder()
                    .url(DEC_API_URL)
                    .header("User-Agent", userAgent)
                    .post(postPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val decResp = NetworkClient.okHttpClient.newCall(decReq).execute()
                if (!decResp.isSuccessful) {
                    return@withContext listOf(
                        ExtractedVideo(
                            url = embedUrl,
                            serverName = serverName,
                            isWebView = true,
                        ),
                    )
                }

                val decJson = JSONObject(decResp.body.string())
                val result = decJson.optString("result")
                val resultJson = JSONObject(result)

                val sources = resultJson.optJSONArray("sources")
                val subtitles = mutableListOf<Subtitle>()

                val tracks = resultJson.optJSONArray("subtitles")
                if (tracks != null) {
                    for (i in 0 until tracks.length()) {
                        val track = tracks.getJSONObject(i)
                        val label = track.optString("lang", "Unknown")
                        val file = track.optString("url")
                        if (file.isNotEmpty()) {
                            subtitles.add(Subtitle(label = label, url = file))
                        }
                    }
                }

                if (sources != null && sources.length() > 0) {
                    val streamUrl = sources.getJSONObject(0).optString("url")
                    if (streamUrl.isNotEmpty()) {
                        return@withContext listOf(
                            ExtractedVideo(
                                url = streamUrl,
                                headers = mapOf("Referer" to VIDEASY_REFERER),
                                subtitles = subtitles,
                                serverName = serverName,
                                isWebView = false,
                            ),
                        )
                    }
                }

                listOf(
                    ExtractedVideo(
                        url = embedUrl,
                        serverName = serverName,
                        isWebView = true,
                    ),
                )
            } catch (e: Exception) {
                e.printStackTrace()
                listOf(
                    ExtractedVideo(
                        url = embedUrl,
                        serverName = serverName,
                        isWebView = true,
                    ),
                )
            }
        }
}
