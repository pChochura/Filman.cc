package com.pointlessapps.filman.data.recommendation

import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.scraper.TmdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class RecommendationManager(
    private val favoritesManager: FavoritesManager,
    private val progressManager: ProgressManager,
    private val tmdbClient: TmdbClient,
) {
    suspend fun getPersonalizedRecommendations(page: Int = 1): Pair<List<MovieItem>, Boolean> =
        withContext(Dispatchers.IO) {
            val recentFavorites = favoritesManager.getFavorites().take(5)
            val recentProgress = progressManager.progressItemsFlow.value.take(5)

            val tmdbIdsAndTypes = ConcurrentHashMap.newKeySet<Pair<String, Boolean>>()

            val deferredFavs = recentFavorites.map { item ->
                async {
                    tmdbClient.getTmdbId(item.titlePl, item.year, item.isTvShow)?.let { id ->
                        tmdbIdsAndTypes.add(id to item.isTvShow)
                    }
                }
            }

            val deferredProgress = recentProgress.map { item ->
                async {
                    val title = item.seriesTitle ?: item.titlePl
                    val isTvShow = item.seriesTitle != null
                    tmdbClient.getTmdbId(title, null, isTvShow)?.let { id ->
                        tmdbIdsAndTypes.add(id to isTvShow)
                    }
                }
            }

            (deferredFavs + deferredProgress).awaitAll()

            if (tmdbIdsAndTypes.isEmpty()) return@withContext emptyList<MovieItem>() to false

            val recommendationsWithPages = tmdbIdsAndTypes.map { (id, isTvShow) ->
                async {
                    tmdbClient.getRecommendations(id, isTvShow, page)
                }
            }.awaitAll()

            val recommendations = recommendationsWithPages.flatMap { it.first }
            val hasMore = recommendationsWithPages.any { it.second > page }

            if (recommendations.isEmpty()) return@withContext emptyList<MovieItem>() to false

            val frequencyMap = recommendations.groupingBy { it.id }.eachCount()

            val topRecommendations = recommendations
                .distinctBy { it.id }
                .sortedByDescending { frequencyMap[it.id] ?: 0 }
                .take(15)

            val movies = topRecommendations.map { tmdbMovie ->
                MovieItem(
                    url = "search_${tmdbMovie.id}",
                    titlePl = tmdbMovie.title,
                    posterUrl = tmdbMovie.posterUrl,
                    year = tmdbMovie.releaseYear,
                    isTvShow = tmdbMovie.isTvShow,
                    detailsRequest = DetailsRequest.Search(
                        title = tmdbMovie.title,
                        year = tmdbMovie.releaseYear,
                        isTvShow = tmdbMovie.isTvShow,
                    ),
                )
            }
            movies to hasMore
        }
}
