package com.pointlessapps.filman.ui.screensaver

import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.PageResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreensaverViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<FilmanRepository>()
    private val watchlist = MutableStateFlow<List<MovieItem>>(emptyList())
    private val manager = mockk<WatchlistManager>()
    private val movie = MovieItem(url = "movie_1", titlePl = "Same title", posterUrl = "poster")
    private val otherMovie = MovieItem(url = "movie_2", titlePl = "Same title", posterUrl = "other")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.getCategoryPage("", 1) } returns PageResult(
            featuredItems = emptyList(),
            movies = listOf(movie, otherMovie),
        )
        every { manager.watchlistFlow } returns watchlist
        every { manager.isInWatchlist(any()) } answers {
            watchlist.value.any { it.url == firstArg<String>() }
        }
        every { manager.addToWatchlist(any()) } answers {
            watchlist.value = watchlist.value + firstArg<MovieItem>()
        }
        every { manager.removeFromWatchlist(any()) } answers {
            watchlist.value = watchlist.value.filterNot { it.url == firstArg<String>() }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `slides retain their original identities and metadata`() = runTest(dispatcher) {
        val viewModel = ScreensaverViewModel(repository, manager)
        advanceUntilIdle()

        assertEquals(listOf(movie, otherMovie), viewModel.movies.value)
        assertSame(watchlist, viewModel.watchlist)
    }

    @Test
    fun `toggle adds and removes the selected item by url`() = runTest(dispatcher) {
        watchlist.value = listOf(otherMovie)
        val viewModel = ScreensaverViewModel(repository, manager)
        advanceUntilIdle()

        assertTrue(viewModel.toggleWatchlist(movie))
        assertEquals(listOf(otherMovie, movie), viewModel.watchlist.value)
        assertFalse(viewModel.toggleWatchlist(movie))
        assertEquals(listOf(otherMovie), viewModel.watchlist.value)
        verify(exactly = 1) { manager.addToWatchlist(movie) }
        verify(exactly = 1) { manager.removeFromWatchlist(movie.url) }
    }

    @Test
    fun `toggle respects watchlist changes made elsewhere`() = runTest(dispatcher) {
        val viewModel = ScreensaverViewModel(repository, manager)
        advanceUntilIdle()
        watchlist.value = listOf(movie)

        assertFalse(viewModel.toggleWatchlist(movie))
        assertTrue(viewModel.watchlist.value.isEmpty())
    }
}
