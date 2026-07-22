package com.example.filman.data.scraper.extractors

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object BoosteradxExtractor : WebViewExtractor() {
    
    override suspend fun extractVideo(embedUrl: String, context: Context?): ExtractedVideo? =
        withContext(Dispatchers.IO) {
            ExtractedVideo(embedUrl, isWebView = true)
        }
}
