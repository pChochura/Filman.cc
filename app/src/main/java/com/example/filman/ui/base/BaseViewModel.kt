package com.example.filman.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.model.MovieItem
import com.example.filman.data.scraper.AuthException
import com.example.filman.ui.components.OverlayMenuData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal abstract class BaseViewModel<State, Event : FilmanEvent, Effect>(
    initialState: State,
    protected val favoritesManager: FavoritesManager? = null,
    protected val progressManager: ProgressManager? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effect = Channel<Effect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    protected val currentState: State get() = _state.value

    protected fun updateState(updater: (State) -> State) {
        _state.update(updater)
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
            is BaseEvent.OpenMovieDetails -> getNavigateToDetailsEffect(event.url)?.let { sendEffect(it) }
            is BaseEvent.RemoveFromFavorites -> favoritesManager?.removeFavorite(event.url)
            is BaseEvent.AddToFavorites -> favoritesManager?.addFavorite(event.movie)
            is BaseEvent.RemoveFromContinueWatching -> progressManager?.removeProgress(event.url)
            is BaseEvent.MarkAsWatched -> progressManager?.markAsWatched(event.url, event.parentUrl)
            is BaseEvent.MarkAsNotWatched -> progressManager?.markAsNotWatched(event.url)
            is BaseEvent.OpenContextMenu -> {
                val menu = createStandardContextMenu(
                    title = event.title,
                    url = event.url,
                    posterUrl = event.posterUrl,
                    isFavorite = favoritesManager?.isFavorite(event.url) == true,
                    isInContinueWatching = event.isInContinueWatching,
                    isWatched = event.isWatched,
                    parentUrl = event.parentUrl,
                    handler = object : ContextMenuActionHandler {
                        override fun onRemoveFromFavorites(url: String) {
                            onEvent(BaseEvent.RemoveFromFavorites(url))
                        }

                        override fun onAddToFavorites(movie: MovieItem) {
                            onEvent(BaseEvent.AddToFavorites(movie))
                        }

                        override fun onCloseContextMenu() {
                            onEvent(BaseEvent.CloseContextMenu)
                        }

                        override fun onRemoveFromContinueWatching(url: String) {
                            onEvent(BaseEvent.RemoveFromContinueWatching(url))
                        }

                        override fun onMarkAsWatched(url: String, parentUrl: String) {
                            onEvent(BaseEvent.MarkAsWatched(url, parentUrl))
                        }

                        override fun onMarkAsNotWatched(url: String) {
                            onEvent(BaseEvent.MarkAsNotWatched(url))
                        }
                    }
                )
                setOverlayMenuData(menu)
            }
            is BaseEvent.CloseContextMenu -> setOverlayMenuData(null)
        }
    }

    protected abstract fun handleEvent(event: Event)

    protected open fun setOverlayMenuData(data: OverlayMenuData?) {}

    protected open fun getNavigateToDetailsEffect(url: String): Effect? = null

    /**
     * Return the effect that should be sent when an AuthException occurs.
     * Return null if the ViewModel does not handle auth navigation.
     */
    protected abstract fun getAuthErrorEffect(): Effect?

    protected fun launchHandled(
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit
    ) = viewModelScope.launch {
        runCatching { block() }.onFailure { t ->
            onError?.invoke(t) ?: handleError(t)
        }
    }

    protected fun handleError(t: Throwable) {
        if (t is AuthException) {
            getAuthErrorEffect()?.let { sendEffect(it) }
        }
    }
}
