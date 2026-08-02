package com.pointlessapps.filman.data.scraper.extractors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object EkinoExtractor : EmbedExtractor {
    override suspend fun extractVideo(embedUrl: String): List<ExtractedVideo> =
        withContext(Dispatchers.IO) {
            try {
                val doc = org.jsoup.Jsoup.connect(embedUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                    )
                    .header("Accept-Language", "pl,en-US;q=0.7,en;q=0.3")
                    .ignoreContentType(true)
                    .get()

                val buttonHref = doc.selectFirst("a.buttonprch")?.attr("href")

                val targetUrl = if (!buttonHref.isNullOrEmpty()) {
                    buttonHref
                } else {
                    doc.selectFirst("iframe")?.attr("src")
                } ?: return@withContext emptyList()

                val finalUrl = if (targetUrl.contains("play.ekino.link")) {
                    val playDoc = org.jsoup.Jsoup.connect(targetUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")
                        .header("Referer", "https://ekino.ws/")
                        .header(
                            "Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                        )
                        .header("Accept-Language", "pl,en-US;q=0.7,en;q=0.3")
                        .ignoreContentType(true)
                        .get()

                    playDoc.selectFirst("iframe")?.attr("src") ?: targetUrl
                } else {
                    targetUrl
                }

                val extractor = getExtractorForUrl(finalUrl)
                if (extractor != null && extractor != this@EkinoExtractor) {
                    extractor.extractVideo(finalUrl)
                } else {
                    listOf(
                        ExtractedVideo(
                            url = finalUrl,
                            isWebView = finalUrl.contains("sb") || finalUrl.contains("upzone") || finalUrl.contains(
                                "voe",
                            ) || finalUrl.contains("dood"),
                        ),
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
}
