package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.api.dto.MediaSummary
import com.pointlessapps.filman.data.api.dto.PaginatedResponse
import com.pointlessapps.filman.data.api.tmdb.TmdbApiClient
import com.pointlessapps.filman.data.scraper.LocalLinkExtractor
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FilmanRepositorySearchTest {
    @Test
    fun `search excludes null empty and blank poster paths`() = runTest {
        val tmdb: TmdbApiClient = mockk()
        val repository = FilmanRepository(tmdb, mockk<LocalLinkExtractor>())
        val results = listOf(
            MediaSummary(1, "Movie", "", posterPath = "/movie.jpg", mediaType = "MOVIE"),
            MediaSummary(2, "Show", "", posterPath = "/show.jpg", mediaType = "TV_SHOW"),
            MediaSummary(3, "Missing", "", mediaType = "MOVIE"),
            MediaSummary(4, "Empty", "", posterPath = "", mediaType = "TV_SHOW"),
            MediaSummary(5, "Blank", "", posterPath = "  ", mediaType = "MOVIE"),
        )
        coEvery { tmdb.search(query = "query", page = 1) } returns
            PaginatedResponse(1, results, 1, results.size)

        val search = repository.searchMovies("query").toList().single()

        assertEquals(listOf("movie_1"), search.movies.map { it.url })
        assertEquals(listOf("show_2"), search.tvShows.map { it.url })
        assertEquals("https://image.tmdb.org/t/p/w500/movie.jpg", search.movies.single().posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w500/show.jpg", search.tvShows.single().posterUrl)
    }
}
