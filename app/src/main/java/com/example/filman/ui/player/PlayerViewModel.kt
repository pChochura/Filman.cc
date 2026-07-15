package com.example.filman.ui.player

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filman.R
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.local.SessionManager
import com.example.filman.data.local.WatchedManager
import com.example.filman.data.model.EmbedLink
import com.example.filman.data.model.MovieItem
import com.example.filman.data.model.TvShow
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

sealed interface PlayerError {
    data object NoServers : PlayerError
    data class LoadServersFailed(val message: String) : PlayerError
    data object ExtractFailed : PlayerError
    data object DecryptFailed : PlayerError
    data class UnsupportedServer(val url: String) : PlayerError
    data class Generic(val message: String) : PlayerError
}

sealed interface PlayerEvent {
    data class LoadMedia(val url: String) : PlayerEvent
    data class SelectServer(val server: EmbedLink) : PlayerEvent
    data class PlayNextEpisode(val userInitiated: Boolean) : PlayerEvent
    data object PlayPrevEpisode : PlayerEvent
    data class SaveProgress(val positionMs: Long, val durationMs: Long) : PlayerEvent
    data class SetPlaybackSpeed(val speed: Float) : PlayerEvent
}

@Immutable
data class PlayerState(
    val currentMediaUrl: String = "",
    val currentRouteToken: String = "",
    val currentMediaTitle: String = "",
    val currentMediaPoster: String = "",
    val seriesTitle: String? = null,
    val seriesUrl: String? = null,
    val directPrevUrl: String? = null,
    val directNextUrl: String? = null,

    val servers: List<EmbedLink> = emptyList(),
    val selectedServer: EmbedLink? = null,
    val attemptedServers: Set<EmbedLink> = emptySet(),
    val isFetchingServers: Boolean = false,
    val serverLoadError: PlayerError? = null,

    val videoUrl: String? = null,
    val videoHeaders: Map<String, String> = emptyMap(),
    val isExtracting: Boolean = false,
    val errorMessage: PlayerError? = null,

    val seasons: List<Season> = emptyList(),
    val currentSeasonIndex: Int = -1,
    val currentEpisodeIndex: Int = -1,

    val initialProgressMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
) {
    fun hasNextEpisode(): Boolean {
        if (currentSeasonIndex != -1 && currentEpisodeIndex != -1) {
            val currentSeason = seasons.getOrNull(currentSeasonIndex) ?: return false
            if (currentEpisodeIndex + 1 < currentSeason.episodes.size) {
                return true
            } else if (currentSeasonIndex + 1 < seasons.size) {
                return true
            }
        }
        return !directNextUrl.isNullOrBlank()
    }

    fun hasPrevEpisode(): Boolean {
        if (currentSeasonIndex != -1 && currentEpisodeIndex != -1) {
            if (currentEpisodeIndex - 1 >= 0) {
                return true
            } else if (currentSeasonIndex - 1 >= 0) {
                return true
            }
        }
        return directPrevUrl != null
    }

    fun getCurrentSeasonName(): String? = seasons.getOrNull(currentSeasonIndex)?.name
}

sealed interface PlayerEffect {
    // Empty for now, but ready for navigation effects
}

