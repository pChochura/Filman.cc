package com.pointlessapps.filman.ui.search

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.components.OverlayMenuData
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import com.pointlessapps.filman.ui.core.TextValue

internal fun buildSearchHistoryContextMenu(
    query: String,
    onRemove: () -> Unit,
): OverlayMenuData = OverlayMenuData(
    title = TextValue.DynamicString(query),
    items = listOf(
        FilmanOverlayMenuItem.Button(
            label = TextValue.StringResource(R.string.search_remove_from_history),
            onClick = { onRemove() },
        ),
    ),
)

internal fun List<String>.nextFocusedQueryAfterRemoving(removedQuery: String): String? {
    val index = indexOf(removedQuery)
    return if (index > 0) get(index - 1) else getOrNull(index + 1)
}

internal fun List<MovieItem>.toSearchGridItems(): List<MoviesGridItem> =
    filter { it.posterUrl.isNotBlank() }
        .distinctBy { it.url }
        .map { MoviesGridItem.Single(it) }
