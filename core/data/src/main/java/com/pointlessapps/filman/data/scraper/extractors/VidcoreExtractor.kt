package com.pointlessapps.filman.data.scraper.extractors

object VidcoreExtractor : EmbedExtractor {
    override suspend fun extractVideo(embedUrl: String): List<ExtractedVideo> =
        listOf(
            ExtractedVideo(
                url = embedUrl,
                serverName = "VidCore",
                isWebView = true,
            ),
        )
}
