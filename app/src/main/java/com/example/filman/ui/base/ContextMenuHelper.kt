package com.example.filman.ui.base

import com.example.filman.R
import com.example.filman.data.model.MovieItem
import com.example.filman.ui.components.FilmanOverlayMenuItem
import com.example.filman.ui.components.OverlayMenuData

internal interface ContextMenuActionHandler {
    fun onRemoveFromFavorites(url: String)
    fun onAddToFavorites(movie: MovieItem)
    fun onCloseContextMenu()
    fun onRemoveFromContinueWatching(url: String)
    fun onMarkAsNotWatched(url: String)
    fun onMarkAsWatched(movie: MovieItem)
    fun onMarkPreviousAsWatched(movie: MovieItem)
}

internal fun createStandardContextMenu(
    movie: MovieItem,
    isFavorite: Boolean,
    handler: ContextMenuActionHandler,
    isInContinueWatching: Boolean = false,
    isWatched: Boolean? = null,
): OverlayMenuData = OverlayMenuData(
    title = movie.titlePl,
    items = buildList {
        if (isInContinueWatching) {
            add(
                FilmanOverlayMenuItem.Button(
                    label = R.string.remove_from_continue_watching,
                    onClick = {
                        handler.onRemoveFromContinueWatching(movie.url)
                        handler.onCloseContextMenu()
                    },
                ),
            )
        }

        if (isWatched != null && movie.seriesUrl != null) {
            if (isWatched) {
                add(
                    FilmanOverlayMenuItem.Button(
                        label = R.string.mark_as_not_watched,
                        onClick = {
                            handler.onMarkAsNotWatched(movie.url)
                            handler.onCloseContextMenu()
                        },
                    ),
                )
            } else {
                add(
                    FilmanOverlayMenuItem.Button(
                        label = R.string.mark_as_watched,
                        onClick = {
                            handler.onMarkAsWatched(movie)
                            handler.onCloseContextMenu()
                        },
                    ),
                )
                if (movie.seasonNumber != null && movie.episodeNumber != null) {
                    add(
                        FilmanOverlayMenuItem.Button(
                            label = R.string.mark_previous_as_watched,
                            onClick = {
                                handler.onMarkPreviousAsWatched(movie)
                                handler.onCloseContextMenu()
                            },
                        ),
                    )
                }
            }
        }

        if (isFavorite) {
            add(
                FilmanOverlayMenuItem.Button(
                    label = R.string.remove_from_favorites,
                    onClick = {
                        handler.onRemoveFromFavorites(movie.url)
                        handler.onCloseContextMenu()
                    },
                ),
            )
        } else {
            add(
                FilmanOverlayMenuItem.Button(
                    label = R.string.add_to_favorites,
                    onClick = {
                        handler.onAddToFavorites(movie)
                        handler.onCloseContextMenu()
                    },
                ),
            )
        }
    },
)
