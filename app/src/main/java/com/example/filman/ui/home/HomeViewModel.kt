package com.example.filman.ui.home

import android.webkit.CookieManager
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filman.R
import com.example.filman.Route
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.local.SessionManager
import com.example.filman.data.model.FeaturedItem
import com.example.filman.data.model.Movie
import com.example.filman.data.model.ProgressItem
import com.example.filman.data.scraper.AuthException
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.components.FilmanOverlayMenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HomeEvent {
    data object LoadHomeData : HomeEvent
    data class LoadNextPage(val tabIndex: Int) : HomeEvent
    data class OnSearchQueryChanged(val query: String) : HomeEvent
    data object OnSearchSubmit : HomeEvent
    data class OnSearchVisibleChanged(val isVisible: Boolean) : HomeEvent
    data class OnTabSelected(val index: Int) : HomeEvent
    data object OnLogoutClick : HomeEvent
    data class OnMovieClick(val url: String) : HomeEvent
    data class RemoveFromFavorites(val url: String) : HomeEvent
    data class AddToFavorites(val movie: Movie) : HomeEvent
    data class RemoveFromProgress(val url: String) : HomeEvent
    data class UpdateFilter(val tabIndex: Int, val filterState: FilterState) : HomeEvent
    data class ClearFilters(val tabIndex: Int) : HomeEvent

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
data class CategoryState(
    val page: Int = 1,
    val items: List<Movie> = emptyList(),
    val isLoading: Boolean = false,
    val filterState: FilterState = FilterState(),
    val filters: com.example.filman.data.model.FilterData? = null,
    val featuredItems: List<FeaturedItem> = emptyList(),
)

@Immutable
internal data class HomeState(
    val isLoading: Boolean = true,
    val route: Route.Home = Route.Home.Home,
    val featuredItems: List<FeaturedItem> = emptyList(),
    val homeMovies: List<Movie> = emptyList(),
    val favorites: List<Movie> = emptyList(),
    val progressItems: List<ProgressItem> = emptyList(),
    val searchResults: List<Movie>? = null,
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
    val selectedTabIndex: Int = 0,
    val error: String? = null,
    val movies: CategoryState = CategoryState(),
    val series: CategoryState = CategoryState(),
    val kids: CategoryState = CategoryState(),
    val overlayMenuData: OverlayMenuData? = null,
) {
    // Compatibility properties for UI
    val moviesPage get() = movies.page
    val moviesList get() = movies.items
    val isMoviesLoading get() = movies.isLoading
    val moviesFilterState get() = movies.filterState
    val moviesFeaturedItems get() = movies.featuredItems
    val moviesFilters get() = movies.filters

    val seriesPage get() = series.page
    val seriesList get() = series.items
    val isSeriesLoading get() = series.isLoading
    val seriesFilterState get() = series.filterState
    val seriesFeaturedItems get() = series.featuredItems
    val seriesFilters get() = series.filters

    val kidsPage get() = kids.page
    val kidsList get() = kids.items
    val isKidsLoading get() = kids.isLoading
}

@Immutable
data class FilterState(
    val versions: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val qualities: Set<String> = emptySet(),
    val years: Set<String> = emptySet(),
    val sort: String? = null,
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
    private val sessionManager: SessionManager,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        HomeState(
            selectedTabIndex = savedStateHandle["selectedTabIndex"] ?: 0,
            searchQuery = savedStateHandle["searchQuery"] ?: "",
            isSearchVisible = savedStateHandle["isSearchVisible"] ?: false,
        ),
    )
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _effect = Channel<HomeEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

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
            HomeEvent.LoadHomeData -> loadHomeData()
            is HomeEvent.LoadNextPage -> loadNextPage(event.tabIndex)
            is HomeEvent.OnSearchQueryChanged -> {
                savedStateHandle["searchQuery"] = event.query
                _state.update { it.copy(searchQuery = event.query) }
            }

            HomeEvent.OnSearchSubmit -> performSearch()
            is HomeEvent.OnSearchVisibleChanged -> toggleSearch(event.isVisible)
            is HomeEvent.OnTabSelected -> selectTab(event.index)
            HomeEvent.OnLogoutClick -> logout()
            is HomeEvent.OnMovieClick -> _effect.trySend(HomeEffect.NavigateToDetails(event.url))
            is HomeEvent.RemoveFromFavorites -> favoritesManager.removeFavorite(event.url)
            is HomeEvent.AddToFavorites -> favoritesManager.addFavorite(event.movie)
            is HomeEvent.RemoveFromProgress -> removeFromProgress(event.url)
            is HomeEvent.UpdateFilter -> applyFilter(event.tabIndex, event.filterState)
            is HomeEvent.ClearFilters -> applyFilter(event.tabIndex, FilterState())

            is HomeEvent.OnPageSelected -> _state.update { it.copy(route = event.route) }
            is HomeEvent.OpenContextMenu -> _state.update {
                it.copy(overlayMenuData = createOverlayMenuData(event))
            }

            is HomeEvent.CloseContextMenu -> _state.update { it.copy(overlayMenuData = null) }
        }
    }

    private fun createOverlayMenuData(
        event: HomeEvent.OpenContextMenu,
    ): OverlayMenuData {
        return OverlayMenuData(
            title = event.title,
            items = buildList {
                if (event.isInContinueWatching) {
                    add(
                        FilmanOverlayMenuItem.Button(
                            label = R.string.remove_from_continue_watching,
                            onClick = { onEvent(HomeEvent.RemoveFromProgress(event.url)) },
                        ),
                    )
                }

                if (favoritesManager.isFavorite(event.url)) {
                    add(
                        FilmanOverlayMenuItem.Button(
                            label = R.string.remove_from_favorites,
                            onClick = { onEvent(HomeEvent.RemoveFromFavorites(event.url)) },
                        ),
                    )
                } else {
                    add(
                        FilmanOverlayMenuItem.Button(
                            label = R.string.add_to_favorites,
                            onClick = {
                                onEvent(
                                    HomeEvent.AddToFavorites(
                                        Movie(
                                            url = event.url,
                                            titlePl = event.title,
                                            posterUrl = event.posterUrl,
                                        ),
                                    ),
                                )
                            },
                        ),
                    )
                }
            },
        )
    }

    private fun toggleSearch(isVisible: Boolean) {
        savedStateHandle["isSearchVisible"] = isVisible
        _state.update {
            it.copy(
                isSearchVisible = isVisible,
                searchQuery = if (!isVisible) {
                    savedStateHandle["searchQuery"] = ""
                    ""
                } else {
                    it.searchQuery
                },
                searchResults = if (!isVisible) null else it.searchResults,
            )
        }
    }

    private fun selectTab(index: Int) {
        savedStateHandle["selectedTabIndex"] = index
        savedStateHandle["isSearchVisible"] = false
        _state.update {
            it.validateCaches().copy(
                selectedTabIndex = index,
                searchResults = null,
                isSearchVisible = false,
            )
        }
        triggerInitialLoad(index)
    }

    private fun loadHomeData() {
        if (_state.value.homeMovies.isNotEmpty()) return
        _state.update { it.copy(isLoading = true, error = null) }
        launchHandled(
            onError = { t ->
                handleError(t, "Unknown error")
                _state.update { it.copy(isLoading = false) }
            },
        ) {
            val featured = scraper.getFeaturedItems()
            val movies = scraper.getHomeMovies()
            _state.update {
                it.copy(
                    featuredItems = featured,
                    homeMovies = movies,
                    isLoading = false,
                )
            }
            _effect.send(HomeEffect.FocusFeaturedSection)
        }
    }

    private fun loadNextPage(tabIndex: Int) {
        val path = getPath(tabIndex) ?: return
        val current = _state.value.getCategory(tabIndex)
        if (current.isLoading) return

        launchHandled(
            onError = { t ->
                updateCategoryState(tabIndex) { copy(isLoading = false) }
                handleError(t, "Failed to load")
            },
        ) {
            updateCategoryState(tabIndex) { copy(isLoading = true) }

            val filters = current.filters ?: if (tabIndex != 3) scraper.getFilters(path) else null
            val filterToUse = if (tabIndex == 3) FilterState() else current.filterState
            val newItems = scraper.getCategoryMovies(path, current.page, filterToUse)
            val featured = if (current.page == 1 && current.filterState == FilterState()) {
                scraper.getFeaturedItems(path)
            } else {
                current.featuredItems
            }

            updateCategoryState(tabIndex) {
                copy(
                    items = (items + newItems).distinctBy { it.url },
                    page = page + 1,
                    filters = filters ?: this.filters,
                    isLoading = false,
                    featuredItems = featured,
                )
            }
        }
    }

    private fun performSearch() {
        launchHandled {
            val query = _state.value.searchQuery
            val results = if (query.isNotBlank()) scraper.searchMovies(query) else null
            _state.update { it.copy(searchResults = results) }
        }
    }

    private fun logout() {
        sessionManager.clearCookie()
        CookieManager.getInstance().removeAllCookies(null)
        _effect.trySend(HomeEffect.NavigateToAuth)
    }

    private fun removeFromProgress(url: String) {
        val item = progressManager.getProgressForUrl(url)
        if (item != null) {
            progressManager.saveProgress(item.copy(durationMs = 0L))
        }
    }

    private fun applyFilter(tabIndex: Int, filterState: FilterState) {
        updateCategoryState(tabIndex) {
            copy(filterState = filterState, items = emptyList(), page = 1)
        }
        loadNextPage(tabIndex)
    }

    private fun triggerInitialLoad(tabIndex: Int) {
        val current = _state.value
        val isEmpty = when (tabIndex) {
            1 -> current.movies.items.isEmpty()
            2 -> current.series.items.isEmpty()
            3 -> current.kids.items.isEmpty()
            else -> false
        }
        if (isEmpty) loadNextPage(tabIndex)
    }

    private fun updateCategoryState(tabIndex: Int, reducer: CategoryState.() -> CategoryState) {
        _state.update { it.updateCategory(tabIndex, reducer) }
    }

    private fun launchHandled(
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit,
    ) = viewModelScope.launch {
        runCatching { block() }.onFailure { t ->
            onError?.invoke(t) ?: handleError(t, "Operation failed")
        }
    }

    private fun handleError(t: Throwable, defaultMessage: String) {
        if (t is AuthException) {
            _effect.trySend(HomeEffect.NavigateToAuth)
        } else {
            _state.update { it.copy(error = t.localizedMessage ?: defaultMessage) }
        }
    }

    private fun getPath(tabIndex: Int) = when (tabIndex) {
        1 -> "/filmy/"
        2 -> "/seriale/"
        3 -> "/dla-dzieci-pl/"
        else -> null
    }

    private fun HomeState.getCategory(tabIndex: Int) = when (tabIndex) {
        1 -> movies
        2 -> series
        3 -> kids
        else -> CategoryState()
    }

    private fun HomeState.updateCategory(
        tabIndex: Int,
        reducer: CategoryState.() -> CategoryState,
    ) = when (tabIndex) {
        1 -> copy(movies = movies.reducer())
        2 -> copy(series = series.reducer())
        3 -> copy(kids = kids.reducer())
        else -> this
    }

    private fun HomeState.validateCaches(): HomeState {
        var newState = this
        if (movies.items.any { it.url.contains("/s/") }) {
            newState = newState.copy(movies = movies.copy(items = emptyList(), page = 1))
        }
        if (series.items.any { !it.url.contains("/s/") }) {
            newState = newState.copy(series = series.copy(items = emptyList(), page = 1))
        }
        return newState
    }
}
