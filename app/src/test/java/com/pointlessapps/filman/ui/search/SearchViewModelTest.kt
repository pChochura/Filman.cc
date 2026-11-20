package com.pointlessapps.filman.ui.search

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.TiwiRepository
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SearchHistoryManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaFilterState
import com.pointlessapps.filman.data.model.MediaType
import com.pointlessapps.filman.data.model.MediaTypeFilter
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.PageResult
import com.pointlessapps.filman.data.model.Rating
import com.pointlessapps.filman.data.model.SearchResults
import com.pointlessapps.filman.data.model.SortOption
import com.pointlessapps.filman.data.model.TmdbFeed
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import com.pointlessapps.filman.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: TiwiRepository = mockk(relaxed = true)
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

    @Test
    fun `combining text query and filters narrows cached results locally`() = runTest {
        every { repository.searchMovies("Dune") } returns flow {
            emit(
                SearchResults(
                    movies = listOf(
                        MovieItem(
                            url = "movie_1",
                            titlePl = "Dune",
                            posterUrl = "p1",
                            tiwiRating = Rating(8.5f, 10f),
                            genreIds = listOf(878),
                        ),
                        MovieItem(
                            url = "movie_2",
                            titlePl = "Dune Old",
                            posterUrl = "p2",
                            tiwiRating = Rating(6.0f, 10f),
                            genreIds = listOf(18),
                        ),
                    ),
                    tvShows = listOf(
                        MovieItem(
                            url = "show_5",
                            titlePl = "Dune Prophecy",
                            posterUrl = "p5",
                            tiwiRating = Rating(8.2f, 10f),
                            genreIds = listOf(10765),
                        ),
                    ),
                ),
            )
        }
        val viewModel = createViewModel()
        viewModel.onEvent(SearchEvent.LoadSearchData("Dune", force = true))
        advanceUntilIdle()

        viewModel.onEvent(
            SearchEvent.UpdateFilter(
                MediaFilterState(
                    mediaTypeFilter = MediaTypeFilter.MOVIES,
                    selectedGenreIds = listOf(878),
                    minRating = 7.0f,
                ),
            ),
        )
        advanceUntilIdle()

        val items = viewModel.state.value.shared.moviesSections.single().movies
        assertEquals(listOf("movie_1"), items.map { it.movieItem.url })

        viewModel.onEvent(SearchEvent.ResetFilters)
        advanceUntilIdle()
        val restored = viewModel.state.value.shared.moviesSections.single().movies
        assertEquals(listOf("movie_1", "movie_2", "show_5"), restored.map { it.movieItem.url })
    }

    @Test
    fun `multi-criteria discovery uses DiscoverFiltered and respects MediaTypeFilter`() = runTest {
        val filter = MediaFilterState(
            mediaTypeFilter = MediaTypeFilter.TV_SHOWS,
            selectedGenreIds = listOf(10765),
            sortBy = SortOption.RATING_DESC,
        )
        val tvFeed = TmdbFeed.DiscoverFiltered(MediaType.TV, filter)
        coEvery { repository.getFeedPage(tvFeed, 1) } returns PageResult(
            featuredItems = emptyList(),
            movies = listOf(MovieItem(url = "show_9", titlePl = "Filtered Show", posterUrl = "p9")),
            feed = tvFeed,
            page = 1,
            totalPages = 1,
        )

        val viewModel = createViewModel()
        viewModel.onEvent(SearchEvent.UpdateFilter(filter))
        advanceUntilIdle()

        val sections = viewModel.state.value.shared.moviesSections
        assertEquals(1, sections.size)
        assertEquals(R.string.common_results_tv_shows, sections.single().title)
        assertEquals(listOf("show_9"), sections.single().movies.map { it.movieItem.url })
        coVerify(exactly = 0) {
            repository.getFeedPage(match { it is TmdbFeed.DiscoverFiltered && it.mediaType == MediaType.MOVIE }, any())
        }
    }

    @Test
    fun `ClearQuery switches to filtered discovery when filters are active`() = runTest {
        every { repository.searchMovies("Dune") } returns flow {
            emit(
                SearchResults(
                    movies = listOf(
                        MovieItem(url = "movie_1", titlePl = "Dune", posterUrl = "p1", genreIds = listOf(878)),
                    ),
                ),
            )
        }
        val activeFilter = MediaFilterState(
            mediaTypeFilter = MediaTypeFilter.MOVIES,
            selectedGenreIds = listOf(878),
        )
        val moviesFeed = TmdbFeed.DiscoverFiltered(MediaType.MOVIE, activeFilter)
        coEvery { repository.getFeedPage(moviesFeed, 1) } returns PageResult(
            featuredItems = emptyList(),
            movies = listOf(MovieItem(url = "movie_77", titlePl = "Discover Sci-Fi", posterUrl = "p77")),
            feed = moviesFeed,
            page = 1,
            totalPages = 1,
        )

        val viewModel = createViewModel()
        viewModel.onEvent(SearchEvent.UpdateFilter(activeFilter))
        advanceUntilIdle()

        viewModel.onEvent(SearchEvent.LoadSearchData("Dune", force = true))
        advanceUntilIdle()
        assertEquals("Dune", viewModel.state.value.query)

        viewModel.onEvent(SearchEvent.ClearQuery)
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.query)
        assertTrue(viewModel.state.value.filterState.isActive)
        assertFalse(viewModel.state.value.shared.moviesSections.isEmpty())
        assertEquals(
            listOf("movie_77"),
            viewModel.state.value.shared.moviesSections.single().movies.map { it.movieItem.url },
        )
    }
}
