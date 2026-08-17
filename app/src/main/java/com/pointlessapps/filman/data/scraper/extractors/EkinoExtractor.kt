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
                    doc.selectFirst("iframe")?.attr("src") ?: embedUrl
                }

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
                } else if (targetUrl.contains("dood") && targetUrl.contains("/d/")) {
                    targetUrl.replace("/d/", "/e/")
                } else if (targetUrl.contains("onlystream") && !targetUrl.contains("/e/")) {
                    targetUrl.replace("onlystream.tv/", "onlystream.tv/e/")
                } else {
                    targetUrl
                }

                val extractor = getExtractorForUrl(finalUrl)
                if (extractor != null && extractor != this@EkinoExtractor) {
                    val extracted = extractor.extractVideo(finalUrl)
                    extracted.ifEmpty {
                        listOf(
                            ExtractedVideo(
                                url = finalUrl,
                                isWebView = true,
                            ),
                        )
                    }
                } else {
                    listOf(
                        ExtractedVideo(
                            url = finalUrl,
                            isWebView = true,
                        ),
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                listOf(
                    ExtractedVideo(
                        url = embedUrl,
                        isWebView = true,
                    ),
                )
            }
        }
}
