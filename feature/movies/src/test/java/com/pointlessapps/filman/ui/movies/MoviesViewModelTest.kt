package com.pointlessapps.filman.ui.movies

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.TiwiRepository
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaFilterState
import com.pointlessapps.filman.data.model.MediaType
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.PageResult
import com.pointlessapps.filman.data.model.SortOption
import com.pointlessapps.filman.data.model.TmdbFeed
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoviesViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository: TiwiRepository = mockk(relaxed = true)
    private val favoritesManager: FavoritesManager = mockk(relaxed = true)
    private val watchlistManager: WatchlistManager = mockk(relaxed = true)
    private val progressManager: ProgressManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { favoritesManager.favoritesFlow } returns MutableStateFlow(emptyList())
        every { watchlistManager.watchlistFlow } returns MutableStateFlow(emptyList())
        every { progressManager.progressItemsFlow } returns MutableStateFlow(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MoviesViewModel(
        repository = repository,
        favoritesManager = favoritesManager,
        watchlistManager = watchlistManager,
        progressManager = progressManager,
    )

    @Test
    fun `LoadHomeData loads curated feeds and movie genres in parallel`() = runTest(testDispatcher) {
        val sample = MovieItem(url = "movie_1", titlePl = "Sample", posterUrl = "poster1")
        coEvery { repository.getFeedPage(any(), any()) } returns PageResult(listOf(sample), listOf(sample))
        coEvery { repository.getMovieCategories() } returns listOf(FilterOption("28", "Action"))

        val viewModel = createViewModel()
        viewModel.onEvent(MoviesEvent.LoadHomeData)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf(FilterOption("28", "Action")), state.availableGenres)
        assertTrue(state.moviesSections.isNotEmpty())
    }

    @Test
    fun `toggling genre loads filtered results, paginates, and resetting restores curated rails`() =
        runTest(testDispatcher) {
            val curated = MovieItem(url = "movie_1", titlePl = "Curated", posterUrl = "p1")
            val filtered1 = MovieItem(url = "movie_10", titlePl = "Filtered 1", posterUrl = "p10")
            val filtered2 = MovieItem(url = "movie_11", titlePl = "Filtered 2", posterUrl = "p11")
            val expectedFilter = MediaFilterState(selectedGenreIds = listOf(28))
            val expectedFeed = TmdbFeed.DiscoverFiltered(MediaType.MOVIE, expectedFilter)

            coEvery { repository.getFeedPage(any(), any()) } returns PageResult(listOf(curated), listOf(curated))
            coEvery { repository.getMovieCategories() } returns listOf(FilterOption("28", "Action"))
            coEvery { repository.getFeedPage(expectedFeed, 1) } returns PageResult(
                featuredItems = emptyList(),
                movies = listOf(filtered1),
                feed = expectedFeed,
                page = 1,
                totalPages = 2,
            )
            coEvery { repository.getFeedPage(expectedFeed, 2) } returns PageResult(
                featuredItems = emptyList(),
                movies = listOf(filtered2),
                feed = expectedFeed,
                page = 2,
                totalPages = 2,
            )

            val viewModel = createViewModel()
            viewModel.onEvent(MoviesEvent.LoadHomeData)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(MoviesEvent.ToggleGenre(28))
            testDispatcher.scheduler.advanceUntilIdle()

            val filtered = viewModel.state.value.filteredSection
            assertNotNull(filtered)
            assertEquals(listOf("movie_10"), filtered!!.movies.map { it.movieItem.url })
            assertTrue(filtered.hasMore)

            viewModel.onEvent(MoviesEvent.LoadMoreForSection(R.string.filter_results_title))
            testDispatcher.scheduler.advanceUntilIdle()

            val paginated = viewModel.state.value.filteredSection!!
            assertEquals(listOf("movie_10", "movie_11"), paginated.movies.map { it.movieItem.url })
            assertFalse(paginated.hasMore)

            viewModel.onEvent(MoviesEvent.ResetFilters)
            testDispatcher.scheduler.advanceUntilIdle()

            assertNull(viewModel.state.value.filteredSection)
            assertFalse(viewModel.state.value.filterState.isActive)
            assertTrue(viewModel.state.value.moviesSections.isNotEmpty())
        }

    @Test
    fun `OpenFilterMenu live-updates overlayMenuData on filter change`() = runTest(testDispatcher) {
        coEvery { repository.getFeedPage(any(), any()) } returns PageResult(emptyList(), emptyList())
        coEvery { repository.getMovieCategories() } returns listOf(FilterOption("28", "Action"))

        val viewModel = createViewModel()
        viewModel.onEvent(MoviesEvent.LoadHomeData)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(MoviesEvent.OpenFilterMenu)
        assertNotNull(viewModel.state.value.overlayMenuData)
        val initialCount = viewModel.state.value.overlayMenuData!!.items.size

        viewModel.onEvent(MoviesEvent.SelectSort(SortOption.RATING_DESC))
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedMenu = viewModel.state.value.overlayMenuData
        assertNotNull(updatedMenu)
        assertTrue(updatedMenu!!.items.size > initialCount)
        assertTrue(updatedMenu.items.any { it.id == "btn_reset_filters" })
    }
}
