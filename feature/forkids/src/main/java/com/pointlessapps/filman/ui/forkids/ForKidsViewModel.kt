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
                val popularMoviesDeferred = async { repository.getFeedPage(TmdbFeed.KidsPopularMovies) }
                val kidsTvDeferred = async { repository.getFeedPage(TmdbFeed.KidsTvShows) }
                val familyClassicsDeferred = async { repository.getFeedPage(TmdbFeed.KidsTopRatedFamily) }
                val disneyPixarDeferred = async { repository.getFeedPage(TmdbFeed.KidsDisneyPixar) }
                val dreamWorksGhibliDeferred = async { repository.getFeedPage(TmdbFeed.KidsDreamWorksGhibli) }

                val (popularMovies, kidsTv, familyClassics, disneyPixar, dreamWorksGhibli) =
                    awaitAll(
                        popularMoviesDeferred,
                        kidsTvDeferred,
                        familyClassicsDeferred,
                        disneyPixarDeferred,
                        dreamWorksGhibliDeferred,
                    )

                val allResults = listOf(popularMovies, kidsTv, familyClassics, disneyPixar, dreamWorksGhibli)
                if (allResults.all { it.movies.isEmpty() } && allResults.any { it.errorMessage != null }) {
                    val errorMsg = allResults.firstNotNullOfOrNull { it.errorMessage }
                    updateSharedState {
                        it.copy(
                            isLoading = false,
                            errorMessage = errorMsg?.let(TextValue::DynamicString)
                                ?: TextValue.StringResource(R.string.error_unknown),
                        )
                    }
                    return@launchHandled
                }

                val featured = (popularMovies.featuredItems + kidsTv.featuredItems)
                    .distinctBy { it.url }
                    .take(5)

                val sectionPairs = listOf(
                    R.string.kids_popular_movies to popularMovies,
                    R.string.kids_tv_shows to kidsTv,
                    R.string.kids_top_rated_family to familyClassics,
                    R.string.kids_disney_pixar to disneyPixar,
                    R.string.kids_dreamworks_ghibli to dreamWorksGhibli,
                )

                updateSharedState {
                    it.copy(
                        featuredItems = featured,
                        moviesSections =
                            sectionPairs.mapNotNull { (titleRes, pageResult) ->
                                if (pageResult.movies.isEmpty()) {
                                    null
                                } else {
                                    MoviesSection(
                                        title = titleRes,
                                        movies = pageResult.movies.map(MoviesGridItem::Single),
                                        feed = pageResult.feed,
                                        page = pageResult.page,
                                        hasMore = pageResult.hasMore,
                                    )
                                }
                            },
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
