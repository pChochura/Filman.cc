package com.pointlessapps.filman.data.recommendation

import com.pointlessapps.filman.data.api.dto.MediaSummary
import com.pointlessapps.filman.data.api.dto.PaginatedResponse
import com.pointlessapps.filman.data.api.tmdb.TmdbApiClient
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.model.TmdbFeed
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationManagerTest {

    private val favoritesManager: FavoritesManager = mockk()
    private val progressManager: ProgressManager = mockk()
    private val tmdbApiClient: TmdbApiClient = mockk()

    private val manager = RecommendationManager(
        favoritesManager = favoritesManager,
        progressManager = progressManager,
        tmdbApiClient = tmdbApiClient,
    )

    @Test
    fun `returns empty list when no favorites or progress items exist`() = runTest {
        every { favoritesManager.getFavorites() } returns emptyList()
        every { progressManager.progressItemsFlow } returns MutableStateFlow(emptyList())

        val (movies, hasMore) = manager.getPersonalizedRecommendations()

        assertTrue(movies.isEmpty())
        assertFalse(hasMore)
    }

    @Test
    fun `excludes already watched movies from recommendations and prioritizes favorite-based items`() = runTest {
        val favoriteMovie = MovieItem(url = "movie_1", titlePl = "Favorite SciFi", posterUrl = "p1")
        val watchedMovie = ProgressItem.Watched(url = "movie_99", parentUrl = null)

        every { favoritesManager.getFavorites() } returns listOf(favoriteMovie)
        every { progressManager.progressItemsFlow } returns MutableStateFlow(listOf(watchedMovie))

        val rec1 = MediaSummary(
            id = 99, // Already watched! Must be excluded
            title = "Watched Movie Rec",
            overview = "Desc",
            posterPath = "/watched.jpg",
            mediaType = "MOVIE",
        )
        val rec2 = MediaSummary(
            id = 200,
            title = "New Movie Rec",
            overview = "Desc",
            posterPath = "/new.jpg",
            mediaType = "MOVIE",
        )

        coEvery { tmdbApiClient.fetchFeed(any<TmdbFeed.Recommendations>(), any()) } returns PaginatedResponse(
            page = 1,
            results = listOf(rec1, rec2),
            totalPages = 1,
            totalResults = 2,
        )

        val (movies, hasMore) = manager.getPersonalizedRecommendations()

        assertEquals(1, movies.size)
        assertEquals("movie_200", movies[0].url)
        assertFalse(hasMore)
    }
}
