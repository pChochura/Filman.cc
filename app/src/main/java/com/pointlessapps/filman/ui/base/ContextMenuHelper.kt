package com.pointlessapps.filman.ui.base

import com.pointlessapps.filman.R
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.components.OverlayMenuData
import com.pointlessapps.filman.ui.core.TextValue

internal enum class ContextMenuOption {
    REMOVE_FROM_CONTINUE_WATCHING,
    MARK_AS_WATCHED,
    MARK_AS_NOT_WATCHED,
    MARK_PREVIOUS_AS_WATCHED,
    FAVORITES,
}

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
    options: Set<ContextMenuOption> = setOf(ContextMenuOption.FAVORITES),
): OverlayMenuData = OverlayMenuData(
    title = TextValue.DynamicString(movie.titlePl),
    items = buildList {
        if (ContextMenuOption.REMOVE_FROM_CONTINUE_WATCHING in options) {
            add(
                FilmanOverlayMenuItem.Button(
                    label = TextValue.StringResource(R.string.remove_from_continue_watching),
                    onClick = {
                        handler.onRemoveFromContinueWatching(movie.url)
                        handler.onCloseContextMenu()
                    },
                ),
            )
        }

        if (ContextMenuOption.MARK_AS_NOT_WATCHED in options && movie.seriesUrl != null) {
            add(
                FilmanOverlayMenuItem.Button(
                    label = TextValue.StringResource(R.string.mark_as_not_watched),
                    onClick = {
                        handler.onMarkAsNotWatched(movie.url)
                        handler.onCloseContextMenu()
                    },
                ),
            )
        }

        if (ContextMenuOption.MARK_AS_WATCHED in options && movie.seriesUrl != null) {
            add(
                FilmanOverlayMenuItem.Button(
                    label = TextValue.StringResource(R.string.mark_as_watched),
                    onClick = {
                        handler.onMarkAsWatched(movie)
                        handler.onCloseContextMenu()
                    },
                ),
            )
        }

        if (ContextMenuOption.MARK_PREVIOUS_AS_WATCHED in options) {
            add(
                FilmanOverlayMenuItem.Button(
                    label = TextValue.StringResource(R.string.mark_previous_as_watched),
                    onClick = {
                        handler.onMarkPreviousAsWatched(movie)
                        handler.onCloseContextMenu()
                    },
                ),
            )
        }

        if (ContextMenuOption.FAVORITES in options) {
            if (isFavorite) {
                add(
                    FilmanOverlayMenuItem.Button(
                        label = TextValue.StringResource(R.string.remove_from_favorites),
                        onClick = {
                            handler.onRemoveFromFavorites(movie.url)
                            handler.onCloseContextMenu()
                        },
                    ),
                )
            } else {
                add(
                    FilmanOverlayMenuItem.Button(
                        label = TextValue.StringResource(R.string.add_to_favorites),
                        onClick = {
                            handler.onAddToFavorites(movie)
                            handler.onCloseContextMenu()
                        },
                    ),
                )
            }
        }
    },
)
