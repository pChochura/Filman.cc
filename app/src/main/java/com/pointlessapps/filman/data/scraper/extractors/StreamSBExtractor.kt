package com.pointlessapps.filman.data.scraper.extractors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object StreamSBExtractor : EmbedExtractor {
    override suspend fun extractVideo(embedUrl: String): ExtractedVideo? =
        withContext(Dispatchers.IO) {
            ExtractedVideo(
                url = embedUrl,
                serverName = "StreamSB (WebView)",
                isWebView = true
            )
        }
}
