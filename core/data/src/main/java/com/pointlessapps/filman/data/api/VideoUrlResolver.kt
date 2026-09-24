package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.EmbedLink
import com.pointlessapps.filman.data.model.TvShowSourceSettings
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo

class VideoUrlResolver(private val repository: FilmanRepository) {
    suspend fun prefetch(mediaUrl: String, detailedMedia: DetailedMedia? = null) {}

    suspend fun getFastest(
        mediaUrl: String,
        detailedMedia: DetailedMedia? = null,
        targetSettings: TvShowSourceSettings? = null
    ): ExtractedVideo? {
        val media = detailedMedia ?: repository.getMediaDetails(mediaUrl) ?: return null
        val bestEmbed = media.embeds.firstOrNull() ?: return null
        return ExtractedVideo(
            url = bestEmbed.url,
            serverName = bestEmbed.serverName,
            sourceWebsite = bestEmbed.serverName
        )
    }

    fun getAlternativeUrls(mediaUrl: String, detailedMedia: DetailedMedia? = null): List<ExtractedVideo> {
        val embeds = detailedMedia?.embeds ?: return emptyList()
        return embeds.map { 
            ExtractedVideo(
                url = it.url,
                serverName = it.serverName,
                sourceWebsite = it.serverName
            )
        }
    }
}
