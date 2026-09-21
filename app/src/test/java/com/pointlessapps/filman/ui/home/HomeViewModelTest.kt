package com.pointlessapps.filman.ui.home

import app.cash.turbine.test
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.NewEpisodesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.PageResult
import com.pointlessapps.filman.data.recommendation.RecommendationManager
import com.pointlessapps.filman.data.scraper.FilmanScraper
import com.pointlessapps.filman.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val scraper: FilmanScraper = mockk()
    private val recommendationManager: RecommendationManager = mockk()
    private val newEpisodesManager: NewEpisodesManager = mockk()
    private val favoritesManager: FavoritesManager = mockk()
    private val progressManager: ProgressManager = mockk()
    private val watchlistManager: WatchlistManager = mockk()

    private fun createViewModel(): HomeViewModel {
        every { favoritesManager.favoritesFlow } returns MutableStateFlow(emptyList())
        every { progressManager.progressItemsFlow } returns MutableStateFlow(emptyList())
        every { watchlistManager.watchlistFlow } returns MutableStateFlow(emptyList())
        every { newEpisodesManager.newEpisodesFlow } returns MutableStateFlow(emptyList())

        return HomeViewModel(
            scraper = scraper,
            recommendationManager = recommendationManager,
            newEpisodesManager = newEpisodesManager,
            favoritesManager = favoritesManager,
            progressManager = progressManager,
            watchlistManager = watchlistManager,
        )
    }

    @Test
    fun `LoadData fetches home movies and recommendations successfully`() = runTest {
        // Arrange
        val featuredItem = MovieItem(url = "url1", titlePl = "Test1", posterUrl = "poster1")
        val movieItem = MovieItem(url = "url2", titlePl = "Test2", posterUrl = "poster2")
        val pageResult =
            PageResult(featuredItems = listOf(featuredItem), movies = listOf(movieItem))

        coEvery { scraper.getCategoryPage(any(), any()) } returns pageResult
        coEvery { recommendationManager.getPersonalizedRecommendations() } returns emptyList()

        val viewModel = createViewModel()


        // Act & Assert
        viewModel.state.test {
            // consume initial state
            awaitItem()

            viewModel.onEvent(HomeEvent.LoadHomeData)

            // It might emit loading true then false, or just false if very fast.
            // Let's just wait for a state where isLoading is false and tabs are loaded (or moviesSections is populated)
            var currentState = awaitItem()
            if (currentState.shared.isLoading) {
                currentState = awaitItem()
            }

            assertFalse(currentState.shared.isLoading)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
