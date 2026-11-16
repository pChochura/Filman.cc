package com.pointlessapps.filman

import android.util.Log
import android.content.Context
import android.webkit.WebSettings
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
import com.pointlessapps.filman.ui.core.getPlayerUserAgent
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
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
    private lateinit var filmanStartup: MutableStateFlow<Boolean>
    private lateinit var zaluknijStartup: MutableStateFlow<Boolean>
    private lateinit var cdaHdStartup: MutableStateFlow<Boolean>

    @Before
    fun setUp() {
        mockkObject(NetworkClient)
        every { NetworkClient.setCloudflareCookie(any(), any()) } just Runs

        filmanChallengeActive = MutableStateFlow(false)
        zaluknijChallengeActive = MutableStateFlow(false)
        cdaHdChallengeActive = MutableStateFlow(false)
        filmanStartup = MutableStateFlow(false)
        zaluknijStartup = MutableStateFlow(false)
        cdaHdStartup = MutableStateFlow(false)

        sessionManager = mockk(relaxed = true) {
            every { userAgentFlow } returns MutableStateFlow("TestAgent/1.0")
            every { cookieFlow } returns MutableStateFlow(null)
            every { isChallengeActive } returns filmanChallengeActive
            every { isStartupVerification } returns filmanStartup
            every { challengeRequest } returns MutableStateFlow(null)
        }

        zaluknijSessionManager = mockk(relaxed = true) {
            every { isChallengeActive } returns zaluknijChallengeActive
            every { isStartupVerification } returns zaluknijStartup
            every { challengeRequest } returns MutableStateFlow(null)
        }

        cdaHdSessionManager = mockk(relaxed = true) {
            every { isChallengeActive } returns cdaHdChallengeActive
            every { isStartupVerification } returns cdaHdStartup
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
            every { isLektorEnabled } returns MutableStateFlow(false)
            every { lektorVoiceId } returns MutableStateFlow("meski")
            every { lektorDuckingVolume } returns MutableStateFlow(0.22f)
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
    fun `startup verification starts each provider once without opening a dialog`() = runTest {
        val viewModel = createViewModel()

        viewModel.startProviderVerification()
        viewModel.startProviderVerification()

        verify(exactly = 1) { sessionManager.startStartupVerification() }
        verify(exactly = 1) { zaluknijSessionManager.startStartupVerification() }
        verify(exactly = 1) { cdaHdSessionManager.startStartupVerification() }
        assertNull(viewModel.visibleChallengeDomain.value)
    }

    @Test
    fun `user agent updates are immediately visible to verification`() = runTest {
        val agent = MutableStateFlow("previous")
        every { sessionManager.userAgentFlow } returns agent
        val viewModel = createViewModel()

        assertEquals("previous", viewModel.userAgent.value)
        agent.value = "current webview agent"
        assertEquals("current webview agent", viewModel.userAgent.value)
    }

    @Test
    fun `provider verification and player use the installed WebView user agent`() {
        val context = mockk<Context>()
        val userAgent = "Mozilla/5.0 (Linux; Android 16; wv) Version/4.0 Chrome/157.0.0.0"
        mockkStatic(WebSettings::class)
        try {
            every { WebSettings.getDefaultUserAgent(context) } returns userAgent
            createViewModel().setUserAgent(context)
            verify(exactly = 1) { sessionManager.saveUserAgent(userAgent) }
            assertEquals(userAgent, getPlayerUserAgent(context))
        } finally {
            unmockkStatic(WebSettings::class)
        }
    }

    @Test
    fun `background timeout clears the provider without marking it cancelled`() = runTest {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        filmanChallengeActive.value = true
        val viewModel = createViewModel()

        try {
            viewModel.onChallengeTimedOut(FilmanConfig.BASE_URL)

            verify(exactly = 1) { sessionManager.resetChallengeState() }
            verify(exactly = 0) { sessionManager.cancelChallenge() }
            assertNull(viewModel.visibleChallengeDomain.value)
            assertTrue(viewModel.activeChallenges.value.isEmpty())
        } finally {
            unmockkStatic(Log::class)
        }
    }

    @Test
    fun `a real challenge replaces a provider startup verification`() = runTest {
        val viewModel = createViewModel()
        zaluknijStartup.value = true
        zaluknijChallengeActive.value = true
        assertTrue(viewModel.activeChallenges.value.getValue(ZaluknijConfig.BASE_URL).isStartup)

        zaluknijStartup.value = false
        assertFalse(viewModel.activeChallenges.value.getValue(ZaluknijConfig.BASE_URL).isStartup)
        assertNull(viewModel.visibleChallengeDomain.value)
    }

    @Test
    fun `successful verification with no cookie preserves saved provider cookies`() = runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.onChallengeSolved(FilmanConfig.BASE_URL, ""))
        assertTrue(viewModel.onChallengeSolved(ZaluknijConfig.BASE_URL, ""))
        assertTrue(viewModel.onChallengeSolved(CdaHdConfig.BASE_URL, ""))

        verify(exactly = 0) { sessionManager.saveCloudflareCookie(any()) }
        coVerify(exactly = 0) { zaluknijSessionManager.saveCookie(any()) }
        coVerify(exactly = 0) { cdaHdSessionManager.saveCookie(any()) }
        verify(exactly = 1) { sessionManager.onChallengeSolved() }
        verify(exactly = 1) { zaluknijSessionManager.onChallengeSolved() }
        verify(exactly = 1) { cdaHdSessionManager.onChallengeSolved() }
    }

    @Test
    fun `interactive challenges open after the grace period`() {
        assertFalse(shouldShowChallengeDialog("loading", 34_999))
        assertFalse(shouldShowChallengeDialog("clear", 34_999))
        assertTrue(shouldShowChallengeDialog("loading", 35_000))
        assertTrue(shouldShowChallengeDialog("clear", 35_000))
        assertFalse(shouldShowChallengeDialog("interactive", 5_999))
        assertTrue(shouldShowChallengeDialog("interactive", 6_000))
    }

    @Test
    fun `uninspectable challenges open after the grace period including startup`() {
        assertFalse(shouldShowChallengeDialog("challenge", 11_999))
        assertTrue(shouldShowChallengeDialog("challenge", 12_000))
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
