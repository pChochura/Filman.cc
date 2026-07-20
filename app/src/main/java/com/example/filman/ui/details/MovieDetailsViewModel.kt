package com.example.filman.ui.details

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.example.filman.R
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.model.DetailedMedia
import com.example.filman.data.model.EpisodeItem
import com.example.filman.data.model.EpisodeLink
import com.example.filman.data.model.MovieItem
import com.example.filman.data.model.ProgressItem
import com.example.filman.data.model.Season
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.components.OverlayMenuData
import com.example.filman.ui.components.sections.TabRowSectionItem
import com.example.filman.ui.details.MovieDetailsEffect.NavigateToPlayer
import kotlinx.coroutines.launch

internal sealed interface MovieDetailsEvent : FilmanEvent {
    data class LoadDetails(val url: String) : MovieDetailsEvent
    data object ToggleFavorite : MovieDetailsEvent
    data class PlayItem(val url: String) : MovieDetailsEvent
    data class TabChanged(val tab: TabRowSectionItem) : MovieDetailsEvent
}

@Immutable
internal data class MovieDetailsState(
    val isLoading: Boolean = true,
    val mediaDetails: DetailedMedia? = null,
    val isFavorite: Boolean = false,
    val progressMap: Map<String, ProgressItem> = emptyMap(),
    val progressList: List<ProgressItem> = emptyList(),
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

    fun getSeasonEpisodes(season: Season) = season.episodes.map { episode ->
        val progress = progressMap[episode.url]
        EpisodeItem(
            titlePl = episode.title,
            titleEn = null,
            url = episode.url,
            posterUrl = mediaDetails?.baseItem?.posterUrl.orEmpty(),
            progress = progress,
        )
    }

    val watchButtonState: WatchButtonState
        get() {
            val baseItem = mediaDetails?.baseItem ?: return WatchButtonState.Default
            val isSeries = baseItem.seasons != null

            val mostRecent = progressList.firstOrNull { progress ->
                progress.parentUrl == baseItem.url
            }

            if (!isSeries) {
                return when {
                    mostRecent != null && mostRecent.progressPercentage < 0.95f ->
                        WatchButtonState.Continue

                    mostRecent != null -> WatchButtonState.WatchAgain
                    else -> WatchButtonState.Default
                }
            }

            val flatEpisodesUrls = baseItem.seasons.flatMapIndexed { index, season ->
                season.episodes.map { index + 1 to it.url }
            }
            if (mostRecent != null) {
                val currentIndex = flatEpisodesUrls.indexOfFirst { it.second == mostRecent.url }

                val isFinished = when (mostRecent) {
                    is ProgressItem.Watched -> true
                    is ProgressItem.InProgress -> mostRecent.progressPercentage > 0.95f
                }

                if (isFinished) {
                    flatEpisodesUrls.getOrNull(currentIndex + 1)?.let {
                        return WatchButtonState.WatchNextEpisode(
                            season = it.first.toString(), episode = (currentIndex + 1).toString(),
                        )
                    }
                } else {
                    flatEpisodesUrls.getOrNull(currentIndex)?.let {
                        return WatchButtonState.ContinueEpisode(
                            season = it.first.toString(), episode = currentIndex.toString(),
                        )
                    }
                }
            }

            return WatchButtonState.Default
        }

    fun getWatchButtonUrl(): String? {
        val baseItem = mediaDetails?.baseItem ?: return null
        val isSeries = baseItem.seasons != null

        if (!isSeries) return baseItem.url

        val mostRecent = progressList.firstOrNull { progress ->
            progress.parentUrl == baseItem.url
        }

        val flatEpisodesUrls = baseItem.seasons.flatMap { it.episodes.map(EpisodeLink::url) }
        if (mostRecent != null) {
            val currentIndex = flatEpisodesUrls.indexOf(mostRecent.url)

            val isFinished = when (mostRecent) {
                is ProgressItem.Watched -> true
                is ProgressItem.InProgress -> mostRecent.progressPercentage > 0.95f
            }

            if (isFinished) {
                flatEpisodesUrls.getOrNull(currentIndex + 1)?.let {
                    return it
                }
            } else {
                flatEpisodesUrls.getOrNull(currentIndex)?.let {
                    return it
                }
            }
        }

        return flatEpisodesUrls.firstOrNull() ?: baseItem.url
    }
}

internal enum class TabRowItemId(val id: Int) {
    Episodes(0), Similar(1), Details(2)
}

internal sealed interface WatchButtonState {
    data object Default : WatchButtonState
    data object WatchAgain : WatchButtonState
    data object Continue : WatchButtonState
    data class WatchNextEpisode(val season: String, val episode: String) : WatchButtonState
    data class ContinueEpisode(val season: String, val episode: String) : WatchButtonState
}

internal sealed interface MovieDetailsEffect {
    data object NavigateToAuth : MovieDetailsEffect
    data class NavigateToPlayer(val url: String) : MovieDetailsEffect
    data class NavigateToDetails(val url: String) : MovieDetailsEffect
}

internal class MovieDetailsViewModel(
    private val scraper: FilmanScraper,
    private val _favoritesManager: FavoritesManager,
    private val _progressManager: ProgressManager,
) : BaseViewModel<MovieDetailsState, MovieDetailsEvent, MovieDetailsEffect>(
    initialState = MovieDetailsState(),
    favoritesManager = _favoritesManager,
    progressManager = _progressManager,
) {

    init {
        viewModelScope.launch {
            _progressManager.progressItemsFlow.collect { progressList ->
                updateState { state ->
                    state.copy(
                        progressMap = progressList.associateBy { it.url },
                        progressList = progressList,
                    )
                }
            }
        }
    }

    override fun getAuthErrorEffect(): MovieDetailsEffect = MovieDetailsEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(url: String): MovieDetailsEffect = MovieDetailsEffect.NavigateToDetails(url)

    override fun setOverlayMenuData(data: OverlayMenuData?) {
        updateState { it.copy(overlayMenuData = data) }
    }

    override fun handleEvent(event: MovieDetailsEvent) {
        when (event) {
            is MovieDetailsEvent.LoadDetails -> loadDetails(event.url)
            is MovieDetailsEvent.ToggleFavorite -> toggleFavorite()
            is MovieDetailsEvent.PlayItem -> sendEffect(NavigateToPlayer(event.url))
            is MovieDetailsEvent.TabChanged -> updateState { it.copy(selectedTabId = event.tab.id) }
        }
    }

    private fun loadDetails(url: String) {
        launchHandled(
            onError = {
                updateState { it.copy(isLoading = false) }
                handleError(it)
            },
        ) {
            updateState { it.copy(isLoading = true) }

            val details = scraper.getMediaDetails(url)
            val isFavorite = _favoritesManager.isFavorite(url)
            val baseItem = details?.baseItem

            updateState {
                it.copy(
                    mediaDetails = details,
                    isFavorite = isFavorite,
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
        val current = currentState
        val details = current.mediaDetails?.baseItem ?: return

        if (current.isFavorite) {
            _favoritesManager.removeFavorite(details.url)
            updateState { it.copy(isFavorite = false) }
        } else {
            val targetTitle = details.titlePl.substringBefore(" - ").trim()
            val movieToSave = MovieItem(
                url = details.url,
                titlePl = targetTitle,
                posterUrl = details.posterUrl,
            )
            _favoritesManager.addFavorite(movieToSave)
            updateState { it.copy(isFavorite = true) }
        }
    }
}
