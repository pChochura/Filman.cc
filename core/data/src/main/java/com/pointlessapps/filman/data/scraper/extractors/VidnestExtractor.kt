package com.pointlessapps.filman.data.scraper.extractors

import com.pointlessapps.filman.data.scraper.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

object VidnestExtractor : EmbedExtractor {
    private val fileRegex = Regex("""file\s*:\s*["']([^"']+)["']""")
    private val tracksRegex = Regex("""tracks\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
    private val objectRegex = Regex("""\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
    private val kindRegex = Regex("""kind\s*:\s*"([^"]+)"""")
    private val rawFileRegex = Regex("""file\s*:\s*"([^"]+)"""")
    private val labelRegex = Regex("""label\s*:\s*"([^"]+)"""")
    private val httpUrlRegex = Regex("""https?://[^\s"']+\.vtt[^\s"']*""")

    fun extractSubtitles(text: String): List<Subtitle> {
        val tracksBlock = tracksRegex.find(text)?.groupValues?.get(1) ?: return emptyList()

        return objectRegex
            .findAll(tracksBlock)
            .mapNotNull { match ->
                val obj = match.groupValues[1]
                val kind = kindRegex.find(obj)?.groupValues?.get(1)
                if (kind != "captions") return@mapNotNull null

                val rawFile = rawFileRegex.find(obj)?.groupValues?.get(1) ?: return@mapNotNull null
                val label = labelRegex.find(obj)?.groupValues?.get(1) ?: return@mapNotNull null

                val file = httpUrlRegex.find(rawFile)?.value ?: rawFile
                Subtitle(
                    url = file,
                    label = label,
                )
            }.toList()
    }

    override suspend fun extractVideo(embedUrl: String): List<ExtractedVideo> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request
                        .Builder()
                        .url(embedUrl)
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
                        ).build()

                val response = NetworkClient.okHttpClient.newCall(request).execute()
                val html = response.body.string()
                val doc = Jsoup.parse(html)

                val scriptTags = doc.select("script")
                var m3u8: String? = null
                var subtitles: List<Subtitle> = emptyList()

                for (script in scriptTags) {
                    val scriptData = script.data()
                    if ("jwplayer" in scriptData && "sources" in scriptData && "file" in scriptData) {
                        val match = fileRegex.find(scriptData)
                        if (match != null) {
                            m3u8 = match.groupValues[1]
                            subtitles = extractSubtitles(scriptData)
                            break
                        }
                    }
                }

                if (m3u8.isNullOrBlank()) {
                    return@withContext listOf(
                        ExtractedVideo(
                            url = embedUrl,
                            serverName = "VidNest",
                            isWebView = true,
                        ),
                    )
                }

                listOf(
                    ExtractedVideo(
                        url = m3u8,
                        subtitles = subtitles,
                        serverName = "VidNest",
                        isWebView = false,
                    ),
                )
            } catch (e: Exception) {
                e.printStackTrace()
                listOf(
                    ExtractedVideo(
                        url = embedUrl,
                        serverName = "VidNest",
                        isWebView = true,
                    ),
                )
            }
        }
}
