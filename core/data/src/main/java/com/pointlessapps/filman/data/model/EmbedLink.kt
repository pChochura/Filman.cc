package com.pointlessapps.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class EmbedLink(
    val url: String,
    val serverName: String,
    val version: String = "",
    val quality: String = "",
    val sourceWebsite: String = "",
    val subtitles: List<com.pointlessapps.filman.data.api.extractors.Subtitle> = emptyList(),
    val isWebView: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
)
