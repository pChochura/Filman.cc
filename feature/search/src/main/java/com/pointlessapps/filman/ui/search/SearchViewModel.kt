package com.pointlessapps.filman.ui.search

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SearchHistoryManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaType
import com.pointlessapps.filman.data.model.TmdbFeed
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.loadMoreMoviesForSection
import com.pointlessapps.filman.ui.components.sections.MoviesSection
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onCompletion
import kotlin.time.Duration.Companion.seconds

class SearchViewModel(
    private val repository: FilmanRepository,
    favoritesManager: FavoritesManager,
    watchlistManager: WatchlistManager,
    progressManager: ProgressManager,
    private val searchHistoryManager: SearchHistoryManager,
) : BaseViewModel<SearchState, SearchEvent, SearchEffect>(
    initialState = SearchState(),
    favoritesManager = favoritesManager,
    watchlistManager = watchlistManager,
    progressManager = progressManager,
) {
    private var currentLoadJob: Job? = null
    private var historySaveJob: Job? = null

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
        request: DetailsRequest?,
    ): SearchEffect = SearchEffect.NavigateToDetails(
        request ?: DetailsRequest.Url(url, isTvShow = url.startsWith("show_")),
    )

    override fun handleEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.RetrySearch -> {
                currentState.selectedCategory?.let {
                    loadSearchDataByCategory(it, force = true)
                } ?: loadSearchData(currentState.query, force = true)
            }

            is SearchEvent.LoadHomeData -> loadData()
            is SearchEvent.LoadSearchData -> loadSearchData(event.query, force = event.force)
            is SearchEvent.LoadSearchDataByCategory -> loadSearchDataByCategory(event.category, force = event.force)
            is SearchEvent.ClearSearch -> clearSearch()
            is SearchEvent.LoadMoreForSection -> loadMoreForSection(event.sectionTitle)
            is SearchEvent.OpenSearchHistoryContextMenu -> {
                val menuData = buildSearchHistoryContextMenu(event.query) {
                    onEvent(SearchEvent.RemoveSearchHistory(event.query))
                }
                updateSharedState { it.copy(overlayMenuData = menuData) }
            }

            is SearchEvent.RemoveSearchHistory -> {
                val nextFocus = currentState.searchHistory.nextFocusedQueryAfterRemoving(event.query)
                searchHistoryManager.removeSearchQuery(event.query)
                onEvent(BaseEvent.CloseContextMenu)
                sendEffect(SearchEffect.FocusHistoryItem(nextFocus))
            }

            is SearchEvent.ClearAllSearchHistory -> {
                searchHistoryManager.clearAll()
                onEvent(BaseEvent.CloseContextMenu)
                sendEffect(SearchEffect.FocusHistoryItem(null))
            }

            is SearchEvent.RequestAuth -> sendEffect(SearchEffect.NavigateToAuth)
        }
    }

    private fun loadData() {
        if (currentState.moviesSections.isNotEmpty() || currentState.selectedCategory != null) return

        updateState {
            it.copy(
                selectedCategory = null,
                shared = it.shared.copy(moviesSections = emptyList(), errorMessage = null, isLoading = false),
            )
        }
        refreshCategories()
    }

    private fun refreshCategories() {
        launchHandled {
            val categories = repository.getCategories()
            updateState { it.copy(categories = categories) }
        }
    }

    private fun loadSearchData(query: String, force: Boolean = false) {
        if (!force && query == currentState.query && currentState.shared.moviesSections.isNotEmpty()) {
            return
        }

        if (query.isEmpty()) {
            if (currentState.selectedCategory != null) return
            updateState {
                it.copy(
                    query = query,
                    selectedCategory = null,
                    shared = it.shared.copy(
                        moviesSections = emptyList(),
                        errorMessage = null,
                        showAuthError = false,
                        isLoading = false,
                    ),
                )
            }
            refreshCategories()
            return
        }

        historySaveJob?.cancel()
        historySaveJob = launchHandled {
            delay(10.seconds)
            searchHistoryManager.addSearchQuery(query)
        }

        updateState {
            it.copy(
                query = query,
                selectedCategory = null,
                isSearching = true,
                shared = it.shared.copy(
                    moviesSections = emptyList(),
                    errorMessage = null,
                    showAuthError = false,
                    isLoadingNextPage = true,
                ),
            )
        }

        currentLoadJob?.cancel()
        currentLoadJob = launchHandled(
            onError = { t ->
                updateState { it.copy(isSearching = false) }
                updateSharedState { it.copy(isLoadingNextPage = false) }
                handleError(t)
            },
        ) {
            repository.searchMovies(query)
                .onCompletion { error ->
                    updateState { it.copy(isSearching = false) }
                    updateSharedState { it.copy(isLoadingNextPage = false) }
                    if (error != null) throw error
                }.collect { results ->
                    if (results.errorMessage != null && results.movies.isEmpty() && results.tvShows.isEmpty()) {
                        updateSharedState {
                            it.copy(errorMessage = results.errorMessage?.let(TextValue::DynamicString))
                        }
                    } else {
                        val allItems = results.movies.map { it.copy(isTvShow = false) } +
                            results.tvShows.map { it.copy(isTvShow = true) }

                        updateSharedState {
                            it.copy(
                                errorMessage = null,
                                moviesSections = listOf(
                                    MoviesSection(
                                        title = R.string.common_results,
                                        movies = allItems.toSearchGridItems(),
                                    ),
                                ),
                            )
                        }
                    }
                }
        }
    }

    private fun loadSearchDataByCategory(category: FilterOption, force: Boolean = false) {
        if (!force && category == currentState.selectedCategory && currentState.shared.moviesSections.isNotEmpty()) {
            return
        }

        val genreId = category.id.toIntOrNull()
        if (genreId == null) {
            updateSharedState {
                it.copy(
                    isLoading = false,
                    isLoadingNextPage = false,
                    errorMessage = TextValue.StringResource(R.string.error_unknown),
                )
            }
            return
        }

        updateState {
            it.copy(
                selectedCategory = category,
                query = "",
                isSearching = true,
                shared = it.shared.copy(
                    isLoadingNextPage = true,
                    errorMessage = null,
                    showAuthError = false,
                ),
            )
        }
        sendEffect(SearchEffect.ScrollToTop)

        currentLoadJob?.cancel()
        currentLoadJob = launchHandled(
            onError = { t ->
                updateState { it.copy(isSearching = false) }
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        isLoadingNextPage = false,
                        errorMessage = t.message?.let(TextValue::DynamicString)
                            ?: TextValue.StringResource(R.string.error_unknown),
                    )
                }
                handleError(t)
            },
        ) {
            val moviesFeed = TmdbFeed.DiscoverByGenre(MediaType.MOVIE, genreId)
            val tvFeed = TmdbFeed.DiscoverByGenre(MediaType.TV, genreId)

            val moviesDeferred = async { repository.getFeedPage(moviesFeed) }
            val tvDeferred = async { repository.getFeedPage(tvFeed) }

            val moviesResult = moviesDeferred.await()
            val tvResult = tvDeferred.await()

            if (moviesResult.movies.isEmpty() && tvResult.movies.isEmpty() &&
                (moviesResult.errorMessage != null || tvResult.errorMessage != null)
            ) {
                val errorMsg = moviesResult.errorMessage ?: tvResult.errorMessage
                updateState { it.copy(isSearching = false) }
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        isLoadingNextPage = false,
                        errorMessage = errorMsg?.let(TextValue::DynamicString)
                            ?: TextValue.StringResource(R.string.error_unknown),
                    )
                }
                return@launchHandled
            }

            updateSharedState {
                it.copy(
                    moviesSections = buildList {
                        if (moviesResult.movies.isNotEmpty()) {
                            add(
                                MoviesSection(
                                    title = R.string.search_results_movies,
                                    movies = moviesResult.movies.toSearchGridItems(),
                                    feed = moviesFeed,
                                    page = moviesResult.page,
                                    hasMore = moviesResult.hasMore,
                                ),
                            )
                        }
                        if (tvResult.movies.isNotEmpty()) {
                            add(
                                MoviesSection(
                                    title = R.string.common_results_tv_shows,
                                    movies = tvResult.movies.toSearchGridItems(),
                                    feed = tvFeed,
                                    page = tvResult.page,
                                    hasMore = tvResult.hasMore,
                                ),
                            )
                        }
                    },
                    isLoadingNextPage = false,
                )
            }
            updateState { it.copy(isSearching = false) }
            if (currentState.moviesSections.any { it.movies.isNotEmpty() || it.hasMore }) {
                sendEffect(SearchEffect.FocusSearchResults)
            }
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
            val updatedSections = repository.loadMoreMoviesForSection(
                moviesSections = currentState.moviesSections,
                sectionTitle = sectionTitle,
                transform = { newMovies, oldItems ->
                    val mappedNewMovies = if (sectionTitle == R.string.common_results_tv_shows) {
                        newMovies.map { it.copy(isTvShow = true) }
                    } else {
                        newMovies
                    }
                    val oldMovies = oldItems.map { it.movieItem }
                    (oldMovies + mappedNewMovies).toSearchGridItems()
                },
            )

            updateSharedState { state ->
                if (updatedSections != null) {
                    state.copy(moviesSections = updatedSections, isLoadingNextPage = false)
                } else {
                    state.copy(isLoadingNextPage = false)
                }
            }
        }
    }

    private fun clearSearch() {
        currentLoadJob?.cancel()
        updateState {
            it.copy(
                selectedCategory = null,
                isSearching = false,
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
