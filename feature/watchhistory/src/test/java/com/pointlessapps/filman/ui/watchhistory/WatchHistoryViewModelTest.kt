package com.pointlessapps.filman.ui.watchhistory

import app.cash.turbine.test
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.ProgressItem
import io.mockk.coVerify
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchHistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val progressManager: ProgressManager = mockk(relaxed = true)
    private val favoritesManager: FavoritesManager = mockk(relaxed = true)
    private val watchlistManager: WatchlistManager = mockk(relaxed = true)

    private val progressItemsFlow = MutableStateFlow<List<ProgressItem>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { progressManager.progressItemsFlow } returns progressItemsFlow
        every { favoritesManager.favoritesFlow } returns MutableStateFlow(emptyList())
        every { watchlistManager.watchlistFlow } returns MutableStateFlow(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `clear history event flow toggles state and calls progressManager clearAll`() = runTest {
        val viewModel = WatchHistoryViewModel(
            progressManager = progressManager,
            favoritesManager = favoritesManager,
            watchlistManager = watchlistManager,
        )

        assertFalse(viewModel.state.value.showClearConfirmation)

        viewModel.onEvent(WatchHistoryEvent.RequestClearHistory)
        assertTrue(viewModel.state.value.showClearConfirmation)

        viewModel.onEvent(WatchHistoryEvent.DismissClearHistory)
        assertFalse(viewModel.state.value.showClearConfirmation)

        viewModel.onEvent(WatchHistoryEvent.RequestClearHistory)
        assertTrue(viewModel.state.value.showClearConfirmation)

        viewModel.onEvent(WatchHistoryEvent.ConfirmClearHistory)
        assertFalse(viewModel.state.value.showClearConfirmation)
        coVerify(exactly = 1) { progressManager.clearAll() }
    }
}
