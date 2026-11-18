package com.pointlessapps.filman.ui.base

import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import com.pointlessapps.filman.ui.components.sections.MoviesSection

suspend fun FilmanRepository.loadMoreMoviesForSection(
    moviesSections: List<MoviesSection>,
    sectionTitle: Int,
    transform: (List<MovieItem>, List<MoviesGridItem>) -> List<MoviesGridItem> = { new, old ->
        (old + new.map { MoviesGridItem.Single(it) }).distinctBy { it.movieItem.url }
    },
): List<MoviesSection>? {
    val section = moviesSections.find { it.title == sectionTitle }
    if (section == null || section.feed == null || !section.hasMore) return null

    val nextPage = section.page + 1
    val pageResult = getFeedPage(feed = section.feed, page = nextPage)

    return moviesSections.map { s ->
        if (s.title == sectionTitle) {
            s.copy(
                movies = transform(pageResult.movies, s.movies),
                page = nextPage,
                hasMore = pageResult.hasMore,
            )
        } else {
            s
        }
    }
}
