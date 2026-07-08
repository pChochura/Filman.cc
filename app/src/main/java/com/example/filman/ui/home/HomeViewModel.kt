package com.example.filman.ui.home

import android.webkit.CookieManager
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.local.SessionManager
import com.example.filman.data.model.FeaturedItem
import com.example.filman.data.model.Movie
import com.example.filman.data.model.ProgressItem
import com.example.filman.data.scraper.AuthException
import com.example.filman.data.scraper.FilmanScraper
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
}

@Immutable
data class HomeState(
    val isLoading: Boolean = true,
    val featuredItems: List<FeaturedItem> = emptyList(),
    val homeMovies: List<Movie> = emptyList(),
    val favorites: List<Movie> = emptyList(),
    val progressItems: List<ProgressItem> = emptyList(),
    val searchResults: List<Movie>? = null,
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
    val selectedTabIndex: Int = 0,

    val moviesPage: Int = 1,
    val moviesList: List<Movie> = emptyList(),
    val isMoviesLoading: Boolean = false,
    val moviesFilterState: FilterState = FilterState(),
    val moviesFeaturedItems: List<FeaturedItem> = emptyList(),

    val seriesPage: Int = 1,
    val seriesList: List<Movie> = emptyList(),
    val isSeriesLoading: Boolean = false,
    val seriesFilterState: FilterState = FilterState(),
    val seriesFeaturedItems: List<FeaturedItem> = emptyList(),

    val kidsPage: Int = 1,
    val kidsList: List<Movie> = emptyList(),
    val isKidsLoading: Boolean = false,

    val moviesFilters: com.example.filman.data.model.FilterData? = null,
    val seriesFilters: com.example.filman.data.model.FilterData? = null,
)

@Immutable
data class FilterState(
    val versions: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val qualities: Set<String> = emptySet(),
    val years: Set<String> = emptySet(),
    val sort: String? = null,
)

sealed interface HomeEffect {
    data object NavigateToAuth : HomeEffect
    data class NavigateToDetails(val url: String) : HomeEffect
}

