package com.example.filman.data.scraper.extractors

internal data class Subtitle(
    val url: String,
    val label: String,
    val language: String = "",
)

internal data class ExtractedVideo(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val serverName: String = "",
    val version: String = "",
    val quality: String = "",
    val subtitles: List<Subtitle> = emptyList(),
)

internal interface EmbedExtractor {
    suspend fun extractVideo(embedUrl: String): ExtractedVideo?
}
