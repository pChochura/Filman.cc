package com.pointlessapps.filman.ui.player

import app.cash.turbine.test
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.TvShowSettingsManager
import com.pointlessapps.filman.data.model.SubtitleStylePreferences
import com.pointlessapps.filman.data.scraper.FilmanScraper
import com.pointlessapps.filman.data.scraper.VideoUrlResolver
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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

    private val scraper: FilmanScraper = mockk(relaxed = true)
    private val videoUrlResolver: VideoUrlResolver = mockk(relaxed = true)
    private val settingsManager: SettingsManager = mockk()
    private val tvShowSettingsManager: TvShowSettingsManager = mockk(relaxed = true)
    private val progressManager: ProgressManager = mockk(relaxed = true)

    private fun createViewModel(): PlayerViewModel {
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
            scraper = scraper,
            videoUrlResolver = videoUrlResolver,
            settingsManager = settingsManager,
            tvShowSettingsManager = tvShowSettingsManager,
            progressManager = progressManager,
        )
    }

    @Test
    fun `Initialize handles null detailedMedia correctly with fallback`() = runTest {
        // Arrange
        val testUrl = "https://filman.cc/movie/123-test-movie"
        val fallbackUrl = "https://ekino-tv.pl/movie/test-movie"

        // Initially returns null, which should trigger fallback logic
        coEvery { scraper.getMediaDetails(testUrl) } returns null
        coEvery { scraper.resolveFallbackUrl(testUrl) } returns fallbackUrl
        coEvery { scraper.getMediaDetails(fallbackUrl) } returns null // Fallback also fails for this test

        val viewModel = createViewModel()

        // Act & Assert
        viewModel.effect.test {
            viewModel.onEvent(PlayerEvent.LoadDetails(testUrl))

            // First effect should be the toast!
            val effect = awaitItem()
            assertTrue(effect is PlayerEffect.ShowToast)
            assertEquals(
                com.pointlessapps.filman.R.string.error_fallback_loading,
                ((effect as PlayerEffect.ShowToast).message as TextValue.StringResource).resId,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }
}
