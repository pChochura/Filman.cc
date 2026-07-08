package com.example.filman.ui.details

import android.webkit.CookieManager
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.local.SessionManager
import com.example.filman.data.local.WatchedManager
import com.example.filman.data.model.Episode
import com.example.filman.data.model.MediaDetails
import com.example.filman.data.model.Movie
import com.example.filman.data.model.Season
import com.example.filman.data.scraper.AuthException
import com.example.filman.data.scraper.FilmanScraper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.example.filman.data.model.ProgressItem

sealed interface MovieDetailsEvent {
    data class LoadDetails(val url: String) : MovieDetailsEvent
    data object ToggleFavorite : MovieDetailsEvent
    data class SelectSeason(val season: Season) : MovieDetailsEvent
    data class PlayMovie(val url: String) : MovieDetailsEvent
    data class PlayEpisode(val episode: Episode) : MovieDetailsEvent
}

@Immutable
data class MovieDetailsState(
    val isLoading: Boolean = true,
    val mediaDetails: MediaDetails? = null,
    val seriesDetails: MediaDetails.Series? = null,
    val isFavorite: Boolean = false,
    val selectedSeason: Season? = null,
    val nextEpisode: Episode? = null,
    val nextEpisodeIndex: Int = -1,
    val movieUrl: String = "",
    val progressMap: Map<String, ProgressItem> = emptyMap(),
    val watchedSet: Set<String> = emptySet(),
)

sealed interface MovieDetailsEffect {
    data object NavigateToAuth : MovieDetailsEffect
    data class NavigateToPlayer(val url: String) : MovieDetailsEffect
}

class MovieDetailsViewModel(
    private val scraper: FilmanScraper,
    private val favoritesManager: FavoritesManager,
    private val progressManager: ProgressManager,
    private val sessionManager: SessionManager,
    private val watchedManager: WatchedManager,
) : ViewModel() {
    private val _state = MutableStateFlow(MovieDetailsState())
    val state: StateFlow<MovieDetailsState> = _state.asStateFlow()

    private val _effect = Channel<MovieDetailsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        viewModelScope.launch {
            progressManager.progressItemsFlow.collect { progressList ->
                _state.update { state ->
                    state.copy(progressMap = progressList.associateBy { it.url })
                }
            }
        }
        viewModelScope.launch {
            watchedManager.watchedUrlsFlow.collect { watchedSet ->
                _state.update { state ->
                    state.copy(watchedSet = watchedSet)
                }
            }
        }
    }


    fun onEvent(event: MovieDetailsEvent) {
        when (event) {
            is MovieDetailsEvent.LoadDetails -> loadDetails(event.url)
            MovieDetailsEvent.ToggleFavorite -> toggleFavorite()
            is MovieDetailsEvent.SelectSeason -> {
                _state.update { it.copy(selectedSeason = event.season) }
            }

            is MovieDetailsEvent.PlayMovie -> {
                _effect.trySend(MovieDetailsEffect.NavigateToPlayer(event.url))
            }

            is MovieDetailsEvent.PlayEpisode -> {
                _effect.trySend(MovieDetailsEffect.NavigateToPlayer(event.episode.url))
            }
        }
    }

    private fun loadDetails(url: String) {
        viewModelScope.launch {
            _state.update {
                val targetUrl = url.replace(Regex("(?i)/s\\d+(?:e\\d+)?/?$"), "")
                it.copy(
                    isLoading = true,
                    movieUrl = url,
                    isFavorite = favoritesManager.isFavorite(targetUrl),
                )
            }
            runCatching {
                val details = scraper.getMediaDetails(url)
                var correctTargetUrl = if (details is MediaDetails.MovieOrEpisode && details.seriesUrl != null) {
                    details.seriesUrl
                } else {
                    url.replace(Regex("(?i)/s\\d+(?:e\\d+)?/?$"), "")
                }
                correctTargetUrl = correctTargetUrl.replace(Regex("^https?://[^/]+"), "")
                _state.update { it.copy(isFavorite = favoritesManager.isFavorite(correctTargetUrl)) }
                var nextS: Season? = null
                var nextE: Episode? = null
                var nextEIdx = -1

                if (details is MediaDetails.Series) {
                    nextS = details.seasons.firstOrNull()
                    nextE = nextS?.episodes?.firstOrNull()
                    nextEIdx = if (nextE != null) 0 else -1
                    var foundIncomplete = false

                    for (season in details.seasons) {
                        for (episode in season.episodes) {
                            val prog = progressManager.getProgressForUrl(episode.url)
                            if (prog != null) {
                                if (prog.progressPercentage < 0.95f) {
                                    nextS = season
                                    nextE = episode
                                    nextEIdx = season.episodes.indexOf(episode)
                                    foundIncomplete = true
                                    break
                                } else {
                                    val epIdx = season.episodes.indexOf(episode)
                                    if (epIdx + 1 < season.episodes.size) {
                                        nextS = season
                                        nextE = season.episodes[epIdx + 1]
                                        nextEIdx = epIdx + 1
                                    } else {
                                        val sIdx = details.seasons.indexOf(season)
                                        if (sIdx + 1 < details.seasons.size) {
                                            nextS = details.seasons[sIdx + 1]
                                            nextE = nextS.episodes.firstOrNull()
                                            nextEIdx = if (nextE != null) 0 else -1
                                        }
                                    }
                                }
                            }
                        }
                        if (foundIncomplete) break
                    }
                }

                _state.update {
                    it.copy(
                        mediaDetails = details,
                        seriesDetails = details as? MediaDetails.Series,
                        selectedSeason = nextS,
                        nextEpisode = nextE,
                        nextEpisodeIndex = nextEIdx,
                        isLoading = false,
                    )
                }
            }.onFailure {
                if (it is AuthException) {
                    sessionManager.clearCookie()
                    CookieManager.getInstance().removeAllCookies(null)
                    _effect.trySend(MovieDetailsEffect.NavigateToAuth)
                }
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun toggleFavorite() {
        val current = _state.value
        val url = current.movieUrl
        val details = current.mediaDetails ?: return

        var targetUrl = if (details is MediaDetails.MovieOrEpisode && details.seriesUrl != null) {
            details.seriesUrl
        } else {
            url.replace(Regex("(?i)/s\\d+(?:e\\d+)?/?$"), "")
        }
        targetUrl = targetUrl.replace(Regex("^https?://[^/]+"), "")
        val targetTitle = if (details.title.contains(" - ")) {
            details.title.substringBefore(" - ").trim()
        } else {
            details.title
        }

        if (current.isFavorite) {
            favoritesManager.removeFavorite(targetUrl)
            _state.update { it.copy(isFavorite = false) }
        } else {
            val movieToSave = Movie(
                url = targetUrl,
                title = targetTitle,
                posterUrl = details.posterUrl,
            )
            favoritesManager.addFavorite(movieToSave)
            _state.update { it.copy(isFavorite = true) }
        }
    }
}
