package com.example.filman.data.scraper.extractors

import android.content.Context

internal data class ExtractedVideo(val url: String, val headers: Map<String, String> = emptyMap())

internal interface EmbedExtractor {
    suspend fun extractVideo(embedUrl: String, context: Context? = null): ExtractedVideo?
}
