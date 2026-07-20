package com.example.filman.ui.base

import com.example.filman.data.model.MovieItem

internal interface FilmanEvent

internal sealed interface BaseEvent : FilmanEvent {
    data class OpenMovieDetails(val url: String) : BaseEvent
    data class RemoveFromFavorites(val url: String) : BaseEvent
    data class AddToFavorites(val movie: MovieItem) : BaseEvent
    data class OpenContextMenu(
        val title: String,
        val url: String,
        val posterUrl: String,
        val isInContinueWatching: Boolean = false,
        val isWatched: Boolean? = null,
        val parentUrl: String? = null
    ) : BaseEvent
    data object CloseContextMenu : BaseEvent
    data class RemoveFromContinueWatching(val url: String) : BaseEvent
    data class MarkAsWatched(val url: String, val parentUrl: String) : BaseEvent
    data class MarkAsNotWatched(val url: String) : BaseEvent
}
