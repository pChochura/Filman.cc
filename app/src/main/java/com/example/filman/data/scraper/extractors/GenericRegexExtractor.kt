package com.example.filman.data.scraper.extractors

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

internal object GenericRegexExtractor : EmbedExtractor {

    private val patterns = listOf(
        Regex("""file:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""", RegexOption.IGNORE_CASE),
        Regex("""source[^>]+src=["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""", RegexOption.IGNORE_CASE),
        Regex("""src:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""", RegexOption.IGNORE_CASE),
        Regex("""file:\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""sources:\s*\[\{file:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex(
            """sources:\s*\[\s*\{\s*["']?file["']?\s*:\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        ),
    )

    override suspend fun extractVideo(embedUrl: String, context: Context?): ExtractedVideo? =
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(embedUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()
                val html = doc.html()

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
