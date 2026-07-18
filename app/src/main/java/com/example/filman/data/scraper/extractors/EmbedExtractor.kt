package com.example.filman.data.scraper.extractors

internal data class ExtractedVideo(val url: String, val headers: Map<String, String> = emptyMap())

internal interface EmbedExtractor {
    suspend fun extractVideo(embedUrl: String): ExtractedVideo?
}
