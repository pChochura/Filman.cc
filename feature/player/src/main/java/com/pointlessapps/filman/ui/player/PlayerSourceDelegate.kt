package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.TiwiRepository
import com.pointlessapps.filman.data.api.VideoQualityUtils
import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.data.api.extractors.YoutubeExtractor
import com.pointlessapps.filman.data.api.pollStreamLinks
import com.pointlessapps.filman.data.api.toExtractedVideos
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.TvShowSettingsManager
import com.pointlessapps.filman.data.model.AudioPreferenceMode
import com.pointlessapps.filman.data.model.ContentCategory
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.MediaMetadata
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.model.TvShowSourceSettings
import com.pointlessapps.filman.data.model.classifyContent
import com.pointlessapps.filman.data.model.hasPolishSpokenAudio
import com.pointlessapps.filman.data.model.isOriginallyPolish
import com.pointlessapps.filman.data.model.resolveShouldEnableLektor
import com.pointlessapps.filman.data.model.resolveSubtitlesEnabled
import com.pointlessapps.filman.data.model.getMediaKey
import com.pointlessapps.filman.data.model.getTvShowKey
import com.pointlessapps.filman.player.lektor.engine.LektorModelManager
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.milliseconds

internal class PlayerSourceDelegate(
    private val repository: TiwiRepository,
    private val videoUrlResolver: VideoUrlResolver,
    private val settingsManager: SettingsManager,
    private val tvShowSettingsManager: TvShowSettingsManager,
    private val progressManager: ProgressManager?,
    private val lektorModelManager: LektorModelManager,
    private val subtitleDelegate: PlayerSubtitleDelegate,
    private val nextEpisodeDelegate: PlayerNextEpisodeDelegate,
    private val stateProvider: () -> PlayerState,
    private val updateState: ((PlayerState) -> PlayerState) -> Unit,
    private val refreshOverlayMenu: () -> Unit,
    private val handleError: (Throwable) -> Unit,
    private val launchWork: (
        context: CoroutineContext,
        onError: ((Throwable) -> Unit)?,
        block: suspend CoroutineScope.() -> Unit,
    ) -> Job,
) {
    private var currentMediaUrl: String? = null

    fun loadDetails(
        url: String,
        title: String? = null,
        description: String? = null,
        isTrailer: Boolean = false,
    ) {
        nextEpisodeDelegate.resetForNewMedia()
        resetStateForNewLoad()

        if (!title.isNullOrBlank() || !description.isNullOrBlank()) {
            updateState {
                it.copy(
                    detailedMedia = DetailedMedia(
                        baseItem = MovieItem(
                            url = url,
                            titlePl = title.orEmpty(),
                            titleEn = title,
                            posterUrl = "",
                            description = description.orEmpty(),
                            isTrailer = isTrailer,
                        ),
                    ),
                )
            }
        }

        launchWork(
            EmptyCoroutineContext,
            { t ->
                updateState {
                    it.copy(
                        shared = it.shared.copy(
                            isLoading = false,
                            errorMessage = TextValue.StringResource(R.string.player_no_playable_video),
                        ),
                    )
                }
                handleError(t)
            },
        ) {
            val existingProgress = progressManager?.getProgressForUrlWhenLoaded(url)
            val startPos = (existingProgress as? ProgressItem.InProgress)?.progressMs ?: 0L
            updateState { it.copy(startPositionMs = startPos, currentPositionMs = startPos) }

            val isDirectYoutube = url.contains("youtube.com", ignoreCase = true) ||
                url.contains("youtu.be", ignoreCase = true)

            if (isDirectYoutube) {
                currentMediaUrl = url
                loadDirectYoutubeStream(
                    url = url,
                    initialTitle = title,
                    initialDescription = description,
                    isTrailer = isTrailer,
                )
                return@launchWork
            }

            currentMediaUrl = url
            val detailedMedia = repository.getMediaDetails(url)
            val details = detailedMedia?.baseItem
            if (details == null) {
                updateState {
                    it.copy(
                        shared = it.shared.copy(
                            isLoading = false,
                            errorMessage = TextValue.StringResource(R.string.error_media_not_found),
                        ),
                    )
                }
                return@launchWork
            }

            val mediaKey = details.getMediaKey() ?: details.getTvShowKey()
            val savedSettings = if (mediaKey != null) {
                tvShowSettingsManager.getSettingsForMedia(mediaKey)
            } else {
                null
            }

            val category = classifyContent(
                genreIds = details.genreIds,
                originalLanguage = details.originalLanguage,
                countries = detailedMedia.metaInfo?.countries.orEmpty(),
                isFromKidsSection = url.contains("forkids") || details.url.contains("forkids"),
            )
            val isPolishProduction = isOriginallyPolish(
                originalLanguage = details.originalLanguage,
                countries = detailedMedia.metaInfo?.countries.orEmpty(),
            )
            val effectiveCategory = if (isPolishProduction) ContentCategory.NATIVE_DOMESTIC else category
            val contentRule = settingsManager.contentRulesFlow.value.getRule(effectiveCategory)

            val targetSettingsWithRule = if (savedSettings?.version == null) {
                val preferredVersion = when (contentRule.resolvedAudioMode) {
                    AudioPreferenceMode.DUBBING_PREFERRED -> "dub"
                    AudioPreferenceMode.LEKTOR_PREFERRED -> "lektor"
                    AudioPreferenceMode.ORIGINAL_SUBTITLES -> "napisy"
                    AudioPreferenceMode.ORIGINAL_ONLY -> if (isPolishProduction) "pl" else "napisy"
                }
                savedSettings?.copy(version = preferredVersion) ?: TvShowSourceSettings(version = preferredVersion)
            } else {
                savedSettings
            }

            val allSources = videoUrlResolver.resolveAll(url, detailedMedia = detailedMedia)
            val extracted = videoUrlResolver.findBest(allSources, targetSettings = targetSettingsWithRule)
                ?: videoUrlResolver.findBest(allSources, targetSettings = savedSettings)
                ?: allSources.firstOrNull()

            if (extracted == null) {
                updateState {
                    it.copy(
                        shared = it.shared.copy(
                            isLoading = false,
                            errorMessage = TextValue.StringResource(R.string.stream_api_unavailable),
                        ),
                    )
                }
                return@launchWork
            }

            val isFinalPolishProduction = isOriginallyPolish(
                originalLanguage = details.originalLanguage,
                countries = detailedMedia.metaInfo?.countries.orEmpty(),
                sourceVersion = extracted.version,
                sourceFilename = extracted.filename,
                allSources = allSources,
            )
            val finalCategory = if (isFinalPolishProduction) ContentCategory.NATIVE_DOMESTIC else category
            val finalContentRule = settingsManager.contentRulesFlow.value.getRule(finalCategory)

            val hasPolishSpoken = hasPolishSpokenAudio(
                isPolishProduction = isFinalPolishProduction,
                sourceVersion = extracted.version,
                sourceFilename = extracted.filename,
            )
            val shouldEnableLektor = resolveShouldEnableLektor(
                isPolishProduction = isFinalPolishProduction,
                hasPolishSpokenAudio = hasPolishSpoken,
                contentRule = finalContentRule,
                isLektorSettingEnabled = settingsManager.isLektorEnabled.value,
            )
            val subtitlesDefault = resolveSubtitlesEnabled(
                isPolishProduction = isFinalPolishProduction,
                hasPolishSpokenAudio = hasPolishSpoken,
                contentRule = finalContentRule,
                shouldEnableLektor = shouldEnableLektor,
                savedSubtitlesEnabled = savedSettings?.subtitlesEnabled,
            )

            val selectedSubtitleUrl = if (subtitlesDefault) {
                subtitleDelegate.resolveInitialSubtitleUrl(
                    extracted.subtitles,
                    savedSettings,
                ) ?: if (shouldEnableLektor || finalContentRule.resolvedAudioMode == AudioPreferenceMode.ORIGINAL_SUBTITLES) {
                    extracted.subtitles.find { subtitleDelegate.subtitleRepository.isPolishSubtitle(it) }?.url
                } else null
            } else {
                null
            }
            val speed = savedSettings?.playbackSpeed ?: stateProvider().playbackSpeed
            val aspect = savedSettings?.aspectRatioMode ?: stateProvider().aspectRatioMode
            val offset = savedSettings?.subtitleOffsetMs ?: 0

            updateState {
                it.copy(
                    shared = it.shared.copy(isLoading = false),
                    detailedMedia = detailedMedia,
                    videoHeaders = extracted.headers,
                    videoUrl = extracted.url,
                    subtitles = extracted.subtitles,
                    selectedSubtitleUrl = selectedSubtitleUrl,
                    subtitleOffsetMs = offset,
                    startPositionMs = startPos,
                    playbackSpeed = speed,
                    aspectRatioMode = aspect,
                    alternativeSources = allSources,
                    isLektorEnabled = shouldEnableLektor,
                )
            }

            if (selectedSubtitleUrl != null) {
                subtitleDelegate.loadRawSubtitleContent(selectedSubtitleUrl)
            }
            if (shouldEnableLektor || (subtitlesDefault && (savedSettings?.subtitleLanguage?.lowercase() in setOf("pl", "pol")))) {
                subtitleDelegate.ensurePolishSubtitlesForLektorOrPreference(
                    sourceSubtitles = extracted.subtitles,
                    selectedSubtitleUrl = selectedSubtitleUrl,
                    savedSettings = savedSettings,
                )
            }

            if (mediaKey != null && savedSettings == null) {
                val initialSettings = TvShowSourceSettings(
                    serverName = extracted.serverName,
                    version = extracted.version,
                    quality = extracted.quality,
                    sourceWebsite = extracted.sourceWebsite,
                    subtitlesEnabled = selectedSubtitleUrl != null,
                    subtitleLanguage = extracted.subtitles.find { it.url == selectedSubtitleUrl }?.language,
                    subtitleLabel = extracted.subtitles.find { it.url == selectedSubtitleUrl }?.label,
                    playbackSpeed = speed,
                    aspectRatioMode = aspect,
                )
                tvShowSettingsManager.saveSettingsForMedia(mediaKey, initialSettings)
            }

            saveProgress(details.url, startPos)

            launchWork(EmptyCoroutineContext, null) {
                updateState { it.copy(isFetchingMoreSources = true) }
                try {
                    repository.pollStreamLinks(
                        url = url,
                        isTvShow = detailedMedia.baseItem.isTvShow,
                        maxAttempts = 30,
                        delayMs = 2000L,
                        shouldContinue = { currentMediaUrl == url },
                    ) { linksResponse ->
                        val newSources = videoUrlResolver.sortVideos(linksResponse.links.toExtractedVideos(), detailedMedia = detailedMedia)
                        val isStillFetching = linksResponse.status != "READY" && linksResponse.status != "ERROR"
                        val currentVideoUrl = stateProvider().videoUrl
                        val currentVideo = newSources.find { it.url == currentVideoUrl }
                        val bestSource = videoUrlResolver.findBest(newSources, targetSettings = targetSettingsWithRule)
                            ?: newSources.firstOrNull()
                        if (currentVideo != null && !VideoUrlResolver.isPolishSource(currentVideo) &&
                            bestSource != null && VideoUrlResolver.isPolishSource(bestSource) &&
                            stateProvider().currentPositionMs < 10_000L
                        ) {
                            changeVideoSource(bestSource)
                        }
                        updateState { it.copy(alternativeSources = newSources, isFetchingMoreSources = isStillFetching) }
                        if (stateProvider().shared.overlayMenuData != null) {
                            refreshOverlayMenu()
                        }
                    }
                } finally {
                    updateState { it.copy(isFetchingMoreSources = false) }
                    if (stateProvider().shared.overlayMenuData != null) {
                        refreshOverlayMenu()
                    }
                }
            }
        }
    }

    private fun resetStateForNewLoad() {
        updateState {
            it.copy(
                shared = it.shared.copy(isLoading = true, errorMessage = null),
                detailedMedia = null,
                videoHeaders = emptyMap(),
                videoUrl = null,
                subtitles = emptyList(),
                selectedSubtitleUrl = null,
                rawSubtitleContent = null,
                failedUrls = emptySet(),
                alternativeSources = emptyList(),
                isFetchingMoreSources = false,
                audioTracks = emptyList(),
                selectedAudioTrackId = null,
                openSubtitles = emptyList(),
                subtitleCandidates = emptyList(),
                openSubtitleCandidates = emptyList(),
                duration = 0,
                startPositionMs = 0,
                currentPositionMs = 0,
                isTimerCancelled = false,
                isInitialPhaseDismissed = false,
                isSecondaryPhaseDismissed = false,
                downloadedLektorVoices = lektorModelManager.getDownloadedVoices(),
            )
        }
    }

    private suspend fun loadDirectYoutubeStream(
        url: String,
        initialTitle: String? = null,
        initialDescription: String? = null,
        isTrailer: Boolean = false,
    ) {
        val extractionResult = runCatching {
            YoutubeExtractor.extractYoutubeVideo(url)
        }.getOrNull()

        val extractedList = extractionResult?.videos.orEmpty()

        if (extractedList.isNotEmpty()) {
            val preferredQuality = settingsManager.preferredQualityFlow.first()
            val bestExtracted = VideoQualityUtils.selectVideoMatchingQuality(extractedList, preferredQuality)
                ?: extractedList.first()

            val finalTitle = initialTitle?.ifBlank { null }
                ?: extractionResult?.title?.ifBlank { null }
                ?: ""
            val finalDescription = initialDescription?.ifBlank { null }
                ?: extractionResult?.description?.ifBlank { null }
                ?: ""

            val durationMs = extractionResult?.durationMs
            val detailedMedia = DetailedMedia(
                baseItem = MovieItem(
                    url = url,
                    titlePl = finalTitle,
                    titleEn = finalTitle,
                    posterUrl = extractionResult?.thumbnailUrl.orEmpty(),
                    description = finalDescription,
                    isTrailer = isTrailer,
                ),
                metaInfo = durationMs?.let {
                    MediaMetadata(
                        duration = it.milliseconds,
                    )
                },
            )

            updateState {
                it.copy(
                    shared = it.shared.copy(isLoading = false, errorMessage = null),
                    detailedMedia = detailedMedia,
                    videoHeaders = bestExtracted.headers,
                    videoUrl = bestExtracted.url,
                    subtitles = bestExtracted.subtitles,
                    selectedSubtitleUrl = subtitleDelegate.getPreferredSubtitleUrl(bestExtracted.subtitles),
                    startPositionMs = 0L,
                    alternativeSources = extractedList,
                )
            }
        } else {
            updateState {
                it.copy(
                    shared = it.shared.copy(
                        isLoading = false,
                        errorMessage = TextValue.StringResource(R.string.player_no_playable_video),
                    ),
                )
            }
        }
    }

    fun changeVideoSource(source: ExtractedVideo) {
        val currentPos = stateProvider().currentPositionMs
        val detailedMedia = stateProvider().detailedMedia
        val details = detailedMedia?.baseItem

        val isPolishProduction = isOriginallyPolish(
            originalLanguage = details?.originalLanguage,
            countries = detailedMedia?.metaInfo?.countries.orEmpty(),
            sourceVersion = source.version,
            sourceFilename = source.filename,
            allSources = stateProvider().alternativeSources,
        )
        val category = if (details != null) {
            classifyContent(
                genreIds = details.genreIds,
                originalLanguage = details.originalLanguage,
                countries = detailedMedia.metaInfo?.countries.orEmpty(),
                isFromKidsSection = details.url.contains("forkids"),
            )
        } else {
            ContentCategory.FOREIGN_MAINSTREAM
        }
        val effectiveCategory = if (isPolishProduction) ContentCategory.NATIVE_DOMESTIC else category
        val contentRule = settingsManager.contentRulesFlow.value.getRule(effectiveCategory)

        val hasPolishSpoken = hasPolishSpokenAudio(
            isPolishProduction = isPolishProduction,
            sourceVersion = source.version,
            sourceFilename = source.filename,
        )
        val shouldEnableLektor = resolveShouldEnableLektor(
            isPolishProduction = isPolishProduction,
            hasPolishSpokenAudio = hasPolishSpoken,
            contentRule = contentRule,
            isLektorSettingEnabled = settingsManager.isLektorEnabled.value,
        )
        val subtitlesDefault = resolveSubtitlesEnabled(
            isPolishProduction = isPolishProduction,
            hasPolishSpokenAudio = hasPolishSpoken,
            contentRule = contentRule,
            shouldEnableLektor = shouldEnableLektor,
            savedSubtitlesEnabled = null,
        )

        val preferredSub = if (subtitlesDefault) {
            subtitleDelegate.getPreferredSubtitleUrl(source.subtitles)
                ?: if (shouldEnableLektor || contentRule.resolvedAudioMode == AudioPreferenceMode.ORIGINAL_SUBTITLES) {
                    source.subtitles.find { subtitleDelegate.subtitleRepository.isPolishSubtitle(it) }?.url
                } else null
        } else null

        updateState {
            it.copy(
                videoUrl = source.url,
                videoHeaders = source.headers,
                audioTracks = emptyList(),
                selectedAudioTrackId = null,
                subtitles = source.subtitles,
                selectedSubtitleUrl = preferredSub,
                rawSubtitleContent = if (preferredSub == null) null else it.rawSubtitleContent,
                startPositionMs = currentPos,
                duration = 0,
                failedUrls = it.failedUrls - source.url,
                isLektorEnabled = shouldEnableLektor,
                shared = it.shared.copy(errorMessage = null),
            )
        }
        if (preferredSub != null) {
            subtitleDelegate.loadRawSubtitleContent(preferredSub)
        }
        if (shouldEnableLektor) {
            subtitleDelegate.ensurePolishSubtitlesForLektorOrPreference(
                sourceSubtitles = source.subtitles,
                selectedSubtitleUrl = preferredSub,
            )
        }
        updateTvShowSettings {
            it.copy(
                serverName = source.serverName,
                version = source.version,
                quality = source.quality,
                sourceWebsite = source.sourceWebsite,
                subtitlesEnabled = subtitlesDefault,
            )
        }
    }

    fun handlePlayerError() {
        val currentState = stateProvider()
        val currentUrl = currentState.videoUrl
        val alternatives = currentState.alternativeSources.ifEmpty {
            currentState.detailedMedia?.let { videoUrlResolver.getAlternativeUrls(it) }.orEmpty()
        }

        val newFailedUrls = currentState.failedUrls + listOfNotNull(currentUrl)
        updateState { it.copy(failedUrls = newFailedUrls) }

        val currentIndex = alternatives.indexOfFirst { it.url == currentUrl }
        val orderedAlternatives = if (currentIndex != -1) {
            alternatives.subList(currentIndex + 1, alternatives.size) +
                alternatives.subList(0, currentIndex + 1)
        } else {
            alternatives
        }

        val nextSource = orderedAlternatives.firstOrNull { it.url !in newFailedUrls }
        if (nextSource != null) {
            changeVideoSource(nextSource)
        } else {
            updateState {
                it.copy(
                    shared = it.shared.copy(
                        errorMessage = TextValue.StringResource(R.string.player_all_sources_failed),
                    ),
                )
            }
        }
        if (stateProvider().shared.overlayMenuData != null) {
            refreshOverlayMenu()
        }
    }

    fun validateDuration(streamDurationMs: Long): Boolean {
        val currentState = stateProvider()
        if (currentState.detailedMedia?.baseItem?.isTrailer == true) return true
        val expectedDuration = currentState.detailedMedia?.metaInfo?.duration ?: return true
        val expectedMs = expectedDuration.inWholeMilliseconds
        if (expectedMs <= 0) return true

        val isTrailer = streamDurationMs < 5 * 60_000L && expectedMs > 15 * 60_000L
        val diff = kotlin.math.abs(streamDurationMs - expectedMs)
        val maxAllowedDiff = maxOf(15 * 60_000L, (expectedMs * 0.25).toLong())

        if (isTrailer || diff > maxAllowedDiff) {
            handlePlayerError()
            return false
        }
        return true
    }

    fun saveProgress(url: String, positionMs: Long) {
        val currentState = stateProvider()
        val detailedMedia = currentState.detailedMedia ?: return
        if (detailedMedia.baseItem.url != url) return
        if (detailedMedia.baseItem.isTrailer) return
        progressManager?.saveProgress(detailedMedia.baseItem, positionMs, currentState.duration)
    }

    fun loadNextEpisode() {
        val detailedMedia = stateProvider().detailedMedia ?: return
        val nextEpisodeUrl = detailedMedia.baseItem.nextEpisodeUrl ?: return
        progressManager?.markAsWatched(detailedMedia.baseItem)
        loadDetails(nextEpisodeUrl)
    }

    fun updateTvShowSettings(transform: (TvShowSourceSettings) -> TvShowSourceSettings) {
        val movie = stateProvider().detailedMedia?.baseItem ?: return
        val mediaKey = movie.getMediaKey() ?: movie.getTvShowKey() ?: return
        val current = tvShowSettingsManager.getSettingsForMediaSync(mediaKey) ?: TvShowSourceSettings()
        tvShowSettingsManager.saveSettingsForMedia(mediaKey, transform(current))
    }
}
