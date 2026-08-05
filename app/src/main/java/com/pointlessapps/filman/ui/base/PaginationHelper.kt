package com.pointlessapps.filman.ui.base

import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.scraper.FilmanScraper
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import com.pointlessapps.filman.ui.components.sections.MoviesSection

internal suspend fun FilmanScraper.loadMoreMoviesForSection(
    moviesSections: List<MoviesSection>,
    sectionTitle: Int,
    transform: (List<MovieItem>, List<MoviesGridItem>) -> List<MoviesGridItem> = { new, old ->
        (old + new.map { MoviesGridItem.Single(it) }).distinctBy { it.movieItem.url }
    },
): List<MoviesSection>? {
    val section = moviesSections.find { it.title == sectionTitle }
    if (section == null || section.path == null || !section.hasMore) return null

    val nextPage = section.page + 1
    val newMovies = getCategoryPage(path = section.path, page = nextPage).movies

    return moviesSections.map { s ->
        if (s.title == sectionTitle) {
            s.copy(
                movies = transform(newMovies, s.movies),
                page = nextPage,
                hasMore = newMovies.isNotEmpty(),
            )
        } else {
            s
        }
    }
}
