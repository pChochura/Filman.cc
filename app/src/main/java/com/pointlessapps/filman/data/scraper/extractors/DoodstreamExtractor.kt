package com.pointlessapps.filman.data.scraper.extractors

import com.pointlessapps.filman.data.scraper.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

internal object DoodstreamExtractor : EmbedExtractor {
    private val md5Regex = Regex("""/pass_md5/[^"']+""")
    private val domainRegex = Regex("""https?://[^/]+""")

    override suspend fun extractVideo(embedUrl: String): List<ExtractedVideo> =
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

                val md5Match = md5Regex.find(html)
                if (md5Match != null) {
                    val md5Url = md5Match.value
                    val token = md5Url.substringAfterLast("/")
                    val domain = domainRegex.find(embedUrl)?.value ?: return@withContext emptyList()

                    val req2 = Request.Builder()
                        .url(domain + md5Url)
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        )
                        .header("Referer", embedUrl)
                        .build()
                    val res2 = NetworkClient.okHttpClient.newCall(req2).execute()
                    val bodyText = res2.body?.string() ?: ""

                    val randomString = "abcdefghij"
                    val expiry = System.currentTimeMillis()
                    val videoUrl = "$bodyText$randomString?token=$token&expiry=$expiry"

                    return@withContext listOf(
                        ExtractedVideo(
                            url = videoUrl,
                            headers = mapOf("Referer" to embedUrl),
                        ),
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            emptyList()
        }
}
