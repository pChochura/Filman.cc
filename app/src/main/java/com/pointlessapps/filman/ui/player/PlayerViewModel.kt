package com.pointlessapps.filman.ui.player

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.R
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeInitialAppearance
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeSecondaryAppearance
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
import kotlinx.coroutines.launch
import java.net.URL

internal sealed interface PlayerEvent : FilmanEvent {
    data class LoadDetails(val url: String) : PlayerEvent
    data class IsPlayingChanged(val isPlaying: Boolean) : PlayerEvent
    data class IsBufferingChanged(val isBuffering: Boolean) : PlayerEvent
    data class DurationProvided(val duration: Long) : PlayerEvent
    data object NextEpisodeRequested : PlayerEvent
    data object NextEpisodeBoxAppeared : PlayerEvent
    data class SaveProgress(val url: String, val positionMs: Long) : PlayerEvent
    data class OpenSettingsMenu(
        val currentPositionMs: Long,
        val initialMenuId: String? = null,
    ) : PlayerEvent

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
    val initialAppearanceType: String = NextEpisodeInitialAppearance.SHOW_IN_OVERLAY,
    val initialAppearanceOffset: Long = 120L,
    val secondaryAppearanceType: String = NextEpisodeSecondaryAppearance.SHOW_WITH_TIMER,
    val secondaryAppearanceOffset: Long = 60L,
    val secondaryTimerAmount: Long = 10L,
    val initialAppearancePercentage: Long = 5L,
    val secondaryAppearancePercentage: Long = 3L,
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
        viewModelScope.launch {
            settingsManager.initialAppearanceTypeFlow.collect { type ->
                updateState { it.copy(initialAppearanceType = type) }
            }
        }
        viewModelScope.launch {
            settingsManager.initialAppearanceOffsetFlow.collect { offset ->
                updateState { it.copy(initialAppearanceOffset = offset) }
            }
        }
        viewModelScope.launch {
            settingsManager.secondaryAppearanceTypeFlow.collect { type ->
                updateState { it.copy(secondaryAppearanceType = type) }
            }
        }
        viewModelScope.launch {
            settingsManager.secondaryAppearanceOffsetFlow.collect { offset ->
                updateState { it.copy(secondaryAppearanceOffset = offset) }
            }
        }
        viewModelScope.launch {
            settingsManager.secondaryTimerAmountFlow.collect { amount ->
                updateState { it.copy(secondaryTimerAmount = amount) }
            }
        }
        viewModelScope.launch {
            settingsManager.initialAppearancePercentageFlow.collect { percentage ->
                updateState { it.copy(initialAppearancePercentage = percentage) }
            }
        }
        viewModelScope.launch {
            settingsManager.secondaryAppearancePercentageFlow.collect { percentage ->
                updateState { it.copy(secondaryAppearancePercentage = percentage) }
            }
        }
        viewModelScope.launch {
            settingsManager.autoPlayNextFlow.collect { autoplay ->
                updateState { it.copy(autoPlayNextEpisode = autoplay) }
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
            is PlayerEvent.NextEpisodeRequested -> loadNextEpisode()
            is PlayerEvent.NextEpisodeBoxAppeared -> handleNextEpisodeBoxAppeared()
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

        val menuItems = mutableListOf<FilmanOverlayMenuItem>()
        val grouped = alternatives.groupBy {
            it.serverName.ifEmpty { runCatching { URL(it.url).host }.getOrNull().orEmpty() }
        }

        grouped.forEach { (server, items) ->
            menuItems.add(FilmanOverlayMenuItem.Header(label = TextValue.DynamicString(server)))

            items.filterNot { it.url in state.value.failedUrls }.forEach { extracted ->
                val tags = listOf(extracted.version, extracted.quality).filter {
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
                startPositionMs = 0,
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
                    val preferredQuality = settingsManager.preferredQualityFlow.value
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
                val existingProgress = progressManager?.getProgressForUrl(url)
                val startPos = (existingProgress as? ProgressItem.InProgress)?.progressMs ?: 0L

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
