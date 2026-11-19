package com.pointlessapps.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class MediaTypeFilter {
    ALL,
    MOVIES,
    TV_SHOWS,
}

@Serializable
enum class SortOption(
    val movieApiValue: String,
    val tvApiValue: String,
) {
    POPULARITY_DESC("popularity.desc", "popularity.desc"),
    RATING_DESC("vote_average.desc", "vote_average.desc"),
    RELEASE_DATE_DESC("primary_release_date.desc", "first_air_date.desc"),
    VOTE_COUNT_DESC("vote_count.desc", "vote_count.desc"),
    ;

    fun apiValue(mediaType: MediaType): String =
        if (mediaType == MediaType.TV) tvApiValue else movieApiValue

    fun next(): SortOption = entries[(ordinal + 1) % entries.size]
}

@Serializable
enum class DecadeOption(
    val fromYear: Int?,
    val toYear: Int?,
) {
    ALL(null, null),
    YEAR_2024_PLUS(2024, null),
    YEAR_2020_2023(2020, 2023),
    YEAR_2010S(2010, 2019),
    YEAR_2000S(2000, 2009),
    YEAR_1990S(1990, 1999),
    CLASSICS(null, 1989),
    ;

    fun matchesYear(year: Int?): Boolean {
        if (this == ALL) return true
        if (year == null) return false
        val afterFrom = fromYear == null || year >= fromYear
        val beforeTo = toYear == null || year <= toYear
        return afterFrom && beforeTo
    }
}

@Serializable
@Immutable
data class MediaFilterState(
    val mediaTypeFilter: MediaTypeFilter = MediaTypeFilter.ALL,
    val selectedGenreIds: List<Int> = emptyList(),
    val decade: DecadeOption = DecadeOption.ALL,
    val minRating: Float? = null,
    val sortBy: SortOption = SortOption.POPULARITY_DESC,
) {
    val isActive: Boolean
        get() = mediaTypeFilter != MediaTypeFilter.ALL ||
            selectedGenreIds.isNotEmpty() ||
            decade != DecadeOption.ALL ||
            minRating != null ||
            sortBy != SortOption.POPULARITY_DESC

    val activeCount: Int
        get() = (if (mediaTypeFilter != MediaTypeFilter.ALL) 1 else 0) +
            selectedGenreIds.size +
            (if (decade != DecadeOption.ALL) 1 else 0) +
            (if (minRating != null) 1 else 0) +
            (if (sortBy != SortOption.POPULARITY_DESC) 1 else 0)

    val isSingleGenreOnly: Boolean
        get() = mediaTypeFilter == MediaTypeFilter.ALL &&
            selectedGenreIds.size == 1 &&
            decade == DecadeOption.ALL &&
            minRating == null &&
            sortBy == SortOption.POPULARITY_DESC

    fun toggleGenre(genreId: Int): MediaFilterState {
        val nextGenres = if (genreId in selectedGenreIds) {
            selectedGenreIds - genreId
        } else {
            (selectedGenreIds + genreId).distinct().sorted()
        }
        return copy(selectedGenreIds = nextGenres)
    }
}
