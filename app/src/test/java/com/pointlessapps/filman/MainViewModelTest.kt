package com.pointlessapps.filman

import com.pointlessapps.filman.data.api.StreamApiLinkSource
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SearchHistoryManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.utils.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var sessionManager: SessionManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var progressManager: ProgressManager
    private lateinit var searchHistoryManager: SearchHistoryManager
    private lateinit var streamApiLinkSource: StreamApiLinkSource

    @Before
    fun setUp() {
        sessionManager = mockk(relaxed = true) {
            every { authTokenFlow } returns MutableStateFlow(null)
            every { usernameFlow } returns MutableStateFlow(null)
        }

        settingsManager = mockk(relaxed = true) {
            every { preferredQualityFlow } returns MutableStateFlow(SettingsConstants.Quality.AUTO)
            every { autoPlayNextFlow } returns MutableStateFlow(true)
            every { initialAppearanceTypeFlow } returns
                MutableStateFlow(SettingsConstants.NextEpisodeAppearance.SHOW_IN_OVERLAY)
            every { initialAppearanceOffsetFlow } returns MutableStateFlow(100L)
            every { secondaryAppearanceTypeFlow } returns
                MutableStateFlow(SettingsConstants.NextEpisodeAppearance.SHOW_WITH_TIMER)
            every { secondaryAppearanceOffsetFlow } returns MutableStateFlow(30L)
            every { secondaryTimerAmountFlow } returns MutableStateFlow(10L)
            every { initialAppearancePercentageFlow } returns MutableStateFlow(5L)
            every { secondaryAppearancePercentageFlow } returns MutableStateFlow(2L)
            every { isScreensaverEnabled } returns MutableStateFlow(true)
            every { screensaverInactivityTime } returns MutableStateFlow(120_000L)
            every { screensaverSlideDuration } returns MutableStateFlow(10_000L)
            every { isLektorEnabled } returns MutableStateFlow(false)
            every { lektorHideSubtitles } returns MutableStateFlow(false)
            every { lektorVoiceId } returns MutableStateFlow("meski")
            every { lektorDuckingVolume } returns MutableStateFlow(1.0f)
            every { lektorTimingOffsetMs } returns MutableStateFlow(-500)
        }

        progressManager = mockk(relaxed = true)
        searchHistoryManager = mockk(relaxed = true)
        streamApiLinkSource = mockk(relaxed = true)
    }

    private fun createViewModel(): MainViewModel = MainViewModel(
        sessionManager = sessionManager,
        settingsManager = settingsManager,
        progressManager = progressManager,
        searchHistoryManager = searchHistoryManager,
        streamApiLinkSource = streamApiLinkSource,
    )

    @Test
    fun `clear cache clears stream caches`() = runTest {
        val viewModel = createViewModel()
        viewModel.clearCache()
        testScheduler.runCurrent()

        coVerify(exactly = 1) { streamApiLinkSource.clearCache() }
    }

    @Test
    fun `handleNavigateTo Login does not push duplicate Login routes`() {
        val viewModel = createViewModel()
        viewModel.handleNavigateTo(Route.Home)
        assertEquals(listOf(Route.Home), viewModel.backStack.toList())

        viewModel.handleNavigateTo(Route.Login(returnRoute = Route.Movies, replaceCurrentRoute = false))
        val sizeAfterFirstLogin = viewModel.backStack.size
        assertTrue(viewModel.backStack.last() is Route.Login)

        // Second concurrent Route.Login emission (e.g. from parallel API failures) must be ignored
        viewModel.handleNavigateTo(Route.Login(returnRoute = Route.Home, replaceCurrentRoute = true))
        assertEquals(sizeAfterFirstLogin, viewModel.backStack.size)
        assertEquals(Route.Movies, (viewModel.backStack.last() as Route.Login).returnRoute)
    }
}
