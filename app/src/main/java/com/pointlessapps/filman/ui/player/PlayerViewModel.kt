package com.pointlessapps.filman.ui.player

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.R
import com.pointlessapps.filman.config.EkinoConfig
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.config.ZaluknijConfig
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.HIDE
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.SHOW
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.SHOW_IN_OVERLAY
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.SHOW_WITH_TIMER
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.scraper.FilmanScraper
import com.pointlessapps.filman.data.scraper.VideoUrlResolver
import com.pointlessapps.filman.data.scraper.extractors.ExtractedVideo
import com.pointlessapps.filman.data.scraper.extractors.Subtitle
import com.pointlessapps.filman.data.scraper.extractors.getExtractorForUrl
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.components.OverlayMenuData
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel.AppearanceModel.Show
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel.AppearanceModel.ShowInOverlay
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel.AppearanceModel.ShowWithTimer
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonUIState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URL

internal sealed interface PlayerEvent : FilmanEvent {
    data class LoadDetails(val url: String) : PlayerEvent
    data class IsPlayingChanged(val isPlaying: Boolean) : PlayerEvent
    data class IsBufferingChanged(val isBuffering: Boolean) : PlayerEvent
    data class DurationProvided(val duration: Long) : PlayerEvent
    data object NextEpisodeRequested : PlayerEvent
    data class SaveProgress(val url: String, val positionMs: Long) : PlayerEvent
    data class OpenSettingsMenu(
        val currentPositionMs: Long,
        val initialMenuId: String? = null,
    ) : PlayerEvent

    data class ControlsVisibilityChanged(val isVisible: Boolean) : PlayerEvent
    data class CurrentPositionChanged(val positionMs: Long) : PlayerEvent
    data object NextEpisodePromptDismissed : PlayerEvent
    data object CancelNextEpisodeTimer : PlayerEvent
    data class ChangeVideoSource(val source: ExtractedVideo) : PlayerEvent
    data class SelectSubtitle(val subtitleUrl: String?) : PlayerEvent
    data class ChangePlaybackSpeed(val speed: Float) : PlayerEvent
    data class ChangeAspectRatio(val mode: Int) : PlayerEvent
    data object PlayerError : PlayerEvent
}

@Immutable
internal data class PlayerState(
    val videoUrl: String? = null,
    val videoHeaders: Map<String, String> = emptyMap(),
    val detailedMedia: DetailedMedia? = null,
    val isPlaying: Boolean = true,
    val isBuffering: Boolean = true,
    val duration: Long = 0,
    val startPositionMs: Long = 0,
    val playbackSpeed: Float = PlayerConstants.PlaybackSpeed.X1_0,
    val aspectRatioMode: Int = PlayerConstants.AspectRatio.FIT,
    val subtitles: List<Subtitle> = emptyList(),
    val selectedSubtitleUrl: String? = null,
    val isWebView: Boolean = false,
    val failedUrls: Set<String> = emptySet(),
    val alternativeSources: List<ExtractedVideo> = emptyList(),
    val currentPositionMs: Long = 0,
    val areControlsVisible: Boolean = false,
    val nextEpisodeButtonUIState: NextEpisodeButtonUIState = NextEpisodeButtonUIState(),
    val isInitialPhaseDismissed: Boolean = false,
    val isSecondaryPhaseDismissed: Boolean = false,
    val isTimerCancelled: Boolean = false,
    val autoPlayNextEpisode: Boolean = true,
    override val shared: SharedState = SharedState(),
) : StateWithShared<PlayerState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

internal sealed interface PlayerEffect {
    data object NavigateToAuth : PlayerEffect
}

