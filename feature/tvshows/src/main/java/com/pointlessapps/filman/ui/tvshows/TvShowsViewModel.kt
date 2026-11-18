package com.pointlessapps.filman.ui.tvshows

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.TmdbFeed
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.base.loadMoreMoviesForSection
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import com.pointlessapps.filman.ui.components.sections.MoviesSection
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

sealed interface TvShowsEvent : FilmanEvent {
    data object LoadHomeData : TvShowsEvent

    data class LoadMoreForSection(
        val sectionTitle: Int,
    ) : TvShowsEvent
}

@Immutable
data class TvShowsState(
    override val shared: SharedState = SharedState(),
) : StateWithShared<TvShowsState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

sealed interface TvShowsEffect {
    data object ScrollToTop : TvShowsEffect

    data object NavigateToAuth : TvShowsEffect

    data class NavigateToDetails(
        val request: DetailsRequest,
    ) : TvShowsEffect
}

class TvShowsViewModel(
    private val repository: FilmanRepository,
    favoritesManager: FavoritesManager,
    watchlistManager: WatchlistManager,
    progressManager: ProgressManager,
) : BaseViewModel<TvShowsState, TvShowsEvent, TvShowsEffect>(
    initialState = TvShowsState(),
    favoritesManager = favoritesManager,
    watchlistManager = watchlistManager,
    progressManager = progressManager,
) {
    private var currentLoadJob: Job? = null

    override fun getAuthErrorEffect(): TvShowsEffect = TvShowsEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(
        url: String,
        autoplay: Boolean,
        episodeUrl: String?,
        request: DetailsRequest?,
    ): TvShowsEffect = TvShowsEffect.NavigateToDetails(request ?: DetailsRequest.Url(url, isTvShow = true))

    override fun handleEvent(event: TvShowsEvent) {
        when (event) {
            is TvShowsEvent.LoadHomeData -> loadData()
            is TvShowsEvent.LoadMoreForSection -> loadMoreForSection(event.sectionTitle)
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
        currentLoadJob =
            launchHandled(
                onError = { t ->
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
                val onTheAirDeferred = async { repository.getFeedPage(TmdbFeed.TvOnTheAir) }
                val popularDeferred = async { repository.getFeedPage(TmdbFeed.TvPopular) }
                val topRatedDeferred = async { repository.getFeedPage(TmdbFeed.TvTopRated) }
                val newReleasesDeferred = async { repository.getFeedPage(TmdbFeed.TvNewReleases) }

                val (onTheAirResult, popularResult, topRatedResult, newReleasesResult) =
                    awaitAll(
                        onTheAirDeferred,
                        popularDeferred,
                        topRatedDeferred,
                        newReleasesDeferred,
                    )

                val allResults = listOf(onTheAirResult, popularResult, topRatedResult, newReleasesResult)
                if (allResults.all { it.movies.isEmpty() } && allResults.any { it.errorMessage != null }) {
                    val errorMsg = allResults.firstNotNullOfOrNull { it.errorMessage }
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

                val featuredItems =
                    (onTheAirResult.featuredItems + popularResult.featuredItems + topRatedResult.featuredItems)
                        .distinctBy { it.url }
                        .take(5)

                val sectionPairs = listOf(
                    R.string.tvshows_new_episodes to onTheAirResult,
                    R.string.common_most_viewed to popularResult,
                    R.string.common_highest_rating to topRatedResult,
                    R.string.common_recently_added to newReleasesResult,
                )

                updateSharedState {
                    it.copy(
                        featuredItems = featuredItems,
                        moviesSections =
                            sectionPairs.mapNotNull { (titleRes, result) ->
                                if (result.movies.isEmpty()) {
                                    null
                                } else {
                                    MoviesSection(
                                        title = titleRes,
                                        movies = result.movies.map(MoviesGridItem::Single),
                                        feed = result.feed,
                                        page = result.page,
                                        hasMore = result.hasMore,
                                    )
                                }
                            },
                        isLoading = false,
                    )
                }
                sendEffect(TvShowsEffect.ScrollToTop)
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
