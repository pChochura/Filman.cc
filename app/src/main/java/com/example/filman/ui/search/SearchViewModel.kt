package com.example.filman.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filman.R
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.model.FilterOption
import com.example.filman.data.model.MovieItem
import com.example.filman.data.scraper.AuthException
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.components.FilmanOverlayMenuItem
import com.example.filman.ui.components.OverlayMenuData
import com.example.filman.ui.components.sections.MoviesSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface SearchEvent {
    data object RetrySearch : SearchEvent
    data object LoadHomeData : SearchEvent
    data class LoadSearchData(val query: String) : SearchEvent
    data class LoadSearchDataByCategory(val category: FilterOption) : SearchEvent
    data object ClearSearch : SearchEvent
    data class LoadMoreForSection(val sectionTitle: Int) : SearchEvent
    data class OpenMovieDetails(val url: String) : SearchEvent
    data class RemoveFromFavorites(val url: String) : SearchEvent
    data class AddToFavorites(val movie: MovieItem) : SearchEvent

    data class OpenContextMenu(
        val title: String,
        val url: String,
        val posterUrl: String,
    ) : SearchEvent

    data object CloseContextMenu : SearchEvent
}

@Immutable
internal data class SearchState(
    val isLoading: Boolean = true,
    val isLoadingNextPage: Boolean = false,
    val moviesSections: List<MoviesSection> = emptyList(),
    val categories: List<FilterOption> = emptyList(),
    val selectedCategory: FilterOption? = null,
    val errorMessage: String? = null,
    val query: String = "",
    val overlayMenuData: OverlayMenuData? = null,
)

internal sealed interface SearchEffect {
    data object ScrollToTop : SearchEffect
    data object FocusFirstGridItem : SearchEffect
    data object NavigateToAuth : SearchEffect
    data class NavigateToDetails(val url: String) : SearchEffect
}

