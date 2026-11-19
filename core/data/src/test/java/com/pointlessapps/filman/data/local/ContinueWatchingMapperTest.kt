package com.pointlessapps.filman.data.local

import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.ProgressItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingMapperTest {

    private val now = 1_700_000_000_000L
    private val fiveDaysAgo = now - 5L * 24 * 60 * 60 * 1000L
    private val fortyDaysAgo = now - 40L * 24 * 60 * 60 * 1000L

    @Test
    fun `filterUnwatched filters out watched movies and fully watched series`() {
        val fullyWatched = setOf("movie_100", "show_200")

        val watchlist = listOf(
            MovieItem(url = "movie_100", titlePl = "Watched Movie", posterUrl = "poster"),
            MovieItem(url = "movie_101", titlePl = "Unwatched Movie", posterUrl = "poster"),
            MovieItem(url = "show_200", titlePl = "Finished Show", posterUrl = "poster", isTvShow = true),
            MovieItem(url = "show_201", titlePl = "Ongoing Show", posterUrl = "poster", isTvShow = true),
        )

        val filtered = watchlist.filterUnwatched(fullyWatched)

        assertEquals(2, filtered.size)
        assertEquals("movie_101", filtered[0].url)
        assertEquals("show_201", filtered[1].url)
    }

    @Test
    fun `extractFullyWatchedUrls captures movies and completed series`() {
        val progressList = listOf(
            ProgressItem.Watched(url = "movie_1", parentUrl = null),
            ProgressItem.Watched(url = "movie_2", parentUrl = "movie_2"),
            // TV show with more episodes remaining
            ProgressItem.Watched(url = "show_10_1_1", parentUrl = "show_10", hasNextEpisode = true),
            // TV show with finale watched
            ProgressItem.Watched(url = "show_20_1_10", parentUrl = "show_20", hasNextEpisode = false),
            // TV in progress
            ProgressItem.InProgress(
                url = "show_30_1_2",
                parentUrl = "show_30",
                progressPercentage = 0.5f,
                progressMs = 1_000_000L,
            ),
        )

        val fullyWatched = progressList.extractFullyWatchedUrls()

        assertEquals(setOf("movie_1", "movie_2", "show_20"), fullyWatched)
    }

    @Test
    fun `isSampled identifies briefly previewed items`() {
        val sampledMovie = ProgressItem.InProgress(
            url = "movie_1",
            parentUrl = null,
            progressPercentage = 0.02f,
            progressMs = 60_000L,
        )
        val watchedMovie = ProgressItem.InProgress(
            url = "movie_2",
            parentUrl = null,
            progressPercentage = 0.40f,
            progressMs = 1_800_000L,
        )

        assertTrue(sampledMovie.isSampled())
        assertFalse(watchedMovie.isSampled())

        // TV show episode 1 with 1 minute watched and no other history
        val sampledEpisode1 = ProgressItem.InProgress(
            url = "show_1_1_1",
            parentUrl = "show_1",
            season = 1,
            episode = 1,
            progressPercentage = 0.02f,
            progressMs = 60_000L,
        )
        assertTrue(sampledEpisode1.isSampled(listOf(sampledEpisode1)))

        // TV show episode 1 with another episode already watched in that series
        val prevWatchedEpisode = ProgressItem.Watched(
            url = "show_1_1_0",
            parentUrl = "show_1",
            season = 1,
            episode = 0,
        )
        assertFalse(sampledEpisode1.isSampled(listOf(prevWatchedEpisode, sampledEpisode1)))

        // TV show later episode (e.g. S2E3) with low progress is not considered sampled
        val laterEpisode = ProgressItem.InProgress(
            url = "show_1_2_3",
            parentUrl = "show_1",
            season = 2,
            episode = 3,
            progressPercentage = 0.02f,
            progressMs = 60_000L,
        )
        assertFalse(laterEpisode.isSampled(listOf(laterEpisode)))
    }

    @Test
    fun `toActiveContinueWatchingItems excludes sampled and dropped items`() {
        val activeMovie = ProgressItem.InProgress(
            url = "movie_active",
            parentUrl = null,
            progressPercentage = 0.35f,
            progressMs = 1_200_000L,
            timestamp = fiveDaysAgo,
        )
        val droppedMovie = ProgressItem.InProgress(
            url = "movie_dropped",
            parentUrl = null,
            progressPercentage = 0.50f,
            progressMs = 2_000_000L,
            timestamp = fortyDaysAgo,
        )
        val sampledMovie = ProgressItem.InProgress(
            url = "movie_sampled",
            parentUrl = null,
            progressPercentage = 0.01f,
            progressMs = 30_000L,
            timestamp = fiveDaysAgo,
        )

        val items = listOf(activeMovie, droppedMovie, sampledMovie)
        val activeItems = items.toActiveContinueWatchingItems(now = now, allProgressItems = items)

        assertEquals(1, activeItems.size)
        assertEquals("movie_active", activeItems.first().url)
    }

    @Test
    fun `toJumpBackInItems returns dropped items with meaningful progress`() {
        val activeMovie = ProgressItem.InProgress(
            url = "movie_active",
            parentUrl = null,
            progressPercentage = 0.35f,
            progressMs = 1_200_000L,
            timestamp = fiveDaysAgo,
        )
        val droppedMovie = ProgressItem.InProgress(
            url = "movie_dropped",
            parentUrl = null,
            progressPercentage = 0.50f,
            progressMs = 2_000_000L,
            timestamp = fortyDaysAgo,
        )
        val sampledDroppedMovie = ProgressItem.InProgress(
            url = "movie_sampled_dropped",
            parentUrl = null,
            progressPercentage = 0.01f,
            progressMs = 30_000L,
            timestamp = fortyDaysAgo,
        )

        val items = listOf(activeMovie, droppedMovie, sampledDroppedMovie)
        val jumpBackInItems = items.toJumpBackInItems(now = now, allProgressItems = items)

        assertEquals(1, jumpBackInItems.size)
        assertEquals("movie_dropped", jumpBackInItems.first().url)
    }
}
