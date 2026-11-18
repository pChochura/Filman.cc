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
import com.pointlessapps.filman.ui.base.FeedSectionSpec
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.base.loadFeedCatalog
import com.pointlessapps.filman.ui.base.loadMoreMoviesForSection
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.Job

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
            FeedSectionSpec(R.string.tvshows_new_episodes, TmdbFeed.TvOnTheAir, includeInFeatured = true),
            FeedSectionSpec(R.string.common_most_viewed, TmdbFeed.TvPopular, includeInFeatured = true),
            FeedSectionSpec(R.string.common_highest_rating, TmdbFeed.TvTopRated, includeInFeatured = true),
            FeedSectionSpec(R.string.common_recently_added, TmdbFeed.TvNewReleases),
        )
    }
}