internal class SearchViewModel(
    private val scraper: FilmanScraper,
    private val favoritesManager: FavoritesManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _effect = Channel<SearchEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var currentLoadJob: Job? = null

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.RetrySearch -> _state.value.selectedCategory?.let {
                loadSearchDataByCategory(it)
            } ?: loadSearchData(_state.value.query)

            is SearchEvent.LoadHomeData -> loadData()
            is SearchEvent.LoadSearchData -> loadSearchData(event.query)
            is SearchEvent.LoadSearchDataByCategory -> loadSearchDataByCategory(event.category)
            is SearchEvent.ClearSearch -> clearSearch()
            is SearchEvent.LoadMoreForSection -> loadMoreForSection(event.sectionTitle)
            is SearchEvent.OpenMovieDetails -> _effect.trySend(SearchEffect.NavigateToDetails(event.url))
            is SearchEvent.RemoveFromFavorites -> favoritesManager.removeFavorite(event.url)
            is SearchEvent.AddToFavorites -> favoritesManager.addFavorite(event.movie)
            is SearchEvent.OpenContextMenu -> _state.update {
                it.copy(overlayMenuData = createOverlayMenuData(event))
            }

            is SearchEvent.CloseContextMenu -> _state.update { it.copy(overlayMenuData = null) }
        }
    }

    private fun createOverlayMenuData(event: SearchEvent.OpenContextMenu) = OverlayMenuData(
        title = event.title,
        items = buildList {
            if (favoritesManager.isFavorite(event.url)) {
                add(
                    FilmanOverlayMenuItem.Button(
                        label = R.string.remove_from_favorites,
                        onClick = {
                            onEvent(SearchEvent.RemoveFromFavorites(event.url))
                            onEvent(SearchEvent.CloseContextMenu)
                        },
                    ),
                )
            } else {
                add(
                    FilmanOverlayMenuItem.Button(
                        label = R.string.add_to_favorites,
                        onClick = {
                            onEvent(
                                SearchEvent.AddToFavorites(
                                    MovieItem(
                                        url = event.url,
                                        titlePl = event.title,
                                        posterUrl = event.posterUrl,
                                    ),
                                ),
                            )
                            onEvent(SearchEvent.CloseContextMenu)
                        },
                    ),
                )
            }
        },
    )

    private fun loadData() {
        if (_state.value.moviesSections.isNotEmpty()) return

        _state.update {
            it.copy(
                selectedCategory = null,
                moviesSections = emptyList(),
                errorMessage = null,
                isLoading = false,
            )
        }

        viewModelScope.launch {
            val categories = scraper.getCategories()
            _state.update { it.copy(categories = categories) }
        }
    }

    private fun loadSearchData(query: String) {
        if (query.isEmpty()) {
            _state.update {
                it.copy(
                    query = query,
                    selectedCategory = null,
                    moviesSections = emptyList(),
                    errorMessage = null,
                    isLoading = false,
                )
            }

            viewModelScope.launch {
                val categories = scraper.getCategories()
                _state.update { it.copy(categories = categories) }
            }

            return
        }

        _state.update {
            it.copy(
                query = query,
                errorMessage = null,
                isLoadingNextPage = true,
                selectedCategory = null,
            )
        }

        currentLoadJob?.cancel()
        currentLoadJob = launchHandled(
            onError = { t ->
                handleError(t)
                _state.update { it.copy(isLoadingNextPage = false) }
            },
        ) {
            val results = scraper.searchMovies(query)
            if (results.errorMessage != null) {
                _state.update {
                    it.copy(
                        isLoadingNextPage = false,
                        errorMessage = results.errorMessage,
                    )
                }
                return@launchHandled
            }

            _state.update {
                it.copy(
                    moviesSections = listOf(
                        MoviesSection(
                            title = R.string.search_results_movies,
                            movies = results.movies.distinctBy { m -> m.url },
                        ),
                        MoviesSection(
                            title = R.string.search_results_tv_shows,
                            movies = results.tvShows.distinctBy { m -> m.url },
                        ),
                    ),
                    isLoadingNextPage = false,
                )
            }
            _effect.trySend(SearchEffect.FocusFirstGridItem)
        }
    }

    private fun loadSearchDataByCategory(category: FilterOption) {
        _state.update {
            it.copy(
                isLoadingNextPage = true,
                selectedCategory = category,
                errorMessage = null,
            )
        }
        _effect.trySend(SearchEffect.ScrollToTop)

        currentLoadJob?.cancel()
        currentLoadJob = launchHandled(
            onError = { t ->
                handleError(t)
                _state.update {
                    it.copy(
                        isLoadingNextPage = false,
                        errorMessage = t.message ?: "Unknown error",
                    )
                }
            },
        ) {
            val moviesPath = "/filmy/category:${category.id}"
            val seriesPath = "/seriale/category:${category.id}"
            val moviesDeferred = async {
                scraper.getCategoryPage(path = moviesPath)
            }
            val seriesDeferred = async {
                scraper.getCategoryPage(path = seriesPath)
            }

            val (moviesResult, tvShowsResult) = awaitAll(moviesDeferred, seriesDeferred)

            if (moviesResult.errorMessage != null || tvShowsResult.errorMessage != null) {
                _state.update {
                    it.copy(
                        isLoadingNextPage = false,
                        errorMessage = moviesResult.errorMessage ?: tvShowsResult.errorMessage,
                    )
                }
                return@launchHandled
            }

            val movies = moviesResult.movies
            val tvShows = tvShowsResult.movies

            _state.update {
                it.copy(
                    moviesSections = buildList {
                        if (movies.isNotEmpty()) {
                            add(
                                MoviesSection(
                                    title = R.string.search_results_movies,
                                    movies = movies.distinctBy { m -> m.url },
                                    path = moviesPath,
                                    page = 1,
                                    hasMore = movies.size >= 20,
                                ),
                            )
                        }
                        if (tvShows.isNotEmpty()) {
                            add(
                                MoviesSection(
                                    title = R.string.search_results_tv_shows,
                                    movies = tvShows.distinctBy { m -> m.url },
                                    path = seriesPath,
                                    page = 1,
                                    hasMore = tvShows.size >= 20,
                                ),
                            )
                        }
                    },
                    isLoadingNextPage = false,
                )
            }
            _effect.trySend(SearchEffect.FocusFirstGridItem)
        }
    }

    private fun loadMoreForSection(sectionTitle: Int) {
        if (_state.value.isLoadingNextPage) return
        val section = _state.value.moviesSections.find { it.title == sectionTitle }
        if (section == null || section.path == null || !section.hasMore) return

        _state.update { it.copy(isLoadingNextPage = true) }

        launchHandled(
            onError = { t ->
                handleError(t)
                _state.update { it.copy(isLoadingNextPage = false) }
            },
        ) {
            val nextPage = section.page + 1
            val newMovies = scraper.getCategoryPage(path = section.path, page = nextPage).movies

            _state.update { state ->
                val updatedSections = state.moviesSections.map { s ->
                    if (s.title == sectionTitle) {
                        s.copy(
                            movies = (s.movies + newMovies).distinctBy { m -> m.url },
                            page = nextPage,
                            hasMore = newMovies.isNotEmpty(),
                        )
                    } else {
                        s
                    }
                }
                state.copy(
                    moviesSections = updatedSections,
                    isLoadingNextPage = false,
                )
            }
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

    private fun clearSearch() {
        currentLoadJob?.cancel()
        _state.update {
            it.copy(
                moviesSections = emptyList(),
                selectedCategory = null,
                isLoadingNextPage = false,
            )
        }
    }

    private fun handleError(t: Throwable) {
        if (t is AuthException) {
            _effect.trySend(SearchEffect.NavigateToAuth)
        }
    }
}
