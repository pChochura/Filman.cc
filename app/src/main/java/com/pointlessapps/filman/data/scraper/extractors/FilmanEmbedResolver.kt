package com.pointlessapps.filman.data.scraper.extractors

import android.util.Base64
import com.pointlessapps.filman.config.FilmanConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val eRegex = Regex("var _e\\s*=\\s*'([^']+)'")
private val aRegex = Regex("var _a\\s*=\\s*'([^']+)'")
private val bRegex = Regex("var _b\\s*=\\s*'([^']+)'")
private val cRegex = Regex("var _c\\s*=\\s*'([^']+)'")

// Helper to resolve the AJAX link token and the obfuscated tmp-url.pro response
suspend fun resolveFilmanEmbedLink(
    cookie: String,
    userAgent: String,
    linkId: String,
    routeToken: String,
): String? = withContext(Dispatchers.IO) {
    try {
        val tokenUrl = "${FilmanConfig.BASE_URL}/link/token?link_id=$linkId&rt=$routeToken"

        val req1 = okhttp3.Request.Builder()
            .url(tokenUrl)
            .header("User-Agent", userAgent)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Cookie", cookie)
            .build()
        val responseText =
            com.pointlessapps.filman.data.scraper.NetworkClient.okHttpClient.newCall(req1)
                .execute().body?.string() ?: ""

        val json = JSONObject(responseText)
        val b64Url = json.getString("url")
        val tmpUrl = String(Base64.decode(b64Url, Base64.DEFAULT))

        // Now fetch tmpUrl
        val req2 = okhttp3.Request.Builder()
            .url(tmpUrl)
            .header("User-Agent", userAgent)
            .build()
        val htmlContent =
            com.pointlessapps.filman.data.scraper.NetworkClient.okHttpClient.newCall(req2)
                .execute().body?.string() ?: ""

        // Find _e, _a, _b, _c
        val eMatch = eRegex.find(htmlContent)
        val aMatch = aRegex.find(htmlContent)
        val bMatch = bRegex.find(htmlContent)
        val cMatch = cRegex.find(htmlContent)

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

        // Sometimes tmpUrl redirects directly if not obfuscated
        return@withContext tmpUrl
    } catch (e: Exception) {
        e.printStackTrace()
    }
    null
}

internal fun getExtractorForUrl(url: String) = when {
    url.matches("ekino.ws") -> EkinoExtractor
    url.matches("vidoza") -> VidozaExtractor
    url.matches("streamtape") -> StreamtapeExtractor
    url.matches("dood") || url.matches("myvidplay") -> DoodstreamExtractor
    url.matches("vidmoly") -> GenericRegexExtractor
    url.matches("luluvdo") || url.matches("lulustream") -> GenericRegexExtractor
    url.matches("savefiles") -> GenericRegexExtractor
    url.matches("vidara") -> GenericRegexExtractor
    url.matches("upzone") -> GenericRegexExtractor
    url.matches("voe.sx") ||
            url.matches("jennifereconomicgive") ||
            url.matches("streamflix")
        -> VoeExtractor

    Regex("https?://(?:sb[a-zA-Z0-9]*|pelistop|cloudemb|vidgomunime|keephealth|streamsss|lvturbo|ssbstream)\\.[a-z]+/.*").containsMatchIn(
        url,
    ) -> StreamSBExtractor

    else -> null
}

private fun String.matches(prefix: String) = contains(prefix, ignoreCase = true)
