package com.pointlessapps.filman.data.api.tmdb

import com.pointlessapps.filman.data.model.MediaFilterState
import com.pointlessapps.filman.data.model.MediaTypeFilter
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.SortOption

fun List<MovieItem>.applyLocalFilter(
    filter: MediaFilterState,
    localeProvider: TmdbLocaleProvider = TmdbLocaleProvider(),
): List<MovieItem> {
    if (!filter.isActive) return this

    val filtered = filter { item ->
        val matchesType = when (filter.mediaTypeFilter) {
            MediaTypeFilter.ALL -> true
            MediaTypeFilter.MOVIES -> !item.isTvShow
            MediaTypeFilter.TV_SHOWS -> item.isTvShow
        }
        val matchesGenres = filter.selectedGenreIds.all { genreId ->
            localeProvider.matchesGenre(item.genreIds, genreId, item.isTvShow)
        }
        val matchesDecade = filter.decade.matchesYear(item.year)
        val matchesRating = filter.minRating == null ||
            (item.filmanRating?.normalizedScore ?: 0f) >= filter.minRating

        matchesType && matchesGenres && matchesDecade && matchesRating
    }

    return when (filter.sortBy) {
        SortOption.POPULARITY_DESC -> filtered
        SortOption.RATING_DESC -> filtered.sortedByDescending { it.filmanRating?.normalizedScore ?: -1f }
        SortOption.RELEASE_DATE_DESC -> filtered.sortedByDescending { it.year ?: Int.MIN_VALUE }
        SortOption.VOTE_COUNT_DESC -> filtered.sortedByDescending { it.voteCount ?: -1 }
    }
}
