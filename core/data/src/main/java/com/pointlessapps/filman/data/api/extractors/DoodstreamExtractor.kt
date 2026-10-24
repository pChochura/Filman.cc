package com.pointlessapps.filman.data.api.extractors

import com.pointlessapps.filman.data.api.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object DoodstreamExtractor : EmbedExtractor {
    private val md5Regex = Regex("""/pass_md5/[^"']+""")
    private val domainRegex = Regex("""https?://[^/]+""")

    override suspend fun extractVideo(embedUrl: String): List<ExtractedVideo> =
        withContext(Dispatchers.IO) {
            try {
                val normalizedUrl = if (embedUrl.contains("/d/")) embedUrl.replace("/d/", "/e/") else embedUrl
                val request =
                    Request
                        .Builder()
                        .url(normalizedUrl)
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        ).build()

                val (html, finalUrl) = NetworkClient.okHttpClient.newCall(request).execute().use { resp ->
                    resp.body.string() to resp.request.url.toString()
                }

                val md5Match = md5Regex.find(html)
                if (md5Match != null) {
                    val md5Url = md5Match.value
                    val token = md5Url.substringAfterLast("/")
                    val baseDomain = domainRegex.find(finalUrl)?.value
                        ?: domainRegex.find(normalizedUrl)?.value
                        ?: return@withContext emptyList()

                    val req2 =
                        Request
                            .Builder()
                            .url(baseDomain + md5Url)
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                            ).header("Referer", finalUrl)
                            .build()

                    val bodyText = NetworkClient.okHttpClient.newCall(req2).execute().use { resp ->
                        resp.body.string().trim()
                    }

                    if (bodyText.isNotEmpty()) {
                        val randomString = "abcdefghij"
                        val expiry = System.currentTimeMillis()
                        val videoUrl = "$bodyText$randomString?token=$token&expiry=$expiry"

                        return@withContext listOf(
                            ExtractedVideo(
                                url = videoUrl,
                                serverName = "Doodstream",
                                headers = mapOf("Referer" to finalUrl),
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
            }
            emptyList()
        }
}
