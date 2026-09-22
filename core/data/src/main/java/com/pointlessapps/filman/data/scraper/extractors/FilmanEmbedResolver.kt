package com.pointlessapps.filman.data.scraper.extractors

import android.util.Base64
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.data.scraper.NetworkClient
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
): String? =
    withContext(Dispatchers.IO) {
        try {
            val tokenUrl = "${FilmanConfig.BASE_URL}/link/token?link_id=$linkId&rt=$routeToken"

            val req1 =
                okhttp3.Request
                    .Builder()
                    .url(tokenUrl)
                    .header("User-Agent", userAgent)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Cookie", cookie)
                    .build()
            val responseText =
                NetworkClient.okHttpClient
                    .newCall(req1)
                    .execute()
                    .use { it.body.string() }

            val b64Url =
                try {
                    val json = JSONObject(responseText)
                    json.getString("url")
                } catch (e: Exception) {
                    return@withContext null
                }
            val tmpUrl = String(Base64.decode(b64Url, Base64.DEFAULT))

            // Now fetch tmpUrl
            val req2 =
                okhttp3.Request
                    .Builder()
                    .url(tmpUrl)
                    .header("User-Agent", userAgent)
                    .build()
            val response = NetworkClient.okHttpClient.newCall(req2).execute()
            val (htmlContent, finalUrl) = response.use { r ->
                r.body.string() to r.request.url.toString()
            }

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
            return@withContext finalUrl
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

fun getExtractorForUrl(
    url: String,
    serverName: String? = null,
) = when {
    url.matches("ekino.ws") || serverName?.matches("ekino") == true -> EkinoExtractor

    url.matches("vidoza") || serverName?.matches("vidoza") == true -> VidozaExtractor

    url.matches("streamtape") || serverName?.matches("streamtape") == true -> StreamtapeExtractor

    url.matches("dood") || url.matches("myvidplay") || serverName?.matches("dood") == true -> DoodstreamExtractor

    url.matches("vidmoly") || serverName?.matches("vidmoly") == true -> GenericRegexExtractor

    url.matches("luluvdo") || url.matches("lulustream") -> GenericRegexExtractor

    url.matches("savefiles") -> GenericRegexExtractor

    url.matches("vidara") -> GenericRegexExtractor

    url.matches("upzone") -> GenericRegexExtractor

    url.matches("voe.sx") ||
        url.matches("jennifereconomicgive") ||
        url.matches("streamflix") ||
        serverName?.matches("voe") == true
    -> VoeExtractor

    Regex("https?://(?:sb[a-zA-Z0-9]*|pelistop|cloudemb|vidgomunime|keephealth|streamsss|lvturbo|ssbstream)\\.[a-z]+/.*").containsMatchIn(
        url,
    ) || serverName?.matches("streamsb") == true -> StreamSBExtractor

    url.matches("vidsrc") || url.matches("vsembed") || serverName?.matches("vidsrc") == true -> VidsrcExtractor

    url.matches("vidnest") || serverName?.matches("vidnest") == true -> VidnestExtractor

    url.matches("vidcore") || serverName?.matches("vidcore") == true -> VidcoreExtractor

    url.matches("vidfast") || serverName?.matches("vidfast") == true -> VidfastExtractor

    url.matches("videasy") || url.matches("speedracelight") || serverName?.matches("videasy") == true -> VideasyExtractor

    url.matches("youtube.com") || url.matches("youtu.be") || serverName?.matches("youtube") == true -> YoutubeExtractor

    else -> null
}

private fun String.matches(prefix: String) = contains(prefix, ignoreCase = true)
