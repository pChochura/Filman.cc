package com.example.filman.ui.forkids

import androidx.compose.runtime.Immutable
import com.example.filman.R
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.model.MovieItem
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.base.loadMoreMoviesForSection
import com.example.filman.ui.components.OverlayMenuData
import com.example.filman.ui.components.sections.MoviesSection
import kotlinx.coroutines.Job

internal sealed interface ForKidsEvent : FilmanEvent {
    data object LoadHomeData : ForKidsEvent
    data class LoadMoreForSection(val sectionTitle: Int) : ForKidsEvent
}

@Immutable
internal data class ForKidsState(
    val isLoading: Boolean = true,
    val isLoadingNextPage: Boolean = false,
    val featuredItems: List<MovieItem> = emptyList(),
    val moviesSections: List<MoviesSection> = emptyList(),
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
    favoritesManager: FavoritesManager,
) : BaseViewModel<ForKidsState, ForKidsEvent, ForKidsEffect>(
    initialState = ForKidsState(),
    favoritesManager = favoritesManager,
) {

    private var currentLoadJob: Job? = null

    override fun getAuthErrorEffect(): ForKidsEffect = ForKidsEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(url: String): ForKidsEffect = ForKidsEffect.NavigateToDetails(url)

    override fun setOverlayMenuData(data: OverlayMenuData?) {
        updateState { it.copy(overlayMenuData = data) }
    }

    override fun handleEvent(event: ForKidsEvent) {
        when (event) {
            is ForKidsEvent.LoadHomeData -> loadData()
            is ForKidsEvent.LoadMoreForSection -> loadMoreForSection(event.sectionTitle)
        }
    }

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
            val mostViewedResult = scraper.getCategoryPage(path = MOST_VIEWED_PATH)

            if (mostViewedResult.errorMessage != null) {
                updateState {
                    it.copy(
                        isLoadingNextPage = false,
                        errorMessage = mostViewedResult.errorMessage,
                    )
                }

                return@launchHandled
            }

            updateState {
                it.copy(
                    featuredItems = mostViewedResult.featuredItems,
                    moviesSections = buildList {
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
        const val BASE_PATH = "/filmy/category:12/"
        const val MOST_VIEWED_PATH = "${BASE_PATH}sort:view/"
    }
}
