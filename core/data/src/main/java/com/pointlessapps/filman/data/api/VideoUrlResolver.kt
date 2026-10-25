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
import java.net.URL

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

        var embeds = media.embeds
        if (embeds.isEmpty()) {
            val linksResponse = pollLinks(mediaUrl, media.baseItem.isTvShow)
            if (linksResponse != null && linksResponse.links.isNotEmpty()) {
                embeds = linksResponse.links.map {
                    EmbedLink(
                        url = it.url,
                        serverName = it.serverName.ifEmpty { "${it.sourceWebsite} ${it.language}".trim() },
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

        val extracted = embeds.map {
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
        return sortVideos(extracted)
    }

    private suspend fun pollLinks(mediaUrl: String, isTvShow: Boolean): LinksResponse? {
        var response: LinksResponse? = null
        var hasRequestedChallenge = false
        repeat(MAX_POLL_ATTEMPTS) { attempt ->
            response = repository.getLinks(mediaUrl, isTvShow)
            val current = response ?: return null

            if (current.requiresCloudflare && !zaluknijSessionManager.isChallengeActive.value && !hasRequestedChallenge) {
                hasRequestedChallenge = true
                zaluknijSessionManager.requestChallenge()
            }

            val settled = current.status != "PENDING" && current.status != "PARTIAL"
            if (settled) {
                if (current.links.isEmpty() && current.requiresFilmanAuth) {
                    throw AuthException("Filman requires authentication")
                }
                return current
            }

            if (current.links.isNotEmpty()) {
                val hasPolishSource = current.links.any { !it.sourceWebsite.contains("tmdb", ignoreCase = true) }
                if (hasPolishSource || attempt >= 3) {
                    return current
                }
            }

            delay(POLL_DELAY_MS)
        }

        if (response?.links.isNullOrEmpty() && response?.requiresFilmanAuth == true) {
            throw AuthException("Filman requires authentication")
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
        val embeds = detailedMedia?.embeds?.ifEmpty { null } ?: run {
            val key = detailedMedia?.baseItem?.url ?: return emptyList()
            repository.getCachedLinks(key)?.links?.map {
                EmbedLink(
                    url = it.url,
                    serverName = it.serverName.ifEmpty { "${it.sourceWebsite} ${it.language}".trim() },
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
                sourceWebsite = it.sourceWebsite,
                quality = it.quality,
                isWebView = it.isWebView,
                headers = it.headers,
                subtitles = it.subtitles,
            )
        }
        return sortVideos(extracted)
    }

    private fun getWebsiteConfigName(sourceWebsite: String): String {
        return when {
            sourceWebsite.contains("filman", ignoreCase = true) -> "filman"
            sourceWebsite.contains("zaluknij", ignoreCase = true) -> "zaluknij"
            sourceWebsite.contains("ekino", ignoreCase = true) -> "ekino"
            sourceWebsite.contains("tmdb", ignoreCase = true) -> "tmdb"
            else -> sourceWebsite.lowercase()
        }
    }

    fun sortVideos(videos: List<ExtractedVideo>): List<ExtractedVideo> {
        val sourcesConfig = settingsManager.sourcesPriorityFlow.value
        val preferredQuality = settingsManager.preferredQualityFlow.value

        return videos.filter { video ->
            val websiteConfigName = getWebsiteConfigName(video.sourceWebsite)
            val websiteConfig = sourcesConfig.find { it.name == websiteConfigName }
            if (websiteConfig != null && !websiteConfig.isEnabled) {
                false
            } else if (websiteConfig != null) {
                val serverName = video.serverName.ifEmpty {
                    runCatching { URL(video.url).host }.getOrNull().orEmpty()
                }.lowercase()
                val extractorConfig =
                    websiteConfig.extractors.find { it.name.lowercase() == serverName }
                extractorConfig?.isEnabled != false
            } else {
                true
            }
        }.sortedWith(
            compareBy(
                { video ->
                    val websiteConfigName = getWebsiteConfigName(video.sourceWebsite)
                    val index = sourcesConfig.indexOfFirst { it.name == websiteConfigName }
                    if (index != -1) index else Int.MAX_VALUE
                },
                { video ->
                    if (preferredQuality == SettingsConstants.Quality.AUTO) {
                        0
                    } else {
                        val hasQuality =
                            video.quality.contains(preferredQuality, ignoreCase = true) ||
                                video.version.contains(preferredQuality, ignoreCase = true)
                        if (hasQuality) 0 else 1
                    }
                },
                { video ->
                    val serverName = video.serverName.ifEmpty {
                        runCatching { URL(video.url).host }.getOrNull().orEmpty()
                    }.lowercase()
                    val websiteConfigName = getWebsiteConfigName(video.sourceWebsite)
                    val websiteConfig = sourcesConfig.find { it.name == websiteConfigName }
                    val index =
                        websiteConfig?.extractors?.indexOfFirst { it.name.lowercase() == serverName }
                            ?: -1
                    if (index != -1) index else Int.MAX_VALUE
                },
            ),
        )
    }

    fun sortEmbeds(embeds: List<EmbedLink>): List<EmbedLink> {
        val sourcesConfig = settingsManager.sourcesPriorityFlow.value
        val preferredQuality = settingsManager.preferredQualityFlow.value

        return embeds.filter { embed ->
            val websiteConfigName = getWebsiteConfigName(embed.sourceWebsite)
            val websiteConfig = sourcesConfig.find { it.name == websiteConfigName }
            if (websiteConfig != null && !websiteConfig.isEnabled) {
                false
            } else if (websiteConfig != null) {
                val serverName = embed.serverName.ifEmpty {
                    runCatching { URL(embed.url).host }.getOrNull().orEmpty()
                }.lowercase()
                val extractorConfig =
                    websiteConfig.extractors.find { it.name.lowercase() == serverName }
                extractorConfig?.isEnabled != false
            } else {
                true
            }
        }.sortedWith(
            compareBy(
                { embed ->
                    val websiteConfigName = getWebsiteConfigName(embed.sourceWebsite)
                    val index = sourcesConfig.indexOfFirst { it.name == websiteConfigName }
                    if (index != -1) index else Int.MAX_VALUE
                },
                { embed ->
                    if (preferredQuality == SettingsConstants.Quality.AUTO) {
                        0
                    } else {
                        val hasQuality =
                            embed.quality.contains(preferredQuality, ignoreCase = true) ||
                                embed.version.contains(preferredQuality, ignoreCase = true)
                        if (hasQuality) 0 else 1
                    }
                },
                { embed ->
                    val serverName = embed.serverName.ifEmpty {
                        runCatching { URL(embed.url).host }.getOrNull().orEmpty()
                    }.lowercase()
                    val websiteConfigName = getWebsiteConfigName(embed.sourceWebsite)
                    val websiteConfig = sourcesConfig.find { it.name == websiteConfigName }
                    val index =
                        websiteConfig?.extractors?.indexOfFirst { it.name.lowercase() == serverName }
                            ?: -1
                    if (index != -1) index else Int.MAX_VALUE
                },
            ),
        )
    }
}

private const val MAX_POLL_ATTEMPTS = 15
private const val POLL_DELAY_MS = 1000L
