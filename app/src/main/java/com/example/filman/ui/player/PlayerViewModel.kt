package com.example.filman.ui.player

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.local.SessionManager
import com.example.filman.data.model.DetailedMedia
import com.example.filman.data.model.ProgressItem
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.data.scraper.VideoUrlResolver
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.base.SharedState
import com.example.filman.ui.base.StateWithShared
import kotlinx.coroutines.launch

internal sealed interface PlayerEvent : FilmanEvent {
    data class LoadDetails(val url: String) : PlayerEvent
    data class IsPlayingChanged(val isPlaying: Boolean) : PlayerEvent
    data class IsBufferingChanged(val isBuffering: Boolean) : PlayerEvent
    data class DurationProvided(val duration: Long) : PlayerEvent
    data object NextEpisodeRequested : PlayerEvent
    data object NextEpisodeBoxAppeared : PlayerEvent
    data class SaveProgress(val positionMs: Long) : PlayerEvent
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
    private val sessionManager: SessionManager,
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
        }
    }

    private fun handleNextEpisodeBoxAppeared() {
        val nextEpisodeUrl = state.value.detailedMedia?.baseItem?.nextEpisodeUrl ?: return
        viewModelScope.launch {
            videoUrlResolver.prefetch(nextEpisodeUrl)
        }
    }

    private fun saveProgress(positionMs: Long) {
        val detailedMedia = state.value.detailedMedia ?: return
        val duration = state.value.duration
        val item = detailedMedia.baseItem

        val progressPercentage = if (duration > 0) {
            positionMs.toFloat() / duration.toFloat()
        } else {
            val existingProgress = progressManager?.getProgressForUrl(item.url)
            existingProgress?.progressPercentage ?: 0f
        }

        val progressItem = ProgressItem.InProgress(
            progressPercentage = progressPercentage,
            url = item.url,
            parentUrl = item.seriesUrl ?: item.url,
            progressMs = positionMs,
            posterUrl = item.posterUrl,
            titlePl = item.titlePl,
            season = item.seasonNumber,
            episode = item.episodeNumber,
            seriesTitle = if (item.seasonNumber != null) item.titlePl else null,
            episodeTitle = item.episodeTitle,
        )
        progressManager?.saveProgress(progressItem)
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
                startPositionMs = 0,
            )
        }

        viewModelScope.launch {
            val detailedMedia = scraper.getMediaDetails(url)
            val details = detailedMedia?.baseItem
            if (details == null) {
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Media not found",
                    )
                }

                return@launch
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
