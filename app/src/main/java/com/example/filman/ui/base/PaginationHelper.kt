package com.example.filman.ui.base

import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.components.sections.MoviesSection

internal suspend fun FilmanScraper.loadMoreMoviesForSection(
    moviesSections: List<MoviesSection>,
    sectionTitle: Int
): List<MoviesSection>? {
    val section = moviesSections.find { it.title == sectionTitle }
    if (section == null || section.path == null || !section.hasMore) return null

    val nextPage = section.page + 1
    val newMovies = getCategoryPage(path = section.path, page = nextPage).movies

    return moviesSections.map { s ->
        if (s.title == sectionTitle) {
            s.copy(
                movies = (s.movies + newMovies).distinctBy { m -> m.url },
                page = nextPage,
                hasMore = newMovies.isNotEmpty(),
            )
        } else {
            s
        }
    }
}
