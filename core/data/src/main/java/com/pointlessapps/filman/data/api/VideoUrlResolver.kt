package com.pointlessapps.filman.data.api

import android.util.Log
import com.pointlessapps.filman.data.api.dto.LinksResponse
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.data.api.extractors.Subtitle
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.EmbedLink
import com.pointlessapps.filman.data.model.TvShowSourceSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class VideoUrlResolver(
    private val repository: FilmanRepository,
    val settingsManager: SettingsManager,
) {
    suspend fun resolveAll(
        mediaUrl: String,
        detailedMedia: DetailedMedia? = null,
    ): List<ExtractedVideo> {
        val media = detailedMedia ?: repository.getMediaDetails(mediaUrl) ?: return emptyList()
        val linksResponse = pollLinks(mediaUrl, media.baseItem.isTvShow)
        val extracted = linksResponse?.links.orEmpty().map { link ->
            ExtractedVideo(
                url = link.url,
                serverName = link.serverName.ifEmpty { "${link.sourceWebsite} ${link.language}".trim() },
                version = link.language,
                sourceWebsite = link.sourceWebsite,
                quality = link.quality,
                headers = link.headers,
                subtitles = link.subtitles.map { sub ->
                    Subtitle(
                        url = sub.url,
                        label = sub.label,
                        language = sub.language,
                    )
                },
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
            if (settled || current.links.isNotEmpty()) {
                return current
            }

            delay(POLL_DELAY_MS)
        }

        return response
    }

    fun findBest(
        sources: List<ExtractedVideo>,
        targetSettings: TvShowSourceSettings? = null,
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
        targetSettings: TvShowSourceSettings? = null,
    ): ExtractedVideo? {
        val all = resolveAll(mediaUrl, detailedMedia)
        return findBest(all, targetSettings)
    }

    suspend fun prefetch(
        mediaUrl: String,
        detailedMedia: DetailedMedia? = null,
    ): List<ExtractedVideo> {
        return try {
            repository.wakeUp()
            val media = detailedMedia ?: repository.getMediaDetails(mediaUrl)
            if (media != null) {
                resolveAll(mediaUrl, detailedMedia = media)
            } else {
                repository.getLinks(mediaUrl, isTvShow = true)
                emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("VideoUrlResolver", "Prefetch failed for $mediaUrl", e)
            emptyList()
        }
    }

    fun getAlternativeUrls(detailedMedia: DetailedMedia?): List<ExtractedVideo> {
        val key = detailedMedia?.baseItem?.url ?: return emptyList()
        val links = repository.getCachedLinks(key)?.links ?: return emptyList()
        val extracted = links.map { link ->
            ExtractedVideo(
                url = link.url,
                serverName = link.serverName.ifEmpty { "${link.sourceWebsite} ${link.language}".trim() },
                version = link.language,
                sourceWebsite = link.sourceWebsite,
                quality = link.quality,
                headers = link.headers,
                subtitles = link.subtitles.map { sub ->
                    Subtitle(
                        url = sub.url,
                        label = sub.label,
                        language = sub.language,
                    )
                },
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
        quality.contains(preferred, ignoreCase = true) ||
        version.contains(preferred, ignoreCase = true)
    ) {
        0
    } else {
        1
    }

private const val MAX_POLL_ATTEMPTS = 220
private const val POLL_DELAY_MS = 250L
