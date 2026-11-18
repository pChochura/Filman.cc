package com.pointlessapps.filman.ui.home

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.NewEpisodesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.local.distinctBySeries
import com.pointlessapps.filman.data.local.extractFullyWatchedUrls
import com.pointlessapps.filman.data.local.toContinueWatchingItems
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.model.TmdbFeed
import com.pointlessapps.filman.data.model.TrendingWindow
import com.pointlessapps.filman.data.recommendation.RecommendationManager
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.BaseEvent.RemoveFromContinueWatching
import com.pointlessapps.filman.ui.base.BaseEvent.RemoveFromFavorites
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.loadMoreMoviesForSection
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import com.pointlessapps.filman.ui.components.sections.MoviesSection
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine

class HomeViewModel(
    private val repository: FilmanRepository,
    private val recommendationManager: RecommendationManager,
    private val newEpisodesManager: NewEpisodesManager,
    favoritesManager: FavoritesManager,
    progressManager: ProgressManager,
    watchlistManager: WatchlistManager,
) : BaseViewModel<HomeState, HomeEvent, HomeEffect>(
    initialState = HomeState(),
    favoritesManager = favoritesManager,
    progressManager = progressManager,
    watchlistManager = watchlistManager,
) {
    private var currentLoadJob: Job? = null

    init {
        launchHandled {
            combine(
                favoritesManager.favoritesFlow,
                watchlistManager.watchlistFlow,
                progressManager.progressItemsFlow,
                newEpisodesManager.newEpisodesFlow,
            ) { favorites, watchlist, progressItems, newEpisodes ->
                val distinctSeries = progressItems.distinctBySeries()
                val fullyWatchedUrls = distinctSeries.extractFullyWatchedUrls()
                val filteredFavorites = favorites.filterNot { it.url in fullyWatchedUrls }
                val mappedProgressItems = distinctSeries.toContinueWatchingItems()
                LibrarySnapshot(filteredFavorites, watchlist, mappedProgressItems, newEpisodes)
            }.collect { combined ->
                updateState {
                    it.copy(
                        favorites = combined.favorites,
                        watchlist = combined.watchlist,
                        progressItems = combined.progressItems,
                        newEpisodes = combined.newEpisodes,
                    )
                }
            }
        }
    }

    private data class LibrarySnapshot(
        val favorites: List<MovieItem>,
        val watchlist: List<MovieItem>,
        val progressItems: List<ProgressItem>,
        val newEpisodes: List<MovieItem>,
    )

    override fun getAuthErrorEffect(): HomeEffect = HomeEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(
        url: String,
        autoplay: Boolean,
        episodeUrl: String?,
        request: DetailsRequest?,
    ): HomeEffect = HomeEffect.NavigateToDetails(
        request = request ?: DetailsRequest.Url(url),
        autoplay = autoplay,
        episodeUrl = episodeUrl,
    )

    override fun handleEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.LoadHomeData -> loadData()
            is HomeEvent.LoadMoreForSection -> loadMoreForSection(event.sectionTitle)
            is HomeEvent.ClearNewEpisode -> newEpisodesManager.removeNewEpisode(event.url)
        }
    }

    override fun handleBaseEvent(event: BaseEvent) {
        when (event) {
            is RemoveFromFavorites -> {
                val fallbackId = currentState.focusFallbackAfterRemovingFavorite(event.url)
                super.handleBaseEvent(event)
                fallbackId?.let { sendEffect(HomeEffect.OverrideFocus(it)) }
            }

            is RemoveFromContinueWatching -> {
                val fallbackId = currentState.focusFallbackAfterRemovingProgress(event.url)
                super.handleBaseEvent(event)
                fallbackId?.let { sendEffect(HomeEffect.OverrideFocus(it)) }
            }

            is BaseEvent.RemoveFromWatchlist -> {
                val fallbackId = currentState.focusFallbackAfterRemovingWatchlist(event.url)
                super.handleBaseEvent(event)
                fallbackId?.let { sendEffect(HomeEffect.OverrideFocus(it)) }
            }

            else -> super.handleBaseEvent(event)
        }
    }

    private fun loadData() {
        if (currentState.moviesSections.isNotEmpty()) return

        updateSharedState { it.copy(isLoading = true, errorMessage = null) }

        currentLoadJob?.cancel()
        var thisJob: Job? = null
        thisJob = launchHandled(
            onError = { t ->
                if (thisJob?.isCancelled == true) return@launchHandled
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
            val trendingTodayDeferred = async { repository.getFeedPage(TmdbFeed.TrendingAll(TrendingWindow.DAY)) }
            val popularMoviesDeferred = async { repository.getFeedPage(TmdbFeed.MoviesPopular) }
            val trendingSeriesDeferred = async { repository.getFeedPage(TmdbFeed.TrendingTv(TrendingWindow.WEEK)) }
            val personalizedDeferred = async { recommendationManager.getPersonalizedRecommendations() }

            val trendingToday = trendingTodayDeferred.await()
            val popularMovies = popularMoviesDeferred.await()
            val trendingSeries = trendingSeriesDeferred.await()
            val (personalized, hasMorePersonalized) = personalizedDeferred.await()

            val allFeeds = listOf(trendingToday, popularMovies, trendingSeries)
            if (allFeeds.all { it.movies.isEmpty() } && allFeeds.any { it.errorMessage != null }) {
                val errorMsg = allFeeds.firstNotNullOfOrNull { it.errorMessage }
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = errorMsg?.let(TextValue::DynamicString)
                            ?: TextValue.StringResource(R.string.error_unknown),
                    )
                }
                return@launchHandled
            }

            val sections = buildList {
                if (personalized.isNotEmpty()) {
                    add(
                        MoviesSection(
                            title = R.string.home_for_you,
                            movies = personalized.map(MoviesGridItem::Single),
                            feed = TmdbFeed.PersonalizedForYou,
                            page = 1,
                            hasMore = hasMorePersonalized,
                        ),
                    )
                }
                listOf(
                    R.string.home_trending_today to trendingToday,
                    R.string.home_recommended to popularMovies,
                    R.string.home_trending_series to trendingSeries,
                ).forEach { (titleRes, pageResult) ->
                    if (pageResult.movies.isNotEmpty()) {
                        add(
                            MoviesSection(
                                title = titleRes,
                                movies = pageResult.movies.map(MoviesGridItem::Single),
                                feed = pageResult.feed,
                                page = pageResult.page,
                                hasMore = pageResult.hasMore,
                            ),
                        )
                    }
                }
            }

            updateSharedState {
                it.copy(
                    featuredItems = trendingToday.featuredItems.ifEmpty { popularMovies.featuredItems },
                    moviesSections = sections,
                    isLoading = false,
                )
            }
            sendEffect(HomeEffect.ScrollToTop)
        }
        currentLoadJob = thisJob
    }

    private fun loadMoreForSection(sectionTitle: Int) {
        if (currentState.isLoadingNextPage) return
        val section = currentState.moviesSections.find { it.title == sectionTitle } ?: return
        if (!section.hasMore) return

        updateSharedState { it.copy(isLoadingNextPage = true) }

        launchHandled(
            onError = { t ->
                updateSharedState { it.copy(isLoadingNextPage = false) }
                handleError(t)
            },
        ) {
            val nextPage = section.page + 1

            if (sectionTitle == R.string.home_for_you) {
                val (personalized, hasMorePersonalized) = recommendationManager.getPersonalizedRecommendations(nextPage)
                updateSharedState { state ->
                    state.copy(
                        moviesSections = state.moviesSections.map {
                            if (it.title == sectionTitle) {
                                it.copy(
                                    movies = (it.movies + personalized.map(MoviesGridItem::Single))
                                        .distinctBy { item -> item.movieItem.url },
                                    page = nextPage,
                                    hasMore = hasMorePersonalized,
                                )
                            } else {
                                it
                            }
                        },
                        isLoadingNextPage = false,
                    )
                }
            } else {
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
    }
}
