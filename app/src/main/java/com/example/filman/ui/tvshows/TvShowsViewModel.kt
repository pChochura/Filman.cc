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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface TvShowsEvent {
    data object LoadHomeData : TvShowsEvent
    data class LoadMoreForSection(val sectionTitle: Int) : TvShowsEvent
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
    val errorMessage: String? = null,
    val overlayMenuData: OverlayMenuData? = null,
)

internal sealed interface TvShowsEffect {
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
            is TvShowsEvent.LoadMoreForSection -> loadMoreForSection(event.sectionTitle)
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
                _state.update {
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

            _state.update {
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
                    currentPage = 1,
                    isLoading = false,
                )
            }
            _effect.send(TvShowsEffect.ScrollToTop)
            _effect.send(TvShowsEffect.FocusFeaturedSection)
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
            _effect.trySend(TvShowsEffect.NavigateToAuth)
        }
    }

    private companion object {
        const val BASE_PATH = "/seriale/category:all/"
        const val NEW_EPISODES_PATH = "${BASE_PATH}sort:newepisode/"
        const val HIGHEST_RATING_PATH = "${BASE_PATH}sort:rate/"
        const val RECENTLY_ADDED_PATH = "${BASE_PATH}sort:date/"
    }
}
