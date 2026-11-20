package com.pointlessapps.filman.ui.components

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.DecadeOption
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaFilterState
import com.pointlessapps.filman.data.model.MediaTypeFilter
import com.pointlessapps.filman.data.model.SortOption
import com.pointlessapps.filman.ui.core.TextValue

object FilterMenuBuilder {
    private val RATING_OPTIONS = listOf(null, 6f, 7f, 8f, 9f)

    fun build(
        filterState: MediaFilterState,
        availableGenres: List<FilterOption>,
        showMediaTypeFilter: Boolean,
        onUpdateFilter: (MediaFilterState) -> Unit,
        onResetFilters: () -> Unit,
    ): OverlayMenuData {
        val items = buildList {
            if (showMediaTypeFilter) {
                add(mediaTypeSubmenu(filterState, onUpdateFilter))
            }
            add(sortSubmenu(filterState, onUpdateFilter))
            if (availableGenres.isNotEmpty()) {
                add(genresSubmenu(filterState, availableGenres, onUpdateFilter))
            }
            add(decadeSubmenu(filterState, onUpdateFilter))
            add(ratingSubmenu(filterState, onUpdateFilter))
            if (filterState.isActive) {
                add(
                    TiwiOverlayMenuItem.Header(
                        id = "header_filter_actions",
                        label = TextValue.StringResource(R.string.filter_header_actions),
                    ),
                )
                add(
                    TiwiOverlayMenuItem.Button(
                        id = "btn_reset_filters",
                        label = TextValue.StringResource(R.string.filter_reset_all),
                        onClick = { onResetFilters() },
                    ),
                )
            }
        }
        return OverlayMenuData(
            title = TextValue.StringResource(R.string.filter_menu_title),
            items = items,
        )
    }

    private fun mediaTypeSubmenu(
        filterState: MediaFilterState,
        onUpdateFilter: (MediaFilterState) -> Unit,
    ): TiwiOverlayMenuItem.NestedMenu =
        TiwiOverlayMenuItem.NestedMenu(
            id = "menu_filter_media_type",
            label = TextValue.StringResource(R.string.filter_media_type),
            value = TextValue.StringResource(filterState.mediaTypeFilter.labelRes),
            items = MediaTypeFilter.entries.map { type ->
                TiwiOverlayMenuItem.Option(
                    id = "media_type_${type.name}",
                    label = TextValue.StringResource(type.labelRes),
                    isSelected = filterState.mediaTypeFilter == type,
                    onClick = { onUpdateFilter(filterState.copy(mediaTypeFilter = type)) },
                )
            },
        )

    private fun sortSubmenu(
        filterState: MediaFilterState,
        onUpdateFilter: (MediaFilterState) -> Unit,
    ): TiwiOverlayMenuItem.NestedMenu =
        TiwiOverlayMenuItem.NestedMenu(
            id = "menu_filter_sort",
            label = TextValue.StringResource(R.string.filter_sort_by),
            value = TextValue.StringResource(filterState.sortBy.labelRes),
            items = SortOption.entries.map { option ->
                TiwiOverlayMenuItem.Option(
                    id = "sort_${option.name}",
                    label = TextValue.StringResource(option.labelRes),
                    isSelected = filterState.sortBy == option,
                    onClick = { onUpdateFilter(filterState.copy(sortBy = option)) },
                )
            },
        )

    private fun genresSubmenu(
        filterState: MediaFilterState,
        availableGenres: List<FilterOption>,
        onUpdateFilter: (MediaFilterState) -> Unit,
    ): TiwiOverlayMenuItem.NestedMenu {
        val valueText = when (filterState.selectedGenreIds.size) {
            0 -> TextValue.StringResource(R.string.filter_all_genres)
            1 -> {
                val selectedId = filterState.selectedGenreIds.single()
                val label = availableGenres.firstOrNull { it.id.toIntOrNull() == selectedId }?.label
                if (label != null) {
                    TextValue.DynamicString(label)
                } else {
                    TextValue.PluralResource(R.plurals.filter_selected_count, 1, 1)
                }
            }
            else -> {
                val count = filterState.selectedGenreIds.size
                TextValue.PluralResource(R.plurals.filter_selected_count, count, count)
            }
        }
        val items = buildList {
            if (filterState.selectedGenreIds.isNotEmpty()) {
                add(
                    TiwiOverlayMenuItem.Button(
                        id = "btn_clear_genres",
                        label = TextValue.StringResource(R.string.filter_clear_genres),
                        onClick = { onUpdateFilter(filterState.copy(selectedGenreIds = emptyList())) },
                    ),
                )
            }
            availableGenres.forEach { genre ->
                val genreId = genre.id.toIntOrNull() ?: return@forEach
                val isSelected = genreId in filterState.selectedGenreIds
                add(
                    TiwiOverlayMenuItem.Option(
                        id = "genre_$genreId",
                        label = TextValue.DynamicString(genre.label),
                        isSelected = isSelected,
                        onClick = { onUpdateFilter(filterState.toggleGenre(genreId)) },
                    ),
                )
            }
        }
        return TiwiOverlayMenuItem.NestedMenu(
            id = "menu_filter_genres",
            label = TextValue.StringResource(R.string.filter_genres),
            value = valueText,
            items = items,
        )
    }

    private fun decadeSubmenu(
        filterState: MediaFilterState,
        onUpdateFilter: (MediaFilterState) -> Unit,
    ): TiwiOverlayMenuItem.NestedMenu =
        TiwiOverlayMenuItem.NestedMenu(
            id = "menu_filter_decade",
            label = TextValue.StringResource(R.string.filter_release_period),
            value = TextValue.StringResource(filterState.decade.labelRes),
            items = DecadeOption.entries.map { decade ->
                TiwiOverlayMenuItem.Option(
                    id = "decade_${decade.name}",
                    label = TextValue.StringResource(decade.labelRes),
                    isSelected = filterState.decade == decade,
                    onClick = { onUpdateFilter(filterState.copy(decade = decade)) },
                )
            },
        )

    private fun ratingSubmenu(
        filterState: MediaFilterState,
        onUpdateFilter: (MediaFilterState) -> Unit,
    ): TiwiOverlayMenuItem.NestedMenu {
        val currentValue = filterState.minRating?.let {
            TextValue.StringResource(R.string.filter_rating_value, it.toInt())
        } ?: TextValue.StringResource(R.string.filter_any_rating)
        return TiwiOverlayMenuItem.NestedMenu(
            id = "menu_filter_rating",
            label = TextValue.StringResource(R.string.filter_rating),
            value = currentValue,
            items = RATING_OPTIONS.map { rating ->
                val label = if (rating == null) {
                    TextValue.StringResource(R.string.filter_any_rating)
                } else {
                    TextValue.StringResource(R.string.filter_rating_value, rating.toInt())
                }
                TiwiOverlayMenuItem.Option(
                    id = "rating_${rating?.toInt() ?: 0}",
                    label = label,
                    isSelected = filterState.minRating == rating,
                    onClick = { onUpdateFilter(filterState.copy(minRating = rating)) },
                )
            },
        )
    }
}
