package com.pointlessapps.filman.ui.home

import app.cash.turbine.test
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.NewEpisodesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.PageResult
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.recommendation.RecommendationManager
import com.pointlessapps.filman.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: FilmanRepository = mockk()
    private val recommendationManager: RecommendationManager = mockk()
    private val newEpisodesManager: NewEpisodesManager = mockk()
    private val favoritesManager: FavoritesManager = mockk()
    private val progressManager: ProgressManager = mockk()
    private val watchlistManager: WatchlistManager = mockk()

    private lateinit var favoritesFlow: MutableStateFlow<List<MovieItem>>
    private lateinit var progressFlow: MutableStateFlow<List<ProgressItem>>
    private lateinit var watchlistFlow: MutableStateFlow<List<MovieItem>>
    private lateinit var newEpisodesFlow: MutableStateFlow<List<MovieItem>>

    @Before
    fun setUp() {
        favoritesFlow = MutableStateFlow(emptyList())
        progressFlow = MutableStateFlow(emptyList())
        watchlistFlow = MutableStateFlow(emptyList())
        newEpisodesFlow = MutableStateFlow(emptyList())
    }

    private fun createViewModel(): HomeViewModel {
        every { favoritesManager.favoritesFlow } returns favoritesFlow
        every { progressManager.progressItemsFlow } returns progressFlow
        every { watchlistManager.watchlistFlow } returns watchlistFlow
        every { newEpisodesManager.newEpisodesFlow } returns newEpisodesFlow

        return HomeViewModel(
            repository = repository,
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
        val featuredItem = MovieItem(url = "movie_1", titlePl = "Test1", posterUrl = "poster1")
        val movieItem = MovieItem(url = "movie_2", titlePl = "Test2", posterUrl = "poster2")
        val pageResult =
            PageResult(featuredItems = listOf(featuredItem), movies = listOf(movieItem))

        coEvery { repository.getFeedPage(any(), any()) } returns pageResult
        coEvery { recommendationManager.getPersonalizedRecommendations() } returns Pair(emptyList(), false)

        val viewModel = createViewModel()

        // Act & Assert
        viewModel.state.test {
            // consume initial state
            awaitItem()

            viewModel.onEvent(HomeEvent.LoadHomeData)

            var currentState = awaitItem()
            if (currentState.shared.isLoading) {
                currentState = awaitItem()
            }

            assertFalse(currentState.shared.isLoading)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Watchlist filters out watched movies and finished series`() = runTest {
        val watchedMovie = ProgressItem.Watched(url = "movie_10", parentUrl = null)
        val finishedShow = ProgressItem.Watched(url = "show_20_1_10", parentUrl = "show_20", hasNextEpisode = false)
        progressFlow.value = listOf(watchedMovie, finishedShow)

        val item1 = MovieItem(url = "movie_10", titlePl = "Watched Movie", posterUrl = "poster")
        val item2 = MovieItem(url = "movie_11", titlePl = "Unwatched Movie", posterUrl = "poster")
        val item3 = MovieItem(url = "show_20", titlePl = "Finished Show", posterUrl = "poster", isTvShow = true)
        val item4 = MovieItem(url = "show_21", titlePl = "Ongoing Show", posterUrl = "poster", isTvShow = true)
        watchlistFlow.value = listOf(item1, item2, item3, item4)

        val viewModel = createViewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(2, state.watchlist.size)
            assertEquals("movie_11", state.watchlist[0].url)
            assertEquals("show_21", state.watchlist[1].url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Continue watching shows only active items and jump back in shows dropped items`() = runTest {
        val now = System.currentTimeMillis()
        val activeItem = ProgressItem.InProgress(
            url = "movie_active",
            parentUrl = null,
            progressPercentage = 0.4f,
            progressMs = 1_000_000L,
            timestamp = now - 2L * 24 * 60 * 60 * 1000L, // 2 days ago
        )
        val droppedItem = ProgressItem.InProgress(
            url = "movie_dropped",
            parentUrl = null,
            progressPercentage = 0.5f,
            progressMs = 1_500_000L,
            timestamp = now - 45L * 24 * 60 * 60 * 1000L, // 45 days ago
        )
        val sampledItem = ProgressItem.InProgress(
            url = "movie_sampled",
            parentUrl = null,
            progressPercentage = 0.01f,
            progressMs = 30_000L,
            timestamp = now - 1L * 24 * 60 * 60 * 1000L, // 1 day ago
        )
        progressFlow.value = listOf(activeItem, droppedItem, sampledItem)

        val viewModel = createViewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(1, state.progressItems.size)
            assertEquals("movie_active", state.progressItems.first().url)

            assertEquals(1, state.jumpBackInItems.size)
            assertEquals("movie_dropped", state.jumpBackInItems.first().url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Favorites change updates For You section automatically`() = runTest {
        val featuredItem = MovieItem(url = "movie_1", titlePl = "Test1", posterUrl = "poster1")
        val movieItem = MovieItem(url = "movie_2", titlePl = "Test2", posterUrl = "poster2")
        val pageResult = PageResult(featuredItems = listOf(featuredItem), movies = listOf(movieItem))

        val recMovie1 = MovieItem(url = "movie_rec_1", titlePl = "Rec 1", posterUrl = "poster1")
        val recMovie2 = MovieItem(url = "movie_rec_2", titlePl = "Rec 2", posterUrl = "poster2")

        coEvery { repository.getFeedPage(any(), any()) } returns pageResult
        coEvery { recommendationManager.getPersonalizedRecommendations() } returns (listOf(recMovie1) to false) andThen (listOf(recMovie1, recMovie2) to false)

        val viewModel = createViewModel()
        viewModel.onEvent(HomeEvent.LoadHomeData)

        testScheduler.advanceUntilIdle()

        // Add a favorite and advance past debounce window
        favoritesFlow.value = listOf(MovieItem(url = "movie_fav", titlePl = "Fav", posterUrl = "poster_fav"))
        testScheduler.advanceTimeBy(1000)
        testScheduler.advanceUntilIdle()

        val forYouSection = viewModel.state.value.moviesSections.find { it.title == com.pointlessapps.filman.core.ui.R.string.home_for_you }
        assertEquals(2, forYouSection?.movies?.size)
    }
}
