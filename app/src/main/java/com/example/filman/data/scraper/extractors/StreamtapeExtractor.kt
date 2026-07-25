package com.example.filman.data.scraper.extractors

import com.example.filman.data.scraper.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

internal object StreamtapeExtractor : EmbedExtractor {

    private val robotRegex =
        Regex("""document\.getElementById\('robotlink'\)\.innerHTML\s*=\s*(.+?);""")
    private val urlPartRegex = Regex("""(/get_video\?[^'"]+)""")

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

                val robotMatch = robotRegex.find(html)
                if (robotMatch != null) {
                    val statement = robotMatch.groupValues[1]
                    val urlPart = urlPartRegex.find(statement)
                    if (urlPart != null) {
                        return@withContext ExtractedVideo("https://streamtape.com" + urlPart.groupValues[1])
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
}