class HomeViewModel(
    private val scraper: FilmanScraper,
    private val favoritesManager: FavoritesManager,
    private val progressManager: ProgressManager,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _effect = Channel<HomeEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.LoadHomeData -> loadHomeData()
            is HomeEvent.LoadNextPage -> loadNextPage(event.tabIndex)
            is HomeEvent.OnSearchQueryChanged -> {
                _state.update { it.copy(searchQuery = event.query) }
            }

            HomeEvent.OnSearchSubmit -> performSearch()
            is HomeEvent.OnSearchVisibleChanged -> {
                _state.update { it.copy(isSearchVisible = event.isVisible) }
                if (!event.isVisible) {
                    _state.update { it.copy(searchQuery = "", searchResults = null) }
                }
            }

            is HomeEvent.OnTabSelected -> {
                // Check for invalid caches from previous versions of the app where the home page was loaded into categories
                var updatedMovies = _state.value.moviesList
                var updatedMoviesPage = _state.value.moviesPage
                if (updatedMovies.any { it.url.contains("/s/") }) {
                    updatedMovies = emptyList()
                    updatedMoviesPage = 1
                }

                var updatedSeries = _state.value.seriesList
                var updatedSeriesPage = _state.value.seriesPage
                if (updatedSeries.any { !it.url.contains("/s/") }) { // Series should have /s/
                    updatedSeries = emptyList()
                    updatedSeriesPage = 1
                }

                _state.update {
                    it.copy(
                        selectedTabIndex = event.index,
                        searchResults = null,
                        isSearchVisible = false,
                        moviesList = updatedMovies,
                        moviesPage = updatedMoviesPage,
                        seriesList = updatedSeries,
                        seriesPage = updatedSeriesPage,
                    )
                }
                // Trigger initial load if empty
                val current = _state.value
                when (event.index) {
                    1 -> if (current.moviesList.isEmpty()) loadNextPage(1)
                    2 -> if (current.seriesList.isEmpty()) loadNextPage(2)
                    3 -> if (current.kidsList.isEmpty()) loadNextPage(3)
                }
            }

            HomeEvent.OnLogoutClick -> {
                sessionManager.clearCookie()
                CookieManager.getInstance().removeAllCookies(null)
                _effect.trySend(HomeEffect.NavigateToAuth)
            }

            is HomeEvent.OnMovieClick -> {
                _effect.trySend(HomeEffect.NavigateToDetails(event.url))
            }

            is HomeEvent.RemoveFromFavorites -> {
                favoritesManager.removeFavorite(event.url)
                _state.update { it.copy(favorites = favoritesManager.getFavorites()) }
            }

            is HomeEvent.AddToFavorites -> {
                favoritesManager.addFavorite(event.movie)
                _state.update { it.copy(favorites = favoritesManager.getFavorites()) }
            }

            is HomeEvent.RemoveFromProgress -> {
                val item = progressManager.getProgressForUrl(event.url)
                if (item != null) {
                    progressManager.saveProgress(item.copy(durationMs = 0L))
                    _state.update {
                        it.copy(
                            progressItems = progressManager.getProgressItems()
                                .filter { p -> p.progressPercentage < 0.95f },
                        )
                    }
                }
            }

            is HomeEvent.UpdateFilter -> {
                _state.update {
                    when (event.tabIndex) {
                        1 -> it.copy(
                            moviesFilterState = event.filterState,
                            moviesList = emptyList(),
                            moviesPage = 1,
                        )

                        2 -> it.copy(
                            seriesFilterState = event.filterState,
                            seriesList = emptyList(),
                            seriesPage = 1,
                        )

                        else -> it
                    }
                }
                loadNextPage(event.tabIndex)
            }

            is HomeEvent.ClearFilters -> {
                _state.update {
                    when (event.tabIndex) {
                        1 -> it.copy(
                            moviesFilterState = FilterState(),
                            moviesList = emptyList(),
                            moviesPage = 1,
                        )

                        2 -> it.copy(
                            seriesFilterState = FilterState(),
                            seriesList = emptyList(),
                            seriesPage = 1,
                        )

                        else -> it
                    }
                }
                loadNextPage(event.tabIndex)
            }
        }
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    favorites = favoritesManager.getFavorites(),
                    progressItems = progressManager.getProgressItems()
                        .filter { p -> p.progressPercentage < 0.95f },
                )
            }

            runCatching {
                val featured = scraper.getFeaturedItems()
                val movies = scraper.getHomeMovies()
                _state.update {
                    it.copy(
                        featuredItems = featured,
                        homeMovies = movies,
                        isLoading = false,
                    )
                }
            }.onFailure {
                if (it is AuthException) _effect.trySend(HomeEffect.NavigateToAuth)
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun loadNextPage(tabIndex: Int) {
        viewModelScope.launch {
            runCatching {
                when (tabIndex) {
                    1 -> {
                        val current = _state.value
                        if (current.isMoviesLoading) return@launch
                        _state.update { it.copy(isMoviesLoading = true) }
                        val filters = current.moviesFilters ?: scraper.getFilters("/filmy/")
                        val newMovies = scraper.getCategoryMovies(
                            "/filmy/",
                            current.moviesPage,
                            current.moviesFilterState,
                        )
                        val featured = if (current.moviesPage == 1 && current.moviesFilterState == FilterState()) {
                            scraper.getFeaturedItems("/filmy/")
                        } else {
                            current.moviesFeaturedItems
                        }
                        _state.update {
                            it.copy(
                                moviesList = (it.moviesList + newMovies).distinctBy { m -> m.url },
                                moviesPage = it.moviesPage + 1,
                                moviesFilters = filters,
                                isMoviesLoading = false,
                                moviesFeaturedItems = featured,
                            )
                        }
                    }

                    2 -> {
                        val current = _state.value
                        if (current.isSeriesLoading) return@launch
                        _state.update { it.copy(isSeriesLoading = true) }
                        val filters = current.seriesFilters ?: scraper.getFilters("/seriale/")
                        val newSeries = scraper.getCategoryMovies(
                            "/seriale/",
                            current.seriesPage,
                            current.seriesFilterState,
                        )
                        val featured = if (current.seriesPage == 1 && current.seriesFilterState == FilterState()) {
                            scraper.getFeaturedItems("/seriale/")
                        } else {
                            current.seriesFeaturedItems
                        }
                        _state.update {
                            it.copy(
                                seriesList = (it.seriesList + newSeries).distinctBy { s -> s.url },
                                seriesPage = it.seriesPage + 1,
                                seriesFilters = filters,
                                isSeriesLoading = false,
                                seriesFeaturedItems = featured,
                            )
                        }
                    }

                    3 -> {
                        val current = _state.value
                        if (current.isKidsLoading) return@launch
                        _state.update { it.copy(isKidsLoading = true) }
                        val newKids = scraper.getCategoryMovies(
                            "/dla-dzieci-pl/",
                            current.kidsPage,
                            FilterState(),
                        )
                        _state.update {
                            it.copy(
                                kidsList = (it.kidsList + newKids).distinctBy { k -> k.url },
                                kidsPage = it.kidsPage + 1,
                                isKidsLoading = false,
                            )
                        }
                    }
                }
            }.onFailure {
                if (it is AuthException) _effect.trySend(HomeEffect.NavigateToAuth)
            }
        }
    }

    private fun performSearch() {
        viewModelScope.launch {
            runCatching {
                val query = _state.value.searchQuery
                if (query.isNotBlank()) {
                    val results = scraper.searchMovies(query)
                    _state.update { it.copy(searchResults = results) }
                } else {
                    _state.update { it.copy(searchResults = null) }
                }
            }.onFailure {
                if (it is AuthException) _effect.trySend(HomeEffect.NavigateToAuth)
            }
        }
    }
}
