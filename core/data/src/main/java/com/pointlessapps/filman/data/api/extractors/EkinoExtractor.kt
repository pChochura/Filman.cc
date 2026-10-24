package com.pointlessapps.filman.data.api.extractors
import android.webkit.CookieManager
import com.pointlessapps.filman.config.PLAYER_USER_AGENT
import com.pointlessapps.filman.data.api.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

object EkinoExtractor : EmbedExtractor {
    override suspend fun extractVideo(embedUrl: String): List<ExtractedVideo> =
        withContext(Dispatchers.IO) {
            try {
                val cookie =
                    try {
                        CookieManager.getInstance().getCookie(embedUrl)
                    } catch (_: Exception) {
                        null
                    }

                val request = Request.Builder()
                    .url(embedUrl)
                    .header("User-Agent", PLAYER_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "pl,en-US;q=0.7,en;q=0.3")
                    .apply {
                        if (!cookie.isNullOrBlank()) {
                            header("Cookie", cookie)
                        }
                    }.build()

                val html = NetworkClient.okHttpClient.newCall(request).execute().use { resp ->
                    resp.body.string()
                }
                val doc = Jsoup.parse(html, embedUrl)

                val buttonEl = doc.selectFirst("a.buttonprch")
                val buttonHref = buttonEl?.attr("abs:href")?.takeIf { it.isNotBlank() }
                    ?: buttonEl?.attr("href")?.takeIf { it.isNotBlank() }?.let {
                        if (it.startsWith("http")) it else "https://ekino.ws/${it.trimStart('/')}"
                    }

                val iframeEl = doc.selectFirst("iframe")
                val iframeSrc = iframeEl?.attr("abs:src")?.takeIf { it.isNotBlank() }
                    ?: iframeEl?.attr("src")?.takeIf { it.isNotBlank() }?.let {
                        if (it.startsWith("//")) "https:$it"
                        else if (it.startsWith("http")) it
                        else "https://ekino.ws/${it.trimStart('/')}"
                    }

                val targetUrl = buttonHref ?: iframeSrc ?: embedUrl

                val finalUrl = when {
                    targetUrl.contains("ekino-tv.link") || targetUrl.contains("ekino.link") -> {
                        if (targetUrl.contains("/f/")) {
                            targetUrl.replace("/f/", "/e/")
                        } else {
                            targetUrl
                        }
                    }
                    targetUrl.contains("play.ekino.link") -> {
                        val targetCookie =
                            try {
                                CookieManager.getInstance().getCookie(targetUrl)
                            } catch (_: Exception) {
                                null
                            }

                        val playRequest = Request.Builder()
                            .url(targetUrl)
                            .header("User-Agent", PLAYER_USER_AGENT)
                            .header("Referer", "https://ekino.ws/")
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                            .header("Accept-Language", "pl,en-US;q=0.7,en;q=0.3")
                            .apply {
                                if (!targetCookie.isNullOrBlank()) {
                                    header("Cookie", targetCookie)
                                }
                            }.build()

                        val playDoc = runCatching {
                            val playHtml = NetworkClient.okHttpClient.newCall(playRequest).execute().use { resp ->
                                resp.body.string()
                            }
                            Jsoup.parse(playHtml, targetUrl)
                        }.getOrNull()

                        val playIframe = playDoc?.selectFirst("iframe")
                        val rawSrc = playIframe?.attr("abs:src")?.takeIf { it.isNotBlank() }
                            ?: playIframe?.attr("src")?.takeIf { it.isNotBlank() }
                            ?: targetUrl
                        if (rawSrc.startsWith("//")) {
                            "https:$rawSrc"
                        } else {
                            rawSrc
                        }
                    }
                    targetUrl.contains("dood") && targetUrl.contains("/d/") -> targetUrl.replace("/d/", "/e/")
                    targetUrl.contains("onlystream") && !targetUrl.contains("/e/") -> targetUrl.replace("onlystream.tv/", "onlystream.tv/e/")
                    else -> targetUrl
                }

                val extractor = getExtractorForUrl(finalUrl)
                if (extractor != null && extractor != this@EkinoExtractor) {
                    val extracted = extractor.extractVideo(finalUrl)
                    extracted.ifEmpty {
                        listOf(
                            ExtractedVideo(
                                url = finalUrl,
                                serverName = "",
                                sourceWebsite = "ekino.ws",
                                isWebView = true,
                                headers = mapOf("Referer" to "https://ekino.ws/"),
                            ),
                        )
                    }
                } else {
                    listOf(
                        ExtractedVideo(
                            url = finalUrl,
                            serverName = "",
                            sourceWebsite = "ekino.ws",
                            isWebView = true,
                            headers = mapOf("Referer" to "https://ekino.ws/"),
                        ),
                    )
                }
            } catch (_: Exception) {
                listOf(
                    ExtractedVideo(
                        url = embedUrl,
                        serverName = "",
                        sourceWebsite = "ekino.ws",
                        isWebView = true,
                        headers = mapOf("Referer" to "https://ekino.ws/"),
                    ),
                )
            }
        }
}
