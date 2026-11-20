package com.pointlessapps.filman.data.api.tmdb

import com.pointlessapps.filman.data.model.DecadeOption
import com.pointlessapps.filman.data.model.MediaFilterState
import com.pointlessapps.filman.data.model.MediaTypeFilter
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.Rating
import com.pointlessapps.filman.data.model.SortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MediaFilterMatcherTest {
    private val sampleItems = listOf(
        MovieItem(
            url = "movie_1",
            titlePl = "Action Classic",
            posterUrl = "p1",
            year = 1999,
            tiwiRating = Rating(8.7f, 10f),
            isTvShow = false,
            genreIds = listOf(28, 878),
            voteCount = 500,
        ),
        MovieItem(
            url = "show_2",
            titlePl = "Action Show",
            posterUrl = "p2",
            year = 2022,
            tiwiRating = Rating(7.5f, 10f),
            isTvShow = true,
            genreIds = listOf(10759),
            voteCount = 1200,
        ),
        MovieItem(
            url = "movie_3",
            titlePl = "Low Rated Comedy",
            posterUrl = "p3",
            year = 2024,
            tiwiRating = Rating(5.2f, 10f),
            isTvShow = false,
            genreIds = listOf(35),
            voteCount = 80,
        ),
    )

    @Test
    fun `toggleGenre maintains canonical sorted order and computes activeCount`() {
        val state = MediaFilterState()
            .toggleGenre(878)
            .toggleGenre(28)

        assertEquals(listOf(28, 878), state.selectedGenreIds)
        assertEquals(2, state.activeCount)
        assertTrue(state.isActive)

        val toggledOff = state.toggleGenre(28)
        assertEquals(listOf(878), toggledOff.selectedGenreIds)
        assertTrue(toggledOff.isSingleGenreOnly)
    }

    @Test
    fun `mapMovieGenreIds never mixes pipe and comma in multi-genre query`() {
        val provider = TmdbLocaleProvider { Locale.US }

        assertEquals("28|12", provider.mapMovieGenreIds(listOf(10759)))
        assertEquals("28,878", provider.mapMovieGenreIds(listOf(10759, 10765)))
        assertFalse(provider.mapMovieGenreIds(listOf(10759, 35)).contains("|"))
    }

    @Test
    fun `applyLocalFilter filters by mediaType, cross-mapped genre, decade, and rating`() {
        val byMovies = sampleItems.applyLocalFilter(MediaFilterState(mediaTypeFilter = MediaTypeFilter.MOVIES))
        assertEquals(listOf("movie_1", "movie_3"), byMovies.map { it.url })

        val byAction = sampleItems.applyLocalFilter(MediaFilterState(selectedGenreIds = listOf(28)))
        assertEquals(listOf("movie_1", "show_2"), byAction.map { it.url })

        val byDecade = sampleItems.applyLocalFilter(MediaFilterState(decade = DecadeOption.YEAR_2020_2023))
        assertEquals(listOf("show_2"), byDecade.map { it.url })

        val byRating = sampleItems.applyLocalFilter(MediaFilterState(minRating = 8.0f))
        assertEquals(listOf("movie_1"), byRating.map { it.url })
    }

    @Test
    fun `applyLocalFilter sorts by rating, release date, and vote count`() {
        val byRating = sampleItems.applyLocalFilter(MediaFilterState(sortBy = SortOption.RATING_DESC))
        assertEquals(listOf("movie_1", "show_2", "movie_3"), byRating.map { it.url })

        val byRelease = sampleItems.applyLocalFilter(MediaFilterState(sortBy = SortOption.RELEASE_DATE_DESC))
        assertEquals(listOf("movie_3", "show_2", "movie_1"), byRelease.map { it.url })

        val byVotes = sampleItems.applyLocalFilter(MediaFilterState(sortBy = SortOption.VOTE_COUNT_DESC))
        assertEquals(listOf("show_2", "movie_1", "movie_3"), byVotes.map { it.url })
    }
}
