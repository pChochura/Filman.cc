package com.pointlessapps.filman.data.scraper.extractors

import com.pointlessapps.filman.data.scraper.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URI

@OptIn(kotlin.ExperimentalUnsignedTypes::class)
internal object VideasyExtractor : EmbedExtractor {

    private const val SPEEDRACELIGHT_URL = "https://api.speedracelight.com"
    private const val VIDEASY_REFERER = "https://player.videasy.to/"
    private const val VIDEASY_ORIGIN = "https://player.videasy.to"

    private val F_CONSTANTS = uintArrayOf(
        1116352408u, 1899447441u, 3049323471u, 3921009573u,
        961987163u, 1508970993u, 2453635748u, 2870763221u,
        3624381080u, 310598401u, 607225278u, 1426881987u,
        1925078388u, 2162078206u, 2614888103u, 3248222580u,
    )
    private val H_HEADER = byteArrayOf(109, 118, 109, 49) // "mvm1"

    internal enum class VideasyServer(
        val serverName: String,
        val endpoint: String,
    ) {
        YORU("Videasy (Yoru)", "cdn"),
        BREACH("Videasy (Breach)", "m4uhd"),
        NEON("Videasy (Neon)", "vsrc"),
        CYPHER("Videasy (Cypher)", "downloader2"),
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

    private fun w(input: UInt): UInt {
        var e = input
        e = e xor (e shr 16)
        e *= 2246822507u
        e = e xor (e shr 13)
        e *= 3266489909u
        return e xor (e shr 16)
    }

    private fun v(e: UInt, count: Int): UInt {
        val t = count and 31
        return if (t == 0) e else ((e shl t) or (e shr (32 - t)))
    }

    private class Generator(val S: UIntArray, val hasS: BooleanArray, var acc: UInt)

    internal fun decrypt(encText: String, seed: String, mediaId: String): String {
        val base64 = encText.replace('-', '+').replace('_', '/')
            .padEnd((encText.length + 3) / 4 * 4, '=')
        val r = java.util.Base64.getDecoder().decode(base64)

        val S = UIntArray(61)
        val hasS = BooleanArray(61)
        var fnv = 2166136261u
        for (ch in seed) {
            fnv = (fnv xor ch.code.toUInt()) * 16777619u
        }
        val mediaIdNum = mediaId.toLongOrNull()?.toUInt() ?: 0u
        var a = w(w(fnv) xor w(mediaIdNum xor 2654435769u))
        for (eIdx in 0 until 8) {
            val t = (a % 61u).toInt()
            a = v(a + 2654435769u, 7 + (7 and eIdx))
            S[t] = a xor w(a)
            hasS[t] = true
            a = w(a + t.toUInt())
        }
        val acc = w(2779096485u xor a)
        val gen = Generator(S, hasS, acc)

        var counter = 0u
        var oVal = gen.acc
        var e = 0
        while (e < r.size) {
            val n = (oVal % 61u).toInt()
            val inS = gen.hasS[n]
            val i = if (inS) 0u.inv() else 0u
            val d = if (inS) gen.S[n] else 0u
            val s = oVal
            val aTerm = d xor (2654435769u * (counter + 1u))
            val l = (s xor aTerm) or (s and aTerm and i)
            val nextL = v(l + oVal, 31 and n) xor v(oVal, 31 and (n * 7))
            oVal = w(nextL + 2654435769u)
            gen.S[n] = oVal
            gen.hasS[n] = true
            gen.acc = oVal
            counter++
            val t = oVal

            r[e] = (r[e].toInt() xor (t and 0xFFu).toInt()).toByte()
            e++
            if (e < r.size) {
                r[e] = (r[e].toInt() xor ((t shr 8) and 0xFFu).toInt()).toByte()
                e++
            }
            if (e < r.size) {
                r[e] = (r[e].toInt() xor ((t shr 16) and 0xFFu).toInt()).toByte()
                e++
            }
            if (e < r.size) {
                r[e] = (r[e].toInt() xor ((t shr 24) and 0xFFu).toInt()).toByte()
                e++
            }
        }

        for (hIdx in H_HEADER.indices) {
            if (r[hIdx] != H_HEADER[hIdx]) {
                throw IllegalStateException("Decryption header mismatch")
            }
        }
        return String(r, 4, r.size - 4, Charsets.UTF_8)
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

                val userAgent =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"

                // 1. Fetch seed from speedracelight
                val seedReq = Request.Builder()
                    .url("$SPEEDRACELIGHT_URL/seed?mediaId=${params.tmdbId}")
                    .header("User-Agent", userAgent)
                    .header("Referer", VIDEASY_REFERER)
                    .header("Origin", VIDEASY_ORIGIN)
                    .build()

                val seedResp = NetworkClient.okHttpClient.newCall(seedReq).execute()
                val seedBody = seedResp.body.string()
                val seed = JSONObject(seedBody).optString("seed")

                if (seed.isBlank()) {
                    return@withContext listOf(
                        ExtractedVideo(
                            url = embedUrl,
                            serverName = serverName,
                            isWebView = true,
                        ),
                    )
                }

                // 2. Fetch encrypted stream from requested endpoint, with fallbacks
                val endpointsToTry = mutableListOf(params.server.endpoint)
                listOf("cdn", "m4uhd").forEach {
                    if (!endpointsToTry.contains(it)) endpointsToTry.add(it)
                }

                for (endpoint in endpointsToTry) {
                    val queryParams = if (params.mediaType == "movie") {
                        "tmdbId=${params.tmdbId}&mediaType=movie&enc=2&seed=$seed"
                    } else {
                        val s = params.season ?: 1
                        val e = params.episode ?: 1
                        "tmdbId=${params.tmdbId}&mediaType=tv&seasonId=$s&episodeId=$e&enc=2&seed=$seed"
                    }

                    val streamReq = Request.Builder()
                        .url("$SPEEDRACELIGHT_URL/$endpoint/sources-with-title?$queryParams")
                        .header("User-Agent", userAgent)
                        .header("Referer", VIDEASY_REFERER)
                        .header("Origin", VIDEASY_ORIGIN)
                        .build()

                    val streamResp = runCatching { NetworkClient.okHttpClient.newCall(streamReq).execute() }.getOrNull()
                        ?: continue
                    val encData = streamResp.body.string()

                    if (!streamResp.isSuccessful || encData.isBlank() || encData.startsWith("{")) {
                        continue
                    }

                    val decryptedJson = runCatching {
                        decrypt(encData, seed, params.tmdbId)
                    }.getOrNull() ?: continue

                    val resultJson = runCatching { JSONObject(decryptedJson) }.getOrNull() ?: continue
                    val sources = resultJson.optJSONArray("sources")
                    val subtitles = mutableListOf<Subtitle>()

                    val tracks = resultJson.optJSONArray("subtitles")
                    if (tracks != null) {
                        for (i in 0 until tracks.length()) {
                            val track = tracks.getJSONObject(i)
                            val label = track.optString("language").ifEmpty { track.optString("lang", "Unknown") }
                            val file = track.optString("url")
                            if (file.isNotEmpty()) {
                                subtitles.add(Subtitle(label = label, url = file))
                            }
                        }
                    }

                    val playlistUrl = resultJson.optString("playlist")
                    val firstSourceUrl = sources?.optJSONObject(0)?.optString("url").orEmpty()
                    val streamUrl = playlistUrl.ifEmpty { firstSourceUrl }

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
