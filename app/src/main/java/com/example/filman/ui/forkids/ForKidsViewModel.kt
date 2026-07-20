package com.example.filman.ui.forkids

import androidx.compose.runtime.Immutable
import com.example.filman.R
import com.example.filman.config.FilmanConfig
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

internal sealed interface ForKidsEvent : FilmanEvent {
    data object LoadHomeData : ForKidsEvent
    data class LoadMoreForSection(val sectionTitle: Int) : ForKidsEvent
}

@Immutable
internal data class ForKidsState(
    override val shared: SharedState = SharedState(),
) : StateWithShared<ForKidsState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

internal sealed interface ForKidsEffect {
    data object ScrollToTop : ForKidsEffect
    data object FocusFeaturedSection : ForKidsEffect
    data object NavigateToAuth : ForKidsEffect
    data class NavigateToDetails(val url: String) : ForKidsEffect
}

internal class ForKidsViewModel(
    private val scraper: FilmanScraper,
    favoritesManager: FavoritesManager,
) : BaseViewModel<ForKidsState, ForKidsEvent, ForKidsEffect>(
    initialState = ForKidsState(),
    favoritesManager = favoritesManager,
) {

    private var currentLoadJob: Job? = null

    override fun getAuthErrorEffect(): ForKidsEffect = ForKidsEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(url: String): ForKidsEffect =
        ForKidsEffect.NavigateToDetails(url)

    override fun handleEvent(event: ForKidsEvent) {
        when (event) {
            is ForKidsEvent.LoadHomeData -> loadData()
            is ForKidsEvent.LoadMoreForSection -> loadMoreForSection(event.sectionTitle)
        }
    }

    override fun handleStaleData(staleData: Any) {
        val result = staleData as? PageResult ?: return
        updateSharedState {
            it.copy(
                featuredItems = result.featuredItems,
                moviesSections = listOf(
                    MoviesSection(
                        title = R.string.most_viewed,
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
            val mostViewedResult = scraper.getCategoryPage(
                path = "${FilmanConfig.PATH_FOR_KIDS}${FilmanConfig.SORT_VIEW}",
            )

            if (mostViewedResult.errorMessage != null) {
                updateSharedState {
                    it.copy(
                        isLoadingNextPage = false,
                        errorMessage = mostViewedResult.errorMessage,
                    )
                }

                return@launchHandled
            }

            updateSharedState {
                it.copy(
                    featuredItems = mostViewedResult.featuredItems,
                    moviesSections = buildList {
                        if (mostViewedResult.movies.isNotEmpty()) {
                            add(
                                MoviesSection(
                                    title = R.string.most_viewed,
                                    movies = mostViewedResult.movies,
                                    path = "${FilmanConfig.PATH_FOR_KIDS}${FilmanConfig.SORT_VIEW}",
                                    page = 1,
                                    hasMore = mostViewedResult.movies.size >= 20,
                                ),
                            )
                        }
                    },
                    isLoading = false,
                )
            }
            sendEffect(ForKidsEffect.ScrollToTop)
            sendEffect(ForKidsEffect.FocusFeaturedSection)
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
}
