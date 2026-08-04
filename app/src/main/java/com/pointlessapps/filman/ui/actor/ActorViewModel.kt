package com.pointlessapps.filman.ui.actor

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.R
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.model.ActorDetails
import com.pointlessapps.filman.data.scraper.FilmanScraper
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.components.sections.MoviesSection
import com.pointlessapps.filman.ui.core.TextValue

internal sealed interface ActorEvent : FilmanEvent {
    data class LoadDetails(val url: String) : ActorEvent
    data object LoadNextPage : ActorEvent
}

@Immutable
internal data class ActorState(
    override val shared: SharedState = SharedState(),
    val actorDetails: ActorDetails? = null,
) : StateWithShared<ActorState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

internal sealed interface ActorEffect {
    data object NavigateToAuth : ActorEffect
    data class NavigateToDetails(val url: String) : ActorEffect
}

internal class ActorViewModel(
    private val scraper: FilmanScraper,
    favoritesManager: FavoritesManager,
    progressManager: ProgressManager,
) : BaseViewModel<ActorState, ActorEvent, ActorEffect>(
    initialState = ActorState(),
    favoritesManager = favoritesManager,
    progressManager = progressManager,
) {

    private var currentPage = 1
    private var currentUrl = ""
    private var canLoadMore = false

    override fun getAuthErrorEffect(): ActorEffect = ActorEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(
        url: String,
        autoplay: Boolean,
        episodeUrl: String?,
    ): ActorEffect = ActorEffect.NavigateToDetails(url)

    override fun handleEvent(event: ActorEvent) {
        when (event) {
            is ActorEvent.LoadDetails -> loadDetails(event.url)
            ActorEvent.LoadNextPage -> loadNextPage()
        }
    }

    private fun loadDetails(url: String) {
        currentPage = 1
        currentUrl = url
        canLoadMore = true

        updateState {
            it.copy(
                actorDetails = null,
                shared = it.shared.copy(
                    isLoading = false,
                    errorMessage = null,
                    moviesSections = emptyList(),
                ),
            )
        }

        launchHandled(
            onError = { t ->
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = t.message?.let(TextValue::DynamicString)
                            ?: TextValue.StringResource(R.string.error_unknown),
                    )
                }
                handleError(t)
            },
        ) {
            updateSharedState { it.copy(isLoading = true) }

            val details = scraper.getActorDetails(url, currentPage)

            if (details == null) {
                updateSharedState {
                    it.copy(
                        isLoadingNextPage = false,
                        errorMessage = TextValue.StringResource(R.string.error_unknown),
                    )
                }

                return@launchHandled
            }

            canLoadMore = details.moviesCast.isNotEmpty()

            updateState {
                it.copy(
                    shared = it.shared.copy(
                        isLoading = false,
                        moviesSections = buildList {
                            if (details.moviesDirector.isNotEmpty()) {
                                add(
                                    MoviesSection(
                                        title = R.string.details_movies_director,
                                        movies = details.moviesDirector,
                                    ),
                                )
                            }

                            if (details.moviesWriter.isNotEmpty()) {
                                add(
                                    MoviesSection(
                                        title = R.string.details_movies_writer,
                                        movies = details.moviesWriter,
                                    ),
                                )
                            }

                            if (details.moviesCast.isNotEmpty()) {
                                add(
                                    MoviesSection(
                                        title = R.string.details_movies_cast,
                                        movies = details.moviesCast,
                                    ),
                                )
                            }
                        },
                    ),
                    actorDetails = details,
                )
            }
        }
    }

    private fun loadNextPage() {
        if (!canLoadMore || state.value.shared.isLoadingNextPage || currentUrl.isEmpty()) {
            return
        }

        updateSharedState { it.copy(isLoadingNextPage = true) }

        launchHandled(
            onError = { t ->
                updateSharedState {
                    it.copy(
                        isLoadingNextPage = false,
                        errorMessage = t.message?.let(TextValue::DynamicString)
                            ?: TextValue.StringResource(R.string.error_unknown),
                    )
                }
                handleError(t)
            },
        ) {
            currentPage++
            val nextPageDetails = scraper.getActorDetails(currentUrl, currentPage)

            if (nextPageDetails == null || nextPageDetails.moviesCast.isEmpty()) {
                canLoadMore = false
                updateSharedState { it.copy(isLoadingNextPage = false) }
                return@launchHandled
            }

            updateState { currentState ->
                val currentDetails = currentState.actorDetails ?: return@updateState currentState
                val newMoviesCast = (currentDetails.moviesCast + nextPageDetails.moviesCast)
                    .distinctBy { it.url }

                currentState.copy(
                    shared = currentState.shared.copy(
                        isLoadingNextPage = false,
                        moviesSections = currentState.shared.moviesSections.map { section ->
                            if (section.title == R.string.details_movies_cast) {
                                section.copy(movies = newMoviesCast)
                            } else {
                                section
                            }
                        },
                    ),
                    actorDetails = currentDetails.copy(moviesCast = newMoviesCast),
                )
            }
        }
    }
}
