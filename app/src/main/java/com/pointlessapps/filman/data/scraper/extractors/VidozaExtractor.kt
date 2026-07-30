package com.pointlessapps.filman.data.scraper.extractors

import com.pointlessapps.filman.data.scraper.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

internal object VidozaExtractor : EmbedExtractor {

    private val regex = Regex("sources:\\s*\\[\\s*\"([^\"]+\\.mp4)\"")

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

                val doc = Jsoup.parse(html)
                val source = doc.selectFirst("source[type=video/mp4]")
                if (source != null) {
                    return@withContext ExtractedVideo(source.attr("src"))
                }

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
