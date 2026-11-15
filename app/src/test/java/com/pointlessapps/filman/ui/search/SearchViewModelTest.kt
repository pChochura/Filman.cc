package com.pointlessapps.filman.ui.search

import app.cash.turbine.test
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SearchHistoryManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.PageResult
import com.pointlessapps.filman.data.model.SearchResults
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
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

    private val repository: FilmanRepository = mockk(relaxed = true)
    private val favoritesManager: FavoritesManager = mockk(relaxed = true)
    private val watchlistManager: WatchlistManager = mockk(relaxed = true)
    private val progressManager: ProgressManager = mockk(relaxed = true)
    private val searchHistoryManager: SearchHistoryManager = mockk(relaxed = true)
    private val sessionManager: SessionManager = mockk(relaxed = true)

    private val isChallengeActiveFlow = MutableStateFlow(false)
    private val isStartupVerificationFlow = MutableStateFlow(false)
    private val isChallengeCancelledFlow = MutableStateFlow(false)
    private val cookieFlow = MutableStateFlow<String?>(null)

    private fun createViewModel(): SearchViewModel {
        every { favoritesManager.favoritesFlow } returns MutableStateFlow(emptyList())
        every { progressManager.progressItemsFlow } returns MutableStateFlow(emptyList())
        every { watchlistManager.watchlistFlow } returns MutableStateFlow(emptyList())
        every { searchHistoryManager.historyFlow } returns MutableStateFlow(emptyList())
        every { sessionManager.cookieFlow } returns cookieFlow
        every { sessionManager.isChallengeActive } returns isChallengeActiveFlow
        every { sessionManager.isStartupVerification } returns isStartupVerificationFlow
        every { sessionManager.isChallengeCancelled } returns isChallengeCancelledFlow

        return SearchViewModel(
            repository = repository,
            favoritesManager = favoritesManager,
            watchlistManager = watchlistManager,
            progressManager = progressManager,
            searchHistoryManager = searchHistoryManager,
            sessionManager = sessionManager,
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
        assertTrue(items.all { it is MoviesGridItem.Single && it.sources.isEmpty() })
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
        coEvery { repository.getCategoryPage(any(), 1) } returns PageResult(emptyList(), noPosters)
        coEvery { repository.getCategoryPage(any(), 2) } returns PageResult(emptyList(), nextPage)
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
        assertTrue(items.all { it is MoviesGridItem.Single && it.sources.isEmpty() })
    }

    @Test
    fun `startup verification does not block search but a real challenge does`() = runTest {
        isStartupVerificationFlow.value = true
        isChallengeActiveFlow.value = true
        every { repository.searchMovies("Batman") } returns flow {
            delay(1000)
            emit(SearchResults(movies = listOf(MovieItem(url = "batman-url", titlePl = "Batman", posterUrl = "poster"))))
        }
        val viewModel = createViewModel()
        viewModel.onEvent(SearchEvent.LoadSearchData("Batman", force = true))

        assertTrue(viewModel.state.value.isSearching)
        assertFalse(viewModel.state.value.isChallengeInProgress)

        isStartupVerificationFlow.value = false
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isSearching)
        assertTrue(viewModel.state.value.isChallengeInProgress)
    }

    @Test
    fun `when searching and challenge becomes active, search is stopped and challenge progress is displayed`() = runTest {
        every { repository.searchMovies("Batman") } returns flow {
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
        every { repository.searchMovies("Gladiator") } answers {
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
