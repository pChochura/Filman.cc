package com.pointlessapps.filman.ui.player

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.R
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.scraper.FilmanScraper
import com.pointlessapps.filman.data.scraper.VideoUrlResolver
import com.pointlessapps.filman.data.scraper.extractors.ExtractedVideo
import com.pointlessapps.filman.data.scraper.extractors.Subtitle
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.components.OverlayMenuData
import com.pointlessapps.filman.ui.core.TextValue
import java.net.URL

internal sealed interface PlayerEvent : FilmanEvent {
    data class LoadDetails(val url: String) : PlayerEvent
    data class IsPlayingChanged(val isPlaying: Boolean) : PlayerEvent
    data class IsBufferingChanged(val isBuffering: Boolean) : PlayerEvent
    data class DurationProvided(val duration: Long) : PlayerEvent
    data object NextEpisodeRequested : PlayerEvent
    data object NextEpisodeBoxAppeared : PlayerEvent
    data class SaveProgress(val positionMs: Long) : PlayerEvent
    data class OpenSettingsMenu(val currentPositionMs: Long) : PlayerEvent
    data class ChangeVideoSource(val source: ExtractedVideo) : PlayerEvent
    data class SelectSubtitle(val subtitleUrl: String?) : PlayerEvent
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
    val subtitles: List<Subtitle> = emptyList(),
    val selectedSubtitleUrl: String? = null,
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
    progressManager: ProgressManager,
) : BaseViewModel<PlayerState, PlayerEvent, PlayerEffect>(
    initialState = PlayerState(),
    progressManager = progressManager,
) {

    override fun getAuthErrorEffect(): PlayerEffect = PlayerEffect.NavigateToAuth

    override fun handleEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.LoadDetails -> loadDetails(event.url)
            is PlayerEvent.IsPlayingChanged -> updateState { it.copy(isPlaying = event.isPlaying) }
            is PlayerEvent.IsBufferingChanged -> updateState { it.copy(isBuffering = event.isBuffering) }
            is PlayerEvent.DurationProvided -> updateState { it.copy(duration = event.duration) }
            is PlayerEvent.NextEpisodeRequested -> loadNextEpisode()
            is PlayerEvent.NextEpisodeBoxAppeared -> handleNextEpisodeBoxAppeared()
            is PlayerEvent.SaveProgress -> saveProgress(event.positionMs)
            is PlayerEvent.OpenSettingsMenu -> openSettingsMenu(event.currentPositionMs)
            is PlayerEvent.ChangeVideoSource -> changeVideoSource(event.source)
            is PlayerEvent.SelectSubtitle -> updateState { it.copy(selectedSubtitleUrl = event.subtitleUrl) }
        }
    }

    private fun openSettingsMenu(currentPositionMs: Long) {
        updateState { it.copy(startPositionMs = currentPositionMs) }
        val detailedMedia = state.value.detailedMedia ?: return
        val url = detailedMedia.baseItem.url
        val alternatives = videoUrlResolver.getAlternativeUrls(url)
        val currentUrl = state.value.videoUrl

        val menuItems = mutableListOf<FilmanOverlayMenuItem>()
        val grouped = alternatives.groupBy {
            it.serverName.ifEmpty { runCatching { URL(it.url).host }.getOrNull().orEmpty() }
        }

        grouped.forEach { (server, items) ->
            menuItems.add(FilmanOverlayMenuItem.Header(label = TextValue.DynamicString(server)))

            items.forEach { extracted ->
                val tags = listOf(extracted.version, extracted.quality).filter { it.isNotBlank() }
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

        val menuData = OverlayMenuData(
            title = TextValue.StringResource(R.string.player_settings),
            items = overlayItems,
        )

        updateSharedState { it.copy(overlayMenuData = menuData) }
    }

    private fun changeVideoSource(source: ExtractedVideo) {
        updateState {
            it.copy(
                videoUrl = source.url,
                videoHeaders = source.headers,
                subtitles = source.subtitles,
                selectedSubtitleUrl = null,
            )
        }
    }

    private fun handleNextEpisodeBoxAppeared() {
        val nextEpisodeUrl = state.value.detailedMedia?.baseItem?.nextEpisodeUrl ?: return
        launchHandled {
            videoUrlResolver.prefetch(nextEpisodeUrl)
        }
    }

    private fun saveProgress(positionMs: Long) {
        val detailedMedia = state.value.detailedMedia ?: return
        val duration = state.value.duration
        val item = detailedMedia.baseItem

        progressManager?.saveProgress(item, positionMs, duration)
    }

    private fun loadNextEpisode() {
        val detailedMedia = state.value.detailedMedia ?: return
        val nextEpisodeUrl = detailedMedia.baseItem.nextEpisodeUrl ?: return

        saveProgress(state.value.startPositionMs)
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
            )
        }

        launchHandled {
            val detailedMedia = scraper.getMediaDetails(url)
            val details = detailedMedia?.baseItem
            if (details == null) {
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Media not found",
                    )
                }

                return@launchHandled
            }

            updateState {
                it.copy(
                    shared = it.shared.copy(isLoading = false),
                    detailedMedia = detailedMedia,
                )
            }

            videoUrlResolver.prefetch(url, detailedMedia)
            val extracted = videoUrlResolver.getFastest(url)

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
                        selectedSubtitleUrl = null,
                        startPositionMs = startPos,
                    )
                }

                saveProgress(startPos)
            } else {
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = "No playable video found",
                    )
                }
            }
        }
    }
}
