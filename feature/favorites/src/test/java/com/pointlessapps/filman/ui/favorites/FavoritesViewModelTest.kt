package com.pointlessapps.filman.ui.favorites

import app.cash.turbine.test
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val favoritesManager: FavoritesManager = mockk(relaxed = true)
    private val watchlistManager: WatchlistManager = mockk(relaxed = true)
    private val progressManager: ProgressManager = mockk(relaxed = true)

    private val favoritesFlow = MutableStateFlow<List<MovieItem>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { favoritesManager.favoritesFlow } returns favoritesFlow
        every { progressManager.progressItemsFlow } returns MutableStateFlow(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `favorites flow maps movie items to grid items`() = runTest {
        val movie = MovieItem(
            titlePl = "Test Movie",
            url = "http://test.com/movie",
            posterUrl = "http://test.com/poster.jpg",
        )
        favoritesFlow.value = listOf(movie)

        val viewModel = FavoritesViewModel(
            favoritesManager = favoritesManager,
            watchlistManager = watchlistManager,
            progressManager = progressManager,
        )

        viewModel.favorites.test {
            val items = awaitItem()
            if (items.isEmpty()) {
                val next = awaitItem()
                assertEquals(1, next.size)
                assertEquals(movie, next.first().movieItem)
            } else {
                assertEquals(1, items.size)
                assertEquals(movie, items.first().movieItem)
            }
        }
    }
}
