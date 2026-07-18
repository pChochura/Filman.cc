package com.example.filman.ui.details

import android.webkit.CookieManager
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filman.R
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.local.SessionManager
import com.example.filman.data.local.WatchedManager
import com.example.filman.data.model.DetailedMedia
import com.example.filman.data.model.EpisodeLink
import com.example.filman.data.model.MovieItem
import com.example.filman.data.model.ProgressItem
import com.example.filman.data.model.Season
import com.example.filman.data.scraper.AuthException
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.components.OverlayMenuData
import com.example.filman.ui.components.sections.TabRowSectionItem
import com.example.filman.ui.details.MovieDetailsEffect.NavigateToAuth
import com.example.filman.ui.details.MovieDetailsEffect.NavigateToPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface MovieDetailsEvent {
    data class LoadDetails(val url: String) : MovieDetailsEvent
    data object ToggleFavorite : MovieDetailsEvent
    data class SelectSeason(val season: Season) : MovieDetailsEvent
    data class PlayMovie(val url: String) : MovieDetailsEvent
    data class PlayEpisode(val episode: EpisodeLink) : MovieDetailsEvent
    data class TabChanged(val tab: TabRowSectionItem) : MovieDetailsEvent

    data class OpenContextMenu(
        val title: String,
        val url: String,
        val posterUrl: String,
    ) : MovieDetailsEvent

    data object CloseContextMenu : MovieDetailsEvent
}

@Immutable
internal data class MovieDetailsState(
    val isLoading: Boolean = true,
    val mediaDetails: DetailedMedia? = null,
    val seriesDetails: MovieItem? = null,
    val isFavorite: Boolean = false,
    val selectedSeason: Season? = null,
    val nextEpisode: EpisodeLink? = null,
    val nextEpisodeIndex: Int = -1,
    val movieUrl: String = "",
    val progressMap: Map<String, ProgressItem> = emptyMap(),
    val watchedSet: Set<String> = emptySet(),
    val selectedTabId: Int = TabRowItemId.Similar.id,
    val overlayMenuData: OverlayMenuData? = null,
) {
    val tabs: List<TabRowSectionItem>
        get() = buildList {
            if (mediaDetails?.baseItem?.seasons != null) {
                add(
                    TabRowSectionItem(
                        title = R.string.details_episodes,
                        id = TabRowItemId.Episodes.id,
                    ),
                )
            }

            if (mediaDetails?.similarMovies?.isNotEmpty() == true) {
                add(
                    TabRowSectionItem(
                        title = R.string.details_similar,
                        id = TabRowItemId.Similar.id,
                    ),
                )
            }

            add(
                TabRowSectionItem(
                    title = R.string.details_about,
                    id = TabRowItemId.Details.id,
                ),
            )
        }
}

internal enum class TabRowItemId(val id: Int) {
    Episodes(0), Similar(1), Details(2)
}

internal sealed interface MovieDetailsEffect {
    data object NavigateToAuth : MovieDetailsEffect
    data class NavigateToPlayer(val url: String) : MovieDetailsEffect
}

internal class MovieDetailsViewModel(
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
            is MovieDetailsEvent.ToggleFavorite -> toggleFavorite()
            is MovieDetailsEvent.SelectSeason -> {
                _state.update { it.copy(selectedSeason = event.season) }
            }

            is MovieDetailsEvent.PlayMovie -> {
                _effect.trySend(NavigateToPlayer(event.url))
            }

            is MovieDetailsEvent.PlayEpisode -> {
                _effect.trySend(NavigateToPlayer(event.episode.url))
            }

            is MovieDetailsEvent.TabChanged -> {
                _state.update { it.copy(selectedTabId = event.tab.id) }
            }

            is MovieDetailsEvent.CloseContextMenu -> TODO()
            is MovieDetailsEvent.OpenContextMenu -> TODO()
        }
    }

    private fun loadDetails(url: String) {
        launchHandled(
            onError = { t ->
                if (t is AuthException) {
                    logout()
                }
                _state.update { it.copy(isLoading = false) }
            },
        ) {
            _state.update { it.copy(isLoading = true, movieUrl = url) }

            val details = scraper.getMediaDetails(url)
            val correctTargetUrl = getCanonicalUrl(url, details?.baseItem)
            val isFavorite = favoritesManager.isFavorite(correctTargetUrl)

            var nextS: Season? = null
            var nextE: EpisodeLink? = null
            var nextEIdx = -1

            val baseItem = details?.baseItem
            if (baseItem?.seasons != null) {
                val nextInfo = findNextEpisode(baseItem)
                nextS = nextInfo.first
                nextE = nextInfo.second
                nextEIdx = nextInfo.third
            }

            _state.update {
                it.copy(
                    mediaDetails = details,
                    seriesDetails = if (baseItem?.seasons != null) baseItem else null,
                    isFavorite = isFavorite,
                    selectedSeason = nextS,
                    nextEpisode = nextE,
                    nextEpisodeIndex = nextEIdx,
                    isLoading = false,
                    selectedTabId = if (baseItem?.seasons != null) {
                        TabRowItemId.Episodes.id
                    } else {
                        TabRowItemId.Similar.id
                    },
                )
            }
        }
    }

    private fun toggleFavorite() {
        val current = _state.value
        val details = current.mediaDetails?.baseItem ?: return
        val targetUrl = getCanonicalUrl(current.movieUrl, details)

        if (current.isFavorite) {
            favoritesManager.removeFavorite(targetUrl)
            _state.update { it.copy(isFavorite = false) }
        } else {
            val targetTitle = details.titlePl.substringBefore(" - ").trim()
            val movieToSave = MovieItem(
                url = targetUrl,
                titlePl = targetTitle,
                posterUrl = details.posterUrl,
            )
            favoritesManager.addFavorite(movieToSave)
            _state.update { it.copy(isFavorite = true) }
        }
    }

    private fun findNextEpisode(series: MovieItem): Triple<Season?, EpisodeLink?, Int> {
        val seasons = series.seasons ?: emptyList()
        var nextS: Season? = seasons.firstOrNull()
        var nextE: EpisodeLink? = nextS?.episodes?.firstOrNull()
        var nextEIdx = if (nextE != null) 0 else -1

        for (season in seasons) {
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
                            val sIdx = seasons.indexOf(season)
                            if (sIdx + 1 < seasons.size) {
                                nextS = seasons[sIdx + 1]
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

    private fun getCanonicalUrl(currentUrl: String, details: MovieItem?): String {
        val seriesUrl = details?.seriesUrl
        val targetUrl = seriesUrl ?: currentUrl.replace(Regex("(?i)/s\\d+(?:e\\d+)?/?$"), "")
        return targetUrl.replace(Regex("^https?://[^/]+"), "")
    }

    private fun logout() {
        sessionManager.clearCookie()
        CookieManager.getInstance().removeAllCookies(null)
        _effect.trySend(NavigateToAuth)
    }

    private fun launchHandled(
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit,
    ) = viewModelScope.launch {
        runCatching { block() }.onFailure { t ->
            onError?.invoke(t)
        }
    }
}
