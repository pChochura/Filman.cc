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
import com.pointlessapps.filman.data.api.OpenSubtitlesClient
import com.pointlessapps.filman.data.api.OpenSubtitleCandidate
import com.pointlessapps.filman.data.api.OpenSubtitlesDownloadResponse
import com.pointlessapps.filman.data.api.VideoHasher
import com.pointlessapps.filman.data.api.WyzieSubsClient
import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.player.lektor.engine.LektorVoiceTester
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private val openSubtitlesClient: OpenSubtitlesClient = mockk(relaxed = true)
    private val wyzieSubsClient: WyzieSubsClient = mockk(relaxed = true)
    private val openSubtitlesHasher: VideoHasher = mockk(relaxed = true)

    private fun createViewModel(isLektorEnabled: Boolean = false): PlayerViewModel {
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
        every { settingsManager.isLektorEnabled } returns MutableStateFlow(isLektorEnabled)
        every { settingsManager.lektorHideSubtitles } returns MutableStateFlow(false)
        every { settingsManager.lektorVoiceId } returns MutableStateFlow("meski")
        every { settingsManager.lektorDuckingVolume } returns MutableStateFlow(1.0f)
        every { settingsManager.lektorTimingOffsetMs } returns MutableStateFlow(-500)
        every { settingsManager.lektorEdgeRate } returns MutableStateFlow("+15%")
        every { settingsManager.lektorEdgePitch } returns MutableStateFlow("+0Hz")
        every { settingsManager.lektorEdgeVolume } returns MutableStateFlow("+0%")

        return PlayerViewModel(
            repository = repository,
            videoUrlResolver = videoUrlResolver,
            settingsManager = settingsManager,
            tvShowSettingsManager = tvShowSettingsManager,
            sessionManager = sessionManager,
            zaluknijSessionManager = zaluknijSessionManager,
            cdaHdSessionManager = cdaHdSessionManager,
            progressManager = progressManager,
        ).apply {
            this.openSubtitlesClient = this@PlayerViewModelTest.openSubtitlesClient
            this.wyzieSubsClient = this@PlayerViewModelTest.wyzieSubsClient
            this.openSubtitlesHasher = this@PlayerViewModelTest.openSubtitlesHasher
        }
    }

    @Test
    fun `voice only preference updates state and is persisted`() = runTest {
        every { settingsManager.setLektorHideSubtitles(true) } returns Unit
        every { settingsManager.sourcesPriorityFlow } returns MutableStateFlow(emptyList())
        every { videoUrlResolver.settingsManager } returns settingsManager
        val viewModel = createViewModel()
        testScheduler.runCurrent()

        assertTrue(viewModel.state.value.showSubtitles)
        viewModel.onEvent(PlayerEvent.SetLektorHideSubtitles(true))

        assertTrue(viewModel.state.value.lektorHideSubtitles)
        assertTrue(viewModel.state.value.showSubtitles)
        verify { settingsManager.setLektorHideSubtitles(true) }
    }

    @Test
    fun `edge tts voice options update state and are persisted`() = runTest {
        every { settingsManager.setLektorEdgeRate("+25%") } returns Unit
        every { settingsManager.setLektorEdgePitch("-10Hz") } returns Unit
        every { settingsManager.setLektorEdgeVolume("+15%") } returns Unit
        every { settingsManager.sourcesPriorityFlow } returns MutableStateFlow(emptyList())
        every { videoUrlResolver.settingsManager } returns settingsManager
        val viewModel = createViewModel()
        testScheduler.runCurrent()

        viewModel.onEvent(PlayerEvent.SetLektorEdgeRate("+25%"))
        assertEquals("+25%", viewModel.state.value.lektorEdgeRate)
        verify { settingsManager.setLektorEdgeRate("+25%") }

        viewModel.onEvent(PlayerEvent.SetLektorEdgePitch("-10Hz"))
        assertEquals("-10Hz", viewModel.state.value.lektorEdgePitch)
        verify { settingsManager.setLektorEdgePitch("-10Hz") }

        viewModel.onEvent(PlayerEvent.SetLektorEdgeVolume("+15%"))
        assertEquals("+15%", viewModel.state.value.lektorEdgeVolume)
        verify { settingsManager.setLektorEdgeVolume("+15%") }
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

    @Test
    fun `failed sources can be selected again after all automatic fallbacks fail`() = runTest {
        val url = "movie_123"
        val details = DetailedMedia(baseItem = MovieItem(url = url, titlePl = "Movie", posterUrl = ""))
        val first = ExtractedVideo(url = "https://example.test/first", sourceWebsite = "stream_api")
        val second = ExtractedVideo(url = "https://example.test/second", sourceWebsite = "stream_api")
        coEvery { repository.getMediaDetails(url) } returns details
        coEvery { videoUrlResolver.resolveAll(url, details) } returns listOf(first, second)
        every { videoUrlResolver.findBest(any(), any()) } returns first
        every { videoUrlResolver.getAlternativeUrls(details) } returns emptyList()
        coEvery { repository.getLinks(url, false, any()) } returns null

        val viewModel = createViewModel()
        viewModel.onEvent(PlayerEvent.LoadDetails(url))
        testScheduler.runCurrent()
        viewModel.onEvent(PlayerEvent.CurrentPositionChanged(25_000))

        viewModel.onEvent(PlayerEvent.PlayerError)
        assertEquals(second.url, viewModel.state.value.videoUrl)
        viewModel.onEvent(PlayerEvent.PlayerError)
        assertEquals(
            com.pointlessapps.filman.core.ui.R.string.player_all_sources_failed,
            (viewModel.state.value.shared.errorMessage as TextValue.StringResource).resId,
        )
        assertEquals(setOf(first.url, second.url), viewModel.state.value.failedUrls)

        viewModel.onEvent(PlayerEvent.ChangeVideoSource(first))
        assertNull(viewModel.state.value.shared.errorMessage)
        assertEquals(first.url, viewModel.state.value.videoUrl)
        assertEquals(25_000L, viewModel.state.value.startPositionMs)
        assertEquals(setOf(second.url), viewModel.state.value.failedUrls)

        viewModel.onEvent(PlayerEvent.PlayerError)
        assertEquals(
            com.pointlessapps.filman.core.ui.R.string.player_all_sources_failed,
            (viewModel.state.value.shared.errorMessage as TextValue.StringResource).resId,
        )
    }

    @Test
    fun `when video has embedded Polish subtitles and lektor is enabled, embedded subtitle is selected`() = runTest {
        val url = "movie_123"
        val details = DetailedMedia(baseItem = MovieItem(url = url, titlePl = "Gladiator II", posterUrl = ""))
        val embeddedSub = com.pointlessapps.filman.data.api.extractors.Subtitle(url = "https://example.test/pl.srt", label = "Polski", language = "pl")
        val video = ExtractedVideo(url = "https://example.test/video.mp4", sourceWebsite = "stream_api", subtitles = listOf(embeddedSub))

        coEvery { repository.getMediaDetails(url) } returns details
        coEvery { videoUrlResolver.resolveAll(url, details) } returns listOf(video)
        every { videoUrlResolver.findBest(any(), any()) } returns video
        every { videoUrlResolver.getAlternativeUrls(details) } returns emptyList()

        val viewModel = createViewModel(isLektorEnabled = true)
        viewModel.onEvent(PlayerEvent.LoadDetails(url))
        testScheduler.runCurrent()

        assertEquals("https://example.test/pl.srt", viewModel.state.value.selectedSubtitleUrl)
        coVerify(exactly = 0) { openSubtitlesClient.search(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `when video has no embedded Polish subtitles and lektor is enabled, it automatically searches OpenSubtitles with video signature`() = runTest {
        val url = "movie_123"
        val details = DetailedMedia(baseItem = MovieItem(url = url, titlePl = "Gladiator II", posterUrl = ""))
        val videoUrl = "https://cdn.test/Gladiator.II.2024.1080p.AMZN.WEB-DL.DDP5.1.Atmos.H.264-FLUX.mkv"
        val video = ExtractedVideo(url = videoUrl, sourceWebsite = "stream_api")

        val candidate = OpenSubtitleCandidate(
            fileId = 42,
            fileName = "Gladiator.II.2024.1080p.AMZN.WEB-DL.DDP5.1.Atmos.H.264-FLUX.srt",
            language = "pl",
            release = "FLUX",
            downloadCount = 100,
            rating = 9.0,
            foreignPartsOnly = false,
            hearingImpaired = false,
        )

        coEvery { repository.getMediaDetails(url) } returns details
        coEvery { videoUrlResolver.resolveAll(url, details) } returns listOf(video)
        every { videoUrlResolver.findBest(any(), any()) } returns video
        every { videoUrlResolver.getAlternativeUrls(details) } returns emptyList()

        coEvery {
            openSubtitlesClient.search(
                query = any(),
                language = "pl",
                season = any(),
                episode = any(),
                tmdbId = any(),
                videoFileName = "Gladiator.II.2024.1080p.AMZN.WEB-DL.DDP5.1.Atmos.H.264-FLUX",
                movieHash = any(),
            )
        } returns listOf(candidate)

        coEvery { openSubtitlesClient.download(42) } returns OpenSubtitlesDownloadResponse(
            link = "https://download.opensubtitles.com/sub/42.srt",
            fileName = "Gladiator.II.2024.1080p.AMZN.WEB-DL.DDP5.1.Atmos.H.264-FLUX.srt",
        )

        val viewModel = createViewModel(isLektorEnabled = true)
        viewModel.onEvent(PlayerEvent.LoadDetails(url))
        testScheduler.runCurrent()

        coVerify {
            openSubtitlesClient.search(
                query = any(),
                language = "pl",
                season = any(),
                episode = any(),
                tmdbId = any(),
                videoFileName = "Gladiator.II.2024.1080p.AMZN.WEB-DL.DDP5.1.Atmos.H.264-FLUX",
                movieHash = any(),
            )
        }
        assertEquals("https://download.opensubtitles.com/sub/42.srt?ext=.srt", viewModel.state.value.selectedSubtitleUrl)
    }

    @Test
    fun `test voice event invokes voice tester with current settings`() = runTest {
        every { settingsManager.setLektorVoiceId(any()) } returns Unit
        every { settingsManager.setLektorEdgeRate(any()) } returns Unit
        every { settingsManager.setLektorEdgePitch(any()) } returns Unit
        every { settingsManager.setLektorEdgeVolume(any()) } returns Unit
        every { settingsManager.sourcesPriorityFlow } returns MutableStateFlow(emptyList())
        every { videoUrlResolver.settingsManager } returns settingsManager

        val voiceTester = mockk<LektorVoiceTester>(relaxed = true)
        val viewModel = createViewModel(isLektorEnabled = true).let {
            PlayerViewModel(
                repository = repository,
                videoUrlResolver = videoUrlResolver,
                settingsManager = settingsManager,
                tvShowSettingsManager = tvShowSettingsManager,
                sessionManager = sessionManager,
                zaluknijSessionManager = zaluknijSessionManager,
                cdaHdSessionManager = cdaHdSessionManager,
                progressManager = progressManager,
                voiceTester = voiceTester,
            )
        }
        viewModel.onEvent(PlayerEvent.SelectLektorVoice("edge_brian"))
        viewModel.onEvent(PlayerEvent.SetLektorEdgeRate("+25%"))
        viewModel.onEvent(PlayerEvent.SetLektorEdgePitch("-10Hz"))
        viewModel.onEvent(PlayerEvent.SetLektorEdgeVolume("+15%"))
        viewModel.onEvent(PlayerEvent.TestVoice)
        testScheduler.runCurrent()

        verify {
            voiceTester.testVoice(
                voiceId = "edge_brian",
                edgeRate = "+25%",
                edgePitch = "-10Hz",
                edgeVolume = "+15%",
            )
        }
    }

    @Test
    fun `when playing tv episode and position passes prefetch threshold before next episode button appears, next episode is prefetched`() = runTest {
        val currentUrl = "show_123_1_1"
        val nextUrl = "show_123_1_2"
        val movie = MovieItem(
            url = currentUrl,
            titlePl = "TV Show S01E01",
            posterUrl = "",
            isTvShow = true,
            nextEpisodeUrl = nextUrl,
        )
        val details = DetailedMedia(baseItem = movie)
        val video = ExtractedVideo(url = "https://native.example/video", sourceWebsite = "filman")
        coEvery { progressManager.getProgressForUrlWhenLoaded(currentUrl) } returns null
        coEvery { repository.getMediaDetails(currentUrl) } returns details
        coEvery { videoUrlResolver.resolveAll(currentUrl, details) } returns listOf(video)
        every { videoUrlResolver.findBest(any(), any()) } returns video
        coEvery { videoUrlResolver.prefetch(nextUrl) } returns emptyList()

        val viewModel = createViewModel()
        viewModel.onEvent(PlayerEvent.LoadDetails(currentUrl))
        testScheduler.runCurrent()

        // 20 minute episode (1,200,000 ms).
        // Initial offset = 85s (85,000 ms). Next episode button appears at: 1,200,000 - 85,000 = 1,115,000 ms.
        // Prefetch lead time = 3 minutes (180,000 ms). Prefetch threshold is: 1,200,000 - 180,000 = 1,020,000 ms.
        viewModel.onEvent(PlayerEvent.DurationProvided(1_200_000L))
        testScheduler.runCurrent()

        // Before prefetch threshold (e.g. 1,000,000 ms = 16:40):
        viewModel.onEvent(PlayerEvent.CurrentPositionChanged(1_000_000L))
        testScheduler.runCurrent()

        coVerify(exactly = 0) { videoUrlResolver.prefetch(any()) }
        assertEquals(false, viewModel.state.value.nextEpisodeButtonUIState.isVisible)

        // At prefetch threshold (1,020,000 ms = 17:00, which is 95 seconds BEFORE button appears at 18:35):
        viewModel.onEvent(PlayerEvent.CurrentPositionChanged(1_020_000L))
        testScheduler.runCurrent()

        coVerify(exactly = 1) { videoUrlResolver.prefetch(nextUrl) }
        // Next episode button is STILL NOT visible:
        assertEquals(false, viewModel.state.value.nextEpisodeButtonUIState.isVisible)

        // As playback continues past the button threshold (1,115,000 ms = 18:35):
        viewModel.onEvent(PlayerEvent.CurrentPositionChanged(1_115_000L))
        testScheduler.runCurrent()

        // Button is now visible, but prefetch was NOT called again (still exactly 1):
        assertEquals(true, viewModel.state.value.nextEpisodeButtonUIState.isVisible)
        coVerify(exactly = 1) { videoUrlResolver.prefetch(nextUrl) }
    }

    @Test
    fun `prefetching resets when loading next episode and prefetches subsequent episode`() = runTest {
        val ep1Url = "show_123_1_1"
        val ep2Url = "show_123_1_2"
        val ep3Url = "show_123_1_3"

        val ep1 = MovieItem(url = ep1Url, titlePl = "S01E01", posterUrl = "", isTvShow = true, nextEpisodeUrl = ep2Url)
        val ep1Details = DetailedMedia(baseItem = ep1)

        val ep2 = MovieItem(url = ep2Url, titlePl = "S01E02", posterUrl = "", isTvShow = true, nextEpisodeUrl = ep3Url)
        val ep2Details = DetailedMedia(baseItem = ep2)

        val video = ExtractedVideo(url = "https://native.example/video", sourceWebsite = "filman")
        coEvery { progressManager.getProgressForUrlWhenLoaded(any()) } returns null
        coEvery { repository.getMediaDetails(ep1Url) } returns ep1Details
        coEvery { repository.getMediaDetails(ep2Url) } returns ep2Details
        coEvery { videoUrlResolver.resolveAll(any(), any()) } returns listOf(video)
        every { videoUrlResolver.findBest(any(), any()) } returns video
        coEvery { videoUrlResolver.prefetch(any()) } returns emptyList()

        val viewModel = createViewModel()
        viewModel.onEvent(PlayerEvent.LoadDetails(ep1Url))
        testScheduler.runCurrent()

        viewModel.onEvent(PlayerEvent.DurationProvided(1_200_000L))
        viewModel.onEvent(PlayerEvent.CurrentPositionChanged(1_020_000L))
        testScheduler.runCurrent()

        coVerify(exactly = 1) { videoUrlResolver.prefetch(ep2Url) }

        // Transition to next episode:
        viewModel.onEvent(PlayerEvent.NextEpisodeRequested)
        testScheduler.runCurrent()

        assertEquals(ep2Url, viewModel.state.value.detailedMedia?.baseItem?.url)

        // Episode 2 plays and reaches threshold:
        viewModel.onEvent(PlayerEvent.DurationProvided(1_200_000L))
        viewModel.onEvent(PlayerEvent.CurrentPositionChanged(1_020_000L))
        testScheduler.runCurrent()

        coVerify(exactly = 1) { videoUrlResolver.prefetch(ep3Url) }
    }

    @Test
    fun `movie or series finale with null nextEpisodeUrl does not trigger prefetch`() = runTest {
        val finaleUrl = "show_123_1_10"
        val finaleMovie = MovieItem(
            url = finaleUrl,
            titlePl = "Finale",
            posterUrl = "",
            isTvShow = true,
            nextEpisodeUrl = null,
        )
        val finaleDetails = DetailedMedia(baseItem = finaleMovie)
        val video = ExtractedVideo(url = "https://native.example/video", sourceWebsite = "filman")
        coEvery { progressManager.getProgressForUrlWhenLoaded(finaleUrl) } returns null
        coEvery { repository.getMediaDetails(finaleUrl) } returns finaleDetails
        coEvery { videoUrlResolver.resolveAll(finaleUrl, finaleDetails) } returns listOf(video)
        every { videoUrlResolver.findBest(any(), any()) } returns video

        val viewModel = createViewModel()
        viewModel.onEvent(PlayerEvent.LoadDetails(finaleUrl))
        testScheduler.runCurrent()

        viewModel.onEvent(PlayerEvent.DurationProvided(1_200_000L))
        viewModel.onEvent(PlayerEvent.CurrentPositionChanged(1_150_000L))
        testScheduler.runCurrent()

        coVerify(exactly = 0) { videoUrlResolver.prefetch(any()) }
    }
}
