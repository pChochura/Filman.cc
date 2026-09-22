package com.pointlessapps.filman.ui.core

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

interface Event {
    data object ScrollToTopEvent : Event

    data object FocusOnContent : Event
}

class EventDispatcher {
    private val _events = Channel<Event>(BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    suspend fun dispatch(event: Event) {
        _events.send(event)
    }

    fun tryDispatch(event: Event) {
        _events.trySend(event)
    }
}

val LocalEventDispatcher =
    compositionLocalOf<EventDispatcher> {
        error("No EventDispatcher provided")
    }

val LocalIsPlaying =
    compositionLocalOf<MutableState<Boolean>> {
        error("No IsPlaying provided")
    }
