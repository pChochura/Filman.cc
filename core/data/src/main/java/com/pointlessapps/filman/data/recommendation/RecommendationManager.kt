package com.pointlessapps.filman.data.recommendation

import com.pointlessapps.filman.data.api.tmdb.TmdbApiClient
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.MediaRef
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.Rating
import com.pointlessapps.filman.data.model.TmdbFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class RecommendationManager(
    private val favoritesManager: FavoritesManager,
    private val progressManager: ProgressManager,
    private val tmdbApiClient: TmdbApiClient,
) {
    suspend fun getPersonalizedRecommendations(page: Int = 1): Pair<List<MovieItem>, Boolean> =
        withContext(Dispatchers.IO) {
            val favRefs = favoritesManager.getFavorites()
                .take(5)
                .mapNotNull { MediaRef.parse(it.url, it.isTvShow)?.parentRef }
            val progressRefs = progressManager.progressItemsFlow.value
                .take(5)
                .mapNotNull {
                    MediaRef.parse(
                        it.parentUrl ?: it.url,
                        it.season != null || it.parentUrl?.startsWith("show_") == true,
                    )?.parentRef
                }

            val seedRefs = (favRefs + progressRefs).distinct()
            if (seedRefs.isEmpty()) return@withContext emptyList<MovieItem>() to false

            val excludedKeys = seedRefs.map { it.canonicalKey }.toSet()
            val pages = seedRefs.map { ref ->
                async {
                    runCatching { tmdbApiClient.fetchFeed(TmdbFeed.Recommendations(ref), page) }.getOrNull()
                }
            }.awaitAll().filterNotNull()

            val recommendations = pages.flatMap { it.results }
                .filter { !it.posterPath.isNullOrBlank() && it.mediaType != "PERSON" }
            val hasMore = pages.any { it.page < it.totalPages }

            if (recommendations.isEmpty()) return@withContext emptyList<MovieItem>() to false

            val keyOf = { s: com.pointlessapps.filman.data.api.dto.MediaSummary ->
                if (s.mediaType == "TV_SHOW") "show_${s.id}" else "movie_${s.id}"
            }

            val filtered = recommendations.filter { keyOf(it) !in excludedKeys }
            val frequencyMap = filtered.groupingBy(keyOf).eachCount()

            val topRecommendations = filtered
                .distinctBy(keyOf)
                .sortedByDescending { frequencyMap[keyOf(it)] ?: 0 }
                .take(15)

            val movies = topRecommendations.map { summary ->
                val isTv = summary.mediaType == "TV_SHOW"
                val itemUrl = if (isTv) "show_${summary.id}" else "movie_${summary.id}"
                MovieItem(
                    url = itemUrl,
                    titlePl = summary.title,
                    titleEn = summary.originalTitle,
                    posterUrl = TmdbApiClient.POSTER_BASE_URL + summary.posterPath,
                    backgroundUrl = summary.backdropPath?.let { TmdbApiClient.BACKDROP_BASE_URL + it },
                    description = summary.overview,
                    year = summary.releaseDate?.take(4)?.toIntOrNull(),
                    filmanRating = summary.voteAverage?.takeIf { it > 0.0 }?.let { Rating(it.toFloat(), 10f) },
                    imdbRating = summary.voteAverage?.takeIf { it > 0.0 }?.let { Rating(it.toFloat(), 10f) },
                    isTvShow = isTv,
                    detailsRequest = DetailsRequest.Url(itemUrl, isTv),
                )
            }
            movies to hasMore
        }
}
