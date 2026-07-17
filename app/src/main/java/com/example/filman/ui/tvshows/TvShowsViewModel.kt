package com.example.filman.ui.tvshows

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

sealed interface TvShowsEvent {
    data object LoadHomeData : TvShowsEvent
    data object LoadNextPageData : TvShowsEvent
    data class OpenMovieDetails(val url: String) : TvShowsEvent
    data class RemoveFromFavorites(val url: String) : TvShowsEvent
    data class AddToFavorites(val movie: MovieItem) : TvShowsEvent

    data class OpenContextMenu(
        val title: String,
        val url: String,
        val posterUrl: String,
    ) : TvShowsEvent

    data object CloseContextMenu : TvShowsEvent
}

@Immutable
internal data class TvShowsState(
    val isLoading: Boolean = true,
    val isLoadingNextPage: Boolean = false,
    val featuredItems: List<MovieItem> = emptyList(),
    val moviesSections: List<MoviesSection> = emptyList(),
    val currentPage: Int = 1,
    val overlayMenuData: OverlayMenuData? = null,
)

sealed interface TvShowsEffect {
    data object ScrollToTop : TvShowsEffect
    data object FocusFeaturedSection : TvShowsEffect
    data object NavigateToAuth : TvShowsEffect
    data class NavigateToDetails(val url: String) : TvShowsEffect
}

internal class TvShowsViewModel(
    private val scraper: FilmanScraper,
    private val favoritesManager: FavoritesManager,
) : ViewModel() {

    private val _state = MutableStateFlow(TvShowsState())
    val state: StateFlow<TvShowsState> = _state.asStateFlow()

    private val _effect = Channel<TvShowsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var currentLoadJob: Job? = null

    fun onEvent(event: TvShowsEvent) {
        when (event) {
            is TvShowsEvent.LoadHomeData -> loadData()
            is TvShowsEvent.LoadNextPageData -> loadNextPageData()
            is TvShowsEvent.OpenMovieDetails -> _effect.trySend(
                TvShowsEffect.NavigateToDetails(event.url),
            )

            is TvShowsEvent.RemoveFromFavorites -> favoritesManager.removeFavorite(event.url)
            is TvShowsEvent.AddToFavorites -> favoritesManager.addFavorite(event.movie)
            is TvShowsEvent.OpenContextMenu -> _state.update {
                it.copy(overlayMenuData = createOverlayMenuData(event))
            }

            is TvShowsEvent.CloseContextMenu -> _state.update { it.copy(overlayMenuData = null) }
        }
    }

    private fun createOverlayMenuData(event: TvShowsEvent.OpenContextMenu) =
        OverlayMenuData(
            title = event.title,
            items = buildList {
                if (favoritesManager.isFavorite(event.url)) {
                    add(
                        FilmanOverlayMenuItem.Button(
                            label = R.string.remove_from_favorites,
                            onClick = {
                                onEvent(TvShowsEvent.RemoveFromFavorites(event.url))
                                onEvent(TvShowsEvent.CloseContextMenu)
                            },
                        ),
                    )
                } else {
                    add(
                        FilmanOverlayMenuItem.Button(
                            label = R.string.add_to_favorites,
                            onClick = {
                                onEvent(
                                    TvShowsEvent.AddToFavorites(
                                        MovieItem(
                                            url = event.url,
                                            titlePl = event.title,
                                            posterUrl = event.posterUrl,
                                        ),
                                    ),
                                )
                                onEvent(TvShowsEvent.CloseContextMenu)
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
            val featured = scraper.getFeaturedItems(PATH)
            val movies = scraper.getCategoryMovies(PATH)
            _state.update {
                it.copy(
                    featuredItems = featured,
                    moviesSections = listOf(
                        MoviesSection(
                            title = R.string.home_tv_shows,
                            movies = movies,
                        ),
                    ),
                    currentPage = 1,
                    isLoading = false,
                )
            }

            _effect.send(TvShowsEffect.ScrollToTop)
            _effect.send(TvShowsEffect.FocusFeaturedSection)
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
            val movies = scraper.getCategoryMovies(
                path = PATH,
                page = _state.value.currentPage + 1,
            )
            _state.update {
                it.copy(
                    moviesSections = it.moviesSections.map { section ->
                        section.copy(movies = section.movies + movies)
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
            _effect.trySend(TvShowsEffect.NavigateToAuth)
        }
    }

    private companion object {
        const val PATH = "/seriale/category:all"
    }
}
