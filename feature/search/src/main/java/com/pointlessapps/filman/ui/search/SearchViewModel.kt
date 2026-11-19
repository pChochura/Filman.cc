package com.pointlessapps.filman.ui.search

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SearchHistoryManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.DecadeOption
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaFilterState
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.loadMoreMoviesForSection
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.Job
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

    private val filterDelegate = SearchFilterDelegate(
        repository = repository,
        stateProvider = { currentState },
        updateState = ::updateState,
        sendEffect = ::sendEffect,
        onEvent = ::onEvent,
        launchLoadWork = { onError, block ->
            currentLoadJob?.cancel()
            currentLoadJob = launchHandled(
                onError = { t ->
                    onError(t)
                    handleError(t)
                },
                block = block,
            )
        },
        cancelLoadWork = { currentLoadJob?.cancel() },
        reloadQuery = { query -> loadSearchData(query, force = true) },
    )

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

    override fun handleBaseEvent(event: BaseEvent) {
        if (event is BaseEvent.CloseContextMenu) {
            filterDelegate.isFilterMenuOpen = false
        }
        super.handleBaseEvent(event)
    }

    override fun handleEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.RetrySearch -> retrySearch()
            is SearchEvent.LoadHomeData -> loadData()
            is SearchEvent.LoadSearchData -> loadSearchData(event.query, force = event.force)
            is SearchEvent.LoadSearchDataByCategory -> loadSearchDataByCategory(event.category, force = event.force)
            is SearchEvent.ClearSearch -> clearSearch()
            is SearchEvent.ClearQuery -> clearQuery()
            is SearchEvent.OpenFilterMenu -> filterDelegate.openFilterMenu()
            is SearchEvent.UpdateFilter -> filterDelegate.updateFilter(event.filterState)
            is SearchEvent.ToggleGenre ->
                filterDelegate.updateFilter(currentState.filterState.toggleGenre(event.genreId))
            is SearchEvent.SelectSort ->
                filterDelegate.updateFilter(currentState.filterState.copy(sortBy = event.sortOption))
            is SearchEvent.SelectMediaType ->
                filterDelegate.updateFilter(currentState.filterState.copy(mediaTypeFilter = event.mediaTypeFilter))
            is SearchEvent.ClearDecade ->
                filterDelegate.updateFilter(currentState.filterState.copy(decade = DecadeOption.ALL))
            is SearchEvent.ClearRating ->
                filterDelegate.updateFilter(currentState.filterState.copy(minRating = null))
            is SearchEvent.ResetFilters -> filterDelegate.resetFilters()
            is SearchEvent.LoadMoreForSection -> loadMoreForSection(event.sectionTitle)
            is SearchEvent.OpenSearchHistoryContextMenu -> {
                filterDelegate.isFilterMenuOpen = false
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

    private fun retrySearch() {
        when {
            currentState.query.isNotBlank() -> loadSearchData(currentState.query, force = true)
            currentState.filterState.isActive -> filterDelegate.loadDiscoverByFilter(currentState.filterState)
            else -> loadData()
        }
    }

    private fun loadData() {
        if (currentState.moviesSections.isNotEmpty() || currentState.filterState.isActive) return

        updateState {
            it.copy(
                selectedCategory = null,
                filterState = MediaFilterState(),
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
            if (currentState.filterState.isActive) {
                clearQuery()
                return
            }
            filterDelegate.clearRawCache()
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

        filterDelegate.clearRawCache()
        updateState {
            it.copy(
                query = query,
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
                        filterDelegate.rawSearchMovies = results.movies
                        filterDelegate.rawSearchTvShows = results.tvShows
                        val sections = buildFilteredSearchSections(
                            movies = results.movies,
                            tvShows = results.tvShows,
                            filterState = currentState.filterState,
                        )
                        updateSharedState {
                            it.copy(
                                errorMessage = null,
                                moviesSections = sections,
                            )
                        }
                    }
                }
        }
    }

    private fun loadSearchDataByCategory(category: FilterOption, force: Boolean = false) {
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

        val nextFilter = MediaFilterState(selectedGenreIds = listOf(genreId))
        if (!force &&
            nextFilter == currentState.filterState &&
            currentState.query.isBlank() &&
            currentState.shared.moviesSections.isNotEmpty()
        ) {
            return
        }

        filterDelegate.clearRawCache()
        filterDelegate.loadDiscoverByFilter(nextFilter, explicitCategory = category)
    }

    private fun clearQuery() {
        historySaveJob?.cancel()
        filterDelegate.clearRawCache()
        if (currentState.filterState.isActive) {
            filterDelegate.loadDiscoverByFilter(currentState.filterState)
        } else {
            clearSearch()
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
        historySaveJob?.cancel()
        filterDelegate.isFilterMenuOpen = false
        filterDelegate.clearRawCache()
        updateState {
            it.copy(
                query = "",
                selectedCategory = null,
                filterState = MediaFilterState(),
                isSearching = false,
                shared = it.shared.copy(
                    moviesSections = emptyList(),
                    isLoadingNextPage = false,
                    errorMessage = null,
                    overlayMenuData = null,
                ),
            )
        }
        sendEffect(SearchEffect.FocusHistoryItem(null))
    }
}
