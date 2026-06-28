package com.example.filman.ui.player

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.local.SessionManager
import com.example.filman.data.model.EmbedLink
import com.example.filman.data.model.MediaDetails
import com.example.filman.data.model.ProgressItem
import com.example.filman.data.model.Season
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.data.scraper.getExtractorForUrl
import com.example.filman.data.scraper.resolveFilmanEmbedLink
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PlayerEvent {
    data class LoadMedia(val url: String) : PlayerEvent
    data class SelectServer(val server: EmbedLink) : PlayerEvent
    data class PlayNextEpisode(val userInitiated: Boolean) : PlayerEvent
    data object PlayPrevEpisode : PlayerEvent
    data class SaveProgress(val positionMs: Long, val durationMs: Long) : PlayerEvent
}

@Immutable
data class PlayerState(
    val currentMediaUrl: String = "",
    val currentRouteToken: String = "",
    val currentMediaTitle: String = "",
    val currentMediaPoster: String = "",
    val seriesTitle: String? = null,
    val directPrevUrl: String? = null,
    val directNextUrl: String? = null,

    val servers: List<EmbedLink> = emptyList(),
    val selectedServer: EmbedLink? = null,
    val attemptedServers: Set<EmbedLink> = emptySet(),
    val isFetchingServers: Boolean = false,
    val serverLoadError: String? = null,

    val videoUrl: String? = null,
    val videoHeaders: Map<String, String> = emptyMap(),
    val isExtracting: Boolean = false,
    val errorMessage: String? = null,

    val seasons: List<Season> = emptyList(),
    val currentSeasonIndex: Int = -1,
    val currentEpisodeIndex: Int = -1,

    val initialProgressMs: Long = 0L
) {
    fun hasNextEpisode(): Boolean {
        if (directNextUrl != null) return true
        if (currentSeasonIndex == -1 || currentEpisodeIndex == -1) return false
        val currentSeason = seasons.getOrNull(currentSeasonIndex) ?: return false

        return if (currentEpisodeIndex + 1 < currentSeason.episodes.size) {
            true
        } else {
            currentSeasonIndex + 1 < seasons.size
        }
    }

    fun hasPrevEpisode(): Boolean {
        if (directPrevUrl != null) return true
        if (currentSeasonIndex == -1 || currentEpisodeIndex == -1) return false

        return if (currentEpisodeIndex - 1 >= 0) {
            true
        } else {
            currentSeasonIndex - 1 >= 0
        }
    }

    fun getCurrentSeasonName(): String? = seasons.getOrNull(currentSeasonIndex)?.name
}

sealed interface PlayerEffect {
    // Empty for now, but ready for navigation effects
}

