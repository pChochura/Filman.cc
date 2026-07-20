package com.example.filman.ui.movies

import androidx.compose.runtime.Immutable
import com.example.filman.R
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.model.MovieItem
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.ContextMenuActionHandler
import com.example.filman.ui.base.createStandardContextMenu
import com.example.filman.ui.base.loadMoreMoviesForSection
import com.example.filman.ui.components.OverlayMenuData
import com.example.filman.ui.components.sections.MoviesSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

internal sealed interface MoviesEvent {
    data object LoadHomeData : MoviesEvent
    data class LoadMoreForSection(val sectionTitle: Int) : MoviesEvent
    data class OpenMovieDetails(val url: String) : MoviesEvent
    data class RemoveFromFavorites(val url: String) : MoviesEvent
    data class AddToFavorites(val movie: MovieItem) : MoviesEvent

    data class OpenContextMenu(
        val title: String,
        val url: String,
        val posterUrl: String,
    ) : MoviesEvent

    data object CloseContextMenu : MoviesEvent
}

@Immutable
internal data class MoviesState(
    val isLoading: Boolean = true,
    val isLoadingNextPage: Boolean = false,
    val featuredItems: List<MovieItem> = emptyList(),
    val moviesSections: List<MoviesSection> = emptyList(),
    val errorMessage: String? = null,
    val overlayMenuData: OverlayMenuData? = null,
)

internal sealed interface MoviesEffect {
    data object ScrollToTop : MoviesEffect
    data object FocusFeaturedSection : MoviesEffect
    data object NavigateToAuth : MoviesEffect
    data class NavigateToDetails(val url: String) : MoviesEffect
}

internal class MoviesViewModel(
    private val scraper: FilmanScraper,
    private val favoritesManager: FavoritesManager,
) : BaseViewModel<MoviesState, MoviesEvent, MoviesEffect>(MoviesState()) {

    private var currentLoadJob: Job? = null

    override fun getAuthErrorEffect(): MoviesEffect = MoviesEffect.NavigateToAuth

    override fun onEvent(event: MoviesEvent) {
        when (event) {
            is MoviesEvent.LoadHomeData -> loadData()
            is MoviesEvent.LoadMoreForSection -> loadMoreForSection(event.sectionTitle)
            is MoviesEvent.OpenMovieDetails -> sendEffect(MoviesEffect.NavigateToDetails(event.url))
            is MoviesEvent.RemoveFromFavorites -> favoritesManager.removeFavorite(event.url)
            is MoviesEvent.AddToFavorites -> favoritesManager.addFavorite(event.movie)
            is MoviesEvent.OpenContextMenu -> updateState {
                it.copy(overlayMenuData = createOverlayMenuData(event))
            }

            is MoviesEvent.CloseContextMenu -> updateState { it.copy(overlayMenuData = null) }
        }
    }

    private fun createOverlayMenuData(event: MoviesEvent.OpenContextMenu) = createStandardContextMenu(
        title = event.title,
        url = event.url,
        posterUrl = event.posterUrl,
        isFavorite = favoritesManager.isFavorite(event.url),
        handler = object : ContextMenuActionHandler {
            override fun onRemoveFromFavorites(url: String) {
                onEvent(MoviesEvent.RemoveFromFavorites(url))
            }

            override fun onAddToFavorites(movie: MovieItem) {
                onEvent(MoviesEvent.AddToFavorites(movie))
            }

            override fun onCloseContextMenu() {
                onEvent(MoviesEvent.CloseContextMenu)
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
                updateState {
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

            updateState {
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
        updateState { it.copy(isLoadingNextPage = true) }

        launchHandled(
            onError = { t ->
                updateState { it.copy(isLoadingNextPage = false) }
                handleError(t)
            },
        ) {
            val updatedSections = scraper.loadMoreMoviesForSection(
                moviesSections = currentState.moviesSections,
                sectionTitle = sectionTitle
            )
            
            if (updatedSections != null) {
                updateState { state ->
                    state.copy(
                        moviesSections = updatedSections,
                        isLoadingNextPage = false,
                    )
                }
            } else {
                updateState { it.copy(isLoadingNextPage = false) }
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