internal class PlayerViewModel(
    private val scraper: FilmanScraper,
    private val videoUrlResolver: VideoUrlResolver,
    private val settingsManager: SettingsManager,
    progressManager: ProgressManager,
) : BaseViewModel<PlayerState, PlayerEvent, PlayerEffect>(
    initialState = PlayerState(),
    progressManager = progressManager,
) {

    private var preferredSubtitleLanguage: String? = null
    private var preferredSubtitleLabel: String? = null

    init {
        val initialModelFlow = combine(
            settingsManager.initialAppearanceTypeFlow,
            settingsManager.initialAppearanceOffsetFlow,
            settingsManager.initialAppearancePercentageFlow,
        ) { type, offset, percentage ->
            val percentageOffset = percentage / 100f
            val maxTimeOffset = offset * 1000L
            when (type) {
                SHOW -> Show(percentageOffset, maxTimeOffset)
                SHOW_IN_OVERLAY -> ShowInOverlay(percentageOffset, maxTimeOffset)
                else -> null
            }
        }

        val secondaryModelFlow = combine(
            settingsManager.secondaryAppearanceTypeFlow,
            settingsManager.secondaryAppearanceOffsetFlow,
            settingsManager.secondaryAppearancePercentageFlow,
            settingsManager.secondaryTimerAmountFlow,
        ) { type, offset, percentage, timerAmount ->
            val percentageOffset = percentage / 100f
            val maxTimeOffset = offset * 1000L
            when (type) {
                SHOW -> Show(percentageOffset, maxTimeOffset)
                SHOW_IN_OVERLAY -> ShowInOverlay(percentageOffset, maxTimeOffset)
                SHOW_WITH_TIMER -> ShowWithTimer(
                    percentageOffset,
                    maxTimeOffset,
                    timerAmount * 1000L,
                )

                HIDE -> null
            }
        }

        val baseModelFlow = combine(initialModelFlow, secondaryModelFlow) { initial, secondary ->
            NextEpisodeButtonModel(initial, secondary)
        }

        val dynamicUiStateFlow = combine(
            baseModelFlow,
            state,
        ) { model, currentState ->
            val duration = currentState.duration
            val currentPosition = currentState.currentPositionMs
            val areControlsVisible = currentState.areControlsVisible
            val isInitialDismissed = currentState.isInitialPhaseDismissed
            val isSecondaryDismissed = currentState.isSecondaryPhaseDismissed
            val isTimerCancelled = currentState.isTimerCancelled
            fun isPastThreshold(appearance: NextEpisodeButtonModel.AppearanceModel?): Boolean {
                if (appearance == null || duration <= 0) return false
                val threshold = duration - minOf(
                    appearance.maxTimeOffset,
                    (duration * appearance.percentageOffset).toLong(),
                )
                return currentPosition >= threshold
            }

            val isInitialPhase = isPastThreshold(model.initialAppearanceModel)
            val isSecondaryPhase = isPastThreshold(model.appearanceModel)

            val activeModel = if (isSecondaryPhase) {
                model.appearanceModel
            } else if (isInitialPhase) {
                model.initialAppearanceModel
            } else {
                null
            }

            var isVisible = false
            var shouldRunTimer = false

            val autoPlayNextEpisode = settingsManager.autoPlayNextFlow.value

            when (activeModel) {
                is Show -> isVisible = true
                is ShowInOverlay -> isVisible = areControlsVisible

                is ShowWithTimer -> {
                    isVisible = true
                    shouldRunTimer = autoPlayNextEpisode && !areControlsVisible && !isTimerCancelled
                }

                null -> isVisible = false
            }

            if (isSecondaryPhase && isSecondaryDismissed) {
                isVisible = false
                shouldRunTimer = false
            } else if (isInitialPhase && !isSecondaryPhase && isInitialDismissed) {
                isVisible = false
                shouldRunTimer = false
            }

            if (isVisible && autoPlayNextEpisode) {
                handleNextEpisodeBoxAppeared()
            }

            NextEpisodeButtonUIState(
                isVisible = isVisible,
                isSecondaryPhase = isSecondaryPhase,
                isTimerRunning = shouldRunTimer,
                timerDurationMs = (activeModel as? ShowWithTimer)?.timerDuration ?: 0L,
            )
        }

        viewModelScope.launch {
            dynamicUiStateFlow.collect { uiState ->
                updateState { it.copy(nextEpisodeButtonUIState = uiState) }
            }
        }
    }

    override fun getAuthErrorEffect(): PlayerEffect = PlayerEffect.NavigateToAuth

    override fun handleEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.LoadDetails -> loadDetails(event.url)
            is PlayerEvent.IsPlayingChanged -> updateState { it.copy(isPlaying = event.isPlaying) }
            is PlayerEvent.IsBufferingChanged -> updateState { it.copy(isBuffering = event.isBuffering) }
            is PlayerEvent.DurationProvided -> updateState { it.copy(duration = event.duration) }
            is PlayerEvent.ControlsVisibilityChanged -> updateState {
                val shouldCancelTimer =
                    it.nextEpisodeButtonUIState.isSecondaryPhase && event.isVisible
                it.copy(
                    areControlsVisible = event.isVisible,
                    isTimerCancelled = if (shouldCancelTimer) true else it.isTimerCancelled,
                )
            }

            is PlayerEvent.CurrentPositionChanged -> updateState {
                it.copy(currentPositionMs = event.positionMs)
            }

            is PlayerEvent.NextEpisodePromptDismissed -> handleNextEpisodePromptDismissed()
            is PlayerEvent.CancelNextEpisodeTimer -> updateState { it.copy(isTimerCancelled = true) }
            is PlayerEvent.NextEpisodeRequested -> loadNextEpisode()
            is PlayerEvent.SaveProgress -> saveProgress(event.url, event.positionMs)
            is PlayerEvent.OpenSettingsMenu -> openSettingsMenu(
                currentPositionMs = event.currentPositionMs,
                initialMenuId = event.initialMenuId,
            )

            is PlayerEvent.ChangeVideoSource -> changeVideoSource(event.source)
            is PlayerEvent.ChangePlaybackSpeed -> updateState { it.copy(playbackSpeed = event.speed) }
            is PlayerEvent.ChangeAspectRatio -> updateState { it.copy(aspectRatioMode = event.mode) }
            is PlayerEvent.SelectSubtitle -> {
                val selectedSubtitle = state.value.subtitles.find { it.url == event.subtitleUrl }
                preferredSubtitleLanguage = selectedSubtitle?.language
                preferredSubtitleLabel = selectedSubtitle?.label
                updateState { it.copy(selectedSubtitleUrl = event.subtitleUrl) }
            }

            is PlayerEvent.PlayerError -> handlePlayerError()
        }
    }

    private fun openSettingsMenu(currentPositionMs: Long, initialMenuId: String? = null) {
        updateState { it.copy(startPositionMs = currentPositionMs) }
        val currentUrl = state.value.videoUrl
        val alternatives = state.value.detailedMedia?.let {
            videoUrlResolver.getAlternativeUrls(it.baseItem.url)
        } ?: state.value.alternativeSources

        val currentMediaUrl = state.value.detailedMedia?.baseItem?.url
        val currentWebsite = currentMediaUrl?.let { url ->
            if (url.contains(FilmanConfig.DOMAIN)) {
                FilmanConfig.DOMAIN
            } else if (url.contains(EkinoConfig.DOMAIN)) {
                EkinoConfig.DOMAIN
            } else if (url.contains(ZaluknijConfig.DOMAIN)) {
                ZaluknijConfig.DOMAIN
            } else {
                ""
            }
        } ?: ""

        val menuItems = mutableListOf<FilmanOverlayMenuItem>()
        val grouped = alternatives.groupBy {
            it.sourceWebsite.ifEmpty { "Unknown" }
        }

        val sortedGrouped = grouped.toList().sortedBy { (website, _) ->
            when (website) {
                currentWebsite -> 0
                FilmanConfig.DOMAIN -> 1
                else -> 2
            }
        }

        sortedGrouped.forEach { (website, items) ->
            val websiteName = website.substringBefore(".").replaceFirstChar { it.titlecase() }
            menuItems.add(FilmanOverlayMenuItem.Header(label = TextValue.DynamicString(websiteName)))

            items.filterNot { it.url in state.value.failedUrls }.forEach { extracted ->
                val serverName = extracted.serverName.ifEmpty {
                    runCatching { URL(extracted.url).host }.getOrNull().orEmpty()
                }
                val tags = listOf(serverName, extracted.version, extracted.quality).filter {
                    it.isNotBlank()
                }

                menuItems.add(
                    FilmanOverlayMenuItem.Option(
                        label = TextValue.DynamicString(tags.joinToString(" • ")),
                        isSelected = extracted.url == currentUrl,
                        onClick = {
                            onEvent(BaseEvent.CloseContextMenu)
                            onEvent(PlayerEvent.ChangeVideoSource(extracted))
                        },
                    ),
                )
            }
        }

        val subtitleItems = mutableListOf<FilmanOverlayMenuItem>()
        val currentSource = alternatives.find { it.url == currentUrl }
        if (currentSource?.subtitles?.isNotEmpty() == true) {
            subtitleItems.add(
                FilmanOverlayMenuItem.Option(
                    label = TextValue.StringResource(R.string.player_subtitles_off),
                    isSelected = state.value.selectedSubtitleUrl == null,
                    onClick = {
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.SelectSubtitle(null))
                    },
                ),
            )
            currentSource.subtitles.forEach { subtitle ->
                subtitleItems.add(
                    FilmanOverlayMenuItem.Option(
                        label = TextValue.DynamicString(subtitle.label),
                        isSelected = subtitle.url == state.value.selectedSubtitleUrl,
                        onClick = {
                            onEvent(BaseEvent.CloseContextMenu)
                            onEvent(PlayerEvent.SelectSubtitle(subtitle.url))
                        },
                    ),
                )
            }
        }

        val overlayItems = mutableListOf<FilmanOverlayMenuItem>(
            FilmanOverlayMenuItem.NestedMenu(
                id = PlayerConstants.MENU_SOURCES_ID,
                label = TextValue.StringResource(R.string.player_video_source),
                value = null,
                items = menuItems,
            ),
        )

        if (subtitleItems.isNotEmpty()) {
            overlayItems.add(
                FilmanOverlayMenuItem.NestedMenu(
                    label = TextValue.StringResource(R.string.player_subtitles),
                    value = null,
                    items = subtitleItems,
                ),
            )
        }

        overlayItems.add(
            FilmanOverlayMenuItem.NestedMenu(
                label = TextValue.StringResource(R.string.player_playback_speed),
                value = null,
                items = PlayerConstants.PlaybackSpeed.ALL.map { speed ->
                    FilmanOverlayMenuItem.Option(
                        label = TextValue.StringResource(
                            R.string.player_speed_format,
                            listOf(speed.toString()),
                        ),
                        isSelected = state.value.playbackSpeed == speed,
                        onClick = {
                            onEvent(BaseEvent.CloseContextMenu)
                            onEvent(PlayerEvent.ChangePlaybackSpeed(speed))
                        },
                    )
                },
            ),
        )

        overlayItems.add(
            FilmanOverlayMenuItem.NestedMenu(
                label = TextValue.StringResource(R.string.player_aspect_ratio),
                value = null,
                items = listOf(
                    FilmanOverlayMenuItem.Option(
                        label = TextValue.StringResource(R.string.player_aspect_fit),
                        isSelected = state.value.aspectRatioMode == PlayerConstants.AspectRatio.FIT,
                        onClick = {
                            onEvent(BaseEvent.CloseContextMenu)
                            onEvent(PlayerEvent.ChangeAspectRatio(PlayerConstants.AspectRatio.FIT))
                        },
                    ),
                    FilmanOverlayMenuItem.Option(
                        label = TextValue.StringResource(R.string.player_aspect_crop),
                        isSelected = state.value.aspectRatioMode == PlayerConstants.AspectRatio.CROP,
                        onClick = {
                            onEvent(BaseEvent.CloseContextMenu)
                            onEvent(PlayerEvent.ChangeAspectRatio(PlayerConstants.AspectRatio.CROP))
                        },
                    ),
                    FilmanOverlayMenuItem.Option(
                        label = TextValue.StringResource(R.string.player_aspect_stretch),
                        isSelected = state.value.aspectRatioMode == PlayerConstants.AspectRatio.STRETCH,
                        onClick = {
                            onEvent(BaseEvent.CloseContextMenu)
                            onEvent(PlayerEvent.ChangeAspectRatio(PlayerConstants.AspectRatio.STRETCH))
                        },
                    ),
                ),
            ),
        )

        val menuData = OverlayMenuData(
            title = TextValue.StringResource(R.string.player_settings),
            items = overlayItems,
            initialMenuId = initialMenuId,
        )

        updateSharedState { it.copy(overlayMenuData = menuData) }
    }

    private fun getPreferredSubtitleUrl(subtitles: List<Subtitle>): String? {
        if (preferredSubtitleLanguage == null) return null

        return subtitles.find {
            it.language == preferredSubtitleLanguage && it.label == preferredSubtitleLabel
        }?.url ?: subtitles.find { it.language == preferredSubtitleLanguage }?.url
    }

    private fun changeVideoSource(source: ExtractedVideo) {
        updateState {
            it.copy(
                videoUrl = source.url,
                videoHeaders = source.headers,
                subtitles = source.subtitles,
                selectedSubtitleUrl = getPreferredSubtitleUrl(source.subtitles),
                isWebView = source.isWebView,
            )
        }
    }

    private fun handlePlayerError() {
        val currentUrl = state.value.videoUrl
        val alternatives = state.value.detailedMedia?.let {
            videoUrlResolver.getAlternativeUrls(it.baseItem.url)
        } ?: state.value.alternativeSources

        val newFailedUrls = state.value.failedUrls + listOfNotNull(currentUrl)
        updateState { it.copy(failedUrls = newFailedUrls) }

        val nextSource = alternatives.firstOrNull { it.url !in newFailedUrls }

        if (nextSource != null) {
            changeVideoSource(nextSource)
        } else {
            updateSharedState {
                it.copy(errorMessage = TextValue.StringResource(R.string.error_all_sources_failed))
            }
        }
    }

    private fun handleNextEpisodePromptDismissed() {
        val isSecondary = state.value.nextEpisodeButtonUIState.isSecondaryPhase
        if (isSecondary) {
            updateState { it.copy(isSecondaryPhaseDismissed = true) }
        } else {
            updateState { it.copy(isInitialPhaseDismissed = true) }
        }
    }

    private fun handleNextEpisodeBoxAppeared() {
        val nextEpisodeUrl = state.value.detailedMedia?.baseItem?.nextEpisodeUrl ?: return
        launchHandled {
            videoUrlResolver.prefetch(nextEpisodeUrl)
        }
    }

    private fun saveProgress(url: String, positionMs: Long) {
        val detailedMedia = state.value.detailedMedia ?: return
        if (detailedMedia.baseItem.url != url) return
        val duration = state.value.duration
        val item = detailedMedia.baseItem

        progressManager?.saveProgress(item, positionMs, duration)
    }

    private fun loadNextEpisode() {
        val detailedMedia = state.value.detailedMedia ?: return
        val nextEpisodeUrl = detailedMedia.baseItem.nextEpisodeUrl ?: return

        progressManager?.markAsWatched(detailedMedia.baseItem)
        loadDetails(nextEpisodeUrl)
    }

    private fun loadDetails(url: String) {
        val existingProgress = progressManager?.getProgressForUrl(url)
        val startPos = (existingProgress as? ProgressItem.InProgress)?.progressMs ?: 0L

        updateState {
            PlayerState(
                shared = it.shared.copy(
                    isLoading = true,
                    errorMessage = null,
                ),
                detailedMedia = null,
                videoHeaders = emptyMap(),
                videoUrl = null,
                subtitles = emptyList(),
                selectedSubtitleUrl = null,
                startPositionMs = startPos,
                isWebView = false,
                failedUrls = emptySet(),
                alternativeSources = emptyList(),
            )
        }

        launchHandled {
            val isDirectYoutube = url.contains("youtube.com", ignoreCase = true) ||
                    url.contains("youtu.be", ignoreCase = true)

            if (isDirectYoutube) {
                updateState {
                    it.copy(
                        shared = it.shared.copy(isLoading = false),
                        detailedMedia = null,
                    )
                }

                val extractor = getExtractorForUrl(url)
                val extractedList = extractor?.extractVideo(url) ?: emptyList()

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
                            selectedSubtitleUrl = getPreferredSubtitleUrl(bestExtracted.subtitles),
                            startPositionMs = 0L,
                            isWebView = bestExtracted.isWebView,
                            alternativeSources = extractedList,
                        )
                    }
                } else {
                    updateSharedState {
                        it.copy(
                            isLoading = false,
                            errorMessage = TextValue.StringResource(R.string.error_no_playable_video),
                        )
                    }
                }
                return@launchHandled
            }

            var detailedMedia = scraper.getMediaDetails(url)
            val details = detailedMedia?.baseItem
            if (details == null) {
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = TextValue.StringResource(R.string.error_media_not_found),
                    )
                }
                return@launchHandled
            }

            videoUrlResolver.prefetch(url, detailedMedia)
            var extracted = videoUrlResolver.getFastest(url)

            if (extracted == null) {
                scraper.invalidateMediaCache(url)
                detailedMedia = scraper.getMediaDetails(url)
                if (detailedMedia != null) {
                    videoUrlResolver.prefetch(url, detailedMedia)
                    extracted = videoUrlResolver.getFastest(url)
                }
            }

            if (extracted != null) {
                updateState {
                    it.copy(
                        shared = it.shared.copy(isLoading = false),
                        detailedMedia = detailedMedia,
                        videoHeaders = extracted.headers,
                        videoUrl = extracted.url,
                        subtitles = extracted.subtitles,
                        selectedSubtitleUrl = getPreferredSubtitleUrl(extracted.subtitles),
                        startPositionMs = startPos,
                        isWebView = extracted.isWebView,
                    )
                }

                saveProgress(detailedMedia!!.baseItem.url, startPos)
            } else {
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = TextValue.StringResource(R.string.error_no_playable_video),
                    )
                }
            }
        }
    }
}
