package com.pointlessapps.filman.data.api

import android.util.Log
import com.pointlessapps.filman.data.api.dto.LinksResponse
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.EmbedLink
import com.pointlessapps.filman.data.model.TvShowSourceSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class VideoUrlResolver(
    private val repository: TiwiRepository,
    val settingsManager: SettingsManager,
) {
    suspend fun resolveAll(
        mediaUrl: String,
        detailedMedia: DetailedMedia? = null,
    ): List<ExtractedVideo> {
        val media = detailedMedia ?: repository.getMediaDetails(mediaUrl) ?: return emptyList()
        val linksResponse = pollLinks(mediaUrl, media.baseItem.isTvShow)
        return sortVideos(linksResponse?.links.orEmpty().toExtractedVideos(), detailedMedia = media)
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
            val targetVersion = targetSettings.version
            if (!targetVersion.isNullOrBlank()) {
                val fullWithVersionMatch = sources.find { video ->
                    val serverMatches = targetSettings.serverName.isNullOrBlank() ||
                        video.serverName.contains(targetSettings.serverName, ignoreCase = true) ||
                        targetSettings.serverName.contains(video.serverName, ignoreCase = true)
                    val websiteMatches = targetSettings.sourceWebsite.isNullOrBlank() ||
                        video.sourceWebsite.equals(targetSettings.sourceWebsite, ignoreCase = true)
                    val versionMatches = video.version.contains(targetVersion, ignoreCase = true)
                    serverMatches && websiteMatches && versionMatches
                }
                if (fullWithVersionMatch != null) return fullWithVersionMatch

                val versionOnlyMatch = sources.find { video ->
                    video.version.contains(targetVersion, ignoreCase = true)
                }
                if (versionOnlyMatch != null) return versionOnlyMatch
            }

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
        return sortVideos(links.toExtractedVideos(), detailedMedia = detailedMedia)
    }

    fun sortVideos(
        videos: List<ExtractedVideo>,
        detailedMedia: DetailedMedia? = null,
    ): List<ExtractedVideo> {
        val preferredQuality = settingsManager.preferredQualityFlow.value
        val expectedTitle = detailedMedia?.baseItem?.titleEn ?: detailedMedia?.baseItem?.titlePl
        val expectedYear = detailedMedia?.metaInfo?.year ?: detailedMedia?.baseItem?.year
        val season = detailedMedia?.baseItem?.seasonNumber
        val episode = detailedMedia?.baseItem?.episodeNumber

        val compatible = if (detailedMedia?.baseItem?.isTvShow == true) {
            videos.filter { video ->
                val textToCheck = video.filename.ifBlank { video.serverName }
                !VideoSignatureExtractor.isEpisodeMismatched(textToCheck, season, episode)
            }
        } else {
            videos
        }

        return compatible.sortedWith(
            compareByDescending<ExtractedVideo> { video ->
                val textToCheck = video.filename.ifBlank { video.serverName }
                VideoSignatureExtractor.computeConfidence(textToCheck, expectedTitle, expectedYear)
            }.thenByDescending { video ->
                isPolishSource(video)
            }.thenBy { video ->
                VideoQualityUtils.qualityRank(video.quality, video.version, preferredQuality)
            }
        )
    }

    companion object {
        fun isPolishSource(video: ExtractedVideo): Boolean {
            val server = video.serverName.lowercase()
            val version = video.version.lowercase()
            val filename = video.filename.lowercase()
            val url = video.url.lowercase()
            return server.contains("polish") ||
                version.contains("dub") || version.contains("lektor") || version.contains("napisy") || version == "pl" ||
                filename.contains("[pl") || filename.contains("dubbing") || filename.contains("lektor") ||
                url.contains("cda.pl") || url.contains("bajeczki24") || url.contains("obejrzyj") || url.contains("vider")
        }
    }

    fun sortEmbeds(embeds: List<EmbedLink>): List<EmbedLink> {
        val preferredQuality = settingsManager.preferredQualityFlow.value
        return embeds.sortedBy { VideoQualityUtils.qualityRank(it.quality, it.version, preferredQuality) }
    }
}

private const val MAX_POLL_ATTEMPTS = 220
private const val POLL_DELAY_MS = 250L
