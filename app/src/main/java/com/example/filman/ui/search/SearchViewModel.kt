package com.example.filman.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.example.filman.R
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.model.FilterOption
import com.example.filman.data.model.PageResult
import com.example.filman.data.model.SearchResults
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.base.SharedState
import com.example.filman.ui.base.StateWithShared
import com.example.filman.ui.base.loadMoreMoviesForSection
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
    override val shared: SharedState = SharedState(),
    val categories: List<FilterOption> = emptyList(),
    val selectedCategory: FilterOption? = null,
    val query: String = "",
) : StateWithShared<SearchState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

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

    override fun getNavigateToDetailsEffect(url: String): SearchEffect =
        SearchEffect.NavigateToDetails(url)

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

    override fun handleStaleData(staleData: Any) {
        when (staleData) {
            is SearchResults -> {
                updateSharedState {
                    it.copy(
                        moviesSections = listOf(
                            MoviesSection(
                                title = R.string.search_results_movies,
                                movies = staleData.movies.distinctBy { m -> m.url },
                            ),
                            MoviesSection(
                                title = R.string.search_results_tv_shows,
                                movies = staleData.tvShows.distinctBy { m -> m.url },
                            ),
                        ),
                    )
                }
            }

            is PageResult -> {
                val sectionTitle = when {
                    staleData.path.startsWith(MOVIES_PATH) -> R.string.search_results_movies
                    staleData.path.startsWith(TV_SHOWS_PATH) -> R.string.search_results_tv_shows

                    // Ignore mismatched url
                    else -> return
                }
                updateSharedState {
                    it.copy(
                        moviesSections = buildList {
                            if (staleData.movies.isNotEmpty()) {
                                add(
                                    MoviesSection(
                                        title = sectionTitle,
                                        movies = staleData.movies.distinctBy { m -> m.url },
                                        path = staleData.path,
                                        page = 1,
                                        hasMore = staleData.movies.size >= 20,
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    private fun loadData() {
        if (currentState.moviesSections.isNotEmpty()) return

        updateState {
            it.copy(
                selectedCategory = null,
                shared = it.shared.copy(
                    moviesSections = emptyList(),
                    errorMessage = null,
                    isLoading = false,
                ),
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
                    shared = it.shared.copy(
                        moviesSections = emptyList(),
                        errorMessage = null,
                        isLoading = false,
                    ),
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
                selectedCategory = null,
                shared = it.shared.copy(
                    errorMessage = null,
                    isLoadingNextPage = true,
                ),
            )
        }

        currentLoadJob?.cancel()
        currentLoadJob = launchHandled(
            onError = { t ->
                updateSharedState { it.copy(isLoadingNextPage = false) }
                handleError(t)
            },
        ) {
            val results = scraper.searchMovies(query)
            if (results.errorMessage != null) {
                updateSharedState {
                    it.copy(
                        isLoadingNextPage = false,
                        errorMessage = results.errorMessage,
                    )
                }
                return@launchHandled
            }

            updateSharedState {
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
                selectedCategory = category,
                shared = it.shared.copy(
                    isLoadingNextPage = true,
                    errorMessage = null,
                ),
            )
        }
        sendEffect(SearchEffect.ScrollToTop)

        currentLoadJob?.cancel()
        currentLoadJob = launchHandled(
            onError = { t ->
                updateSharedState {
                    it.copy(
                        isLoadingNextPage = false,
                        errorMessage = t.message ?: "Unknown error",
                    )
                }
                handleError(t)
            },
        ) {
            val moviesPath = "$MOVIES_PATH${category.id}"
            val seriesPath = "$TV_SHOWS_PATH${category.id}"
            val moviesDeferred = async {
                scraper.getCategoryPage(path = moviesPath)
            }
            val seriesDeferred = async {
                scraper.getCategoryPage(path = seriesPath)
            }

            val (moviesResult, tvShowsResult) = awaitAll(moviesDeferred, seriesDeferred)

            if (moviesResult.errorMessage != null || tvShowsResult.errorMessage != null) {
                updateSharedState {
                    it.copy(
                        isLoadingNextPage = false,
                        errorMessage = moviesResult.errorMessage ?: tvShowsResult.errorMessage,
                    )
                }
                return@launchHandled
            }

            val movies = moviesResult.movies
            val tvShows = tvShowsResult.movies

            updateSharedState {
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
        updateSharedState { it.copy(isLoadingNextPage = true) }

        launchHandled(
            onError = { t ->
                updateSharedState { it.copy(isLoadingNextPage = false) }
                handleError(t)
            },
        ) {
            val updatedSections = scraper.loadMoreMoviesForSection(
                moviesSections = currentState.moviesSections,
                sectionTitle = sectionTitle,
            )

            if (updatedSections != null) {
                updateSharedState { state ->
                    state.copy(
                        moviesSections = updatedSections,
                        isLoadingNextPage = false,
                    )
                }
            } else {
                updateSharedState { it.copy(isLoadingNextPage = false) }
            }
        }
    }

    private fun clearSearch() {
        currentLoadJob?.cancel()
        updateState {
            it.copy(
                selectedCategory = null,
                shared = it.shared.copy(
                    moviesSections = emptyList(),
                    isLoadingNextPage = false,
                ),
            )
        }
    }

    private companion object {
        const val MOVIES_PATH = "/filmy/category:"
        const val TV_SHOWS_PATH = "/seriale/category:"
    }
}
