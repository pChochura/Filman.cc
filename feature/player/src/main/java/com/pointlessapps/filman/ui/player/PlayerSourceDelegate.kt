package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.TiwiRepository
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
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.model.TvShowSourceSettings
import com.pointlessapps.filman.data.model.classifyContent
import com.pointlessapps.filman.data.model.getMediaKey
import com.pointlessapps.filman.data.model.getTvShowKey
import com.pointlessapps.filman.player.lektor.engine.LektorModelManager
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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

    fun loadDetails(url: String) {
        nextEpisodeDelegate.resetForNewMedia()
        resetStateForNewLoad()

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
                loadDirectYoutubeStream(url)
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
            val contentRule = settingsManager.contentRulesFlow.value.getRule(category)

            val targetSettingsWithRule = if (savedSettings?.version == null) {
                val preferredVersion = when (contentRule.resolvedAudioMode) {
                    AudioPreferenceMode.DUBBING_PREFERRED -> "dub"
                    AudioPreferenceMode.LEKTOR_PREFERRED -> "lektor"
                    AudioPreferenceMode.ORIGINAL_SUBTITLES,
                    AudioPreferenceMode.ORIGINAL_ONLY -> "napisy"
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

            val v = extracted.version.lowercase()
            val isSourceDubbed = v.contains("dub")
            val isSourceLektor = v.contains("lektor") || v.contains("lek")
            val hasPolishSpokenAudio = isSourceDubbed || isSourceLektor

            val shouldEnableLektor = (when (contentRule.resolvedAudioMode) {
                AudioPreferenceMode.LEKTOR_PREFERRED -> !hasPolishSpokenAudio
                AudioPreferenceMode.DUBBING_PREFERRED -> !hasPolishSpokenAudio
                AudioPreferenceMode.ORIGINAL_SUBTITLES,
                AudioPreferenceMode.ORIGINAL_ONLY -> false
            }) || (settingsManager.isLektorEnabled.value && !hasPolishSpokenAudio)

            val subtitlesDefault = when (contentRule.resolvedAudioMode) {
                AudioPreferenceMode.ORIGINAL_SUBTITLES -> true
                AudioPreferenceMode.ORIGINAL_ONLY -> false
                AudioPreferenceMode.DUBBING_PREFERRED,
                AudioPreferenceMode.LEKTOR_PREFERRED -> contentRule.subtitlesEnabled || shouldEnableLektor
            }
            val subtitlesEnabled = savedSettings?.subtitlesEnabled ?: subtitlesDefault

            val selectedSubtitleUrl = if (subtitlesEnabled) {
                subtitleDelegate.resolveInitialSubtitleUrl(
                    extracted.subtitles,
                    savedSettings,
                ) ?: if (shouldEnableLektor || contentRule.resolvedAudioMode == AudioPreferenceMode.ORIGINAL_SUBTITLES) {
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
            subtitleDelegate.ensurePolishSubtitlesForLektorOrPreference(
                sourceSubtitles = extracted.subtitles,
                selectedSubtitleUrl = selectedSubtitleUrl,
                savedSettings = savedSettings,
            )

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
                repository.pollStreamLinks(
                    url = url,
                    isTvShow = detailedMedia.baseItem.isTvShow,
                    maxAttempts = 30,
                    delayMs = 2000L,
                    shouldContinue = { currentMediaUrl == url },
                ) { linksResponse ->
                    val newSources = videoUrlResolver.sortVideos(linksResponse.links.toExtractedVideos(), detailedMedia = detailedMedia)
                    updateState { it.copy(alternativeSources = newSources) }
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

    private suspend fun loadDirectYoutubeStream(url: String) {
        updateState {
            it.copy(
                shared = it.shared.copy(isLoading = false),
                detailedMedia = null,
            )
        }

        val extractedList = YoutubeExtractor.extractVideo(url)
        if (extractedList.isNotEmpty()) {
            val preferredQuality = settingsManager.preferredQualityFlow.first()
            val bestExtracted = if (preferredQuality == SettingsConstants.Quality.AUTO) {
                extractedList.first()
            } else {
                extractedList.find {
                    it.quality.contains(preferredQuality, ignoreCase = true) ||
                        it.version.contains(preferredQuality, ignoreCase = true)
                } ?: extractedList.first()
            }

            updateState {
                it.copy(
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
        val preferredSub = subtitleDelegate.getPreferredSubtitleUrl(source.subtitles)
        updateState {
            it.copy(
                videoUrl = source.url,
                videoHeaders = source.headers,
                audioTracks = emptyList(),
                selectedAudioTrackId = null,
                subtitles = source.subtitles,
                selectedSubtitleUrl = preferredSub,
                startPositionMs = currentPos,
                duration = 0,
                failedUrls = it.failedUrls - source.url,
                shared = it.shared.copy(errorMessage = null),
            )
        }
        if (preferredSub != null) {
            subtitleDelegate.loadRawSubtitleContent(preferredSub)
        }
        subtitleDelegate.ensurePolishSubtitlesForLektorOrPreference(
            sourceSubtitles = source.subtitles,
            selectedSubtitleUrl = preferredSub,
        )
        updateTvShowSettings {
            it.copy(
                serverName = source.serverName,
                version = source.version,
                quality = source.quality,
                sourceWebsite = source.sourceWebsite,
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
