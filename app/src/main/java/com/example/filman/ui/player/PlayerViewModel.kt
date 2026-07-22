package com.example.filman.ui.player

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.example.filman.data.local.SessionManager
import com.example.filman.data.model.DetailedMedia
import com.example.filman.data.source.ContentSource
import com.example.filman.data.scraper.extractors.getExtractorForUrl
import com.example.filman.data.scraper.extractors.resolveFilmanEmbedLink
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.base.SharedState
import com.example.filman.ui.base.StateWithShared
import kotlinx.coroutines.launch

internal sealed interface PlayerEvent : FilmanEvent {
    data class LoadDetails(val url: String) : PlayerEvent
    data class IsPlayingChanged(val isPlaying: Boolean) : PlayerEvent
    data class DurationProvided(val duration: Long) : PlayerEvent
}

@Immutable
internal data class PlayerState(
    val videoUrl: String? = null,
    val videoHeaders: Map<String, String> = emptyMap(),
    val detailedMedia: DetailedMedia? = null,
    val isPlaying: Boolean = false,
    val duration: Long = 0,
    override val shared: SharedState = SharedState(),
) : StateWithShared<PlayerState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

internal sealed interface PlayerEffect {
    data object NavigateToAuth : PlayerEffect
}

internal class PlayerViewModel(
    private val scraper: ContentSource,
    private val sessionManager: SessionManager,
) : BaseViewModel<PlayerState, PlayerEvent, PlayerEffect>(
    initialState = PlayerState(),
) {

    override fun getAuthErrorEffect(): PlayerEffect = PlayerEffect.NavigateToAuth

    override fun handleEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.LoadDetails -> loadDetails(event.url)
            is PlayerEvent.IsPlayingChanged -> updateState { it.copy(isPlaying = event.isPlaying) }
            is PlayerEvent.DurationProvided -> updateState { it.copy(duration = event.duration) }
        }
    }

    private fun loadDetails(url: String) {
        updateSharedState { it.copy(isLoading = true) }

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

            detailedMedia.embeds.forEach { embed ->
                val embedUrl = resolveFilmanEmbedLink(
                    cookie = sessionManager.getCookie().orEmpty(),
                    userAgent = sessionManager.getUserAgent(),
                    linkId = embed.url,
                    routeToken = details.routeToken.orEmpty(),
                )

                if (embedUrl == null) return@forEach

                val extractor = getExtractorForUrl(embedUrl)
                val extracted = extractor?.extractVideo(embedUrl)
                if (extracted != null) {
                    updateState {
                        it.copy(
                            shared = it.shared.copy(isLoading = false),
                            detailedMedia = detailedMedia,
                            videoHeaders = extracted.headers,
                            videoUrl = extracted.url,
                        )
                    }

                    return@launch
                }
            }
        }
    }
}
