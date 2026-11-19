package com.pointlessapps.filman.data.recommendation

import com.pointlessapps.filman.data.api.tmdb.TmdbApiClient
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.canonicalUrl
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
                .mapNotNull { MediaRef.parse(it.url, it.isTvShow)?.parentRef }
            val favSeedKeys = favRefs.map { it.canonicalKey }.toSet()

            val progressRefs = progressManager.progressItemsFlow.value
                .mapNotNull {
                    MediaRef.parse(
                        it.parentUrl ?: it.url,
                        it.season != null || it.parentUrl?.startsWith("show_") == true,
                    )?.parentRef
                }

            // Prioritize favorites as seeds; supplement with progress seeds if needed
            val seedRefs = (favRefs.take(6) + progressRefs)
                .distinctBy { it.canonicalKey }
                .take(8)

            if (seedRefs.isEmpty()) return@withContext emptyList<MovieItem>() to false

            // Exclude seeds and all watched items from progress
            val allWatchedKeys = progressManager.progressItemsFlow.value
                .flatMap { item ->
                    listOfNotNull(
                        item.url.canonicalUrl(),
                        item.parentUrl?.canonicalUrl(),
                    )
                }.toSet()

            val excludedKeys = (seedRefs.map { it.canonicalKey } + allWatchedKeys).toSet()

            val seedPages = seedRefs.map { ref ->
                async {
                    val result = runCatching { tmdbApiClient.fetchFeed(TmdbFeed.Recommendations(ref), page) }.getOrNull()
                    ref to result
                }
            }.awaitAll()

            val pages = seedPages.mapNotNull { it.second }
            val hasMore = pages.any { it.page < it.totalPages }

            val keyOf = { s: com.pointlessapps.filman.data.api.dto.MediaSummary ->
                if (s.mediaType == "TV_SHOW") "show_${s.id}" else "movie_${s.id}"
            }

            // Score items: base score from frequency + higher weight if recommended by a favorite seed
            val scores = mutableMapOf<String, Int>()
            val candidatesByKey = mutableMapOf<String, com.pointlessapps.filman.data.api.dto.MediaSummary>()

            for ((seedRef, pageResult) in seedPages) {
                if (pageResult == null) continue
                val isFavSeed = seedRef.canonicalKey in favSeedKeys
                for (item in pageResult.results) {
                    if (item.posterPath.isNullOrBlank() || item.mediaType == "PERSON") continue
                    val key = keyOf(item)
                    if (key in excludedKeys) continue

                    candidatesByKey.putIfAbsent(key, item)
                    val weight = if (isFavSeed) 3 else 1
                    scores[key] = (scores[key] ?: 0) + weight
                }
            }

            if (candidatesByKey.isEmpty()) return@withContext emptyList<MovieItem>() to false

            val topRecommendations = candidatesByKey.values
                .sortedByDescending { scores[keyOf(it)] ?: 0 }
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
