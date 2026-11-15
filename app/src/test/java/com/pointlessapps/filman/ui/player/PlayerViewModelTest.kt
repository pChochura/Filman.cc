package com.pointlessapps.filman.ui.player

import app.cash.turbine.test
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.TvShowSettingsManager
import com.pointlessapps.filman.data.model.SubtitleStylePreferences
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: FilmanRepository = mockk(relaxed = true)
    private val videoUrlResolver: VideoUrlResolver = mockk(relaxed = true)
    private val settingsManager: SettingsManager = mockk()
    private val tvShowSettingsManager: TvShowSettingsManager = mockk(relaxed = true)
    private val progressManager: ProgressManager = mockk(relaxed = true)
    private val sessionManager: com.pointlessapps.filman.data.local.SessionManager = mockk(relaxed = true)
    private val zaluknijSessionManager: com.pointlessapps.filman.data.local.ZaluknijSessionManager = mockk(relaxed = true)
    private val cdaHdSessionManager: com.pointlessapps.filman.data.local.CdaHdSessionManager = mockk(relaxed = true)

    private fun createViewModel(): PlayerViewModel {
        every { zaluknijSessionManager.cookieFlow } returns MutableStateFlow(null)
        every { cdaHdSessionManager.cookieFlow } returns MutableStateFlow(null)
        every { zaluknijSessionManager.verificationGeneration } returns MutableStateFlow(0L)
        every { cdaHdSessionManager.verificationGeneration } returns MutableStateFlow(0L)
        every { sessionManager.verificationGeneration } returns MutableStateFlow(0L)
        every { progressManager.progressItemsFlow } returns MutableStateFlow(emptyList())
        every { settingsManager.subtitleStylePreferencesFlow } returns MutableStateFlow(
            SubtitleStylePreferences(),
        )
        every { settingsManager.initialAppearanceTypeFlow } returns MutableStateFlow(
            SettingsConstants.NextEpisodeAppearance.SHOW,
        )
        every { settingsManager.initialAppearanceOffsetFlow } returns MutableStateFlow(85L)
        every { settingsManager.initialAppearancePercentageFlow } returns MutableStateFlow(100L)
        every { settingsManager.secondaryAppearanceTypeFlow } returns MutableStateFlow(
            SettingsConstants.NextEpisodeAppearance.SHOW_IN_OVERLAY,
        )
        every { settingsManager.secondaryAppearanceOffsetFlow } returns MutableStateFlow(10L)
        every { settingsManager.secondaryAppearancePercentageFlow } returns MutableStateFlow(100L)
        every { settingsManager.secondaryTimerAmountFlow } returns MutableStateFlow(10L)
        every { settingsManager.autoPlayNextFlow } returns MutableStateFlow(true)
        every { settingsManager.preferredQualityFlow } returns MutableStateFlow("1080p")

        return PlayerViewModel(
            repository = repository,
            videoUrlResolver = videoUrlResolver,
            settingsManager = settingsManager,
            tvShowSettingsManager = tvShowSettingsManager,
            sessionManager = sessionManager,
            zaluknijSessionManager = zaluknijSessionManager,
            cdaHdSessionManager = cdaHdSessionManager,
            progressManager = progressManager,
        )
    }

    @Test
    fun `Initialize handles null detailedMedia correctly`() = runTest {
        val testUrl = "123"

        coEvery { repository.getMediaDetails(testUrl) } returns null

        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem() // Initial state

            viewModel.onEvent(PlayerEvent.LoadDetails(testUrl))

            var state = awaitItem()
            if (state.shared.isLoading) {
                state = awaitItem()
            }

            assertEquals(
                com.pointlessapps.filman.core.ui.R.string.error_media_not_found,
                (state.shared.errorMessage as TextValue.StringResource).resId
            )

            cancelAndIgnoreRemainingEvents()
        }

    }

    @Test
    fun `resume waits for stored progress and retains position when switching providers`() = runTest {
        val url = "https://filman.cc/movie"
        val movie = MovieItem(url = url, titlePl = "Movie", posterUrl = "")
        val details = DetailedMedia(baseItem = movie)
        val native = ExtractedVideo(url = "https://native.example/video", sourceWebsite = "filman")
        val web = ExtractedVideo(url = "https://web.example/video", sourceWebsite = "ekino", isWebView = true)
        val storedProgress = CompletableDeferred<ProgressItem?>()
        coEvery { progressManager.getProgressForUrlWhenLoaded(url) } coAnswers { storedProgress.await() }
        coEvery { repository.getMediaDetails(url) } returns details
        coEvery { videoUrlResolver.resolveAll(url, details) } returns listOf(native, web)
        every { videoUrlResolver.findBest(any(), any()) } returns native
        coEvery { repository.getLinks(url, false, any()) } returns null

        val viewModel = createViewModel()
        viewModel.onEvent(PlayerEvent.LoadDetails(url))
        testScheduler.runCurrent()
        assertTrue(viewModel.state.value.shared.isLoading)
        storedProgress.complete(
            ProgressItem.InProgress(
                url = url, parentUrl = url, progressMs = 120_000, progressPercentage = 0.3f,
            ),
        )
        testScheduler.runCurrent()

        assertEquals(120_000L, viewModel.state.value.startPositionMs)
        assertEquals(120_000L, viewModel.state.value.currentPositionMs)
        verify { progressManager.saveProgress(movie, 120_000L, 0L) }

        viewModel.onEvent(PlayerEvent.CurrentPositionChanged(135_000))
        viewModel.onEvent(PlayerEvent.DurationProvided(400_000))
        viewModel.onEvent(PlayerEvent.ChangeVideoSource(web))
        assertTrue(viewModel.state.value.isWebView)
        assertEquals(135_000L, viewModel.state.value.startPositionMs)
        assertEquals(135_000L, viewModel.state.value.currentPositionMs)
        assertEquals(0L, viewModel.state.value.duration)

        viewModel.onEvent(PlayerEvent.ChangeVideoSource(native))
        assertEquals(135_000L, viewModel.state.value.startPositionMs)
    }

    @Test
    fun `webview first and source failure retain saved position`() = runTest {
        val url = "https://filman.cc/movie"
        val movie = MovieItem(url = url, titlePl = "Movie", posterUrl = "")
        val details = DetailedMedia(baseItem = movie)
        val web = ExtractedVideo(url = "https://web.example/video", sourceWebsite = "ekino", isWebView = true)
        val native = ExtractedVideo(url = "https://native.example/video", sourceWebsite = "filman")
        coEvery { progressManager.getProgressForUrlWhenLoaded(url) } returns
            ProgressItem.InProgress(url = url, parentUrl = url, progressMs = 120_000, progressPercentage = 0.3f)
        coEvery { repository.getMediaDetails(url) } returns details
        coEvery { videoUrlResolver.resolveAll(url, details) } returns listOf(web, native)
        every { videoUrlResolver.findBest(any(), any()) } returns web
        every { videoUrlResolver.getAlternativeUrls(details) } returns listOf(web, native)
        coEvery { repository.getLinks(url, false, any()) } returns null

        val viewModel = createViewModel()
        viewModel.onEvent(PlayerEvent.LoadDetails(url))
        testScheduler.runCurrent()

        assertTrue(viewModel.state.value.isWebView)
        assertEquals(120_000L, viewModel.state.value.startPositionMs)
        viewModel.onEvent(PlayerEvent.SaveProgress(url, viewModel.state.value.currentPositionMs))
        verify(atLeast = 1) { progressManager.saveProgress(movie, 120_000L, 0L) }

        viewModel.onEvent(PlayerEvent.PlayerError)
        assertEquals(native.url, viewModel.state.value.videoUrl)
        assertEquals(120_000L, viewModel.state.value.startPositionMs)
    }
}
