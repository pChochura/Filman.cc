package com.pointlessapps.filman.ui.search

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.tmdb.applyLocalFilter
import com.pointlessapps.filman.data.model.MediaFilterState
import com.pointlessapps.filman.data.model.MediaType
import com.pointlessapps.filman.data.model.MediaTypeFilter
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.TmdbFeed
import com.pointlessapps.filman.ui.components.sections.MoviesSection

internal fun buildFilteredSearchSections(
    movies: List<MovieItem>,
    tvShows: List<MovieItem>,
    filterState: MediaFilterState,
): List<MoviesSection> {
    val allItems = movies.map { it.copy(isTvShow = false) } +
        tvShows.map { it.copy(isTvShow = true) }
    val filteredItems = if (filterState.isActive) {
        allItems.applyLocalFilter(filterState)
    } else {
        allItems
    }
    val gridItems = filteredItems.toSearchGridItems()
    return if (gridItems.isEmpty()) {
        emptyList()
    } else {
        listOf(
            MoviesSection(
                title = R.string.common_results,
                movies = gridItems,
            ),
        )
    }
}

internal fun discoverFeedsFor(filterState: MediaFilterState): Pair<TmdbFeed?, TmdbFeed?> {
    val singleGenreId = filterState.selectedGenreIds.firstOrNull()
    return if (filterState.isSingleGenreOnly && singleGenreId != null) {
        TmdbFeed.DiscoverByGenre(MediaType.MOVIE, singleGenreId) to
            TmdbFeed.DiscoverByGenre(MediaType.TV, singleGenreId)
    } else {
        val moviesFeed = if (filterState.mediaTypeFilter != MediaTypeFilter.TV_SHOWS) {
            TmdbFeed.DiscoverFiltered(MediaType.MOVIE, filterState)
        } else {
            null
        }
        val tvFeed = if (filterState.mediaTypeFilter != MediaTypeFilter.MOVIES) {
            TmdbFeed.DiscoverFiltered(MediaType.TV, filterState)
        } else {
            null
        }
        moviesFeed to tvFeed
    }
}
