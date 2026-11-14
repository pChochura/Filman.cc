package com.pointlessapps.filman.ui.search

import app.cash.turbine.test
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SearchHistoryManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.SearchResults
import com.pointlessapps.filman.data.scraper.FilmanScraper
import com.pointlessapps.filman.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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

    private val scraper: FilmanScraper = mockk(relaxed = true)
    private val favoritesManager: FavoritesManager = mockk(relaxed = true)
    private val watchlistManager: WatchlistManager = mockk(relaxed = true)
    private val progressManager: ProgressManager = mockk(relaxed = true)
    private val searchHistoryManager: SearchHistoryManager = mockk(relaxed = true)
    private val sessionManager: SessionManager = mockk(relaxed = true)

    private val isChallengeActiveFlow = MutableStateFlow(false)
    private val isChallengeCancelledFlow = MutableStateFlow(false)
    private val cookieFlow = MutableStateFlow<String?>(null)

    private fun createViewModel(): SearchViewModel {
        every { favoritesManager.favoritesFlow } returns MutableStateFlow(emptyList())
        every { progressManager.progressItemsFlow } returns MutableStateFlow(emptyList())
        every { watchlistManager.watchlistFlow } returns MutableStateFlow(emptyList())
        every { searchHistoryManager.historyFlow } returns MutableStateFlow(emptyList())
        every { sessionManager.cookieFlow } returns cookieFlow
        every { sessionManager.isChallengeActive } returns isChallengeActiveFlow
        every { sessionManager.isChallengeCancelled } returns isChallengeCancelledFlow

        return SearchViewModel(
            scraper = scraper,
            favoritesManager = favoritesManager,
            watchlistManager = watchlistManager,
            progressManager = progressManager,
            searchHistoryManager = searchHistoryManager,
            sessionManager = sessionManager,
        )
    }

    @Test
    fun `when searching and challenge becomes active, search is stopped and challenge progress is displayed`() = runTest {
        coEvery { scraper.searchMovies("Batman") } returns flow {
            delay(1000)
            emit(SearchResults(movies = listOf(MovieItem(url = "batman-url", titlePl = "Batman", posterUrl = "poster"))))
        }

        val viewModel = createViewModel()
        viewModel.onEvent(SearchEvent.LoadSearchData("Batman", force = true))

        assertTrue(viewModel.state.value.isSearching)
        assertFalse(viewModel.state.value.isChallengeInProgress)

        // Cloudflare challenge triggered
        isChallengeActiveFlow.value = true
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSearching)
        assertTrue(viewModel.state.value.isChallengeInProgress)
    }

    @Test
    fun `when challenge is solved, search is restarted with new cookies`() = runTest {
        var searchCalls = 0
        coEvery { scraper.searchMovies("Gladiator") } answers {
            searchCalls++
            flow {
                delay(1000)
                emit(SearchResults(movies = listOf(MovieItem(url = "gladiator-url", titlePl = "Gladiator", posterUrl = "poster"))))
            }
        }

        val viewModel = createViewModel()
        viewModel.onEvent(SearchEvent.LoadSearchData("Gladiator", force = true))

        // Challenge starts while search is in progress
        isChallengeActiveFlow.value = true
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isChallengeInProgress)

        // Challenge solved
        isChallengeActiveFlow.value = false
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isChallengeInProgress)
        assertEquals(2, searchCalls)
        assertEquals("Gladiator", viewModel.state.value.query)
    }

    @Test
    fun `when challenge is active and search is requested, search is deferred and challenge progress shown`() = runTest {
        isChallengeActiveFlow.value = true
        val viewModel = createViewModel()

        viewModel.onEvent(SearchEvent.LoadSearchData("Inception", force = true))
        advanceUntilIdle()

        assertEquals("Inception", viewModel.state.value.query)
        assertFalse(viewModel.state.value.isSearching)
        assertTrue(viewModel.state.value.isChallengeInProgress)
    }
}
