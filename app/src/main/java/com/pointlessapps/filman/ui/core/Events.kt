package com.pointlessapps.filman.ui.core

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

internal interface Event {
    data object ScrollToTopEvent : Event
    data object FocusOnContent : Event
}

internal class EventDispatcher {

    private val _events = Channel<Event>(BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    suspend fun dispatch(event: Event) {
        _events.send(event)
    }

    fun tryDispatch(event: Event) {
        _events.trySend(event)
    }
}

internal val LocalEventDispatcher = compositionLocalOf<EventDispatcher> {
    error("No EventDispatcher provided")
}
