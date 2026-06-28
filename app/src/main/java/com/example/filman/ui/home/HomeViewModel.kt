package com.example.filman.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
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
    data class OnTabSelected(val index: Int) : HomeEvent
    data object OnLogoutClick : HomeEvent
    data class OnMovieClick(val url: String) : HomeEvent
}

data class HomeState(
    val isLoading: Boolean = true,
    val homeMovies: List<Movie> = emptyList(),
    val favorites: List<Movie> = emptyList(),
    val progressItems: List<ProgressItem> = emptyList(),
    val searchResults: List<Movie>? = null,
    val searchQuery: String = "",
    val selectedTabIndex: Int = 0,

    val moviesPage: Int = 1,
    val moviesList: List<Movie> = emptyList(),
    val isMoviesLoading: Boolean = false,

    val seriesPage: Int = 1,
    val seriesList: List<Movie> = emptyList(),
    val isSeriesLoading: Boolean = false,

    val kidsPage: Int = 1,
    val kidsList: List<Movie> = emptyList(),
    val isKidsLoading: Boolean = false,
)

sealed interface HomeEffect {
    data object NavigateToAuth : HomeEffect
    data class NavigateToDetails(val url: String) : HomeEffect
}

class HomeViewModel(
    private val scraper: FilmanScraper,
    private val favoritesManager: FavoritesManager,
    private val progressManager: ProgressManager,
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
            is HomeEvent.OnTabSelected -> {
                _state.update { it.copy(selectedTabIndex = event.index) }
                // Trigger initial load if empty
                val current = _state.value
                when (event.index) {
                    1 -> if (current.moviesList.isEmpty()) loadNextPage(1)
                    2 -> if (current.seriesList.isEmpty()) loadNextPage(2)
                    3 -> if (current.kidsList.isEmpty()) loadNextPage(3)
                }
            }

            HomeEvent.OnLogoutClick -> {
                _effect.trySend(HomeEffect.NavigateToAuth)
            }

            is HomeEvent.OnMovieClick -> {
                _effect.trySend(HomeEffect.NavigateToDetails(event.url))
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

            try {
                val movies = scraper.getHomeMovies()
                _state.update { it.copy(homeMovies = movies, isLoading = false) }
            } catch (e: AuthException) {
                _effect.trySend(HomeEffect.NavigateToAuth)
            } catch (e: Exception) {
                e.printStackTrace()
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadNextPage(tabIndex: Int) {
        viewModelScope.launch {
            try {
                when (tabIndex) {
                    1 -> {
                        val current = _state.value
                        if (current.isMoviesLoading) return@launch
                        _state.update { it.copy(isMoviesLoading = true) }
                        val newMovies = scraper.getCategoryMovies("/filmy/", current.moviesPage)
                        _state.update {
                            it.copy(
                                moviesList = it.moviesList + newMovies,
                                moviesPage = it.moviesPage + 1,
                                isMoviesLoading = false,
                            )
                        }
                    }

                    2 -> {
                        val current = _state.value
                        if (current.isSeriesLoading) return@launch
                        _state.update { it.copy(isSeriesLoading = true) }
                        val newSeries = scraper.getCategoryMovies("/seriale/", current.seriesPage)
                        _state.update {
                            it.copy(
                                seriesList = it.seriesList + newSeries,
                                seriesPage = it.seriesPage + 1,
                                isSeriesLoading = false,
                            )
                        }
                    }

                    3 -> {
                        val current = _state.value
                        if (current.isKidsLoading) return@launch
                        _state.update { it.copy(isKidsLoading = true) }
                        val newKids = scraper.getCategoryMovies("/dla-dzieci-pl/", current.kidsPage)
                        _state.update {
                            it.copy(
                                kidsList = it.kidsList + newKids,
                                kidsPage = it.kidsPage + 1,
                                isKidsLoading = false,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is AuthException) _effect.trySend(HomeEffect.NavigateToAuth)
            }
        }
    }

    private fun performSearch() {
        viewModelScope.launch {
            try {
                val query = _state.value.searchQuery
                if (query.isNotBlank()) {
                    val results = scraper.searchMovies(query)
                    _state.update { it.copy(searchResults = results) }
                } else {
                    _state.update { it.copy(searchResults = null) }
                }
            } catch (e: AuthException) {
                _effect.trySend(HomeEffect.NavigateToAuth)
            }
        }
    }
}
