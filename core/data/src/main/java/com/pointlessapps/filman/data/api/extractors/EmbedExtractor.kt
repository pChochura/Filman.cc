package com.pointlessapps.filman.data.api.extractors

import kotlinx.serialization.Serializable

@Serializable
data class Subtitle(
    val url: String,
    val label: String,
    val language: String = "",
)

data class ExtractedVideo(
    val url: String,
    val audioUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val serverName: String = "",
    val version: String = "",
    val quality: String = "",
    val subtitles: List<Subtitle> = emptyList(),
    val latency: Long = Long.MAX_VALUE,
    val sourceWebsite: String = "",
    val filename: String = "",
)

interface EmbedExtractor {
    suspend fun extractVideo(embedUrl: String): List<ExtractedVideo>
}
