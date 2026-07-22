package com.example.filman.data.scraper.extractors

import android.content.Context
import kotlinx.coroutines.Dispatchers
import android.content.Context
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

internal object StreamtapeExtractor : EmbedExtractor {

    private val robotRegex =
        Regex("""document\.getElementById\('robotlink'\)\.innerHTML\s*=\s*(.+?);""")
    private val urlPartRegex = Regex("""(/get_video\?[^'"]+)""")

    override suspend fun extractVideo(embedUrl: String, context: Context?): ExtractedVideo? =
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(embedUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()
                val html = doc.html()

                val script = doc.select("script").firstOrNull { it.html().contains("robotlink") }
                if (script != null) {
                    val content = script.html()
                    val robotMatch = robotRegex.find(content)
                    if (robotMatch != null) {
                        val statement = robotMatch.groupValues[1]
                        val urlPart = urlPartRegex.find(statement)
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
