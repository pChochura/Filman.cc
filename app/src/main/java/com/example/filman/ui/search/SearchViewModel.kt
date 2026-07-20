package com.example.filman.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.example.filman.R
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.model.FilterOption
import com.example.filman.data.model.MovieItem
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.base.loadMoreMoviesForSection
import com.example.filman.ui.components.OverlayMenuData
import com.example.filman.ui.components.sections.MoviesSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

internal sealed interface SearchEvent : FilmanEvent {
    data object RetrySearch : SearchEvent
    data object LoadHomeData : SearchEvent
    data class LoadSearchData(val query: String) : SearchEvent
    data class LoadSearchDataByCategory(val category: FilterOption) : SearchEvent
    data object ClearSearch : SearchEvent
    data class LoadMoreForSection(val sectionTitle: Int) : SearchEvent
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
    favoritesManager: FavoritesManager,
) : BaseViewModel<SearchState, SearchEvent, SearchEffect>(
    initialState = SearchState(),
    favoritesManager = favoritesManager,
) {

    private var currentLoadJob: Job? = null

    override fun getAuthErrorEffect(): SearchEffect = SearchEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(url: String): SearchEffect = SearchEffect.NavigateToDetails(url)

    override fun setOverlayMenuData(data: OverlayMenuData?) {
        updateState { it.copy(overlayMenuData = data) }
    }

    override fun handleEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.RetrySearch -> currentState.selectedCategory?.let {
                loadSearchDataByCategory(it)
            } ?: loadSearchData(currentState.query)

            is SearchEvent.LoadHomeData -> loadData()
            is SearchEvent.LoadSearchData -> loadSearchData(event.query)
            is SearchEvent.LoadSearchDataByCategory -> loadSearchDataByCategory(event.category)
            is SearchEvent.ClearSearch -> clearSearch()
            is SearchEvent.LoadMoreForSection -> loadMoreForSection(event.sectionTitle)
        }
    }

    private fun loadData() {
        if (currentState.moviesSections.isNotEmpty()) return

        updateState {
            it.copy(
                selectedCategory = null,
                moviesSections = emptyList(),
                errorMessage = null,
                isLoading = false,
            )
        }

        viewModelScope.launch {
            val categories = scraper.getCategories()
            updateState { it.copy(categories = categories) }
        }
    }

    private fun loadSearchData(query: String) {
        if (query.isEmpty()) {
            updateState {
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
                updateState { it.copy(categories = categories) }
            }

            return
        }

        updateState {
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
                updateState { it.copy(isLoadingNextPage = false) }
                handleError(t)
            },
        ) {
            val results = scraper.searchMovies(query)
            if (results.errorMessage != null) {
                updateState {
                    it.copy(
                        isLoadingNextPage = false,
                        errorMessage = results.errorMessage,
                    )
                }
                return@launchHandled
            }

            updateState {
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
            sendEffect(SearchEffect.FocusFirstGridItem)
        }
    }

    private fun loadSearchDataByCategory(category: FilterOption) {
        updateState {
            it.copy(
                isLoadingNextPage = true,
                selectedCategory = category,
                errorMessage = null,
            )
        }
        sendEffect(SearchEffect.ScrollToTop)

        currentLoadJob?.cancel()
        currentLoadJob = launchHandled(
            onError = { t ->
                updateState {
                    it.copy(
                        isLoadingNextPage = false,
                        errorMessage = t.message ?: "Unknown error",
                    )
                }
                handleError(t)
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
                updateState {
                    it.copy(
                        isLoadingNextPage = false,
                        errorMessage = moviesResult.errorMessage ?: tvShowsResult.errorMessage,
                    )
                }
                return@launchHandled
            }

            val movies = moviesResult.movies
            val tvShows = tvShowsResult.movies

            updateState {
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
            sendEffect(SearchEffect.FocusFirstGridItem)
        }
    }

    private fun loadMoreForSection(sectionTitle: Int) {
        if (currentState.isLoadingNextPage) return
        updateState { it.copy(isLoadingNextPage = true) }

        launchHandled(
            onError = { t ->
                updateState { it.copy(isLoadingNextPage = false) }
                handleError(t)
            },
        ) {
            val updatedSections = scraper.loadMoreMoviesForSection(
                moviesSections = currentState.moviesSections,
                sectionTitle = sectionTitle
            )

            if (updatedSections != null) {
                updateState { state ->
                    state.copy(
                        moviesSections = updatedSections,
                        isLoadingNextPage = false,
                    )
                }
            } else {
                updateState { it.copy(isLoadingNextPage = false) }
            }
        }
    }

    private fun clearSearch() {
        currentLoadJob?.cancel()
        updateState {
            it.copy(
                moviesSections = emptyList(),
                selectedCategory = null,
                isLoadingNextPage = false,
            )
        }
    }
}
