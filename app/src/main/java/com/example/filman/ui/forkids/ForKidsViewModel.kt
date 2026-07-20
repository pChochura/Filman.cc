package com.example.filman.ui.forkids

import androidx.compose.runtime.Immutable
import com.example.filman.R
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.model.MovieItem
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.ContextMenuActionHandler
import com.example.filman.ui.base.createStandardContextMenu
import com.example.filman.ui.components.OverlayMenuData
import com.example.filman.ui.components.sections.MoviesSection
import kotlinx.coroutines.Job

internal sealed interface ForKidsEvent {
    data object LoadHomeData : ForKidsEvent
    data object LoadNextPageData : ForKidsEvent
    data class OpenMovieDetails(val url: String) : ForKidsEvent
    data class RemoveFromFavorites(val url: String) : ForKidsEvent
    data class AddToFavorites(val movie: MovieItem) : ForKidsEvent

    data class OpenContextMenu(
        val title: String,
        val url: String,
        val posterUrl: String,
    ) : ForKidsEvent

    data object CloseContextMenu : ForKidsEvent
}

@Immutable
internal data class ForKidsState(
    val isLoading: Boolean = true,
    val isLoadingNextPage: Boolean = false,
    val featuredItems: List<MovieItem> = emptyList(),
    val moviesSections: List<MoviesSection> = emptyList(),
    val currentPage: Int = 1,
    val errorMessage: String? = null,
    val overlayMenuData: OverlayMenuData? = null,
)

internal sealed interface ForKidsEffect {
    data object ScrollToTop : ForKidsEffect
    data object FocusFeaturedSection : ForKidsEffect
    data object NavigateToAuth : ForKidsEffect
    data class NavigateToDetails(val url: String) : ForKidsEffect
}

internal class ForKidsViewModel(
    private val scraper: FilmanScraper,
    private val favoritesManager: FavoritesManager,
) : BaseViewModel<ForKidsState, ForKidsEvent, ForKidsEffect>(ForKidsState()) {

    private var currentLoadJob: Job? = null

    override fun getAuthErrorEffect(): ForKidsEffect = ForKidsEffect.NavigateToAuth

    override fun onEvent(event: ForKidsEvent) {
        when (event) {
            is ForKidsEvent.LoadHomeData -> loadData()
            is ForKidsEvent.LoadNextPageData -> loadNextPageData()
            is ForKidsEvent.OpenMovieDetails -> sendEffect(
                ForKidsEffect.NavigateToDetails(event.url),
            )

            is ForKidsEvent.RemoveFromFavorites -> favoritesManager.removeFavorite(event.url)
            is ForKidsEvent.AddToFavorites -> favoritesManager.addFavorite(event.movie)
            is ForKidsEvent.OpenContextMenu -> updateState {
                it.copy(overlayMenuData = createOverlayMenuData(event))
            }

            is ForKidsEvent.CloseContextMenu -> updateState { it.copy(overlayMenuData = null) }
        }
    }

    private fun createOverlayMenuData(event: ForKidsEvent.OpenContextMenu) = createStandardContextMenu(
        title = event.title,
        url = event.url,
        posterUrl = event.posterUrl,
        isFavorite = favoritesManager.isFavorite(event.url),
        handler = object : ContextMenuActionHandler {
            override fun onRemoveFromFavorites(url: String) {
                onEvent(ForKidsEvent.RemoveFromFavorites(url))
            }

            override fun onAddToFavorites(movie: MovieItem) {
                onEvent(ForKidsEvent.AddToFavorites(movie))
            }

            override fun onCloseContextMenu() {
                onEvent(ForKidsEvent.CloseContextMenu)
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
                                title = R.string.home_for_kids,
                                movies = result.movies,
                            ),
                        ),
                        currentPage = 1,
                        isLoading = false,
                    )
                }

                sendEffect(ForKidsEffect.ScrollToTop)
                sendEffect(ForKidsEffect.FocusFeaturedSection)
            }
        }
    }

    private fun loadNextPageData() {
        updateState { it.copy(isLoadingNextPage = true) }

        currentLoadJob?.cancel()
        currentLoadJob = launchHandled(
            onError = { t ->
                updateState { it.copy(isLoadingNextPage = false) }
                handleError(t)
            },
        ) {
            val result = scraper.getCategoryPage(
                path = PATH,
                page = currentState.currentPage + 1,
            )
            updateState {
                it.copy(
                    moviesSections = it.moviesSections.map { section ->
                        section.copy(movies = section.movies + result.movies)
                    },
                    currentPage = it.currentPage + 1,
                    isLoadingNextPage = false,
                )
            }
        }
    }

    private companion object {
        const val PATH = "/dla-dzieci-pl/"
    }
}
