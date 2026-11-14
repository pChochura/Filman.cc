package com.pointlessapps.filman

import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.config.CdaHdConfig
import com.pointlessapps.filman.config.ZaluknijConfig
import com.pointlessapps.filman.data.cache.ModelCache
import com.pointlessapps.filman.data.local.CdaHdSessionManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SearchHistoryManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.ZaluknijSessionManager
import com.pointlessapps.filman.data.model.SourcePriorityConfig
import com.pointlessapps.filman.data.api.NetworkClient
import com.pointlessapps.filman.utils.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var sessionManager: SessionManager
    private lateinit var zaluknijSessionManager: ZaluknijSessionManager
    private lateinit var cdaHdSessionManager: CdaHdSessionManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var progressManager: ProgressManager
    private lateinit var searchHistoryManager: SearchHistoryManager
    private lateinit var modelCache: ModelCache

    private lateinit var filmanChallengeActive: MutableStateFlow<Boolean>
    private lateinit var zaluknijChallengeActive: MutableStateFlow<Boolean>
    private lateinit var cdaHdChallengeActive: MutableStateFlow<Boolean>

    @Before
    fun setUp() {
        mockkObject(NetworkClient)
        every { NetworkClient.setCloudflareCookie(any(), any()) } just Runs

        filmanChallengeActive = MutableStateFlow(false)
        zaluknijChallengeActive = MutableStateFlow(false)
        cdaHdChallengeActive = MutableStateFlow(false)

        sessionManager = mockk(relaxed = true) {
            every { userAgentFlow } returns MutableStateFlow("TestAgent/1.0")
            every { cookieFlow } returns MutableStateFlow(null)
            every { isChallengeActive } returns filmanChallengeActive
            every { challengeRequest } returns MutableStateFlow(null)
        }

        zaluknijSessionManager = mockk(relaxed = true) {
            every { isChallengeActive } returns zaluknijChallengeActive
            every { challengeRequest } returns MutableStateFlow(null)
        }

        cdaHdSessionManager = mockk(relaxed = true) {
            every { isChallengeActive } returns cdaHdChallengeActive
            every { challengeRequest } returns MutableStateFlow(null)
        }

        settingsManager = mockk(relaxed = true) {
            every { sourcesPriorityFlow } returns MutableStateFlow(emptyList())
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
        }

        progressManager = mockk(relaxed = true)
        searchHistoryManager = mockk(relaxed = true)
        modelCache = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkObject(NetworkClient)
    }

    private fun createViewModel(): MainViewModel = MainViewModel(
        sessionManager = sessionManager,
        zaluknijSessionManager = zaluknijSessionManager,
        cdaHdSessionManager = cdaHdSessionManager,
        settingsManager = settingsManager,
        progressManager = progressManager,
        searchHistoryManager = searchHistoryManager,
        modelCache = modelCache,
    )

    @Test
    fun `source toggle persists only the selected source`() = runTest {
        val sources = listOf(
            SourcePriorityConfig("tmdb", true),
            SourcePriorityConfig("filman", false),
            SourcePriorityConfig("ekino", true),
        )
        every { settingsManager.sourcesPriorityFlow } returns MutableStateFlow(sources)
        val viewModel = createViewModel()
        testScheduler.runCurrent()

        viewModel.onToggleSource(1)

        verify(exactly = 1) {
            settingsManager.setSourcesPriority(
                listOf(sources[0], sources[1].copy(isEnabled = true), sources[2]),
            )
        }
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

    @Test
    fun `challenge starts in background without dialog and resets visibility on solve or cancel`() =
        runTest {
            val viewModel = createViewModel()

            assertNull(viewModel.challengeRequest.value)
            assertFalse(viewModel.isChallengeDialogVisible.value)

            filmanChallengeActive.value = true

            assertNotNull(viewModel.challengeRequest.value)
            assertEquals(FilmanConfig.BASE_URL, viewModel.challengeRequest.value?.targetUrl)
            assertFalse(viewModel.isChallengeDialogVisible.value)

            viewModel.setChallengeDialogVisible(true)
            assertTrue(viewModel.isChallengeDialogVisible.value)

            viewModel.onChallengeSolved(FilmanConfig.BASE_URL, "cf_clearance=fresh_token")

            coVerify(exactly = 1) { sessionManager.saveCloudflareCookie("cf_clearance=fresh_token") }
            verify(exactly = 1) { sessionManager.onChallengeSolved() }
            assertNull(viewModel.challengeRequest.value)
            assertFalse(viewModel.isChallengeDialogVisible.value)

            filmanChallengeActive.value = false
            filmanChallengeActive.value = true
            viewModel.setChallengeDialogVisible(true)
            viewModel.cancelChallenge()

            verify(exactly = 1) { sessionManager.cancelChallenge() }
            verify(exactly = 1) { zaluknijSessionManager.cancelChallenge() }
            assertNull(viewModel.challengeRequest.value)
            assertFalse(viewModel.isChallengeDialogVisible.value)
        }

    @Test
    fun `pending providers survive absent collectors and dialogs do not replace one another`() = runTest {
        zaluknijChallengeActive.value = true
        val viewModel = createViewModel()
        cdaHdChallengeActive.value = true

        assertEquals(2, viewModel.activeChallenges.value.size)
        viewModel.setChallengeDialogVisible(true, ZaluknijConfig.BASE_URL)
        viewModel.setChallengeDialogVisible(true, CdaHdConfig.BASE_URL)
        assertEquals(ZaluknijConfig.BASE_URL, viewModel.visibleChallengeDomain.value)

        viewModel.cancelChallenge(ZaluknijConfig.BASE_URL)
        assertEquals(setOf(CdaHdConfig.BASE_URL), viewModel.activeChallenges.value.keys)
        verify(exactly = 1) { zaluknijSessionManager.cancelChallenge() }
        verify(exactly = 0) { cdaHdSessionManager.cancelChallenge() }
    }
}
