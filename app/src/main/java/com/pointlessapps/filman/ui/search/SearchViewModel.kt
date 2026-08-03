package com.pointlessapps.filman.ui.search

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.R
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.SearchHistoryManager
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.PageResult
import com.pointlessapps.filman.data.model.SearchResults
import com.pointlessapps.filman.data.scraper.FilmanScraper
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.base.loadMoreMoviesForSection
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.components.OverlayMenuData
import com.pointlessapps.filman.ui.components.sections.MoviesSection
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

internal sealed interface SearchEvent : FilmanEvent {
    data object RetrySearch : SearchEvent
    data object LoadHomeData : SearchEvent
    data class LoadSearchData(val query: String) : SearchEvent
    data class LoadSearchDataByCategory(val category: FilterOption) : SearchEvent
    data object ClearSearch : SearchEvent
    data class LoadMoreForSection(val sectionTitle: Int) : SearchEvent
    data class OpenSearchHistoryContextMenu(val query: String) : SearchEvent
    data class RemoveSearchHistory(val query: String) : SearchEvent
    data object ClearAllSearchHistory : SearchEvent
}

@Immutable
internal data class SearchState(
    override val shared: SharedState = SharedState(),
    val categories: List<FilterOption> = emptyList(),
    val selectedCategory: FilterOption? = null,
    val query: String = "",
    val searchHistory: List<String> = emptyList(),
) : StateWithShared<SearchState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

internal sealed interface SearchEffect {
    data object ScrollToTop : SearchEffect
    data object NavigateToAuth : SearchEffect
    data class NavigateToDetails(val url: String) : SearchEffect
    data class FocusHistoryItem(val query: String?) : SearchEffect
    data object FocusSearchResults : SearchEffect
}

internal class SearchViewModel(
    private val scraper: FilmanScraper,
    favoritesManager: FavoritesManager,
    private val searchHistoryManager: SearchHistoryManager,
) : BaseViewModel<SearchState, SearchEvent, SearchEffect>(
    initialState = SearchState(),
    favoritesManager = favoritesManager,
) {

    private var currentLoadJob: Job? = null

    init {
        launchHandled {
            searchHistoryManager.historyFlow.collect { history ->
                updateState { it.copy(searchHistory = history) }
            }
        }
    }

    override fun getAuthErrorEffect(): SearchEffect = SearchEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(
        url: String,
        autoplay: Boolean,
        episodeUrl: String?,
    ): SearchEffect = SearchEffect.NavigateToDetails(url)

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
            is SearchEvent.OpenSearchHistoryContextMenu -> {
                val menuData = OverlayMenuData(
                    title = TextValue.DynamicString(event.query),
                    items = listOf(
                        FilmanOverlayMenuItem.Button(
                            label = TextValue.StringResource(R.string.search_remove_from_history),
                            onClick = { onEvent(SearchEvent.RemoveSearchHistory(event.query)) },
                        ),
                    ),
                )
                updateSharedState { it.copy(overlayMenuData = menuData) }
            }

            is SearchEvent.RemoveSearchHistory -> {
                val currentHistory = currentState.searchHistory
                val index = currentHistory.indexOf(event.query)
                val nextFocus = if (index > 0) {
                    currentHistory[index - 1]
                } else {
                    currentHistory.getOrNull(index + 1)
                }

                searchHistoryManager.removeSearchQuery(event.query)
                onEvent(BaseEvent.CloseContextMenu)
                sendEffect(SearchEffect.FocusHistoryItem(nextFocus))
            }

            is SearchEvent.ClearAllSearchHistory -> {
                searchHistoryManager.clearAll()
                onEvent(BaseEvent.CloseContextMenu)
                sendEffect(SearchEffect.FocusHistoryItem(null))
            }
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
                    staleData.path.startsWith(
                        FilmanConfig.PATH_MOVIES_CATEGORY,
                    ) -> R.string.search_results_movies

                    staleData.path.startsWith(
                        FilmanConfig.PATH_TV_SHOWS_CATEGORY,
                    ) -> R.string.search_results_tv_shows

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

        launchHandled {
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

            launchHandled {
                val categories = scraper.getCategories()
                updateState { it.copy(categories = categories) }
            }

            return
        }

        searchHistoryManager.addSearchQuery(query)

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
            val moviesPath = "${FilmanConfig.PATH_MOVIES_CATEGORY}${category.id}"
            val seriesPath = "${FilmanConfig.PATH_TV_SHOWS_CATEGORY}${category.id}"
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
            sendEffect(SearchEffect.FocusSearchResults)
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
                    errorMessage = null,
                ),
            )
        }
        sendEffect(SearchEffect.FocusHistoryItem(null))
    }
}
