package com.pointlessapps.filman.ui.movies

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.DecadeOption
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaFilterState
import com.pointlessapps.filman.data.model.MediaType
import com.pointlessapps.filman.data.model.TmdbFeed
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.FeedSectionSpec
import com.pointlessapps.filman.ui.base.loadFeedCatalog
import com.pointlessapps.filman.ui.base.loadMoreMoviesForSection
import com.pointlessapps.filman.ui.components.FilterMenuBuilder
import com.pointlessapps.filman.ui.components.OverlayMenuData
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import com.pointlessapps.filman.ui.components.sections.MoviesSection
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.async

class MoviesViewModel(
    private val repository: FilmanRepository,
    favoritesManager: FavoritesManager,
    watchlistManager: WatchlistManager,
    progressManager: ProgressManager,
) : BaseViewModel<MoviesState, MoviesEvent, MoviesEffect>(
    initialState = MoviesState(),
    favoritesManager = favoritesManager,
    watchlistManager = watchlistManager,
    progressManager = progressManager,
) {
    private var currentLoadJob: Job? = null
    private var currentFilterJob: Job? = null
    private var isFilterMenuOpen: Boolean = false

    override fun getAuthErrorEffect(): MoviesEffect = MoviesEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(
        url: String,
        autoplay: Boolean,
        episodeUrl: String?,
        request: DetailsRequest?,
    ): MoviesEffect = MoviesEffect.NavigateToDetails(request ?: DetailsRequest.Url(url, isTvShow = false))

    override fun handleBaseEvent(event: BaseEvent) {
        if (event is BaseEvent.CloseContextMenu) {
            isFilterMenuOpen = false
        }
        super.handleBaseEvent(event)
    }

    override fun handleEvent(event: MoviesEvent) {
        when (event) {
            is MoviesEvent.LoadHomeData -> loadData()
            is MoviesEvent.LoadMoreForSection -> loadMoreForSection(event.sectionTitle)
            is MoviesEvent.OpenFilterMenu -> openFilterMenu()
            is MoviesEvent.UpdateFilter -> updateFilter(event.filterState)
            is MoviesEvent.ToggleGenre -> updateFilter(currentState.filterState.toggleGenre(event.genreId))
            is MoviesEvent.SelectSort -> updateFilter(currentState.filterState.copy(sortBy = event.sortOption))
            is MoviesEvent.ClearDecade -> updateFilter(currentState.filterState.copy(decade = DecadeOption.ALL))
            is MoviesEvent.ClearRating -> updateFilter(currentState.filterState.copy(minRating = null))
            is MoviesEvent.ResetFilters -> resetFilters()
        }
    }

    private fun loadData() {
        if (currentState.moviesSections.isNotEmpty()) return

        updateSharedState { it.copy(isLoading = true, errorMessage = null) }

        currentLoadJob?.cancel()
        currentLoadJob = launchHandled(
            onError = { t ->
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = t.message?.let(TextValue::DynamicString)
                            ?: TextValue.StringResource(R.string.error_unknown),
                    )
                }
                handleError(t)
            },
        ) {
            val catalogDeferred = async { repository.loadFeedCatalog(FEED_SPECS) }
            val genresDeferred = async { repository.getMovieCategories() }

            val catalog = catalogDeferred.await()
            val genres = genresDeferred.await()

            if (catalog.hasError) {
                updateState { state ->
                    state.copy(
                        availableGenres = genres,
                        shared = state.shared.copy(
                            isLoading = false,
                            isLoadingNextPage = false,
                            errorMessage = catalog.errorMessage?.let(TextValue::DynamicString)
                                ?: TextValue.StringResource(R.string.error_unknown),
                        ),
                    )
                }
                return@launchHandled
            }

            updateState { state ->
                state.copy(
                    availableGenres = genres,
                    shared = state.shared.copy(
                        featuredItems = catalog.featuredItems,
                        moviesSections = catalog.sections,
                        isLoading = false,
                    ),
                )
            }
            sendEffect(MoviesEffect.ScrollToTop)
        }
    }

    private fun openFilterMenu() {
        isFilterMenuOpen = true
        val menuData = buildFilterOverlayMenu(currentState.filterState, currentState.availableGenres)
        updateSharedState { it.copy(overlayMenuData = menuData) }
    }

    private fun buildFilterOverlayMenu(
        filterState: MediaFilterState,
        availableGenres: List<FilterOption>,
    ): OverlayMenuData = FilterMenuBuilder.build(
        filterState = filterState,
        availableGenres = availableGenres,
        showMediaTypeFilter = false,
        onUpdateFilter = { onEvent(MoviesEvent.UpdateFilter(it)) },
        onResetFilters = { onEvent(MoviesEvent.ResetFilters) },
    )

    private fun updateFilter(newFilter: MediaFilterState) {
        val updatedMenu = if (isFilterMenuOpen && currentState.overlayMenuData != null) {
            buildFilterOverlayMenu(newFilter, currentState.availableGenres)
        } else {
            currentState.overlayMenuData
        }

        if (!newFilter.isActive) {
            currentFilterJob?.cancel()
            updateState {
                it.copy(
                    filterState = newFilter,
                    isFiltering = false,
                    filteredSection = null,
                    filterErrorMessage = null,
                    shared = it.shared.copy(
                        overlayMenuData = updatedMenu,
                        isLoadingNextPage = false,
                    ),
                )
            }
            return
        }

        loadFiltered(newFilter, updatedMenu)
    }

    private fun loadFiltered(
        newFilter: MediaFilterState,
        updatedMenu: OverlayMenuData?,
    ) {
        currentFilterJob?.cancel()
        updateState {
            it.copy(
                filterState = newFilter,
                isFiltering = true,
                filteredSection = null,
                filterErrorMessage = null,
                shared = it.shared.copy(
                    overlayMenuData = updatedMenu,
                    isLoadingNextPage = false,
                ),
            )
        }

        currentFilterJob = launchHandled(
            onError = { t ->
                updateState {
                    it.copy(
                        isFiltering = false,
                        filterErrorMessage = t.message?.let(TextValue::DynamicString)
                            ?: TextValue.StringResource(R.string.error_unknown),
                    )
                }
                handleError(t)
            },
        ) {
            val feed = TmdbFeed.DiscoverFiltered(MediaType.MOVIE, newFilter)
            val result = repository.getFeedPage(feed, page = 1)
            val errorMsg = result.errorMessage
            if (result.movies.isEmpty() && errorMsg != null) {
                updateState {
                    it.copy(
                        isFiltering = false,
                        filterErrorMessage = TextValue.DynamicString(errorMsg),
                    )
                }
                return@launchHandled
            }

            val items = result.movies
                .filter { it.posterUrl.isNotBlank() }
                .distinctBy { it.url }
                .map(MoviesGridItem::Single)

            updateState {
                it.copy(
                    isFiltering = false,
                    filteredSection = MoviesSection(
                        title = R.string.filter_results_title,
                        movies = items,
                        feed = feed,
                        page = result.page,
                        hasMore = result.hasMore,
                    ),
                )
            }
        }
    }

    private fun resetFilters() {
        isFilterMenuOpen = false
        currentFilterJob?.cancel()
        updateState {
            it.copy(
                filterState = MediaFilterState(),
                isFiltering = false,
                filteredSection = null,
                filterErrorMessage = null,
                shared = it.shared.copy(
                    overlayMenuData = null,
                    isLoadingNextPage = false,
                ),
            )
        }
    }

    private fun loadMoreForSection(sectionTitle: Int) {
        if (currentState.isLoadingNextPage) return

        if (currentState.filterState.isActive && sectionTitle == R.string.filter_results_title) {
            loadMoreFiltered(sectionTitle)
            return
        }

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

    private fun loadMoreFiltered(sectionTitle: Int) {
        val section = currentState.filteredSection ?: return
        if (!section.hasMore) return

        updateSharedState { it.copy(isLoadingNextPage = true) }

        launchHandled(
            onError = { t ->
                updateSharedState { it.copy(isLoadingNextPage = false) }
                handleError(t)
            },
        ) {
            val updatedSection = repository.loadMoreMoviesForSection(
                moviesSections = listOf(section),
                sectionTitle = sectionTitle,
                transform = { newMovies, oldItems ->
                    val filteredNew = newMovies
                        .filter { it.posterUrl.isNotBlank() }
                        .map(MoviesGridItem::Single)
                    (oldItems + filteredNew).distinctBy { it.movieItem.url }
                },
            )?.firstOrNull()

            updateState { state ->
                state.copy(
                    filteredSection = updatedSection ?: state.filteredSection,
                    shared = state.shared.copy(isLoadingNextPage = false),
                )
            }
        }
    }

    private companion object {
        val FEED_SPECS = listOf(
            FeedSectionSpec(R.string.movies_now_playing, TmdbFeed.MoviesNowPlaying, includeInFeatured = true),
            FeedSectionSpec(R.string.common_most_viewed, TmdbFeed.MoviesPopular, includeInFeatured = true),
            FeedSectionSpec(R.string.common_highest_rating, TmdbFeed.MoviesTopRated, includeInFeatured = true),
            FeedSectionSpec(R.string.common_recently_added, TmdbFeed.MoviesNewReleases),
        )
    }
}
