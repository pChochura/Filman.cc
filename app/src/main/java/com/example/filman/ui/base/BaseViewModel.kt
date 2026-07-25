package com.example.filman.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filman.data.cache.StaleDataException
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.model.MovieItem
import com.example.filman.data.scraper.AuthException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal abstract class BaseViewModel<State : StateWithShared<State>, Event : FilmanEvent, Effect>(
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
            is BaseEvent.OpenMovieDetails -> getNavigateToDetailsEffect(event.url, event.autoplay)
                ?.let(::sendEffect)

            is BaseEvent.RemoveFromFavorites -> favoritesManager?.removeFavorite(event.url)
            is BaseEvent.AddToFavorites -> favoritesManager?.addFavorite(event.movie)
            is BaseEvent.RemoveFromContinueWatching -> progressManager?.removeProgress(event.url)
            is BaseEvent.MarkAsWatched -> progressManager?.markAsWatched(event.movie)

            is BaseEvent.MarkAsNotWatched -> progressManager?.markAsNotWatched(event.url)
            is BaseEvent.OpenContextMenu -> {
                val menuData = createStandardContextMenu(
                    movie = event.movie,
                    isFavorite = favoritesManager?.isFavorite(event.movie.url) ?: false,
                    isInContinueWatching = event.isInContinueWatching,
                    isWatched = event.isWatched,
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

                        override fun onMarkAsNotWatched(url: String) {
                            onEvent(BaseEvent.MarkAsNotWatched(url))
                        }

                        override fun onMarkAsWatched(movie: MovieItem) {
                            onEvent(BaseEvent.MarkAsWatched(movie))
                        }
                    },
                )
                updateSharedState { it.copy(overlayMenuData = menuData) }
            }

            is BaseEvent.CloseContextMenu -> updateSharedState { it.copy(overlayMenuData = null) }
        }
    }

    protected abstract fun handleEvent(event: Event)

    protected open fun getNavigateToDetailsEffect(url: String, autoplay: Boolean): Effect? = null

    /**
     * Return the effect that should be sent when an AuthException occurs.
     * Return null if the ViewModel does not handle auth navigation.
     */
    protected abstract fun getAuthErrorEffect(): Effect?

    protected fun launchHandled(
        onError: ((Throwable) -> Unit)? = null,
        onStaleData: ((Any) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit,
    ) = viewModelScope.launch {
        runCatching { block() }.onFailure { t ->
            if (t is StaleDataException) {
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        isLoadingNextPage = false,
                        errorMessage = null,
                    )
                }
                t.staleData?.let {
                    onStaleData?.invoke(it) ?: handleStaleData(it)
                }
            } else {
                onError?.invoke(t) ?: handleError(t)
            }
        }
    }

    protected open fun handleStaleData(staleData: Any) {
        // To be overridden by concrete ViewModels
    }

    protected fun handleError(t: Throwable) {
        if (t is AuthException) {
            getAuthErrorEffect()?.let { sendEffect(it) }
        }
    }
}
