package com.example.filman.ui.base

import com.example.filman.data.model.MovieItem

internal interface FilmanEvent

internal sealed interface BaseEvent : FilmanEvent {
    data class OpenMovieDetails(
        val url: String,
        val autoplay: Boolean = false,
        val episodeUrl: String? = null,
    ) : BaseEvent

    data class RemoveFromFavorites(val url: String) : BaseEvent
    data class AddToFavorites(val movie: MovieItem) : BaseEvent
    data class OpenContextMenu(
        val movie: MovieItem,
        val options: Set<ContextMenuOption> = setOf(ContextMenuOption.FAVORITES),
    ) : BaseEvent

    data object CloseContextMenu : BaseEvent
    data class RemoveFromContinueWatching(val url: String) : BaseEvent
    data class MarkAsWatched(val movie: MovieItem) : BaseEvent
    data class MarkPreviousAsWatched(val movie: MovieItem) : BaseEvent

    data class MarkAsNotWatched(val url: String) : BaseEvent
}
