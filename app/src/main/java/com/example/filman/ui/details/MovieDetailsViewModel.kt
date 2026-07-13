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
import com.example.filman.data.model.ProgressItem
import com.example.filman.data.model.Season
import com.example.filman.data.scraper.AuthException
import com.example.filman.data.scraper.FilmanScraper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
        launchHandled(onError = { t ->
            if (t is AuthException) {
                logout()
            }
            _state.update { it.copy(isLoading = false) }
        }) {
            _state.update { it.copy(isLoading = true, movieUrl = url) }

            val details = scraper.getMediaDetails(url)
            val correctTargetUrl = getCanonicalUrl(url, details)
            val isFavorite = favoritesManager.isFavorite(correctTargetUrl)

            var nextS: Season? = null
            var nextE: Episode? = null
            var nextEIdx = -1

            if (details is MediaDetails.Series) {
                val nextInfo = findNextEpisode(details)
                nextS = nextInfo.first
                nextE = nextInfo.second
                nextEIdx = nextInfo.third
            }

            _state.update {
                it.copy(
                    mediaDetails = details,
                    seriesDetails = details as? MediaDetails.Series,
                    isFavorite = isFavorite,
                    selectedSeason = nextS,
                    nextEpisode = nextE,
                    nextEpisodeIndex = nextEIdx,
                    isLoading = false,
                )
            }
        }
    }

    private fun toggleFavorite() {
        val current = _state.value
        val details = current.mediaDetails ?: return
        val targetUrl = getCanonicalUrl(current.movieUrl, details)

        if (current.isFavorite) {
            favoritesManager.removeFavorite(targetUrl)
            _state.update { it.copy(isFavorite = false) }
        } else {
            val targetTitle = details.titlePl.substringBefore(" - ").trim()
            val movieToSave = Movie(
                url = targetUrl,
                titlePl = targetTitle,
                posterUrl = details.posterUrl,
            )
            favoritesManager.addFavorite(movieToSave)
            _state.update { it.copy(isFavorite = true) }
        }
    }

    private fun findNextEpisode(series: MediaDetails.Series): Triple<Season?, Episode?, Int> {
        var nextS: Season? = series.seasons.firstOrNull()
        var nextE: Episode? = nextS?.episodes?.firstOrNull()
        var nextEIdx = if (nextE != null) 0 else -1

        for (season in series.seasons) {
            for ((index, episode) in season.episodes.withIndex()) {
                val prog = progressManager.getProgressForUrl(episode.url)
                if (prog != null) {
                    if (prog.progressPercentage < 0.95f) {
                        return Triple(season, episode, index)
                    } else {
                        // Current episode finished, suggest next one
                        if (index + 1 < season.episodes.size) {
                            nextS = season
                            nextE = season.episodes[index + 1]
                            nextEIdx = index + 1
                        } else {
                            val sIdx = series.seasons.indexOf(season)
                            if (sIdx + 1 < series.seasons.size) {
                                nextS = series.seasons[sIdx + 1]
                                nextE = nextS.episodes.firstOrNull()
                                nextEIdx = if (nextE != null) 0 else -1
                            }
                        }
                    }
                }
            }
        }
        return Triple(nextS, nextE, nextEIdx)
    }

    private fun getCanonicalUrl(currentUrl: String, details: MediaDetails?): String {
        val targetUrl = if (details is MediaDetails.MovieOrEpisode && details.seriesUrl != null) {
            details.seriesUrl
        } else {
            currentUrl.replace(Regex("(?i)/s\\d+(?:e\\d+)?/?$"), "")
        }
        return targetUrl.replace(Regex("^https?://[^/]+"), "")
    }

    private fun logout() {
        sessionManager.clearCookie()
        CookieManager.getInstance().removeAllCookies(null)
        _effect.trySend(MovieDetailsEffect.NavigateToAuth)
    }

    private fun launchHandled(
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit
    ) = viewModelScope.launch {
        runCatching { block() }.onFailure { t ->
            onError?.invoke(t)
        }
    }
}
