package com.example.filman.data.scraper.extractors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

internal object VidozaExtractor : EmbedExtractor {

    private val regex = Regex("sources:\\s*\\[\\s*\"([^\"]+\\.mp4)\"")

    override suspend fun extractVideo(embedUrl: String): ExtractedVideo? =
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(embedUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()

                val source = doc.selectFirst("source[type=video/mp4]")
                if (source != null) {
                    return@withContext ExtractedVideo(source.attr("src"))
                }

                val html = doc.html()
                val match = regex.find(html)
                if (match != null) {
                    return@withContext ExtractedVideo(match.groupValues[1])
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
}
