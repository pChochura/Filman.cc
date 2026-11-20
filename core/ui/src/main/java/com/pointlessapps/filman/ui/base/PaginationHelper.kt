package com.pointlessapps.filman.ui.base

import androidx.annotation.StringRes
import com.pointlessapps.filman.data.api.TiwiRepository
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.TmdbFeed
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import com.pointlessapps.filman.ui.components.sections.MoviesSection
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class FeedSectionSpec(
    @param:StringRes val titleRes: Int,
    val feed: TmdbFeed,
    val includeInFeatured: Boolean = false,
)

data class LoadedFeedCatalog(
    val featuredItems: List<MovieItem>,
    val sections: List<MoviesSection>,
    val errorMessage: String? = null,
    val hasError: Boolean = false,
)

suspend fun TiwiRepository.loadFeedCatalog(
    specs: List<FeedSectionSpec>,
    featuredLimit: Int = 5,
): LoadedFeedCatalog = coroutineScope {
    val results = specs.map { spec ->
        async { spec to getFeedPage(spec.feed) }
    }.awaitAll()

    val pageResults = results.map { it.second }
    if (pageResults.all { it.movies.isEmpty() } && pageResults.any { it.errorMessage != null }) {
        return@coroutineScope LoadedFeedCatalog(
            featuredItems = emptyList(),
            sections = emptyList(),
            errorMessage = pageResults.firstNotNullOfOrNull { it.errorMessage },
            hasError = true,
        )
    }

    val featured = results
        .filter { (spec, _) -> spec.includeInFeatured }
        .flatMap { (_, pageResult) -> pageResult.featuredItems }
        .distinctBy { it.url }
        .take(featuredLimit)

    val sections = results.mapNotNull { (spec, pageResult) ->
        if (pageResult.movies.isEmpty()) {
            null
        } else {
            MoviesSection(
                title = spec.titleRes,
                movies = pageResult.movies.map(MoviesGridItem::Single),
                feed = pageResult.feed,
                page = pageResult.page,
                hasMore = pageResult.hasMore,
            )
        }
    }

    LoadedFeedCatalog(
        featuredItems = featured,
        sections = sections,
    )
}

suspend fun TiwiRepository.loadMoreMoviesForSection(
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
