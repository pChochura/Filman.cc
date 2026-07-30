package com.pointlessapps.filman.data.scraper.extractors

import android.util.Base64
import com.pointlessapps.filman.data.scraper.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

internal object VoeExtractor : EmbedExtractor {

    private val redirectRegex = Regex("window\\.location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]")
    private val jsonScriptRegex =
        Regex("""<script\s+type="application/json">\s*\[\s*"([^"]+)"\s*\]\s*</script>""")
    private val jsonScriptFallbackRegex =
        Regex("""<script\s+type="application/json">\s*"([^"]+)"\s*</script>""")
    private val b64Regex = Regex("""var\s+[a-zA-Z0-9_]+\s*=\s*['"]([a-zA-Z0-9+/=]{100,})['"]""")
    private val m3u8Regex = Regex("""(https?://[^'"]+\.m3u8[^'"]*)""")
    private val varRegex = Regex("var\\s+source\\s*=\\s*['\"]([^'\"]+)['\"]")
    private val hlsRegex = Regex("hls\\s*:\\s*['\"]([^'\"]+)['\"]")
    private val mp4Regex = Regex("mp4\\s*:\\s*['\"]([^'\"]+)['\"]")
    private val baseSubtitleRegex = Regex("""var\s+base\s*=\s*['"]([^'"]+)['"]""")

    private fun rot13(input: String): String {
        return input.map { c ->
            when (c) {
                in 'A'..'Z' -> ((c - 'A' + 13) % 26 + 'A'.code).toChar()
                in 'a'..'z' -> ((c - 'a' + 13) % 26 + 'a'.code).toChar()
                else -> c
            }
        }.joinToString("")
    }

    private fun replacePatterns(input: String): String {
        val patterns = listOf("@\$", "^^", "~@", "%?", "*~", "!!", "#&")
        return patterns.fold(input) { result, pattern ->
            result.replace(pattern, "_")
        }
    }

    private fun removeUnderscores(input: String): String = input.replace("_", "")

    private fun charShift(input: String, shift: Int): String {
        return input.map { (it.code - shift).toChar() }.joinToString("")
    }

    private fun reverse(input: String): String = input.reversed()

    private fun decryptVoe(encodedString: String): JSONObject? {
        return try {
            val vF = rot13(encodedString)
            val vF2 = replacePatterns(vF)
            val vF3 = removeUnderscores(vF2)
            val vF4 = String(Base64.decode(vF3, Base64.DEFAULT), Charsets.UTF_8)
            val vF5 = charShift(vF4, 3)
            val vF6 = reverse(vF5)
            val vAtob = String(Base64.decode(vF6, Base64.DEFAULT), Charsets.UTF_8)
            JSONObject(vAtob)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractSubtitles(decrypted: JSONObject, html: String): List<Subtitle> {
        val baseMatch = baseSubtitleRegex.find(html)
        val base = baseMatch?.groupValues?.get(1) ?: ""

        if (!decrypted.has("captions")) return emptyList()
        val captions = decrypted.optJSONArray("captions") ?: return emptyList()

        val subs = mutableListOf<Subtitle>()
        for (i in 0 until captions.length()) {
            val cap = captions.optJSONObject(i) ?: continue
            val file = cap.optString("file", "")
            if (file.isNotBlank()) {
                val url = if (file.startsWith("http")) file else base + file
                subs.add(Subtitle(url = url, label = cap.optString("label", "")))
            }
        }
        return subs
    }

    override suspend fun extractVideo(embedUrl: String): ExtractedVideo? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(embedUrl)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    )
                    .build()
                var response = NetworkClient.okHttpClient.newCall(request).execute()
                var html = response.body?.string() ?: ""
                var currentUrl = response.request.url.toString()

                val cookies = response.headers("Set-Cookie").map { it.substringBefore(";") }
                val cookieString = cookies.joinToString("; ")

                // Check for JS redirect
                val redirectMatch = redirectRegex.find(html)
                if (redirectMatch != null) {
                    val redirectUrl = redirectMatch.groupValues[1]
                    val req2 = Request.Builder()
                        .url(redirectUrl)
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        )
                        .header("Referer", currentUrl)
                        .header("Cookie", cookieString)
                        .build()
                    response = NetworkClient.okHttpClient.newCall(req2).execute()
                    html = response.body?.string() ?: ""
                }

                val headers =
                    if (cookieString.isNotBlank()) mapOf("Cookie" to cookieString) else emptyMap()

                // Try to extract via application/json array (Streamflix method)
                val jsonScriptMatch = jsonScriptRegex.find(html)
                if (jsonScriptMatch != null) {
                    val encodedString = jsonScriptMatch.groupValues[1]
                    val decrypted = decryptVoe(encodedString)
                    if (decrypted != null && decrypted.has("source")) {
                        val m3u8 = decrypted.getString("source")
                        if (m3u8.isNotBlank() && !m3u8.contains("test-videos.co.uk")) {
                            return@withContext ExtractedVideo(
                                url = m3u8,
                                headers = headers,
                                subtitles = extractSubtitles(decrypted, html),
                            )
                        }
                    }
                }

                // Try fallback json string without array wrapper
                val jsonScriptMatchFallback = jsonScriptFallbackRegex.find(html)
                if (jsonScriptMatchFallback != null) {
                    val encodedString = jsonScriptMatchFallback.groupValues[1]
                    val decrypted = decryptVoe(encodedString)
                    if (decrypted != null && decrypted.has("source")) {
                        val m3u8 = decrypted.getString("source")
                        if (m3u8.isNotBlank() && !m3u8.contains("test-videos.co.uk")) {
                            return@withContext ExtractedVideo(
                                url = m3u8,
                                headers = headers,
                                subtitles = extractSubtitles(decrypted, html),
                            )
                        }
                    }
                }

                // Try simple Base64 variable
                val b64Match = b64Regex.find(html)
                if (b64Match != null) {
                    try {
                        val decoded = String(
                            Base64.decode(b64Match.groupValues[1], Base64.DEFAULT),
                            Charsets.UTF_8,
                        )
                        val m3u8Match = m3u8Regex.find(decoded)
                        if (m3u8Match != null && !m3u8Match.value.contains("test-videos.co.uk")) {
                            return@withContext ExtractedVideo(m3u8Match.value, headers)
                        }
                    } catch (e: Exception) {
                        // ignore and fall through
                    }
                }

                // Legacy fallbacks
                var match = varRegex.find(html)
                if (match != null && !match.groupValues[1].contains("test-videos.co.uk")) {
                    return@withContext ExtractedVideo(match.groupValues[1], headers)
                }

                match = hlsRegex.find(html)
                if (match != null) {
                    return@withContext ExtractedVideo(match.groupValues[1], headers)
                }

                match = mp4Regex.find(html)
                if (match != null) {
                    return@withContext ExtractedVideo(match.groupValues[1], headers)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
}
