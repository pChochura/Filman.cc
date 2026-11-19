package com.pointlessapps.filman.ui.components

import androidx.annotation.StringRes
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.DecadeOption
import com.pointlessapps.filman.data.model.MediaTypeFilter
import com.pointlessapps.filman.data.model.SortOption

@get:StringRes
val SortOption.labelRes: Int
    get() = when (this) {
        SortOption.POPULARITY_DESC -> R.string.sort_popularity_desc
        SortOption.RATING_DESC -> R.string.sort_rating_desc
        SortOption.RELEASE_DATE_DESC -> R.string.sort_release_date_desc
        SortOption.VOTE_COUNT_DESC -> R.string.sort_vote_count_desc
    }

@get:StringRes
val DecadeOption.labelRes: Int
    get() = when (this) {
        DecadeOption.ALL -> R.string.filter_all_years
        DecadeOption.YEAR_2024_PLUS -> R.string.filter_decade_2024_plus
        DecadeOption.YEAR_2020_2023 -> R.string.filter_decade_2020_2023
        DecadeOption.YEAR_2010S -> R.string.filter_decade_2010s
        DecadeOption.YEAR_2000S -> R.string.filter_decade_2000s
        DecadeOption.YEAR_1990S -> R.string.filter_decade_1990s
        DecadeOption.CLASSICS -> R.string.filter_decade_classics
    }

@get:StringRes
val MediaTypeFilter.labelRes: Int
    get() = when (this) {
        MediaTypeFilter.ALL -> R.string.filter_media_type_all
        MediaTypeFilter.MOVIES -> R.string.filter_media_type_movies
        MediaTypeFilter.TV_SHOWS -> R.string.filter_media_type_tv_shows
    }
