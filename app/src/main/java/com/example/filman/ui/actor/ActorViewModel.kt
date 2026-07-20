package com.example.filman.ui.actor

import androidx.compose.runtime.Immutable
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.model.ActorDetails
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.base.SharedState
import com.example.filman.ui.base.StateWithShared

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
    private val scraper: FilmanScraper,
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
        launchHandled(
            onError = {
                updateSharedState { it.copy(isLoading = false) }
                handleError(it)
            },
        ) {
            updateSharedState { it.copy(isLoading = true) }

            val details = scraper.getActorDetails(url)

            updateState {
                it.copy(
                    shared = it.shared.copy(isLoading = false),
                    actorDetails = details,
                )
            }
        }
    }
}
