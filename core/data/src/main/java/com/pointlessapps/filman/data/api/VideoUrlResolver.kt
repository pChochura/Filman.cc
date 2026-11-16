package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.config.EkinoConfig
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.config.ZaluknijConfig
import com.pointlessapps.filman.data.api.dto.LinksResponse
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.ZaluknijSessionManager
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.EmbedLink
import com.pointlessapps.filman.data.model.TvShowSourceSettings
import kotlinx.coroutines.delay

fun sourceWebsiteConfigName(sourceWebsite: String): String = when {
    sourceWebsite.contains("filman", ignoreCase = true) -> "filman"
    sourceWebsite.contains("zaluknij", ignoreCase = true) -> "zaluknij"
    sourceWebsite.contains("ekino", ignoreCase = true) -> "ekino"
    sourceWebsite.contains("cda", ignoreCase = true) -> "cda_hd"
    sourceWebsite.contains("tmdb", ignoreCase = true) -> "tmdb"
    else -> sourceWebsite.lowercase()
}

class VideoUrlResolver(
    private val repository: FilmanRepository,
    val settingsManager: SettingsManager,
    val sessionManager: SessionManager,
    val zaluknijSessionManager: ZaluknijSessionManager,
) {
    suspend fun resolveAll(
        mediaUrl: String,
        detailedMedia: DetailedMedia? = null,
    ): List<ExtractedVideo> {
        val media = detailedMedia ?: repository.getMediaDetails(mediaUrl) ?: return emptyList()

        val linksResponse = pollLinks(mediaUrl, media.baseItem.isTvShow)
        val embeds = linksResponse?.links.orEmpty().map {
                    EmbedLink(
                        url = it.url,
                        serverName = it.serverName.ifEmpty { "${it.sourceWebsite} ${it.language}".trim() },
                        version = it.language,
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

        val extracted = embeds.map {
            ExtractedVideo(
                url = it.url,
                serverName = it.serverName,
                version = it.version,
                sourceWebsite = it.sourceWebsite,
                quality = it.quality,
                isWebView = it.isWebView,
                headers = it.headers,
                subtitles = it.subtitles,
            )
        }
        return sortVideos(extracted)
    }

    private suspend fun pollLinks(mediaUrl: String, isTvShow: Boolean): LinksResponse? {
        var response: LinksResponse? = null
        repeat(MAX_POLL_ATTEMPTS) {
            response = repository.getLinks(mediaUrl, isTvShow)
            val current = response ?: return null

            val settled = current.status != "PENDING" && current.status != "PARTIAL"
            if (settled) {
                return current
            }

            if (current.links.isNotEmpty()) {
                return current
            }

            delay(POLL_DELAY_MS)
        }

        return response
    }

    fun findBest(
        sources: List<ExtractedVideo>,
        targetSettings: TvShowSourceSettings? = null
    ): ExtractedVideo? {
        if (sources.isEmpty()) return null

        if (targetSettings != null) {
            val matched = sources.find { video ->
                val serverMatches = targetSettings.serverName.isNullOrBlank() ||
                    video.serverName.contains(targetSettings.serverName, ignoreCase = true) ||
                    targetSettings.serverName.contains(video.serverName, ignoreCase = true)
                val websiteMatches = targetSettings.sourceWebsite.isNullOrBlank() ||
                    video.sourceWebsite.equals(targetSettings.sourceWebsite, ignoreCase = true)
                serverMatches && websiteMatches
            } ?: sources.find { video ->
                !targetSettings.sourceWebsite.isNullOrBlank() &&
                    video.sourceWebsite.equals(targetSettings.sourceWebsite, ignoreCase = true)
            }
            if (matched != null) return matched
        }

        return sources.firstOrNull()
    }

    suspend fun getFastest(
        mediaUrl: String,
        detailedMedia: DetailedMedia? = null,
        targetSettings: TvShowSourceSettings? = null
    ): ExtractedVideo? {
        val all = resolveAll(mediaUrl, detailedMedia)
        return findBest(all, targetSettings)
    }

    fun getAlternativeUrls(detailedMedia: DetailedMedia?): List<ExtractedVideo> {
        val embeds = run {
            val key = detailedMedia?.baseItem?.url ?: return emptyList()
            repository.getCachedLinks(key)?.links?.map {
                EmbedLink(
                    url = it.url,
                    serverName = it.serverName.ifEmpty { "${it.sourceWebsite} ${it.language}".trim() },
                    version = it.language,
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
        } ?: return emptyList()

        val extracted = embeds.map {
            ExtractedVideo(
                url = it.url,
                serverName = it.serverName,
                version = it.version,
                sourceWebsite = it.sourceWebsite,
                quality = it.quality,
                isWebView = it.isWebView,
                headers = it.headers,
                subtitles = it.subtitles,
            )
        }
        return sortVideos(extracted)
    }

    fun sortVideos(videos: List<ExtractedVideo>): List<ExtractedVideo> {
        val preferredQuality = settingsManager.preferredQualityFlow.value
        return videos.sortedBy { qualityRank(it.quality, it.version, preferredQuality) }
    }

    fun sortEmbeds(embeds: List<EmbedLink>): List<EmbedLink> {
        val preferredQuality = settingsManager.preferredQualityFlow.value
        return embeds.sortedBy { qualityRank(it.quality, it.version, preferredQuality) }
    }
}

private fun qualityRank(quality: String, version: String, preferred: String): Int =
    if (preferred == SettingsConstants.Quality.AUTO ||
        quality.contains(preferred, ignoreCase = true) || version.contains(preferred, ignoreCase = true)
    ) 0 else 1

private const val MAX_POLL_ATTEMPTS = 55
private const val POLL_DELAY_MS = 1000L
