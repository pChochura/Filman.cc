package com.example.filman.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filman.data.scraper.AuthException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal abstract class BaseViewModel<State, Event, Effect>(initialState: State) : ViewModel() {

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

    abstract fun onEvent(event: Event)

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
