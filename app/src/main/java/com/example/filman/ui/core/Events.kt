package com.example.filman.ui.core

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

interface Event {
    data object ScrollToTopEvent : Event
}

class EventDispatcher {

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events: SharedFlow<Event> = _events

    fun dispatch(event: Event) {
        _events.tryEmit(event)
    }
}

val LocalEventDispatcher = compositionLocalOf<EventDispatcher> {
    error("No EventDispatcher provided")
}
