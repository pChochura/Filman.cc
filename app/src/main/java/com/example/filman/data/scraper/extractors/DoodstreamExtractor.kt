package com.example.filman.data.scraper.extractors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

internal object DoodstreamExtractor : EmbedExtractor {
    private val md5Regex = Regex("""/pass_md5/[^"']+""")
    private val domainRegex = Regex("""https?://[^/]+""")

    override suspend fun extractVideo(embedUrl: String): ExtractedVideo? =
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(embedUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()
                val html = doc.html()

                val md5Match = md5Regex.find(html)
                if (md5Match != null) {
                    val md5Url = md5Match.value
                    val token = md5Url.substringAfterLast("/")
                    val domain = domainRegex.find(embedUrl)?.value ?: return@withContext null

                    val response = Jsoup.connect(domain + md5Url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Referer", embedUrl)
                        .ignoreContentType(true)
                        .execute()
                        .body()

                    val randomString = "abcdefghij"
                    val expiry = System.currentTimeMillis()
                    val videoUrl = "$response$randomString?token=$token&expiry=$expiry"

                    return@withContext ExtractedVideo(
                        url = videoUrl,
                        headers = mapOf("Referer" to embedUrl),
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
}
