package com.pointlessapps.filman.data.scraper.extractors

internal data class Subtitle(
    val url: String,
    val label: String,
    val language: String = "",
)

internal data class ExtractedVideo(
    val url: String,
    val audioUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val serverName: String = "",
    val version: String = "",
    val quality: String = "",
    val subtitles: List<Subtitle> = emptyList(),
    val isWebView: Boolean = false,
    val latency: Long = Long.MAX_VALUE,
)

internal interface EmbedExtractor {
    suspend fun extractVideo(embedUrl: String): List<ExtractedVideo>
}
