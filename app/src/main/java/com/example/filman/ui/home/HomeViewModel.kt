package com.example.filman.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filman.R
import com.example.filman.Route
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.model.MovieItem
import com.example.filman.data.model.ProgressItem
import com.example.filman.data.scraper.AuthException
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.components.FilmanOverlayMenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HomeEvent {
    data object LoadHomeData : HomeEvent
    data object LoadNextPageData : HomeEvent
    data class LoadSearchData(val query: String) : HomeEvent
    data class OpenMovieDetails(val url: String) : HomeEvent
    data class RemoveFromFavorites(val url: String) : HomeEvent
    data class AddToFavorites(val movie: MovieItem) : HomeEvent
    data class RemoveFromContinueWatching(val url: String) : HomeEvent

    data class OnPageSelected(val route: Route.Home) : HomeEvent
    data class OpenContextMenu(
        val title: String,
        val url: String,
        val posterUrl: String,
        val isInContinueWatching: Boolean,
    ) : HomeEvent

    data object CloseContextMenu : HomeEvent
}

@Immutable
internal data class OverlayMenuData(
    val title: String,
    val items: List<FilmanOverlayMenuItem>,
)

@Immutable
internal data class HomeState(
    val isLoading: Boolean = true,
    val isLoadingNextPage: Boolean = false,
    val route: Route.Home = Route.Home.Home,
    val featuredItems: List<MovieItem> = emptyList(),
    val progressItems: List<ProgressItem> = emptyList(),
    val favorites: List<MovieItem> = emptyList(),
    val movies: List<MovieItem> = emptyList(),
    val showSearchBar: Boolean = false,
    val showFavourites: Boolean = true,
    val showContinueWatching: Boolean = true,
    val currentPage: Int = 1,
    val overlayMenuData: OverlayMenuData? = null,
)

sealed interface HomeEffect {
    data object FocusFeaturedSection : HomeEffect
    data object NavigateToAuth : HomeEffect
    data class NavigateToDetails(val url: String) : HomeEffect
}

