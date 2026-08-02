package com.pointlessapps.filman.data.scraper.extractors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object StreamSBExtractor : EmbedExtractor {
    override suspend fun extractVideo(embedUrl: String): List<ExtractedVideo> =
        withContext(Dispatchers.IO) {
            listOf(
                ExtractedVideo(
                    url = embedUrl,
                    serverName = "StreamSB (WebView)",
                    isWebView = true,
                ),
            )
        }
}
