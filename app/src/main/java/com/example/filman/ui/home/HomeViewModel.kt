package com.example.filman.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.example.filman.R
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.model.MovieItem
import com.example.filman.data.model.ProgressItem
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.ContextMenuActionHandler
import com.example.filman.ui.base.createStandardContextMenu
import com.example.filman.ui.components.OverlayMenuData
import com.example.filman.ui.components.sections.MoviesSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal sealed interface HomeEvent {
    data object LoadHomeData : HomeEvent
    data class OpenMovieDetails(val url: String) : HomeEvent
    data class RemoveFromFavorites(val url: String) : HomeEvent
    data class AddToFavorites(val movie: MovieItem) : HomeEvent
    data class RemoveFromContinueWatching(val url: String) : HomeEvent

    data class OpenContextMenu(
        val title: String,
        val url: String,
        val posterUrl: String,
        val isInContinueWatching: Boolean,
    ) : HomeEvent

    data object CloseContextMenu : HomeEvent
}

@Immutable
internal data class HomeState(
    val isLoading: Boolean = true,
    val featuredItems: List<MovieItem> = emptyList(),
    val progressItems: List<ProgressItem.InProgress> = emptyList(),
    val favorites: List<MovieItem> = emptyList(),
    val moviesSections: List<MoviesSection> = emptyList(),
    val errorMessage: String? = null,
    val overlayMenuData: OverlayMenuData? = null,
)

sealed interface HomeEffect {
    data object ScrollToTop : HomeEffect
    data object FocusFeaturedSection : HomeEffect
    data object FocusFirstGridItem : HomeEffect
    data object NavigateToAuth : HomeEffect
    data class NavigateToDetails(val url: String) : HomeEffect
}

internal class HomeViewModel(
    private val scraper: FilmanScraper,
    private val favoritesManager: FavoritesManager,
    private val progressManager: ProgressManager,
) : BaseViewModel<HomeState, HomeEvent, HomeEffect>(HomeState()) {

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
                            .distinctBy { p -> p.parentUrl ?: p.url }
                    )
                }
            }
        }
    }

    override fun getAuthErrorEffect(): HomeEffect = HomeEffect.NavigateToAuth

    override fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.LoadHomeData -> loadData()
            is HomeEvent.OpenMovieDetails -> sendEffect(HomeEffect.NavigateToDetails(event.url))
            is HomeEvent.RemoveFromFavorites -> favoritesManager.removeFavorite(event.url)
            is HomeEvent.AddToFavorites -> favoritesManager.addFavorite(event.movie)
            is HomeEvent.RemoveFromContinueWatching -> removeFromProgress(event.url)
            is HomeEvent.OpenContextMenu -> updateState {
                it.copy(overlayMenuData = createOverlayMenuData(event))
            }

            is HomeEvent.CloseContextMenu -> updateState { it.copy(overlayMenuData = null) }
        }
    }

    private fun createOverlayMenuData(event: HomeEvent.OpenContextMenu) = createStandardContextMenu(
        title = event.title,
        url = event.url,
        posterUrl = event.posterUrl,
        isFavorite = favoritesManager.isFavorite(event.url),
        isInContinueWatching = event.isInContinueWatching,
        handler = object : ContextMenuActionHandler {
            override fun onRemoveFromFavorites(url: String) {
                onEvent(HomeEvent.RemoveFromFavorites(url))
            }

            override fun onAddToFavorites(movie: MovieItem) {
                onEvent(HomeEvent.AddToFavorites(movie))
            }

            override fun onRemoveFromContinueWatching(url: String) {
                onEvent(HomeEvent.RemoveFromContinueWatching(url))
            }

            override fun onCloseContextMenu() {
                onEvent(HomeEvent.CloseContextMenu)
            }
        }
    )

    private fun loadData() {
        if (currentState.moviesSections.isNotEmpty()) return

        updateState {
            it.copy(
                isLoading = true,
                errorMessage = null,
            )
        }

        currentLoadJob?.cancel()
        currentLoadJob = launchHandled(
            onError = { t ->
                updateState {
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
                updateState {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.errorMessage,
                    )
                }
            } else {
                updateState {
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

    private fun removeFromProgress(url: String) {
        progressManager.removeProgress(url)
    }

    private companion object {
        const val PATH = "/"
    }
}