internal class HomeViewModel(
    private val scraper: FilmanScraper,
    private val favoritesManager: FavoritesManager,
    private val progressManager: ProgressManager,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _effect = Channel<HomeEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var currentLoadJob: Job? = null

    init {
        viewModelScope.launch {
            favoritesManager.favoritesFlow.collect { list ->
                _state.update { it.copy(favorites = list) }
            }
        }
        viewModelScope.launch {
            progressManager.progressItemsFlow.collect { list ->
                _state.update {
                    it.copy(progressItems = list.filter { p -> p.progressPercentage < 0.95f })
                }
            }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.LoadHomeData -> loadData(focusFeaturedSection = true)
            is HomeEvent.LoadNextPageData -> loadNextPageData()
            is HomeEvent.LoadSearchData -> loadSearchData(event.query)
            is HomeEvent.OpenMovieDetails -> _effect.trySend(HomeEffect.NavigateToDetails(event.url))
            is HomeEvent.RemoveFromFavorites -> favoritesManager.removeFavorite(event.url)
            is HomeEvent.AddToFavorites -> favoritesManager.addFavorite(event.movie)
            is HomeEvent.RemoveFromContinueWatching -> removeFromProgress(event.url)
            is HomeEvent.OnPageSelected -> loadData(event.route)

            is HomeEvent.OpenContextMenu -> _state.update {
                it.copy(overlayMenuData = createOverlayMenuData(event))
            }

            is HomeEvent.CloseContextMenu -> _state.update { it.copy(overlayMenuData = null) }
        }
    }

    private fun createOverlayMenuData(event: HomeEvent.OpenContextMenu) = OverlayMenuData(
        title = event.title,
        items = buildList {
            if (event.isInContinueWatching) {
                add(
                    FilmanOverlayMenuItem.Button(
                        label = R.string.remove_from_continue_watching,
                        onClick = {
                            onEvent(HomeEvent.RemoveFromContinueWatching(event.url))
                            onEvent(HomeEvent.CloseContextMenu)
                        },
                    ),
                )
            }

            if (favoritesManager.isFavorite(event.url)) {
                add(
                    FilmanOverlayMenuItem.Button(
                        label = R.string.remove_from_favorites,
                        onClick = {
                            onEvent(HomeEvent.RemoveFromFavorites(event.url))
                            onEvent(HomeEvent.CloseContextMenu)
                        },
                    ),
                )
            } else {
                add(
                    FilmanOverlayMenuItem.Button(
                        label = R.string.add_to_favorites,
                        onClick = {
                            onEvent(
                                HomeEvent.AddToFavorites(
                                    MovieItem(
                                        url = event.url,
                                        titlePl = event.title,
                                        posterUrl = event.posterUrl,
                                    ),
                                ),
                            )
                            onEvent(HomeEvent.CloseContextMenu)
                        },
                    ),
                )
            }
        },
    )

    private fun loadData(
        route: Route.Home = Route.Home.Home,
        focusFeaturedSection: Boolean = false,
    ) {
        if (_state.value.route == route && _state.value.movies.isNotEmpty()) return

        if (route == Route.Home.Search) {
            _state.update {
                it.copy(
                    route = route,
                    showSearchBar = true,
                    showFavourites = false,
                    showContinueWatching = false,
                    featuredItems = emptyList(),
                    movies = emptyList(),
                )
            }

            return
        }

        _state.update {
            it.copy(
                isLoading = true,
                route = route,
            )
        }

        currentLoadJob?.cancel()
        currentLoadJob = launchHandled(
            onError = { t ->
                handleError(t)
                _state.update { it.copy(isLoading = false) }
            },
        ) {
            val featured = scraper.getFeaturedItems(route.path)
            val isHome = route == Route.Home.Home
            val movies = if (isHome) {
                scraper.getHomeMovies()
            } else {
                scraper.getCategoryMovies(route.path)
            }
            _state.update {
                it.copy(
                    featuredItems = featured,
                    movies = movies,
                    showSearchBar = false,
                    showFavourites = isHome,
                    showContinueWatching = isHome,
                    currentPage = 1,
                    isLoading = false,
                )
            }

            if (focusFeaturedSection) {
                _effect.send(HomeEffect.FocusFeaturedSection)
            }
        }
    }

    private fun loadNextPageData() {
        if (_state.value.route in listOf(Route.Home.Home, Route.Home.Search)) return

        _state.update { it.copy(isLoadingNextPage = true) }

        currentLoadJob?.cancel()
        currentLoadJob = launchHandled(
            onError = { t ->
                handleError(t)
                _state.update { it.copy(isLoadingNextPage = false) }
            },
        ) {
            val movies = scraper.getCategoryMovies(
                path = _state.value.route.path,
                page = _state.value.currentPage + 1,
            )
            _state.update {
                it.copy(
                    movies = it.movies + movies,
                    currentPage = it.currentPage + 1,
                    isLoadingNextPage = false,
                )
            }
        }
    }

    private fun loadSearchData(query: String) {
        _state.update { it.copy(isLoadingNextPage = true) }

        currentLoadJob?.cancel()
        currentLoadJob = launchHandled(
            onError = { t ->
                handleError(t)
                _state.update { it.copy(isLoadingNextPage = false) }
            },
        ) {
            _state.update {
                it.copy(
                    movies = scraper.searchMovies(query).distinctBy { m -> m.url },
                    isLoadingNextPage = false,
                )
            }
        }
    }

    private fun removeFromProgress(url: String) {
        val item = progressManager.getProgressForUrl(url)
        if (item != null) {
            progressManager.saveProgress(item.copy(durationMs = 0L))
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
            _effect.trySend(HomeEffect.NavigateToAuth)
        }
    }
}
