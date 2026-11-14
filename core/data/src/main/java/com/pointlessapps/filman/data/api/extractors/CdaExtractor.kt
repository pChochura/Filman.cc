package com.pointlessapps.filman.data.api.extractors

import com.pointlessapps.filman.data.api.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

object CdaExtractor : EmbedExtractor {
    private val patterns = listOf(
        Regex("""file:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""", RegexOption.IGNORE_CASE),
        Regex("""["']file["']\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""https?://[a-zA-Z0-9.\-_]+/[a-zA-Z0-9.\-_]+\.mp4\?[^"'\s<>]+""", RegexOption.IGNORE_CASE),
    )

    override suspend fun extractVideo(embedUrl: String): List<ExtractedVideo> =
        withContext(Dispatchers.IO) {
            try {
                val urlToFetch = if (embedUrl.contains("ebd.cda.pl") && !embedUrl.startsWith("http")) {
                    "https:$embedUrl"
                } else {
                    embedUrl
                }

                val request = Request.Builder()
                    .url(urlToFetch)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://cda-hd.cc/")
                    .build()

                val response = NetworkClient.okHttpClient.newCall(request).execute()
                val html = response.body?.string() ?: ""

                val doc = Jsoup.parse(html)
                val videoSrc = doc.selectFirst("video source[src]")?.attr("src")
                    ?: doc.selectFirst("video[src]")?.attr("src")

                if (!videoSrc.isNullOrBlank()) {
                    return@withContext listOf(ExtractedVideo(url = videoSrc))
                }

                for (pattern in patterns) {
                    val match = pattern.find(html)
                    if (match != null) {
                        return@withContext listOf(ExtractedVideo(url = match.groupValues.getOrElse(1) { match.value }))
                    }
                }

                listOf(
                    ExtractedVideo(
                        url = urlToFetch,
                        isWebView = true,
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                listOf(
                    ExtractedVideo(
                        url = embedUrl,
                        isWebView = true,
                    )
                )
            }
        }
}
