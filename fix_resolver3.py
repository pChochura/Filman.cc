with open("core/data/src/main/java/com/pointlessapps/filman/data/api/VideoUrlResolver.kt", "r") as f:
    content = f.read()

correct = """package com.pointlessapps.filman.data.api

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
        
        var embeds = media.embeds
        if (embeds.isEmpty()) {
            val linksResponse = repository.getLinks(mediaUrl, media.baseItem.isTvShow)
            if (linksResponse != null && linksResponse.links.isNotEmpty()) {
                embeds = linksResponse.links.map {
                    EmbedLink(
                        url = it.url,
                        serverName = "${it.sourceWebsite} ${it.language}".trim(),
                        quality = it.quality,
                        sourceWebsite = it.sourceWebsite
                    )
                }
            }
        }
        
        val bestEmbed = embeds.firstOrNull() ?: return null
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
                sourceWebsite = it.sourceWebsite
            )
        }
    }
}
"""

with open("core/data/src/main/java/com/pointlessapps/filman/data/api/VideoUrlResolver.kt", "w") as f:
    f.write(correct)
