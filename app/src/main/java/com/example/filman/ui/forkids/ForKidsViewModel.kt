package com.example.filman.ui.forkids

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

sealed interface ForKidsEvent {
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
    val overlayMenuData: OverlayMenuData? = null,
)

sealed interface ForKidsEffect {
    data object ScrollToTop : ForKidsEffect
    data object FocusFeaturedSection : ForKidsEffect
    data object NavigateToAuth : ForKidsEffect
    data class NavigateToDetails(val url: String) : ForKidsEffect
}

internal class ForKidsViewModel(
    private val scraper: FilmanScraper,
    private val favoritesManager: FavoritesManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ForKidsState())
    val state: StateFlow<ForKidsState> = _state.asStateFlow()

    private val _effect = Channel<ForKidsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var currentLoadJob: Job? = null

    fun onEvent(event: ForKidsEvent) {
        when (event) {
            is ForKidsEvent.LoadHomeData -> loadData()
            is ForKidsEvent.LoadNextPageData -> loadNextPageData()
            is ForKidsEvent.OpenMovieDetails -> _effect.trySend(
                ForKidsEffect.NavigateToDetails(event.url),
            )

            is ForKidsEvent.RemoveFromFavorites -> favoritesManager.removeFavorite(event.url)
            is ForKidsEvent.AddToFavorites -> favoritesManager.addFavorite(event.movie)
            is ForKidsEvent.OpenContextMenu -> _state.update {
                it.copy(overlayMenuData = createOverlayMenuData(event))
            }

            is ForKidsEvent.CloseContextMenu -> _state.update { it.copy(overlayMenuData = null) }
        }
    }

    private fun createOverlayMenuData(event: ForKidsEvent.OpenContextMenu) = OverlayMenuData(
        title = event.title,
        items = buildList {
            if (favoritesManager.isFavorite(event.url)) {
                add(
                    FilmanOverlayMenuItem.Button(
                        label = R.string.remove_from_favorites,
                        onClick = {
                            onEvent(ForKidsEvent.RemoveFromFavorites(event.url))
                            onEvent(ForKidsEvent.CloseContextMenu)
                        },
                    ),
                )
            } else {
                add(
                    FilmanOverlayMenuItem.Button(
                        label = R.string.add_to_favorites,
                        onClick = {
                            onEvent(
                                ForKidsEvent.AddToFavorites(
                                    MovieItem(
                                        url = event.url,
                                        titlePl = event.title,
                                        posterUrl = event.posterUrl,
                                    ),
                                ),
                            )
                            onEvent(ForKidsEvent.CloseContextMenu)
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
                            title = R.string.home_for_kids,
                            movies = result.movies,
                        ),
                    ),
                    currentPage = 1,
                    isLoading = false,
                )
            }

            _effect.send(ForKidsEffect.ScrollToTop)
            _effect.send(ForKidsEffect.FocusFeaturedSection)
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
            _effect.trySend(ForKidsEffect.NavigateToAuth)
        }
    }

    private companion object {
        const val PATH = "/dla-dzieci-pl/"
    }
}
