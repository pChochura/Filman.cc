package com.example.filman.data.scraper.extractors

import android.content.Context
import kotlinx.coroutines.Dispatchers
import android.content.Context
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

internal object VidneoExtractor : EmbedExtractor {
    private val m3u8Regex = Regex("src(?:\\\\)?\"\\s*:\\s*(?:\\\\)?\"(/hls/[^\"]+/master\\.m3u8)(?:\\\\)?\"")

    override suspend fun extractVideo(embedUrl: String, context: Context?): ExtractedVideo? =
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(embedUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()
                
                val html = doc.html()
                val match = m3u8Regex.find(html)
                if (match != null) {
                    val path = match.groupValues[1]
                    val url = "https://vidneo.cc$path"
                    return@withContext ExtractedVideo(url)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
}
