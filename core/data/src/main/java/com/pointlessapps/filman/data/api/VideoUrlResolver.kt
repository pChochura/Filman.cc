package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.EmbedLink
import com.pointlessapps.filman.data.model.TvShowSourceSettings
import com.pointlessapps.filman.data.api.dto.LinksResponse
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import kotlinx.coroutines.delay

class VideoUrlResolver(private val repository: FilmanRepository) {
    suspend fun resolveAll(
        mediaUrl: String,
        detailedMedia: DetailedMedia? = null,
    ): List<ExtractedVideo> {
        val media = detailedMedia ?: repository.getMediaDetails(mediaUrl) ?: return emptyList()

        var embeds = media.embeds
        if (embeds.isEmpty()) {
            val linksResponse = pollLinks(mediaUrl, media.baseItem.isTvShow)
            if (linksResponse != null && linksResponse.links.isNotEmpty()) {
                embeds = linksResponse.links.map {
                    EmbedLink(
                        url = it.url,
                        serverName = "${it.sourceWebsite} ${it.language}".trim(),
                        quality = it.quality,
                        sourceWebsite = it.sourceWebsite,
                        isWebView = it.displayMode == "WEBVIEW",
                        headers = it.headers,
                        subtitles = it.subtitles.map { sub ->
                            com.pointlessapps.filman.data.api.extractors.Subtitle(
                                url = sub.url,
                                label = sub.label,
                                language = sub.language,
                            )
                        }
                    )
                }
            }
        }

        return embeds.map {
            ExtractedVideo(
                url = it.url,
                serverName = it.serverName,
                sourceWebsite = it.sourceWebsite,
                quality = it.quality,
                isWebView = it.isWebView,
                headers = it.headers,
                subtitles = it.subtitles,
            )
        }
    }

    /** Extraction runs on the server in the background, so keep asking until it settles. */
    private suspend fun pollLinks(mediaUrl: String, isTvShow: Boolean): LinksResponse? {
        var response: LinksResponse? = null
        repeat(MAX_POLL_ATTEMPTS) { attempt ->
            response = repository.getLinks(mediaUrl, isTvShow)
            val current = response ?: return null
            val settled = current.status != "PENDING" && current.status != "PARTIAL"
            if (settled || (current.links.isNotEmpty() && attempt >= MIN_POLL_ATTEMPTS_WITH_LINKS)) return current
            delay(POLL_DELAY_MS)
        }
        return response
    }

    suspend fun getFastest(
        mediaUrl: String,
        detailedMedia: DetailedMedia? = null,
        targetSettings: TvShowSourceSettings? = null
    ): ExtractedVideo? {
        val all = resolveAll(mediaUrl, detailedMedia)
        if (all.isEmpty()) return null
        if (targetSettings != null && !targetSettings.sourceWebsite.isNullOrBlank()) {
            val matched = all.find { it.sourceWebsite.equals(targetSettings.sourceWebsite, ignoreCase = true) }
            if (matched != null) return matched
        }
        return all.first()
    }

    fun getAlternativeUrls(detailedMedia: DetailedMedia?): List<ExtractedVideo> {
        val embeds = detailedMedia?.embeds ?: return emptyList()
        return embeds.map { 
            ExtractedVideo(
                url = it.url,
                serverName = it.serverName,
                sourceWebsite = it.sourceWebsite,
                quality = it.quality,
                isWebView = it.isWebView,
                headers = it.headers,
                subtitles = it.subtitles,
            )
        }
    }
}

private const val MAX_POLL_ATTEMPTS = 30
private const val MIN_POLL_ATTEMPTS_WITH_LINKS = 3
private const val POLL_DELAY_MS = 2000L