class PlayerViewModel(
    private val scraper: FilmanScraper,
    private val sessionManager: SessionManager,
    private val progressManager: ProgressManager
) : ViewModel() {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _effect = Channel<PlayerEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.LoadMedia -> loadMedia(event.url)
            is PlayerEvent.SelectServer -> selectServer(event.server)
            is PlayerEvent.PlayNextEpisode -> playNextEpisode()
            PlayerEvent.PlayPrevEpisode -> playPrevEpisode()
            is PlayerEvent.SaveProgress -> saveProgress(event.positionMs, event.durationMs)
        }
    }

    private fun loadMedia(url: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                currentMediaUrl = url,
                isFetchingServers = true,
                serverLoadError = null,
                videoUrl = null,
                errorMessage = null,
                attemptedServers = emptySet()
            )
            }

            try {
                val details = scraper.getMediaDetails(url)
                if (details is MediaDetails.MovieOrEpisode) {
                    _state.update {
                        it.copy(
                        currentRouteToken = details.routeToken,
                        currentMediaTitle = details.title,
                        currentMediaPoster = details.posterUrl,
                        directPrevUrl = details.prevEpisodeUrl,
                        directNextUrl = details.nextEpisodeUrl
                    )
                    }

                    // Restore progress
                    val savedProgress = progressManager.getProgressForUrl(url)
                    if (savedProgress != null && savedProgress.progressMs > 0 && savedProgress.progressPercentage < 0.95f) {
                        _state.update { it.copy(initialProgressMs = savedProgress.progressMs) }
                    } else {
                        _state.update { it.copy(initialProgressMs = 0L) }
                    }

                    // Load series data if needed
                    val currentSeasons = _state.value.seasons
                    if (details.seriesUrl != null && currentSeasons.isEmpty()) {
                        try {
                            val series = scraper.getMediaDetails(details.seriesUrl)
                            if (series is MediaDetails.Series) {
                                var sIdx = -1
                                var eIdx = -1
                                for (i in series.seasons.indices) {
                                    val epIndex = series.seasons[i].episodes.indexOfFirst { it.url == url || url.contains(it.url) }
                                    if (epIndex != -1) {
                                        sIdx = i
                                        eIdx = epIndex
                                        break
                                    }
                                }
                                _state.update {
                                    it.copy(
                                    seriesTitle = series.title,
                                    seasons = series.seasons,
                                    currentSeasonIndex = sIdx,
                                    currentEpisodeIndex = eIdx
                                )
                                }
                            }
                        } catch (e: Exception) {}
                    }

                    if (details.embeds.isNotEmpty()) {
                        val prioritized = details.embeds.sortedBy { link ->
                            when (link.serverName.lowercase()) {
                                "doodstream" -> 0
                                "voe" -> 1
                                else -> 2
                            }
                        }
                        _state.update { it.copy(servers = details.embeds, selectedServer = prioritized.first(), isFetchingServers = false) }
                        selectServer(prioritized.first())
                    } else {
                        _state.update { it.copy(serverLoadError = "No servers found for this media.", isFetchingServers = false) }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(serverLoadError = "Failed to load servers: ${e.message}", isFetchingServers = false) }
            }
        }
    }

    private fun selectServer(server: EmbedLink) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                selectedServer = server,
                attemptedServers = it.attemptedServers + server,
                isExtracting = true,
                videoUrl = null,
                errorMessage = null
            )
            }

            val currentState = _state.value
            try {
                val embedUrl = resolveFilmanEmbedLink(
                    cookie = sessionManager.getCookie() ?: "",
                    userAgent = sessionManager.getUserAgent(),
                    linkId = server.url,
                    routeToken = currentState.currentRouteToken
                )

                if (embedUrl != null) {
                    val extractor = getExtractorForUrl(embedUrl)
                    if (extractor != null) {
                        val extracted = extractor.extractVideo(embedUrl)
                        if (extracted != null) {
                            _state.update {
                                it.copy(
                                videoHeaders = extracted.headers,
                                videoUrl = extracted.url,
                                isExtracting = false
                            )
                            }
                        } else {
                            tryNextServer("Failed to extract video from server.")
                        }
                    } else {
                        tryNextServer("Unsupported server natively: $embedUrl")
                    }
                } else {
                    tryNextServer("Failed to decrypt embed link.")
                }
            } catch (e: Exception) {
                tryNextServer("Error: ${e.message}")
            }
        }
    }

    private fun tryNextServer(fallbackMessage: String) {
        val currentState = _state.value
        val nextServer = currentState.servers.firstOrNull { it !in currentState.attemptedServers }
        if (nextServer != null) {
            selectServer(nextServer)
        } else {
            _state.update { it.copy(isExtracting = false, errorMessage = fallbackMessage) }
        }
    }

    private fun playNextEpisode() {
        val st = _state.value
        if (st.directNextUrl != null) {
            loadMedia(st.directNextUrl)
            return
        }
        if (st.hasNextEpisode()) {
            val currentSeason = st.seasons[st.currentSeasonIndex]
            var nextSIdx = st.currentSeasonIndex
            var nextEIdx = st.currentEpisodeIndex + 1
            if (nextEIdx >= currentSeason.episodes.size) {
                nextSIdx++
                nextEIdx = 0
            }
            val nextUrl = st.seasons[nextSIdx].episodes[nextEIdx].url
            _state.update {
                it.copy(
                currentSeasonIndex = nextSIdx,
                currentEpisodeIndex = nextEIdx
            )
            }
            loadMedia(nextUrl)
        }
    }

    private fun playPrevEpisode() {
        val st = _state.value
        if (st.directPrevUrl != null) {
            loadMedia(st.directPrevUrl)
            return
        }
        if (st.hasPrevEpisode()) {
            var prevSIdx = st.currentSeasonIndex
            var prevEIdx = st.currentEpisodeIndex - 1
            if (prevEIdx < 0) {
                prevSIdx--
                prevEIdx = st.seasons[prevSIdx].episodes.size - 1
            }
            val prevUrl = st.seasons[prevSIdx].episodes[prevEIdx].url
            _state.update {
                it.copy(
                currentSeasonIndex = prevSIdx,
                currentEpisodeIndex = prevEIdx
            )
            }
            loadMedia(prevUrl)
        }
    }

    private fun saveProgress(positionMs: Long, durationMs: Long) {
        val st = _state.value
        if (st.currentMediaTitle.isNotBlank() && durationMs > 0) {
            val seriesName = st.seriesTitle ?: st.currentMediaTitle.substringBefore(" - Sezon").substringBefore(" - Odcinek")
            progressManager.saveProgress(
                ProgressItem(
                    url = st.currentMediaUrl,
                    title = st.currentMediaTitle,
                    posterUrl = st.currentMediaPoster,
                    progressMs = positionMs,
                    durationMs = durationMs,
                    seriesTitle = seriesName
                )
            )
        }
    }
}
