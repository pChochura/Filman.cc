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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MoviesEvent {
    data object LoadHomeData : MoviesEvent
    data object LoadNextPageData : MoviesEvent
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
    val currentPage: Int = 1,
    val overlayMenuData: OverlayMenuData? = null,
)

sealed interface MoviesEffect {
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
            is MoviesEvent.LoadNextPageData -> loadNextPageData()
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

        _state.update { it.copy(isLoading = true) }

        currentLoadJob?.cancel()
        currentLoadJob = launchHandled(
            onError = { t ->
                handleError(t)
                _state.update { it.copy(isLoading = false) }
            },
        ) {
            val result = scraper.getCategoryPage(PATH)
            _state.update {
                it.copy(
                    featuredItems = result.featuredItems,
                    moviesSections = listOf(
                        MoviesSection(
                            title = R.string.home_movies,
                            movies = result.movies,
                        ),
                    ),
                    currentPage = 1,
                    isLoading = false,
                )
            }

            _effect.send(MoviesEffect.ScrollToTop)
            _effect.send(MoviesEffect.FocusFeaturedSection)
        }
    }

    private fun loadNextPageData() {
        _state.update { it.copy(isLoadingNextPage = true) }

        currentLoadJob?.cancel()
        currentLoadJob = launchHandled(
            onError = { t ->
                handleError(t)
                _state.update { it.copy(isLoadingNextPage = false) }
            },
        ) {
            val result = scraper.getCategoryPage(
                path = PATH,
                page = _state.value.currentPage + 1,
            )
            _state.update {
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
        const val PATH = "/filmy/"
    }
}
