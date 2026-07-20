package com.example.filman.ui.details

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
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
import com.example.filman.data.scraper.AuthException
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.components.FilmanOverlayMenuItem
import com.example.filman.ui.components.OverlayMenuData
import com.example.filman.ui.components.sections.TabRowSectionItem
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
    data class OpenMovieDetails(val url: String) : MovieDetailsEvent
    data class PlayItem(val url: String) : MovieDetailsEvent
    data class TabChanged(val tab: TabRowSectionItem) : MovieDetailsEvent
    data class MarkAsWatched(val url: String, val parentUrl: String) : MovieDetailsEvent
    data class MarkAsNotWatched(val url: String) : MovieDetailsEvent

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
    private val favoritesManager: FavoritesManager,
    private val progressManager: ProgressManager,
) : ViewModel() {
    private val _state = MutableStateFlow(MovieDetailsState())
    val state: StateFlow<MovieDetailsState> = _state.asStateFlow()

    private val _effect = Channel<MovieDetailsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        viewModelScope.launch {
            progressManager.progressItemsFlow.collect { progressList ->
                _state.update { state ->
                    state.copy(
                        progressMap = progressList.associateBy { it.url },
                        progressList = progressList,
                    )
                }
            }
        }
    }

    fun onEvent(event: MovieDetailsEvent) {
        when (event) {
            is MovieDetailsEvent.LoadDetails -> loadDetails(event.url)
            is MovieDetailsEvent.OpenMovieDetails -> {
                _effect.trySend(MovieDetailsEffect.NavigateToDetails(event.url))
            }

            is MovieDetailsEvent.ToggleFavorite -> toggleFavorite()

            is MovieDetailsEvent.PlayItem -> {
                _effect.trySend(NavigateToPlayer(event.url))
            }

            is MovieDetailsEvent.TabChanged -> {
                _state.update { it.copy(selectedTabId = event.tab.id) }
            }

            is MovieDetailsEvent.CloseContextMenu -> {
                _state.update { it.copy(overlayMenuData = null) }
            }

            is MovieDetailsEvent.OpenContextMenu -> {
                _state.update { it.copy(overlayMenuData = createOverlayMenuData(event)) }
            }

            is MovieDetailsEvent.MarkAsWatched -> {
                progressManager.markAsWatched(event.url, event.parentUrl)
            }

            is MovieDetailsEvent.MarkAsNotWatched -> {
                progressManager.markAsNotWatched(event.url)
            }
        }
    }

    private fun createOverlayMenuData(event: MovieDetailsEvent.OpenContextMenu) = OverlayMenuData(
        title = event.title,
        items = buildList {
            if (progressManager.isWatched(event.url)) {
                add(
                    FilmanOverlayMenuItem.Button(
                        label = R.string.mark_as_not_watched,
                        onClick = {
                            onEvent(MovieDetailsEvent.MarkAsNotWatched(event.url))
                            onEvent(MovieDetailsEvent.CloseContextMenu)
                        },
                    ),
                )
            } else {
                add(
                    FilmanOverlayMenuItem.Button(
                        label = R.string.mark_as_watched,
                        onClick = {
                            onEvent(
                                MovieDetailsEvent.MarkAsWatched(
                                    url = event.url,
                                    parentUrl = state.value.mediaDetails?.baseItem?.url
                                        ?: event.url,
                                ),
                            )
                            onEvent(MovieDetailsEvent.CloseContextMenu)
                        },
                    ),
                )
            }
        },
    )

    private fun loadDetails(url: String) {
        launchHandled(
            onError = { t ->
                handleError(t)
                _state.update { it.copy(isLoading = false) }
            },
        ) {
            _state.update { it.copy(isLoading = true) }

            val details = scraper.getMediaDetails(url)
            val isFavorite = favoritesManager.isFavorite(url)
            val baseItem = details?.baseItem

            _state.update {
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
        val current = _state.value
        val details = current.mediaDetails?.baseItem ?: return

        if (current.isFavorite) {
            favoritesManager.removeFavorite(details.url)
            _state.update { it.copy(isFavorite = false) }
        } else {
            val targetTitle = details.titlePl.substringBefore(" - ").trim()
            val movieToSave = MovieItem(
                url = details.url,
                titlePl = targetTitle,
                posterUrl = details.posterUrl,
            )
            favoritesManager.addFavorite(movieToSave)
            _state.update { it.copy(isFavorite = true) }
        }
    }

    private fun launchHandled(
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit,
    ) = viewModelScope.launch {
        runCatching { block() }.onFailure { t ->
            onError?.invoke(t) ?: handleError(t)
        }
    }

    private fun handleError(t: Throwable) {
        if (t is AuthException) {
            _effect.trySend(MovieDetailsEffect.NavigateToAuth)
        }
    }
}
