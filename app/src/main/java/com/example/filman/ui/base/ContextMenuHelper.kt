package com.example.filman.ui.base

import com.example.filman.R
import com.example.filman.data.model.MovieItem
import com.example.filman.ui.components.FilmanOverlayMenuItem
import com.example.filman.ui.components.OverlayMenuData

internal interface ContextMenuActionHandler {
    fun onRemoveFromFavorites(url: String)
    fun onAddToFavorites(movie: MovieItem)
    fun onCloseContextMenu()
    fun onRemoveFromContinueWatching(url: String) {}
    fun onMarkAsWatched(url: String, parentUrl: String) {}
    fun onMarkAsNotWatched(url: String) {}
}

internal fun createStandardContextMenu(
    title: String,
    url: String,
    posterUrl: String,
    isFavorite: Boolean,
    handler: ContextMenuActionHandler,
    isInContinueWatching: Boolean = false,
    isWatched: Boolean? = null,
    parentUrl: String? = null,
): OverlayMenuData = OverlayMenuData(
    title = title,
    items = buildList {
        if (isInContinueWatching) {
            add(
                FilmanOverlayMenuItem.Button(
                    label = R.string.remove_from_continue_watching,
                    onClick = {
                        handler.onRemoveFromContinueWatching(url)
                        handler.onCloseContextMenu()
                    },
                ),
            )
        }

        if (isWatched != null && parentUrl != null) {
            if (isWatched) {
                add(
                    FilmanOverlayMenuItem.Button(
                        label = R.string.mark_as_not_watched,
                        onClick = {
                            handler.onMarkAsNotWatched(url)
                            handler.onCloseContextMenu()
                        },
                    ),
                )
            } else {
                add(
                    FilmanOverlayMenuItem.Button(
                        label = R.string.mark_as_watched,
                        onClick = {
                            handler.onMarkAsWatched(url, parentUrl)
                            handler.onCloseContextMenu()
                        },
                    ),
                )
            }
        }

        if (isFavorite) {
            add(
                FilmanOverlayMenuItem.Button(
                    label = R.string.remove_from_favorites,
                    onClick = {
                        handler.onRemoveFromFavorites(url)
                        handler.onCloseContextMenu()
                    },
                ),
            )
        } else {
            add(
                FilmanOverlayMenuItem.Button(
                    label = R.string.add_to_favorites,
                    onClick = {
                        handler.onAddToFavorites(
                            MovieItem(
                                url = url,
                                titlePl = title,
                                posterUrl = posterUrl,
                            )
                        )
                        handler.onCloseContextMenu()
                    },
                ),
            )
        }
    },
)
