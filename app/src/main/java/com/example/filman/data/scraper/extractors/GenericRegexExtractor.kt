package com.example.filman.data.scraper.extractors

import com.example.filman.data.scraper.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
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
                val response = NetworkClient.okHttpClient.newCall(request).execute()
                val html = response.body?.string() ?: ""

                for (pattern in patterns) {
                    val match = pattern.find(html)
                    if (match != null) {
                        // Extract subtitles from <track> tags if present
                        val subtitles = mutableListOf<Subtitle>()
                        if (html.contains("<track", ignoreCase = true)) {
                            val doc = Jsoup.parse(html)
                            doc.select("track").forEach { track ->
                                val src = track.attr("src")
                                val label = track.attr("label")
                                val srclang = track.attr("srclang")
                                if (src.isNotBlank()) {
                                    val name = label.ifBlank { srclang.ifBlank { "Unknown" } }
                                    subtitles.add(Subtitle(url = src, label = name))
                                }
                            }
                        }

                        return@withContext ExtractedVideo(
                            url = match.groupValues[1],
                            subtitles = subtitles,
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
}