class PlayerViewModel(
    private val context: Context,
    private val scraper: FilmanScraper,
    private val sessionManager: SessionManager,
    private val progressManager: ProgressManager,
    private val watchedManager: WatchedManager,
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
            is PlayerEvent.SetPlaybackSpeed -> _state.update { it.copy(playbackSpeed = event.speed) }
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
                    attemptedServers = emptySet(),
                )
            }

            try {
                val detailedMedia = scraper.getMediaDetails(url)
                val details = detailedMedia?.baseItem
                if (details != null && details !is TvShow) {
                    _state.update {
                        it.copy(
                            currentRouteToken = details.routeToken ?: "",
                            currentMediaTitle = details.titlePl,
                            currentMediaPoster = details.posterUrl,
                            seriesUrl = details.seriesUrl?.replace(Regex("^https?://[^/]+"), ""),
                            directPrevUrl = details.prevEpisodeUrl,
                            directNextUrl = details.nextEpisodeUrl,
                        )
                    }

                    // Restore progress
                    val savedProgress = progressManager.getProgressForUrl(url)
                    if (savedProgress != null && savedProgress.progressMs > 0 && savedProgress.progressPercentage < 0.95f) {
                        _state.update { it.copy(initialProgressMs = savedProgress.progressMs) }
                    } else {
                        _state.update { it.copy(initialProgressMs = 0L) }
                    }

                    val currentSeasons = _state.value.seasons
                    val seriesUrl = details.seriesUrl
                    if (seriesUrl != null && currentSeasons.isEmpty()) {
                        try {
                            val seriesDetailed = scraper.getMediaDetails(seriesUrl)
                            val series = seriesDetailed?.baseItem
                            if (series is TvShow) {
                                var sIdx = -1
                                var eIdx = -1
                                for (i in series.seasons.indices) {
                                    val epIndex = series.seasons[i].episodes.indexOfFirst {
                                        val normalizedIt = it.url.replace(Regex("^https?://[^/]+"), "")
                                        val normalizedUrl = url.replace(Regex("^https?://[^/]+"), "")
                                        normalizedIt == normalizedUrl ||
                                                it.url.contains(url) || url.contains(it.url)
                                    }
                                    if (epIndex != -1) {
                                        sIdx = i
                                        eIdx = epIndex
                                        break
                                    }
                                }
                                _state.update {
                                    it.copy(
                                        seriesTitle = series.titlePl,
                                        seasons = series.seasons,
                                        currentSeasonIndex = sIdx,
                                        currentEpisodeIndex = eIdx,
                                    )
                                }
                            }
                        } catch (e: Exception) {
                        }
                    }

                    if (details.embeds.isNotEmpty()) {
                        val prioritized = details.embeds.sortedBy { link ->
                            when (link.serverName.lowercase()) {
                                "doodstream" -> 0
                                "voe" -> 1
                                else -> 2
                            }
                        }
                        _state.update {
                            it.copy(
                                servers = details.embeds,
                                selectedServer = prioritized.first(),
                                isFetchingServers = false,
                            )
                        }
                        selectServer(prioritized.first())
                    } else {
                        _state.update {
                            it.copy(
                                serverLoadError = PlayerError.NoServers,
                                isFetchingServers = false,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        serverLoadError = PlayerError.LoadServersFailed(e.message ?: "Unknown error"),
                        isFetchingServers = false,
                    )
                }
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
                    errorMessage = null,
                )
            }

            val currentState = _state.value
            try {
                val embedUrl = resolveFilmanEmbedLink(
                    cookie = sessionManager.getCookie() ?: "",
                    userAgent = sessionManager.getUserAgent(),
                    linkId = server.url,
                    routeToken = currentState.currentRouteToken,
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
                                    isExtracting = false,
                                )
                            }
                        } else {
                            tryNextServer(PlayerError.ExtractFailed)
                        }
                    } else {
                        tryNextServer(PlayerError.UnsupportedServer(embedUrl))
                    }
                } else {
                    tryNextServer(PlayerError.DecryptFailed)
                }
            } catch (e: Exception) {
                tryNextServer(PlayerError.Generic(e.message ?: "Unknown error"))
            }
        }
    }

    private fun tryNextServer(fallbackError: PlayerError) {
        val currentState = _state.value
        val nextServer = currentState.servers.firstOrNull { it !in currentState.attemptedServers }
        if (nextServer != null) {
            selectServer(nextServer)
        } else {
            _state.update { it.copy(isExtracting = false, errorMessage = fallbackError) }
        }
    }

    private fun playNextEpisode() {
        val st = _state.value

        if (st.currentSeasonIndex != -1 && st.currentEpisodeIndex != -1) {
            val currentSeason = st.seasons.getOrNull(st.currentSeasonIndex)
            if (currentSeason != null) {
                var nextSIdx = st.currentSeasonIndex
                var nextEIdx = st.currentEpisodeIndex + 1
                if (nextEIdx >= currentSeason.episodes.size) {
                    nextSIdx++
                    nextEIdx = 0
                }
                if (nextSIdx < st.seasons.size) {
                    val nextUrl = st.seasons[nextSIdx].episodes[nextEIdx].url
                    _state.update {
                        it.copy(
                            currentSeasonIndex = nextSIdx,
                            currentEpisodeIndex = nextEIdx,
                        )
                    }
                    loadMedia(nextUrl)
                    return
                }
            }
        }

        if (st.directNextUrl != null) {
            loadMedia(st.directNextUrl)
        }
    }

    private fun playPrevEpisode() {
        val st = _state.value

        if (st.currentSeasonIndex != -1 && st.currentEpisodeIndex != -1) {
            var prevSIdx = st.currentSeasonIndex
            var prevEIdx = st.currentEpisodeIndex - 1
            if (prevEIdx < 0) {
                prevSIdx--
                if (prevSIdx >= 0) {
                    prevEIdx = st.seasons[prevSIdx].episodes.size - 1
                }
            }
            if (prevSIdx >= 0 && prevEIdx >= 0) {
                val prevUrl = st.seasons[prevSIdx].episodes[prevEIdx].url
                _state.update {
                    it.copy(
                        currentSeasonIndex = prevSIdx,
                        currentEpisodeIndex = prevEIdx,
                    )
                }
                loadMedia(prevUrl)
                return
            }
        }

        if (st.directPrevUrl != null) {
            loadMedia(st.directPrevUrl)
        }
    }

    private fun saveProgress(positionMs: Long, durationMs: Long) {
        val st = _state.value
        if (st.currentMediaTitle.isNotBlank() && durationMs > 0) {
            val seriesName = st.seriesTitle ?: st.currentMediaTitle.substringBefore(" - Sezon")
                .substringBefore(" - Odcinek")

            val progressPercentage = positionMs.toFloat() / durationMs.toFloat()
            val isFinished = progressPercentage >= 0.95f

            if (isFinished) {
                watchedManager.markAsWatched(st.currentMediaUrl)
            }

            if (isFinished && st.hasNextEpisode()) {
                var nextUrl: String? = null
                var nextTitle: String? = null

                if (st.currentSeasonIndex != -1 && st.currentEpisodeIndex != -1) {
                    val currentSeason = st.seasons.getOrNull(st.currentSeasonIndex)
                    if (currentSeason != null) {
                        var nextSIdx = st.currentSeasonIndex
                        var nextEIdx = st.currentEpisodeIndex + 1
                        if (nextEIdx >= currentSeason.episodes.size) {
                            nextSIdx++
                            nextEIdx = 0
                        }
                        if (nextSIdx < st.seasons.size) {
                            val nextEp = st.seasons[nextSIdx].episodes[nextEIdx]
                            nextUrl = nextEp.url
                            val seasonName = st.seasons[nextSIdx].name
                            nextTitle = "$seriesName - $seasonName - ${nextEp.title}"
                        }
                    }
                }

                if (nextUrl == null && st.directNextUrl != null) {
                    nextUrl = st.directNextUrl

                    val match = Regex("s(\\d+)e(\\d+)", RegexOption.IGNORE_CASE).find(nextUrl)
                    if (match != null) {
                        val seasonNum = match.groupValues[1].toInt()
                        val epNum = match.groupValues[2].toInt()
                        nextTitle = context.getString(
                            R.string.player_next_episode_format,
                            seriesName,
                            seasonNum,
                            epNum,
                        )
                    } else {
                        nextTitle = context.getString(
                            R.string.player_next_episode_fallback,
                            seriesName,
                        )
                    }
                }

                if (nextUrl != null && nextTitle != null) {
                    progressManager.saveProgress(
                        ProgressItem(
                            url = nextUrl,
                            titlePl = nextTitle,
                            posterUrl = st.currentMediaPoster,
                            progressMs = 0L,
                            durationMs = 1L, // set to 1 so duration > 0 and percentage is 0%
                            seriesTitle = seriesName,
                            seriesUrl = st.seriesUrl,
                        ),
                    )
                    return
                }
            }

            if (isFinished) {
                // Remove from progress since it's fully watched and there's no next episode
                progressManager.saveProgress(
                    ProgressItem(
                        url = st.currentMediaUrl,
                        titlePl = st.currentMediaTitle,
                        posterUrl = st.currentMediaPoster,
                        progressMs = positionMs,
                        durationMs = 0L,
                        seriesTitle = seriesName,
                        seriesUrl = st.seriesUrl,
                    ),
                )
                return
            }

            // Normal save for unfinished media
            progressManager.saveProgress(
                ProgressItem(
                    url = st.currentMediaUrl,
                    titlePl = st.currentMediaTitle,
                    posterUrl = st.currentMediaPoster,
                    progressMs = positionMs,
                    durationMs = durationMs,
                    seriesTitle = seriesName,
                    seriesUrl = st.seriesUrl,
                ),
            )
        }
    }
}
