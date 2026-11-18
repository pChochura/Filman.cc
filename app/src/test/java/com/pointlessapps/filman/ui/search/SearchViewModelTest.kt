package com.pointlessapps.filman.ui.search

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SearchHistoryManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaType
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.PageResult
import com.pointlessapps.filman.data.model.SearchResults
import com.pointlessapps.filman.data.model.TmdbFeed
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import com.pointlessapps.filman.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: FilmanRepository = mockk(relaxed = true)
    private val favoritesManager: FavoritesManager = mockk(relaxed = true)
    private val watchlistManager: WatchlistManager = mockk(relaxed = true)
    private val progressManager: ProgressManager = mockk(relaxed = true)
    private val searchHistoryManager: SearchHistoryManager = mockk(relaxed = true)

    private fun createViewModel(): SearchViewModel {
        every { favoritesManager.favoritesFlow } returns MutableStateFlow(emptyList())
        every { progressManager.progressItemsFlow } returns MutableStateFlow(emptyList())
        every { watchlistManager.watchlistFlow } returns MutableStateFlow(emptyList())
        every { searchHistoryManager.historyFlow } returns MutableStateFlow(emptyList())

        return SearchViewModel(
            repository = repository,
            favoritesManager = favoritesManager,
            watchlistManager = watchlistManager,
            progressManager = progressManager,
            searchHistoryManager = searchHistoryManager,
        )
    }

    @Test
    fun `search keeps same-title results separate and excludes results without posters`() = runTest {
        every { repository.searchMovies("Dune") } returns flow {
            emit(
                SearchResults(
                    movies = listOf(
                        MovieItem(url = "movie_1", titlePl = "Dune", posterUrl = "poster1"),
                        MovieItem(url = "movie_2", titlePl = "Dune", posterUrl = "poster2"),
                        MovieItem(url = "movie_3", titlePl = "No poster", posterUrl = ""),
                        MovieItem(url = "movie_4", titlePl = "Blank poster", posterUrl = "  "),
                        MovieItem(url = "movie_1", titlePl = "Duplicate", posterUrl = "poster1"),
                    ),
                    tvShows = listOf(
                        MovieItem(url = "show_5", titlePl = "Dune", posterUrl = "poster5"),
                        MovieItem(url = "show_6", titlePl = "No poster show", posterUrl = ""),
                    ),
                ),
            )
        }
        val viewModel = createViewModel()
        viewModel.onEvent(SearchEvent.LoadSearchData("Dune", force = true))
        advanceUntilIdle()

        val items = viewModel.state.value.shared.moviesSections.single().movies
        assertEquals(listOf("movie_1", "movie_2", "show_5"), items.map { it.movieItem.url })
        assertTrue(items.all { it is MoviesGridItem.Single })
        assertTrue(items.last().movieItem.isTvShow)
    }

    @Test
    fun `category results can load more after a page without posters`() = runTest {
        val noPosters = (1..20).map { MovieItem(url = "movie_$it", titlePl = "Dune", posterUrl = "") }
        val nextPage = listOf(
            MovieItem(url = "movie_21", titlePl = "Dune", posterUrl = "poster1"),
            MovieItem(url = "movie_22", titlePl = "Dune", posterUrl = "poster2"),
            MovieItem(url = "movie_23", titlePl = "Dune", posterUrl = " "),
        )
        val moviesFeed = TmdbFeed.DiscoverByGenre(MediaType.MOVIE, 1)
        val tvFeed = TmdbFeed.DiscoverByGenre(MediaType.TV, 1)
        coEvery { repository.getFeedPage(moviesFeed, 1) } returns PageResult(
            featuredItems = emptyList(),
            movies = noPosters,
            feed = moviesFeed,
            page = 1,
            totalPages = 2,
        )
        coEvery { repository.getFeedPage(moviesFeed, 2) } returns PageResult(
            featuredItems = emptyList(),
            movies = nextPage,
            feed = moviesFeed,
            page = 2,
            totalPages = 2,
        )
        coEvery { repository.getFeedPage(tvFeed, 1) } returns PageResult(emptyList(), emptyList())

        val viewModel = createViewModel()
        viewModel.onEvent(SearchEvent.LoadSearchDataByCategory(FilterOption("1", "Category"), force = true))
        advanceUntilIdle()

        val section = viewModel.state.value.shared.moviesSections.single()
        assertTrue(section.movies.isEmpty())
        assertTrue(section.hasMore)

        viewModel.onEvent(SearchEvent.LoadMoreForSection(R.string.search_results_movies))
        advanceUntilIdle()

        val items = viewModel.state.value.shared.moviesSections.single().movies
        assertEquals(listOf("movie_21", "movie_22"), items.map { it.movieItem.url })
        assertTrue(items.all { it is MoviesGridItem.Single })
    }
}
