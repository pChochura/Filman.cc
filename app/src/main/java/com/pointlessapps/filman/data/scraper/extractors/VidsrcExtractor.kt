package com.pointlessapps.filman.data.scraper.extractors

import com.pointlessapps.filman.data.scraper.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI

internal object VidsrcExtractor : EmbedExtractor {
    private val prorcpRegex = Regex("src:\\s*'(/prorcp/.*?)'")
    private val playerIdRegex = Regex("Playerjs.*file:\\s*([a-zA-Z0-9]+)\\s*,")
    private val playerjsFileRegex = Regex("""Playerjs.*file:\s*"([^"]*?)"""")
    private val defaultSubtitlesRegex =
        Regex(
            """default_subtitles\s*=\s*["']([^"']+)["']""",
            RegexOption.DOT_MATCHES_ALL,
        )

    override suspend fun extractVideo(embedUrl: String): List<ExtractedVideo> =
        withContext(Dispatchers.IO) {
            try {
                val userAgent =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"

                // 1. Fetch initial embed page
                val initialReq =
                    Request
                        .Builder()
                        .url(embedUrl)
                        .header("User-Agent", userAgent)
                        .build()
                val initialResp = NetworkClient.okHttpClient.newCall(initialReq).execute()
                val initialHtml = initialResp.body.string()

                val iframeSrc =
                    Jsoup
                        .parse(initialHtml)
                        .selectFirst("iframe#player_iframe")
                        ?.attr("src")
                        ?.let { if (it.startsWith("//")) "https:$it" else it }

                if (iframeSrc.isNullOrBlank()) {
                    return@withContext listOf(
                        ExtractedVideo(
                            url = embedUrl,
                            serverName = "VidSrc",
                            isWebView = true,
                        ),
                    )
                }

                // 2. Fetch iframe document
                val iframeReq =
                    Request
                        .Builder()
                        .url(iframeSrc)
                        .header("User-Agent", userAgent)
                        .header("Referer", embedUrl)
                        .build()
                val iframeResp = NetworkClient.okHttpClient.newCall(iframeReq).execute()
                val iframeHtml = iframeResp.body.string()

                val prorcpMatch = prorcpRegex.find(iframeHtml)?.groupValues?.get(1)
                if (prorcpMatch == null) {
                    return@withContext listOf(
                        ExtractedVideo(
                            url = iframeSrc,
                            serverName = "VidSrc",
                            isWebView = true,
                        ),
                    )
                }

                val prorcpUrl = iframeSrc.substringBefore("/rcp") + prorcpMatch

                // 3. Fetch prorcp script page
                val prorcpReq =
                    Request
                        .Builder()
                        .url(prorcpUrl)
                        .header("User-Agent", userAgent)
                        .header("Referer", iframeSrc)
                        .build()
                val prorcpResp = NetworkClient.okHttpClient.newCall(prorcpReq).execute()
                val script = prorcpResp.body.string()

                val playerId =
                    playerIdRegex
                        .find(script)
                        ?.groupValues
                        ?.get(1)
                        .orEmpty()
                val decryptedData =
                    if (playerId.isNotBlank()) {
                        val encryptedSource =
                            Regex("""<div id="$playerId" style="display:none;">\s*(.*?)\s*</div>""")
                                .find(script)
                                ?.groupValues
                                ?.get(1)
                                ?: return@withContext listOf(
                                    ExtractedVideo(
                                        url = prorcpUrl,
                                        serverName = "VidSrc",
                                        isWebView = true,
                                    ),
                                )
                        decrypt(playerId, encryptedSource)
                    } else {
                        playerjsFileRegex.find(script)?.groupValues?.get(1)
                    }

                if (decryptedData.isNullOrBlank()) {
                    return@withContext listOf(
                        ExtractedVideo(
                            url = prorcpUrl,
                            serverName = "VidSrc",
                            isWebView = true,
                        ),
                    )
                }

                val streamUrl =
                    decryptedData
                        .split(" or ")
                        .firstOrNull()
                        ?.replace(Regex("\\{[a-z]\\d+\\}"), "quibblezoomfable.com")

                if (streamUrl.isNullOrBlank()) {
                    return@withContext listOf(
                        ExtractedVideo(
                            url = prorcpUrl,
                            serverName = "VidSrc",
                            isWebView = true,
                        ),
                    )
                }

                // Subtitle parsing
                val subtitlesRaw =
                    defaultSubtitlesRegex
                        .find(script)
                        ?.groupValues
                        ?.get(1)
                        .orEmpty()
                val subtitles =
                    if (subtitlesRaw.isNotBlank()) {
                        val baseUri = runCatching { URI(iframeSrc) }.getOrNull()
                        val baseUrl = if (baseUri != null) "${baseUri.scheme}://${baseUri.host}" else ""

                        subtitlesRaw.split(",").mapNotNull { item ->
                            val label = item.substringAfter("[").substringBefore("]").trim()
                            val path = item.substringAfter("]").trim()
                            if (!path.startsWith("/")) return@mapNotNull null
                            Subtitle(
                                label = label,
                                url = if (baseUrl.isNotEmpty()) "$baseUrl$path" else path,
                            )
                        }
                    } else {
                        emptyList()
                    }

                listOf(
                    ExtractedVideo(
                        url = streamUrl,
                        headers = mapOf("Referer" to iframeSrc),
                        subtitles = subtitles,
                        serverName = "VidSrc",
                        isWebView = false,
                    ),
                )
            } catch (e: Exception) {
                e.printStackTrace()
                listOf(
                    ExtractedVideo(
                        url = embedUrl,
                        serverName = "VidSrc",
                        isWebView = true,
                    ),
                )
            }
        }

    internal fun decrypt(
        id: String,
        encrypted: String,
    ): String =
        when (id) {
            "NdonQLf1Tzyx7bMG" -> ndonQLf1Tzyx7bMG(encrypted)
            "sXnL9MQIry" -> sXnL9MQIry(encrypted)
            "IhWrImMIGL" -> ihWrImMIGL(encrypted)
            "xTyBxQyGTA" -> xTyBxQyGTA(encrypted)
            "ux8qjPHC66" -> ux8qjPHC66(encrypted)
            "eSfH1IRMyL" -> eSfH1IRMyL(encrypted)
            "KJHidj7det" -> kJHidj7det(encrypted)
            "o2VSUnjnZl" -> o2VSUnjnZl(encrypted)
            "Oi3v1dAlaM" -> oi3v1dAlaM(encrypted)
            "TsA2KGDGux" -> tsA2KGDGux(encrypted)
            "JoAHUMCLXV" -> joAHUMCLXV(encrypted)
            else -> throw IllegalArgumentException("Encryption type not implemented: $id")
        }

    private fun decodeBase64(input: String): ByteArray {
        val clean = input.trim().replace("\n", "").replace("\r", "")
        return try {
            java.util.Base64
                .getDecoder()
                .decode(clean)
        } catch (_: Throwable) {
            try {
                java.util.Base64
                    .getUrlDecoder()
                    .decode(clean)
            } catch (_: Throwable) {
                android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
            }
        }
    }

    internal fun ndonQLf1Tzyx7bMG(a: String): String {
        val b = 3
        val c = mutableListOf<String>()
        for (d in a.indices step b) {
            c.add(a.substring(d, minOf(d + b, a.length)))
        }
        return c.reversed().joinToString("")
    }

    internal fun sXnL9MQIry(a: String): String {
        val b = "pWB9V)[*4I`nJpp?ozyB~dbr9yt!_n4u"
        val d = a.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        var c = ""
        for (e in d.indices) {
            c += (d[e].code xor b[e % b.length].code).toChar()
        }
        var e = ""
        for (ch in c) {
            e += (ch.code - 3).toChar()
        }
        return String(decodeBase64(e))
    }

    internal fun ihWrImMIGL(a: String): String {
        val b = a.reversed()
        val c =
            b
                .map { ch ->
                    when {
                        (ch in 'a'..'m') || (ch in 'A'..'M') -> (ch.code + 13).toChar()
                        (ch in 'n'..'z') || (ch in 'N'..'Z') -> (ch.code - 13).toChar()
                        else -> ch
                    }
                }.joinToString("")
        val d = c.reversed()
        return String(decodeBase64(d))
    }

    internal fun xTyBxQyGTA(a: String): String {
        val b = a.reversed()
        val c = b.filterIndexed { index, _ -> index % 2 == 0 }
        return String(decodeBase64(c), Charsets.UTF_8)
    }

    internal fun ux8qjPHC66(a: String): String {
        val b = a.reversed()
        val c = "X9a(O;FMV2-7VO5x;Ao\u0005:dN1NoFs?j,"
        val d = b.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        var e = ""
        for (i in d.indices) {
            e += (d[i].code xor c[i % c.length].code).toChar()
        }
        return e
    }

    internal fun eSfH1IRMyL(a: String): String {
        val b = a.reversed()
        val c = b.map { (it.code - 1).toChar() }.joinToString("")
        return c.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
    }

    internal fun kJHidj7det(a: String): String {
        val b = a.substring(10, a.length - 16)
        val c = "3SAY~#%Y(V%>5d/Yg\"\$G[Lh1rK4a;7ok"
        val d = String(decodeBase64(b))
        val e = c.repeat((d.length + c.length - 1) / c.length).take(d.length)
        var f = ""
        for (i in d.indices) {
            f += (d[i].code xor e[i].code).toChar()
        }
        return f
    }

    internal fun o2VSUnjnZl(a: String): String {
        val shift = 3
        return a
            .map { char ->
                when (char) {
                    in 'a'..'z' -> {
                        val shifted = char - shift
                        if (shifted < 'a') shifted + 26 else shifted
                    }

                    in 'A'..'Z' -> {
                        val shifted = char - shift
                        if (shifted < 'A') shifted + 26 else shifted
                    }

                    else -> {
                        char
                    }
                }
            }.joinToString("")
    }

    internal fun oi3v1dAlaM(a: String): String {
        val b = a.reversed()
        val c = b.replace("-", "+").replace("_", "/")
        val d = String(decodeBase64(c))
        var e = ""
        val f = 5
        for (ch in d) {
            e += (ch.code - f).toChar()
        }
        return e
    }

    internal fun tsA2KGDGux(a: String): String {
        val b = a.reversed()
        val c = b.replace("-", "+").replace("_", "/")
        val d = String(decodeBase64(c))
        var e = ""
        val f = 7
        for (ch in d) {
            e += (ch.code - f).toChar()
        }
        return e
    }

    internal fun joAHUMCLXV(a: String): String {
        val b = a.reversed()
        val c = b.replace("-", "+").replace("_", "/")
        val d = String(decodeBase64(c))
        var e = ""
        val f = 3
        for (ch in d) {
            e += (ch.code - f).toChar()
        }
        return e
    }
}
