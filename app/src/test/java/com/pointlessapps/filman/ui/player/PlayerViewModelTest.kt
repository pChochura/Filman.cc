package com.pointlessapps.filman.ui.player

import app.cash.turbine.test
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.TvShowSettingsManager
import com.pointlessapps.filman.data.model.SubtitleStylePreferences
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.api.VideoUrlResolver
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

    private val repository: FilmanRepository = mockk(relaxed = true)
    private val videoUrlResolver: VideoUrlResolver = mockk(relaxed = true)
    private val settingsManager: SettingsManager = mockk()
    private val tvShowSettingsManager: TvShowSettingsManager = mockk(relaxed = true)
    private val progressManager: ProgressManager = mockk(relaxed = true)
    private val sessionManager: com.pointlessapps.filman.data.local.SessionManager = mockk(relaxed = true)

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
            repository = repository,
            videoUrlResolver = videoUrlResolver,
            settingsManager = settingsManager,
            tvShowSettingsManager = tvShowSettingsManager,
            sessionManager = sessionManager,
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
}
