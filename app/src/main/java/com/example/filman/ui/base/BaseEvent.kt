package com.example.filman.ui.base

import com.example.filman.data.model.MovieItem

internal interface FilmanEvent

internal sealed interface BaseEvent : FilmanEvent {
    data class OpenMovieDetails(
        val url: String,
        val autoplay: Boolean = false,
    ) : BaseEvent

    data class RemoveFromFavorites(val url: String) : BaseEvent
    data class AddToFavorites(val movie: MovieItem) : BaseEvent
    data class OpenContextMenu(
        val movie: MovieItem,
        val isInContinueWatching: Boolean = false,
        val isWatched: Boolean? = null,
    ) : BaseEvent

    data object CloseContextMenu : BaseEvent
    data class RemoveFromContinueWatching(val url: String) : BaseEvent
    data class MarkAsWatched(val movie: MovieItem) : BaseEvent

    data class MarkAsNotWatched(val url: String) : BaseEvent
}
