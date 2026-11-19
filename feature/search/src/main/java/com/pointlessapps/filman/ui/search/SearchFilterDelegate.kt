package com.pointlessapps.filman.ui.search

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaFilterState
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.ui.components.FilterMenuBuilder
import com.pointlessapps.filman.ui.components.OverlayMenuData
import com.pointlessapps.filman.ui.components.sections.MoviesSection
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

internal class SearchFilterDelegate(
    private val repository: FilmanRepository,
    private val stateProvider: () -> SearchState,
    private val updateState: ((SearchState) -> SearchState) -> Unit,
    private val sendEffect: (SearchEffect) -> Unit,
    private val onEvent: (SearchEvent) -> Unit,
    private val launchLoadWork: (
        onError: (Throwable) -> Unit,
        block: suspend CoroutineScope.() -> Unit,
    ) -> Unit,
    private val cancelLoadWork: () -> Unit,
    private val reloadQuery: (String) -> Unit,
) {
    var rawSearchMovies: List<MovieItem> = emptyList()
    var rawSearchTvShows: List<MovieItem> = emptyList()
    var isFilterMenuOpen: Boolean = false

    fun clearRawCache() {
        rawSearchMovies = emptyList()
        rawSearchTvShows = emptyList()
    }

    fun openFilterMenu() {
        isFilterMenuOpen = true
        val state = stateProvider()
        val menuData = buildFilterOverlayMenu(state.filterState, state.categories)
        updateState { it.copy(shared = it.shared.copy(overlayMenuData = menuData)) }
    }

    fun resetFilters() {
        isFilterMenuOpen = false
        updateState { it.copy(shared = it.shared.copy(overlayMenuData = null)) }
        updateFilter(MediaFilterState())
    }

    fun updateFilter(newFilter: MediaFilterState) {
        val state = stateProvider()
        val matchingCategory = matchingCategoryFor(newFilter, state)
        val updatedMenu = if (isFilterMenuOpen && state.overlayMenuData != null) {
            buildFilterOverlayMenu(newFilter, state.categories)
        } else {
            state.overlayMenuData
        }

        if (state.query.isNotBlank()) {
            updateState {
                it.copy(
                    filterState = newFilter,
                    selectedCategory = matchingCategory,
                    shared = it.shared.copy(overlayMenuData = updatedMenu),
                )
            }
            if (rawSearchMovies.isNotEmpty() || rawSearchTvShows.isNotEmpty()) {
                val sections = buildFilteredSearchSections(rawSearchMovies, rawSearchTvShows, newFilter)
                updateState {
                    it.copy(shared = it.shared.copy(moviesSections = sections, errorMessage = null))
                }
            } else if (!state.isSearching) {
                reloadQuery(state.query)
            }
            return
        }

        if (newFilter.isActive) {
            updateState { it.copy(shared = it.shared.copy(overlayMenuData = updatedMenu)) }
            loadDiscoverByFilter(newFilter, explicitCategory = matchingCategory)
        } else {
            cancelLoadWork()
            updateState {
                it.copy(
                    filterState = newFilter,
                    selectedCategory = null,
                    isSearching = false,
                    shared = it.shared.copy(
                        moviesSections = emptyList(),
                        isLoadingNextPage = false,
                        errorMessage = null,
                        overlayMenuData = updatedMenu,
                    ),
                )
            }
        }
    }

    fun loadDiscoverByFilter(
        filterState: MediaFilterState,
        explicitCategory: FilterOption? = matchingCategoryFor(filterState, stateProvider()),
    ) {
        updateState {
            it.copy(
                selectedCategory = explicitCategory,
                filterState = filterState,
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

        launchLoadWork(
            { t ->
                updateState {
                    it.copy(
                        isSearching = false,
                        shared = it.shared.copy(
                            isLoading = false,
                            isLoadingNextPage = false,
                            errorMessage = t.message?.let(TextValue::DynamicString)
                                ?: TextValue.StringResource(R.string.error_unknown),
                        ),
                    )
                }
            },
        ) {
            val (moviesFeed, tvFeed) = discoverFeedsFor(filterState)
            val moviesDeferred = moviesFeed?.let { async { repository.getFeedPage(it) } }
            val tvDeferred = tvFeed?.let { async { repository.getFeedPage(it) } }

            val moviesResult = moviesDeferred?.await()
            val tvResult = tvDeferred?.await()

            val noMovies = moviesResult == null || moviesResult.movies.isEmpty()
            val noTv = tvResult == null || tvResult.movies.isEmpty()
            val errorMsg = moviesResult?.errorMessage ?: tvResult?.errorMessage
            if (noMovies && noTv && errorMsg != null) {
                updateState {
                    it.copy(
                        isSearching = false,
                        shared = it.shared.copy(
                            isLoading = false,
                            isLoadingNextPage = false,
                            errorMessage = TextValue.DynamicString(errorMsg),
                        ),
                    )
                }
                return@launchLoadWork
            }

            val sections = buildList {
                if (moviesResult != null &&
                    (moviesResult.movies.isNotEmpty() || moviesResult.hasMore)
                ) {
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
                if (tvResult != null &&
                    (tvResult.movies.isNotEmpty() || tvResult.hasMore)
                ) {
                    add(
                        MoviesSection(
                            title = R.string.common_results_tv_shows,
                            movies = tvResult.movies
                                .map { m -> m.copy(isTvShow = true) }
                                .toSearchGridItems(),
                            feed = tvFeed,
                            page = tvResult.page,
                            hasMore = tvResult.hasMore,
                        ),
                    )
                }
            }

            updateState {
                it.copy(
                    isSearching = false,
                    shared = it.shared.copy(
                        moviesSections = sections,
                        isLoadingNextPage = false,
                    ),
                )
            }
            if (!isFilterMenuOpen && sections.any { it.movies.isNotEmpty() || it.hasMore }) {
                sendEffect(SearchEffect.FocusSearchResults)
            }
        }
    }

    private fun buildFilterOverlayMenu(
        filterState: MediaFilterState,
        categories: List<FilterOption>,
    ): OverlayMenuData = FilterMenuBuilder.build(
        filterState = filterState,
        availableGenres = categories,
        showMediaTypeFilter = true,
        onUpdateFilter = { onEvent(SearchEvent.UpdateFilter(it)) },
        onResetFilters = { onEvent(SearchEvent.ResetFilters) },
    )

    private fun matchingCategoryFor(
        filterState: MediaFilterState,
        state: SearchState,
    ): FilterOption? {
        if (!filterState.isSingleGenreOnly) return null
        val singleId = filterState.selectedGenreIds.single().toString()
        return state.categories.firstOrNull { it.id == singleId }
            ?: state.selectedCategory?.takeIf { it.id == singleId }
    }
}
