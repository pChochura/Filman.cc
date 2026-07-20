package com.example.filman.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.example.filman.R
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.model.MovieItem
import com.example.filman.data.model.PageResult
import com.example.filman.data.model.ProgressItem
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.base.SharedState
import com.example.filman.ui.base.StateWithShared
import com.example.filman.ui.components.sections.MoviesSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal sealed interface HomeEvent : FilmanEvent {
    data object LoadHomeData : HomeEvent
}

@Immutable
internal data class HomeState(
    override val shared: SharedState = SharedState(),
    val progressItems: List<ProgressItem.InProgress> = emptyList(),
    val favorites: List<MovieItem> = emptyList(),
) : StateWithShared<HomeState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

sealed interface HomeEffect {
    data object ScrollToTop : HomeEffect
    data object FocusFeaturedSection : HomeEffect
    data object FocusFirstGridItem : HomeEffect
    data object NavigateToAuth : HomeEffect
    data class NavigateToDetails(val url: String) : HomeEffect
}

internal class HomeViewModel(
    private val scraper: FilmanScraper,
    favoritesManager: FavoritesManager,
    progressManager: ProgressManager,
) : BaseViewModel<HomeState, HomeEvent, HomeEffect>(
    initialState = HomeState(),
    favoritesManager = favoritesManager,
    progressManager = progressManager,
) {

    private var currentLoadJob: Job? = null

    init {
        viewModelScope.launch {
            favoritesManager.favoritesFlow.collect { list ->
                updateState { it.copy(favorites = list) }
            }
        }
        viewModelScope.launch {
            progressManager.progressItemsFlow.collect { list ->
                updateState {
                    it.copy(
                        progressItems = list.filterIsInstance<ProgressItem.InProgress>()
                            .filter { p -> p.progressPercentage < 0.95f }
                            .distinctBy { p -> p.parentUrl ?: p.url },
                    )
                }
            }
        }
    }

    override fun getAuthErrorEffect(): HomeEffect = HomeEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(url: String): HomeEffect =
        HomeEffect.NavigateToDetails(url)

    override fun handleEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.LoadHomeData -> loadData()
        }
    }

    override fun handleStaleData(staleData: Any) {
        val result = staleData as? PageResult ?: return
        updateSharedState {
            it.copy(
                featuredItems = result.featuredItems,
                moviesSections = listOf(
                    MoviesSection(
                        title = R.string.home_recommended,
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
            val result = scraper.getCategoryPage(PATH)
            if (result.errorMessage != null) {
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.errorMessage,
                    )
                }
            } else {
                updateSharedState {
                    it.copy(
                        featuredItems = result.featuredItems,
                        moviesSections = listOf(
                            MoviesSection(
                                title = R.string.home_recommended,
                                movies = result.movies,
                            ),
                        ),
                        isLoading = false,
                    )
                }
                sendEffect(HomeEffect.ScrollToTop)
                sendEffect(HomeEffect.FocusFeaturedSection)
            }
        }
    }

    private companion object {
        const val PATH = "/"
    }
}
