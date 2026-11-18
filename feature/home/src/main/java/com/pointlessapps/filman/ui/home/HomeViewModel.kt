package com.pointlessapps.filman.ui.home

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.NewEpisodesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.WatchlistManager
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
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.base.loadMoreMoviesForSection
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import com.pointlessapps.filman.ui.components.sections.MoviesSection
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine

sealed interface HomeEvent : FilmanEvent {
    data object LoadHomeData : HomeEvent
    data class ClearNewEpisode(val url: String) : HomeEvent
    data class LoadMoreForSection(val sectionTitle: Int) : HomeEvent
}

@Immutable
data class HomeState(
    override val shared: SharedState = SharedState(),
    val progressItems: List<ProgressItem> = emptyList(),
    val favorites: List<MovieItem> = emptyList(),
    val watchlist: List<MovieItem> = emptyList(),
    val newEpisodes: List<MovieItem> = emptyList(),
) : StateWithShared<HomeState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

sealed interface HomeEffect {
    data object ScrollToTop : HomeEffect

    data object FocusFirstGridItem : HomeEffect

    data object NavigateToAuth : HomeEffect

    data class NavigateToDetails(
        val request: DetailsRequest,
        val autoplay: Boolean,
        val episodeUrl: String? = null,
    ) : HomeEffect

