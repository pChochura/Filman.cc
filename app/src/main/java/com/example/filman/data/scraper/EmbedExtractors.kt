package com.example.filman.data.scraper

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup

data class ExtractedVideo(val url: String, val headers: Map<String, String> = emptyMap())

interface EmbedExtractor {
    val serverName: String
    suspend fun extractVideo(embedUrl: String): ExtractedVideo?
}

// Helper to resolve the AJAX link token and the obfuscated tmp-url.pro response
suspend fun resolveFilmanEmbedLink(
    cookie: String,
    userAgent: String,
    linkId: String,
    routeToken: String,
): String? = withContext(Dispatchers.IO) {
    try {
        val tokenUrl = "https://filman.cc/link/token?link_id=$linkId&rt=$routeToken"
        val response = Jsoup.connect(tokenUrl)
            .userAgent(userAgent)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Cookie", cookie)
            .ignoreContentType(true)
            .execute()
            .body()

        val json = JSONObject(response)
        val b64Url = json.getString("url")
        val tmpUrl = String(Base64.decode(b64Url, Base64.DEFAULT))

        // Now fetch tmpUrl
        val tmpDoc = Jsoup.connect(tmpUrl)
            .userAgent(userAgent)
            .get()
        val htmlContent = tmpDoc.html()

        // Find _e, _a, _b, _c
        val eMatch = Regex("var _e\\s*=\\s*'([^']+)'").find(htmlContent)
        val aMatch = Regex("var _a\\s*=\\s*'([^']+)'").find(htmlContent)
        val bMatch = Regex("var _b\\s*=\\s*'([^']+)'").find(htmlContent)
        val cMatch = Regex("var _c\\s*=\\s*'([^']+)'").find(htmlContent)

        if (eMatch != null && aMatch != null && bMatch != null && cMatch != null) {
            val eVal = eMatch.groupValues[1]
            val key = aMatch.groupValues[1] + bMatch.groupValues[1] + cMatch.groupValues[1]

            val raw = Base64.decode(eVal, Base64.DEFAULT)
            var out = ""
            for (i in raw.indices) {
                out += (raw[i].toInt() xor key[i % key.length].code).toChar()
            }
            return@withContext out // This is the real streamtape.com / vidoza / etc link
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    null
}

class VidozaExtractor : EmbedExtractor {
    override val serverName = "vidoza"

    override suspend fun extractVideo(embedUrl: String): ExtractedVideo? =
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(embedUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()

                // Vidoza typically stores the video URL in a source tag or inside a javascript variable.
                // Look for <source src="...mp4" type="video/mp4">
                val source = doc.selectFirst("source[type=video/mp4]")
                if (source != null) {
                    return@withContext ExtractedVideo(source.attr("src"))
                }

                // Fallback to regex on script tags
                val html = doc.html()
                val match = Regex("sources:\\s*\\[\\s*\"([^\"]+\\.mp4)\"").find(html)
                if (match != null) {
                    return@withContext ExtractedVideo(match.groupValues[1])
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
}

class StreamtapeExtractor : EmbedExtractor {
    override val serverName = "streamtape"

    override suspend fun extractVideo(embedUrl: String): ExtractedVideo? =
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(embedUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()
                val html = doc.html()

                val script = doc.select("script").firstOrNull { it.html().contains("robotlink") }
                if (script != null) {
                    val content = script.html()
                    val robotMatch = Regex("""document\.getElementById\('robotlink'\)\.innerHTML\s*=\s*(.+?);""").find(content)
                    if (robotMatch != null) {
                        val statement = robotMatch.groupValues[1]
                        val urlPart = Regex("""(/get_video\?[^'"]+)""").find(statement)
                        if (urlPart != null) {
                            return@withContext ExtractedVideo("https://streamtape.com" + urlPart.groupValues[1])
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
}

class VoeExtractor : EmbedExtractor {
    override val serverName = "voe"

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
        val patterns = listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")
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

    override suspend fun extractVideo(embedUrl: String): ExtractedVideo? =
        withContext(Dispatchers.IO) {
            try {
                var doc = Jsoup.connect(embedUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .followRedirects(true)
                    .execute()

                var html = doc.parse().html()
                val cookies = doc.cookies()

                // Check for JS redirect (e.g., window.location.href = 'https://jennifereconomicgive.com/...')
                val redirectMatch =
                    Regex("window\\.location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]").find(html)
                if (redirectMatch != null) {
                    val redirectUrl = redirectMatch.groupValues[1]
                    val doc2 = Jsoup.connect(redirectUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .cookies(cookies)
                        .header("Referer", embedUrl)
                        .followRedirects(true)
                        .execute()
                    html = doc2.parse().html()
                }

                val cookieString = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                val headers = mapOf("Cookie" to cookieString)

                // Try to extract via application/json array (Streamflix method)
                val jsonScriptMatch =
                    Regex("""<script\s+type="application/json">\s*\[\s*"([^"]+)"\s*\]\s*</script>""").find(
                        html,
                    )
                if (jsonScriptMatch != null) {
                    val encodedString = jsonScriptMatch.groupValues[1]
                    val decrypted = decryptVoe(encodedString)
                    if (decrypted != null && decrypted.has("source")) {
                        val m3u8 = decrypted.getString("source")
                        if (m3u8.isNotBlank() && !m3u8.contains("test-videos.co.uk")) {
                            return@withContext ExtractedVideo(m3u8, headers)
                        }
                    }
                }

                // Try fallback json string without array wrapper
                val jsonScriptMatchFallback =
                    Regex("""<script\s+type="application/json">\s*"([^"]+)"\s*</script>""").find(
                        html,
                    )
                if (jsonScriptMatchFallback != null) {
                    val encodedString = jsonScriptMatchFallback.groupValues[1]
                    val decrypted = decryptVoe(encodedString)
                    if (decrypted != null && decrypted.has("source")) {
                        val m3u8 = decrypted.getString("source")
                        if (m3u8.isNotBlank() && !m3u8.contains("test-videos.co.uk")) {
                            return@withContext ExtractedVideo(m3u8, headers)
                        }
                    }
                }

                // Legacy fallbacks
                var match = Regex("var\\s+source\\s*=\\s*['\"]([^'\"]+)['\"]").find(html)
                if (match != null && !match.groupValues[1].contains("test-videos.co.uk")) {
                    return@withContext ExtractedVideo(match.groupValues[1], headers)
                }

                match = Regex("hls\\s*:\\s*['\"]([^'\"]+)['\"]").find(html)
                if (match != null) {
                    return@withContext ExtractedVideo(match.groupValues[1], headers)
                }

                match = Regex("mp4\\s*:\\s*['\"]([^'\"]+)['\"]").find(html)
                if (match != null) {
                    return@withContext ExtractedVideo(match.groupValues[1], headers)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
}

class DoodstreamExtractor : EmbedExtractor {
    override val serverName = "doodstream"

    override suspend fun extractVideo(embedUrl: String): ExtractedVideo? =
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(embedUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()
                val html = doc.html()

                val md5Match = Regex("""/pass_md5/[^"']+""").find(html)
                if (md5Match != null) {
                    val md5Url = md5Match.value
                    val token = md5Url.substringAfterLast("/")
                    val domain = Regex("""https?://[^/]+""").find(embedUrl)?.value ?: return@withContext null
                    
                    val response = Jsoup.connect(domain + md5Url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Referer", embedUrl)
                        .ignoreContentType(true)
                        .execute()
                        .body()

                    val randomString = "abcdefghij" 
                    val expiry = System.currentTimeMillis()
                    val videoUrl = "$response$randomString?token=$token&expiry=$expiry"
                    
                    return@withContext ExtractedVideo(
                        url = videoUrl,
                        headers = mapOf("Referer" to embedUrl)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
}

class GenericRegexExtractor(override val serverName: String) : EmbedExtractor {
    override suspend fun extractVideo(embedUrl: String): ExtractedVideo? =
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(embedUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()
                val html = doc.html()
                
                val patterns = listOf(
                    Regex("""file:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""", RegexOption.IGNORE_CASE),
                    Regex("""source[^>]+src=["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""", RegexOption.IGNORE_CASE),
                    Regex("""src:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""", RegexOption.IGNORE_CASE),
                    Regex("""file:\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
                )
                
                for (pattern in patterns) {
                    val match = pattern.find(html)
                    if (match != null) {
                        return@withContext ExtractedVideo(match.groupValues[1])
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
}

fun getExtractorForUrl(url: String): EmbedExtractor? {
    if (url.contains("vidoza", ignoreCase = true)) return VidozaExtractor()
    if (url.contains("streamtape", ignoreCase = true)) return StreamtapeExtractor()
    if (url.contains("dood", ignoreCase = true) || url.contains("myvidplay", ignoreCase = true)) return DoodstreamExtractor()
    if (url.contains("vidmoly", ignoreCase = true)) return GenericRegexExtractor("vidmoly")
    if (url.contains("luluvdo", ignoreCase = true) || url.contains("lulustream", ignoreCase = true)) return GenericRegexExtractor("luluvdo")
    if (url.contains("savefiles", ignoreCase = true)) return GenericRegexExtractor("savefiles")
    if (url.contains("vidara", ignoreCase = true)) return GenericRegexExtractor("vidara")
    
    if (url.contains("voe.sx", ignoreCase = true) || url.contains(
            "jennifereconomicgive",
            ignoreCase = true,
        )
    ) return VoeExtractor()
    return null
}
