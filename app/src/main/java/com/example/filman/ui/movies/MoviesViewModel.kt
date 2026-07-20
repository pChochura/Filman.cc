package com.example.filman.ui.movies

import androidx.compose.runtime.Immutable
import com.example.filman.R
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.model.PageResult
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.base.SharedState
import com.example.filman.ui.base.StateWithShared
import com.example.filman.ui.base.loadMoreMoviesForSection
import com.example.filman.ui.components.sections.MoviesSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

internal sealed interface MoviesEvent : FilmanEvent {
    data object LoadHomeData : MoviesEvent
    data class LoadMoreForSection(val sectionTitle: Int) : MoviesEvent
}

@Immutable
internal data class MoviesState(
    override val shared: SharedState = SharedState(),
) : StateWithShared<MoviesState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

internal sealed interface MoviesEffect {
    data object ScrollToTop : MoviesEffect
    data object FocusFeaturedSection : MoviesEffect
    data object NavigateToAuth : MoviesEffect
    data class NavigateToDetails(val url: String) : MoviesEffect
}

internal class MoviesViewModel(
    private val scraper: FilmanScraper,
    favoritesManager: FavoritesManager,
) : BaseViewModel<MoviesState, MoviesEvent, MoviesEffect>(
    initialState = MoviesState(),
    favoritesManager = favoritesManager,
) {

    private var currentLoadJob: Job? = null

    override fun getAuthErrorEffect(): MoviesEffect = MoviesEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(url: String): MoviesEffect =
        MoviesEffect.NavigateToDetails(url)

    override fun handleEvent(event: MoviesEvent) {
        when (event) {
            is MoviesEvent.LoadHomeData -> loadData()
            is MoviesEvent.LoadMoreForSection -> loadMoreForSection(event.sectionTitle)
        }
    }

    override fun handleStaleData(staleData: Any) {
        val result = staleData as? PageResult ?: return

        val sectionTitle = when (result.path) {
            HIGHEST_RATING_PATH -> R.string.highest_rating
            MOST_VIEWED_PATH -> R.string.most_viewed
            RECENTLY_ADDED_PATH -> R.string.recently_added

            // Ignore mismatched url
            else -> return
        }

        updateSharedState {
            it.copy(
                featuredItems = result.featuredItems,
                moviesSections = listOf(
                    MoviesSection(
                        title = sectionTitle,
                        movies = result.movies,
                        path = result.path,
                        page = 1,
                        hasMore = result.movies.size >= 20,
                    ),
                ),
            )
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
        currentLoadJob = launchHandled(
            onError = { t ->
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = t.message ?: "Unknown error",
                    )
                }
                handleError(t)
            },
        ) {
            val highestRatingDeferred = async {
                scraper.getCategoryPage(path = HIGHEST_RATING_PATH)
            }
            val mostViewedDeferred = async {
                scraper.getCategoryPage(path = MOST_VIEWED_PATH)
            }
            val recentlyAddedDeferred = async {
                scraper.getCategoryPage(path = RECENTLY_ADDED_PATH)
            }

            val (highestRatingResult, mostViewedResult, recentlyAddedResult) = awaitAll(
                highestRatingDeferred,
                mostViewedDeferred,
                recentlyAddedDeferred,
            )

            if (
                highestRatingResult.errorMessage != null ||
                mostViewedResult.errorMessage != null ||
                recentlyAddedResult.errorMessage != null
            ) {
                updateSharedState {
                    it.copy(
                        isLoadingNextPage = false,
                        errorMessage = highestRatingResult.errorMessage
                            ?: mostViewedResult.errorMessage
                            ?: recentlyAddedResult.errorMessage,
                    )
                }

                return@launchHandled
            }

            val featuredItems = highestRatingResult.featuredItems +
                    mostViewedResult.featuredItems +
                    recentlyAddedResult.featuredItems

            updateSharedState {
                it.copy(
                    featuredItems = featuredItems.distinctBy { item -> item.url },
                    moviesSections = buildList {
                        if (highestRatingResult.movies.isNotEmpty()) {
                            add(
                                MoviesSection(
                                    title = R.string.highest_rating,
                                    movies = highestRatingResult.movies,
                                    path = HIGHEST_RATING_PATH,
                                    page = 1,
                                    hasMore = highestRatingResult.movies.size >= 20,
                                ),
                            )
                        }
                        if (mostViewedResult.movies.isNotEmpty()) {
                            add(
                                MoviesSection(
                                    title = R.string.most_viewed,
                                    movies = mostViewedResult.movies,
                                    path = MOST_VIEWED_PATH,
                                    page = 1,
                                    hasMore = mostViewedResult.movies.size >= 20,
                                ),
                            )
                        }
                        if (recentlyAddedResult.movies.isNotEmpty()) {
                            add(
                                MoviesSection(
                                    title = R.string.recently_added,
                                    movies = recentlyAddedResult.movies,
                                    path = RECENTLY_ADDED_PATH,
                                    page = 1,
                                    hasMore = recentlyAddedResult.movies.size >= 20,
                                ),
                            )
                        }
                    },
                    isLoading = false,
                )
            }
            sendEffect(MoviesEffect.ScrollToTop)
            sendEffect(MoviesEffect.FocusFeaturedSection)
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
            val updatedSections = scraper.loadMoreMoviesForSection(
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

    private companion object {
        const val BASE_PATH = "/filmy/"
        const val HIGHEST_RATING_PATH = "${BASE_PATH}sort:filmweb/"
        const val MOST_VIEWED_PATH = "${BASE_PATH}sort:view/"
        const val RECENTLY_ADDED_PATH = "${BASE_PATH}sort:date/"
    }
}
