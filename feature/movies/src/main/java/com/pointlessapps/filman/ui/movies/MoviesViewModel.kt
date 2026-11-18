package com.pointlessapps.filman.ui.movies

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

sealed interface MoviesEvent : FilmanEvent {
    data object LoadHomeData : MoviesEvent

    data class LoadMoreForSection(
        val sectionTitle: Int,
    ) : MoviesEvent
}

@Immutable
data class MoviesState(
    override val shared: SharedState = SharedState(),
) : StateWithShared<MoviesState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

sealed interface MoviesEffect {
    data object ScrollToTop : MoviesEffect

    data object NavigateToAuth : MoviesEffect

    data class NavigateToDetails(
        val request: DetailsRequest,
    ) : MoviesEffect
}

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

    override fun getAuthErrorEffect(): MoviesEffect = MoviesEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(
        url: String,
        autoplay: Boolean,
        episodeUrl: String?,
        request: DetailsRequest?,
    ): MoviesEffect = MoviesEffect.NavigateToDetails(request ?: DetailsRequest.Url(url, isTvShow = false))

    override fun handleEvent(event: MoviesEvent) {
        when (event) {
            is MoviesEvent.LoadHomeData -> loadData()
            is MoviesEvent.LoadMoreForSection -> loadMoreForSection(event.sectionTitle)
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
                val nowPlayingDeferred = async { repository.getFeedPage(TmdbFeed.MoviesNowPlaying) }
                val popularDeferred = async { repository.getFeedPage(TmdbFeed.MoviesPopular) }
                val topRatedDeferred = async { repository.getFeedPage(TmdbFeed.MoviesTopRated) }
                val newReleasesDeferred = async { repository.getFeedPage(TmdbFeed.MoviesNewReleases) }

                val (nowPlayingResult, popularResult, topRatedResult, newReleasesResult) =
                    awaitAll(
                        nowPlayingDeferred,
                        popularDeferred,
                        topRatedDeferred,
                        newReleasesDeferred,
                    )

                val allResults = listOf(nowPlayingResult, popularResult, topRatedResult, newReleasesResult)
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
                    (nowPlayingResult.featuredItems + popularResult.featuredItems + topRatedResult.featuredItems)
                        .distinctBy { item -> item.url }
                        .take(5)

                val sectionPairs = listOf(
                    R.string.movies_now_playing to nowPlayingResult,
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
                sendEffect(MoviesEffect.ScrollToTop)
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
