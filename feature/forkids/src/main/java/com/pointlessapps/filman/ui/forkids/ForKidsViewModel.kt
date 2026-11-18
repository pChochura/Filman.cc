package com.pointlessapps.filman.ui.forkids

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

sealed interface ForKidsEvent : FilmanEvent {
    data object LoadHomeData : ForKidsEvent

    data class LoadMoreForSection(
        val sectionTitle: Int,
    ) : ForKidsEvent
}

@Immutable
data class ForKidsState(
    override val shared: SharedState = SharedState(),
) : StateWithShared<ForKidsState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

sealed interface ForKidsEffect {
    data object ScrollToTop : ForKidsEffect

    data object NavigateToAuth : ForKidsEffect

    data class NavigateToDetails(
        val request: DetailsRequest,
    ) : ForKidsEffect
}

class ForKidsViewModel(
    private val repository: FilmanRepository,
    favoritesManager: FavoritesManager,
    watchlistManager: WatchlistManager,
    progressManager: ProgressManager,
) : BaseViewModel<ForKidsState, ForKidsEvent, ForKidsEffect>(
    initialState = ForKidsState(),
    favoritesManager = favoritesManager,
    watchlistManager = watchlistManager,
    progressManager = progressManager,
) {
    private var currentLoadJob: Job? = null

    override fun getAuthErrorEffect(): ForKidsEffect = ForKidsEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(
        url: String,
        autoplay: Boolean,
        episodeUrl: String?,
        request: DetailsRequest?,
    ): ForKidsEffect = ForKidsEffect.NavigateToDetails(request ?: DetailsRequest.Url(url))

    override fun handleEvent(event: ForKidsEvent) {
        when (event) {
            is ForKidsEvent.LoadHomeData -> loadData()
            is ForKidsEvent.LoadMoreForSection -> loadMoreForSection(event.sectionTitle)
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
            sendEffect(ForKidsEffect.ScrollToTop)
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
            FeedSectionSpec(R.string.kids_popular_movies, TmdbFeed.KidsPopularMovies, includeInFeatured = true),
            FeedSectionSpec(R.string.kids_tv_shows, TmdbFeed.KidsTvShows, includeInFeatured = true),
            FeedSectionSpec(R.string.kids_top_rated_family, TmdbFeed.KidsTopRatedFamily),
            FeedSectionSpec(R.string.kids_disney_pixar, TmdbFeed.KidsDisneyPixar),
            FeedSectionSpec(R.string.kids_dreamworks_ghibli, TmdbFeed.KidsDreamWorksGhibli),
        )
    }
}
