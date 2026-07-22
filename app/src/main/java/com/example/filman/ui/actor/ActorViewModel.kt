package com.example.filman.ui.actor

import androidx.compose.runtime.Immutable
import com.example.filman.R
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.model.ActorDetails
import com.example.filman.data.source.ContentSource
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.base.SharedState
import com.example.filman.ui.base.StateWithShared
import com.example.filman.ui.components.sections.MoviesSection

internal sealed interface ActorEvent : FilmanEvent {
    data class LoadDetails(val url: String) : ActorEvent
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
    private val scraper: ContentSource,
    favoritesManager: FavoritesManager,
    progressManager: ProgressManager,
) : BaseViewModel<ActorState, ActorEvent, ActorEffect>(
    initialState = ActorState(),
    favoritesManager = favoritesManager,
    progressManager = progressManager,
) {

    override fun getAuthErrorEffect(): ActorEffect = ActorEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(url: String): ActorEffect =
        ActorEffect.NavigateToDetails(url)

    override fun handleEvent(event: ActorEvent) {
        when (event) {
            is ActorEvent.LoadDetails -> loadDetails(event.url)
        }
    }

    private fun loadDetails(url: String) {
        updateSharedState {
            it.copy(
                isLoading = true,
                errorMessage = null,
            )
        }

        launchHandled(
            onError = { t ->
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = t.message ?: "Unknown error",
                    )
                }
                handleError(t)
            },
        ) {
            updateSharedState { it.copy(isLoading = true) }

            val details = scraper.getActorDetails(url)

            if (details == null) {
                updateSharedState {
                    it.copy(
                        isLoadingNextPage = false,
                        errorMessage = "Unknown error",
                    )
                }

                return@launchHandled
            }

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
}
