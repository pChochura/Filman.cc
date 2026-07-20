package com.example.filman.ui.tvshows

import androidx.compose.runtime.Immutable
import com.example.filman.R
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.model.MovieItem
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.base.SharedState
import com.example.filman.ui.base.StateWithShared
import com.example.filman.ui.base.loadMoreMoviesForSection
import com.example.filman.ui.components.OverlayMenuData
import com.example.filman.ui.components.sections.MoviesSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

internal sealed interface TvShowsEvent : FilmanEvent {
    data object LoadHomeData : TvShowsEvent
    data class LoadMoreForSection(val sectionTitle: Int) : TvShowsEvent
}

@Immutable
internal data class TvShowsState(
    override val shared: SharedState = SharedState(),
) : StateWithShared<TvShowsState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

internal sealed interface TvShowsEffect {
    data object ScrollToTop : TvShowsEffect
    data object FocusFeaturedSection : TvShowsEffect
    data object NavigateToAuth : TvShowsEffect
    data class NavigateToDetails(val url: String) : TvShowsEffect
}

internal class TvShowsViewModel(
    private val scraper: FilmanScraper,
    favoritesManager: FavoritesManager,
) : BaseViewModel<TvShowsState, TvShowsEvent, TvShowsEffect>(
    initialState = TvShowsState(),
    favoritesManager = favoritesManager,
) {

    private var currentLoadJob: Job? = null

    override fun getAuthErrorEffect(): TvShowsEffect = TvShowsEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(url: String): TvShowsEffect = TvShowsEffect.NavigateToDetails(url)

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
            val newEpisodesDeferred = async {
                scraper.getCategoryPage(path = NEW_EPISODES_PATH)
            }
            val highestRatingDeferred = async {
                scraper.getCategoryPage(path = HIGHEST_RATING_PATH)
            }
            val recentlyAddedDeferred = async {
                scraper.getCategoryPage(path = RECENTLY_ADDED_PATH)
            }

            val (newEpisodesResult, highestRatingResult, recentlyAddedResult) = awaitAll(
                newEpisodesDeferred,
                highestRatingDeferred,
                recentlyAddedDeferred,
            )

            if (
                newEpisodesResult.errorMessage != null ||
                highestRatingResult.errorMessage != null ||
                recentlyAddedResult.errorMessage != null
            ) {
                updateSharedState {
                    it.copy(
                        isLoadingNextPage = false,
                        errorMessage = newEpisodesResult.errorMessage
                            ?: highestRatingResult.errorMessage
                            ?: recentlyAddedResult.errorMessage,
                    )
                }

                return@launchHandled
            }

            val featuredItems = newEpisodesResult.featuredItems +
                    highestRatingResult.featuredItems +
                    recentlyAddedResult.featuredItems

            updateSharedState {
                it.copy(
                    featuredItems = featuredItems.distinctBy { it.url },
                    moviesSections = buildList {
                        if (newEpisodesResult.movies.isNotEmpty()) {
                            add(
                                MoviesSection(
                                    title = R.string.new_episodes,
                                    movies = newEpisodesResult.movies,
                                    path = NEW_EPISODES_PATH,
                                    page = 1,
                                    hasMore = newEpisodesResult.movies.size >= 20,
                                ),
                            )
                        }
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
            sendEffect(TvShowsEffect.ScrollToTop)
            sendEffect(TvShowsEffect.FocusFeaturedSection)
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
                sectionTitle = sectionTitle
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
        const val BASE_PATH = "/seriale/category:all/"
        const val NEW_EPISODES_PATH = "${BASE_PATH}sort:newepisode/"
        const val HIGHEST_RATING_PATH = "${BASE_PATH}sort:rate/"
        const val RECENTLY_ADDED_PATH = "${BASE_PATH}sort:date/"
    }
}
