package com.pointlessapps.filman.data.scraper.extractors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException

internal object YoutubeExtractor : EmbedExtractor {
    override suspend fun extractVideo(embedUrl: String): ExtractedVideo? =
        withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val streamExtractor = service.getStreamExtractor(embedUrl)
                
                try {
                    streamExtractor.fetchPage()
                } catch (e: ContentNotAvailableException) {
                    if (e.message?.contains("reload") == true) {
                        streamExtractor.fetchPage()
                    } else {
                        throw e
                    }
                }

                val videoStreams = streamExtractor.videoStreams
                val bestStream = videoStreams.maxByOrNull { it.height }

                if (bestStream != null) {
                    ExtractedVideo(
                        url = bestStream.content,
                        serverName = "YouTube",
                        quality = "${bestStream.height}p",
                        isWebView = false,
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
}
