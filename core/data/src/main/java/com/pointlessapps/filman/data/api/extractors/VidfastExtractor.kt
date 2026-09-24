package com.pointlessapps.filman.data.api.extractors

object VidfastExtractor : EmbedExtractor {
    override suspend fun extractVideo(embedUrl: String): List<ExtractedVideo> =
        listOf(
            ExtractedVideo(
                url = embedUrl,
                serverName = "VidFast",
                isWebView = true,
            ),
        )
}
