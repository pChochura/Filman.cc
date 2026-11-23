package com.pointlessapps.filman.ui.base

import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager.Companion.MARK_AS_WATCHED_PROGRESS_THRESHOLD
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.ui.components.OverlayMenuData

fun buildStandardOverlayContextMenu(
    movie: MovieItem,
    progressMap: Map<String, Float>,
    favoritesManager: FavoritesManager?,
    watchlistManager: WatchlistManager?,
    options: Set<ContextMenuOption>,
    onEvent: (TiwiEvent) -> Unit,
    isSearchingYoutube: Boolean = false,
): OverlayMenuData {
    val progress = progressMap[movie.url] ?: 0f
    val isWatched = progress >= MARK_AS_WATCHED_PROGRESS_THRESHOLD
    val filteredOptions = options.filter { option ->
        when (option) {
            ContextMenuOption.MARK_AS_WATCHED -> !isWatched
            ContextMenuOption.MARK_AS_NOT_WATCHED -> isWatched
            else -> true
        }
    }.toSet()

    return createStandardContextMenu(
        movie = movie,
        isFavorite = favoritesManager?.isFavorite(movie.url) ?: false,
        isWatchlist = watchlistManager?.isInWatchlist(movie.url) ?: false,
        options = filteredOptions,
        isSearchingYoutube = isSearchingYoutube,
        handler = object : ContextMenuActionHandler {
            override fun onRemoveFromFavorites(url: String) =
                onEvent(BaseEvent.RemoveFromFavorites(url))

            override fun onAddToFavorites(movie: MovieItem) =
                onEvent(BaseEvent.AddToFavorites(movie))

            override fun onRemoveFromWatchlist(url: String) =
                onEvent(BaseEvent.RemoveFromWatchlist(url))

            override fun onAddToWatchlist(movie: MovieItem) =
                onEvent(BaseEvent.AddToWatchlist(movie))

            override fun onCloseContextMenu() =
                onEvent(BaseEvent.CloseContextMenu)

            override fun onRemoveFromContinueWatching(url: String) =
                onEvent(BaseEvent.RemoveFromContinueWatching(url))

            override fun onMarkAsNotWatched(url: String) =
                onEvent(BaseEvent.MarkAsNotWatched(url))

            override fun onMarkAsWatched(movie: MovieItem) =
                onEvent(BaseEvent.MarkAsWatched(movie))

            override fun onMarkPreviousAsWatched(movie: MovieItem) =
                onEvent(BaseEvent.MarkPreviousAsWatched(movie))

            override fun onOpenDetails(movie: MovieItem) {
                onEvent(
                    BaseEvent.OpenMovieDetails(
                        url = movie.seriesUrl ?: movie.url,
                        autoplay = false,
                        episodeUrl = null,
                        request = if (movie.seriesUrl == null) movie.detailsRequest else null,
                    ),
                )
            }

            override fun onWatchOnYoutube(movie: MovieItem) =
                onEvent(BaseEvent.WatchOnYoutube(movie))
        },
    )
}
