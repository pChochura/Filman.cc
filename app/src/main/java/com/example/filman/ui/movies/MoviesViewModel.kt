package com.example.filman.ui.movies

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filman.R
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.model.MovieItem
import com.example.filman.data.scraper.AuthException
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.components.FilmanOverlayMenuItem
import com.example.filman.ui.components.OverlayMenuData
import com.example.filman.ui.components.sections.MoviesSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) : ViewModel() {

    private val _state = MutableStateFlow(MoviesState())
    val state: StateFlow<MoviesState> = _state.asStateFlow()

    private val _effect = Channel<MoviesEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var currentLoadJob: Job? = null

    fun onEvent(event: MoviesEvent) {
        when (event) {
            is MoviesEvent.LoadHomeData -> loadData()
            is MoviesEvent.LoadMoreForSection -> loadMoreForSection(event.sectionTitle)
            is MoviesEvent.OpenMovieDetails -> _effect.trySend(MoviesEffect.NavigateToDetails(event.url))
            is MoviesEvent.RemoveFromFavorites -> favoritesManager.removeFavorite(event.url)
            is MoviesEvent.AddToFavorites -> favoritesManager.addFavorite(event.movie)
            is MoviesEvent.OpenContextMenu -> _state.update {
                it.copy(overlayMenuData = createOverlayMenuData(event))
            }

            is MoviesEvent.CloseContextMenu -> _state.update { it.copy(overlayMenuData = null) }
        }
    }

    private fun createOverlayMenuData(event: MoviesEvent.OpenContextMenu) =
        OverlayMenuData(
            title = event.title,
            items = buildList {
                if (favoritesManager.isFavorite(event.url)) {
                    add(
                        FilmanOverlayMenuItem.Button(
                            label = R.string.remove_from_favorites,
                            onClick = {
                                onEvent(MoviesEvent.RemoveFromFavorites(event.url))
                                onEvent(MoviesEvent.CloseContextMenu)
                            },
                        ),
                    )
                } else {
                    add(
                        FilmanOverlayMenuItem.Button(
                            label = R.string.add_to_favorites,
                            onClick = {
                                onEvent(
                                    MoviesEvent.AddToFavorites(
                                        MovieItem(
                                            url = event.url,
                                            titlePl = event.title,
                                            posterUrl = event.posterUrl,
                                        ),
                                    ),
                                )
                                onEvent(MoviesEvent.CloseContextMenu)
                            },
                        ),
                    )
                }
            },
        )

    private fun loadData() {
        if (_state.value.moviesSections.isNotEmpty()) return

        _state.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
            )
        }

        currentLoadJob?.cancel()
        currentLoadJob = launchHandled(
            onError = { t ->
                handleError(t)
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = t.message ?: "Unknown error",
                    )
                }
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
                _state.update {
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

            _state.update {
                it.copy(
                    featuredItems = featuredItems.distinctBy { it.url },
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
            _effect.send(MoviesEffect.ScrollToTop)
            _effect.send(MoviesEffect.FocusFeaturedSection)
        }
    }

    private fun loadMoreForSection(sectionTitle: Int) {
        if (_state.value.isLoadingNextPage) return
        val section = _state.value.moviesSections.find { it.title == sectionTitle }
        if (section == null || section.path == null || !section.hasMore) return

        _state.update { it.copy(isLoadingNextPage = true) }

        launchHandled(
            onError = { t ->
                handleError(t)
                _state.update { it.copy(isLoadingNextPage = false) }
            },
        ) {
            val nextPage = section.page + 1
            val newMovies = scraper.getCategoryPage(path = section.path, page = nextPage).movies

            _state.update { state ->
                val updatedSections = state.moviesSections.map { s ->
                    if (s.title == sectionTitle) {
                        s.copy(
                            movies = (s.movies + newMovies).distinctBy { m -> m.url },
                            page = nextPage,
                            hasMore = newMovies.isNotEmpty(),
                        )
                    } else {
                        s
                    }
                }
                state.copy(
                    moviesSections = updatedSections,
                    isLoadingNextPage = false,
                )
            }
        }
    }

    private fun launchHandled(
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit,
    ) = viewModelScope.launch {
        runCatching { block() }.onFailure { t ->
            onError?.invoke(t) ?: handleError(t)
        }
    }

    private fun handleError(t: Throwable) {
        if (t is AuthException) {
            _effect.trySend(MoviesEffect.NavigateToAuth)
        }
    }

    private companion object {
        const val BASE_PATH = "/filmy/"
        const val HIGHEST_RATING_PATH = "${BASE_PATH}sort:filmweb/"
        const val MOST_VIEWED_PATH = "${BASE_PATH}sort:view/"
        const val RECENTLY_ADDED_PATH = "${BASE_PATH}sort:date/"
    }
}