    data class OverrideFocus(
        val itemId: String,
    ) : HomeEffect
}

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
                val distinctSeries =
                    progressItems.distinctBy { p ->
                        p.parentUrl ?: p.url
                    }

                val fullyWatchedUrls =
                    distinctSeries.mapNotNull { p ->
                        if (p is ProgressItem.Watched) {
                            if (p.parentUrl == null || p.parentUrl == p.url) {
                                p.url
                            } else if (!p.hasNextEpisode) {
                                p.parentUrl
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }.toSet()

                val filteredFavorites =
                    favorites.filterNot {
                        it.url in fullyWatchedUrls
                    }

                val mappedProgressItems =
                    distinctSeries.mapNotNull { p ->
                        if (p is ProgressItem.Watched) {
                            if (p.parentUrl != null && p.parentUrl != p.url && p.hasNextEpisode) {
                                ProgressItem.NextEpisode(
                                    url = p.url,
                                    parentUrl = p.parentUrl,
                                    posterUrl = p.posterUrl,
                                    titlePl = p.seriesTitle ?: p.titlePl,
                                    seriesTitle = p.seriesTitle,
                                )
                            } else {
                                null
                            }
                        } else {
                            p
                        }
                    }

                data class Combined(
                    val favorites: List<MovieItem>,
                    val watchlist: List<MovieItem>,
                    val progressItems: List<ProgressItem>,
                    val newEpisodes: List<MovieItem>,
                )
                Combined(filteredFavorites, watchlist, mappedProgressItems, newEpisodes)
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
            is HomeEvent.ClearNewEpisode -> {
                newEpisodesManager.removeNewEpisode(event.url)
            }
        }
    }

    override fun handleBaseEvent(event: BaseEvent) {
        when (event) {
            is RemoveFromFavorites -> {
                val isLastItem =
                    currentState.favorites.size == 1 &&
                        currentState.favorites.first().url == event.url
                super.handleBaseEvent(event)
                if (isLastItem) {
                    val fallbackId =
                        currentState.progressItems.lastOrNull()?.url?.let {
                            "${SectionFocusRestorationId.CONTINUE_WATCHING.prefix}$it"
                        } ?: currentState.featuredItems.lastOrNull()?.url?.let {
                            "${SectionFocusRestorationId.FEATURED.prefix}$it"
                        }
                    if (fallbackId != null) {
                        sendEffect(HomeEffect.OverrideFocus(fallbackId))
                    }
                }
            }

            is RemoveFromContinueWatching -> {
                val isLastItem =
                    currentState.progressItems.size == 1 &&
                        currentState.progressItems.first().url == event.url
                super.handleBaseEvent(event)
                if (isLastItem) {
                    val fallbackId =
                        currentState.featuredItems.lastOrNull()?.url?.let {
                            "${SectionFocusRestorationId.FEATURED.prefix}$it"
                        }
                    if (fallbackId != null) {
                        sendEffect(HomeEffect.OverrideFocus(fallbackId))
                    }
                }
            }

            is BaseEvent.RemoveFromWatchlist -> {
                val isLastItem =
                    currentState.watchlist.size == 1 &&
                        currentState.watchlist.first().url == event.url
                super.handleBaseEvent(event)
                if (isLastItem) {
                    val fallbackId =
                        currentState.progressItems.lastOrNull()?.url?.let {
                            "${SectionFocusRestorationId.CONTINUE_WATCHING.prefix}$it"
                        } ?: currentState.featuredItems.lastOrNull()?.url?.let {
                            "${SectionFocusRestorationId.FEATURED.prefix}$it"
                        }
                    if (fallbackId != null) {
                        sendEffect(HomeEffect.OverrideFocus(fallbackId))
                    }
                }
            }

            else -> {
                super.handleBaseEvent(event)
            }
        }
    }

    private fun loadData() {
        if (currentState.moviesSections.isNotEmpty()) return

        updateSharedState {
            it.copy(
                isLoading = true,
                errorMessage = null,
            )
        }

        currentLoadJob?.cancel()
        var thisJob: Job? = null
        thisJob =
            launchHandled(
                onError = { t ->
                    if (thisJob?.isCancelled == true) return@launchHandled
                    updateSharedState {
                        it.copy(
                            isLoading = false,
                            errorMessage =
                                t.message?.let(TextValue::DynamicString)
                                    ?: TextValue.StringResource(R.string.error_unknown),
                        )
                    }
                    handleError(t)
                },
            ) {
                val trendingTodayDeferred = async { repository.getFeedPage(TmdbFeed.TrendingAll(TrendingWindow.DAY)) }
                val popularMoviesDeferred = async { repository.getFeedPage(TmdbFeed.MoviesPopular) }
                val trendingSeriesDeferred = async { repository.getFeedPage(TmdbFeed.TrendingTv(TrendingWindow.WEEK)) }
                val personalizedDeferred =
                    async { recommendationManager.getPersonalizedRecommendations() }

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

                val sections = mutableListOf<MoviesSection>()

                if (personalized.isNotEmpty()) {
                    sections.add(
                        MoviesSection(
                            title = R.string.home_for_you,
                            movies = personalized.map(MoviesGridItem::Single),
                            feed = TmdbFeed.PersonalizedForYou,
                            page = 1,
                            hasMore = hasMorePersonalized,
                        ),
                    )
                }

                if (trendingToday.movies.isNotEmpty()) {
                    sections.add(
                        MoviesSection(
                            title = R.string.home_trending_today,
                            movies = trendingToday.movies.map(MoviesGridItem::Single),
                            feed = trendingToday.feed,
                            page = trendingToday.page,
                            hasMore = trendingToday.hasMore,
                        ),
                    )
                }

                if (popularMovies.movies.isNotEmpty()) {
                    sections.add(
                        MoviesSection(
                            title = R.string.home_recommended,
                            movies = popularMovies.movies.map(MoviesGridItem::Single),
                            feed = popularMovies.feed,
                            page = popularMovies.page,
                            hasMore = popularMovies.hasMore,
                        ),
                    )
                }

                if (trendingSeries.movies.isNotEmpty()) {
                    sections.add(
                        MoviesSection(
                            title = R.string.home_trending_series,
                            movies = trendingSeries.movies.map(MoviesGridItem::Single),
                            feed = trendingSeries.feed,
                            page = trendingSeries.page,
                            hasMore = trendingSeries.hasMore,
                        ),
                    )
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
                val (personalized, hasMorePersonalized) = recommendationManager.getPersonalizedRecommendations(
                    nextPage,
                )

                updateSharedState { state ->
                    state.copy(
                        moviesSections = state.moviesSections.map {
                            if (it.title == sectionTitle) {
                                it.copy(
                                    movies = (it.movies + personalized.map(MoviesGridItem::Single)).distinctBy { item -> item.movieItem.url },
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
                val updatedSections =
                    repository.loadMoreMoviesForSection(
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
    }
}
