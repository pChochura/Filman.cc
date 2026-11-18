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
import com.pointlessapps.filman.ui.base.FeedSectionSpec
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.base.loadFeedCatalog
import com.pointlessapps.filman.ui.base.loadMoreMoviesForSection
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.Job

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
            val catalog = repository.loadFeedCatalog(FEED_SPECS)
            if (catalog.hasError) {
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        isLoadingNextPage = false,
                        errorMessage = catalog.errorMessage?.let(TextValue::DynamicString)
                            ?: TextValue.StringResource(R.string.error_unknown),
                    )
                }
                return@launchHandled
            }

            updateSharedState {
                it.copy(
                    featuredItems = catalog.featuredItems,
                    moviesSections = catalog.sections,
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

    private companion object {
        val FEED_SPECS = listOf(
            FeedSectionSpec(R.string.movies_now_playing, TmdbFeed.MoviesNowPlaying, includeInFeatured = true),
            FeedSectionSpec(R.string.common_most_viewed, TmdbFeed.MoviesPopular, includeInFeatured = true),
            FeedSectionSpec(R.string.common_highest_rating, TmdbFeed.MoviesTopRated, includeInFeatured = true),
            FeedSectionSpec(R.string.common_recently_added, TmdbFeed.MoviesNewReleases),
        )
    }
}
