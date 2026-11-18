package com.pointlessapps.filman.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.ProgressItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

abstract class BaseViewModel<State : StateWithShared<State>, Event : FilmanEvent, Effect>(
    initialState: State,
    protected val favoritesManager: FavoritesManager? = null,
    protected val watchlistManager: WatchlistManager? = null,
    protected val progressManager: ProgressManager? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effect = Channel<Effect>(Channel.UNLIMITED)
    val effect = _effect.receiveAsFlow()

    protected val currentState: State get() = _state.value

    init {
        viewModelScope.launch {
            progressManager?.progressItemsFlow?.collect { list ->
                val map = buildMap {
                    list.reversed().forEach {
                        put(it.url, it.progressPercentage)
                        if (it.parentUrl != null) {
                            val progress = if (it is ProgressItem.Watched && it.hasNextEpisode) {
                                0f
                            } else {
                                it.progressPercentage
                            }
                            put(it.parentUrl!!, progress)
                        }
                    }
                }
                updateSharedState { it.copy(progressMap = map) }
            }
        }
    }

    protected fun updateState(updater: (State) -> State) {
        _state.update(updater)
    }

    protected fun updateSharedState(updater: (SharedState) -> SharedState) {
        updateState { it.copyWithShared(updater(it.shared)) }
    }

    protected fun sendEffect(effect: Effect) {
        _effect.trySend(effect)
    }

    fun onEvent(event: FilmanEvent) {
        if (event is BaseEvent) {
            handleBaseEvent(event)
        } else {
            @Suppress("UNCHECKED_CAST")
            handleEvent(event as Event)
        }
    }

    protected open fun handleBaseEvent(event: BaseEvent) {
        when (event) {
            is BaseEvent.OpenMovieDetails -> {
                getNavigateToDetailsEffect(
                    event.url,
                    event.autoplay,
                    event.episodeUrl,
                    event.request,
                )?.let(::sendEffect)
            }

            is BaseEvent.RemoveFromFavorites -> favoritesManager?.removeFavorite(event.url)
            is BaseEvent.AddToFavorites -> favoritesManager?.addFavorite(event.movie)
            is BaseEvent.RemoveFromWatchlist -> watchlistManager?.removeFromWatchlist(event.url)
            is BaseEvent.AddToWatchlist -> watchlistManager?.addToWatchlist(event.movie)
            is BaseEvent.RemoveFromContinueWatching -> progressManager?.removeProgress(event.url)
            is BaseEvent.MarkAsWatched -> progressManager?.markAsWatched(event.movie)
            is BaseEvent.MarkAsNotWatched -> progressManager?.markAsNotWatched(event.url)
            is BaseEvent.MarkPreviousAsWatched -> Unit
            is BaseEvent.OpenContextMenu -> {
                val menuData = buildStandardOverlayContextMenu(
                    movie = event.movie,
                    progressMap = currentState.shared.progressMap,
                    favoritesManager = favoritesManager,
                    watchlistManager = watchlistManager,
                    options = event.options,
                    onEvent = ::onEvent,
                )
                updateSharedState { it.copy(overlayMenuData = menuData) }
            }

            is BaseEvent.CloseContextMenu -> {
                updateSharedState { it.copy(overlayMenuData = null) }
            }
        }
    }

    protected abstract fun handleEvent(event: Event)

    protected open fun getNavigateToDetailsEffect(
        url: String,
        autoplay: Boolean,
        episodeUrl: String? = null,
        request: DetailsRequest? = null,
    ): Effect? = null

    protected abstract fun getAuthErrorEffect(): Effect?

    protected fun launchHandled(
        context: CoroutineContext = EmptyCoroutineContext,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit,
    ) = viewModelScope.launch(context) {
        runCatching { block() }.onFailure { t ->
            onError?.invoke(t) ?: handleError(t)
        }
    }

    protected open fun handleError(t: Throwable) = Unit
}
