package com.pointlessapps.filman.ui.player

import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.api.OpenSubtitlesClient
import com.pointlessapps.filman.data.api.SubtitleRepository
import com.pointlessapps.filman.data.api.VideoHasher
import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.api.WyzieSubsClient
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.TvShowSettingsManager
import com.pointlessapps.filman.player.lektor.controller.LektorControllerDelegate
import com.pointlessapps.filman.player.lektor.engine.LektorModelManager
import com.pointlessapps.filman.player.lektor.engine.LektorVoiceTester
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceCatalog
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val repository: FilmanRepository,
    private val videoUrlResolver: VideoUrlResolver,
    private val settingsManager: SettingsManager,
    private val tvShowSettingsManager: TvShowSettingsManager,
    progressManager: ProgressManager,
    val lektorModelManager: LektorModelManager = LektorModelManager(),
    val systemVoiceCatalog: SystemVoiceCatalog? = null,
    val voiceTester: LektorVoiceTester? = null,
) : BaseViewModel<PlayerState, PlayerEvent, PlayerEffect>(
    initialState = PlayerState(
        isLektorEnabled = settingsManager.isLektorEnabled.value,
        lektorHideSubtitles = settingsManager.lektorHideSubtitles.value,
        lektorVoiceId = settingsManager.lektorVoiceId.value,
        lektorDuckingVolume = settingsManager.lektorDuckingVolume.value,
        lektorTimingOffsetMs = settingsManager.lektorTimingOffsetMs.value,
        lektorEdgeRate = settingsManager.lektorEdgeRate.value,
        lektorEdgePitch = settingsManager.lektorEdgePitch.value,
        lektorEdgeVolume = settingsManager.lektorEdgeVolume.value,
        lektorEdgeCalmMode = settingsManager.lektorEdgeCalmMode.value,
        downloadedLektorVoices = lektorModelManager.getDownloadedVoices(),
    ),
    progressManager = progressManager,
) {
    private val subtitleRepository = SubtitleRepository()

    var openSubtitlesClient: OpenSubtitlesClient
        get() = subtitleRepository.openSubtitlesClient
        set(value) { subtitleRepository.openSubtitlesClient = value }

    var wyzieSubsClient: WyzieSubsClient
        get() = subtitleRepository.wyzieSubsClient
        set(value) { subtitleRepository.wyzieSubsClient = value }

    var openSubtitlesHasher: VideoHasher
        get() = subtitleRepository.openSubtitlesHasher
        set(value) { subtitleRepository.openSubtitlesHasher = value }

    private val lektorController = LektorControllerDelegate(
        scope = viewModelScope,
        settingsManager = settingsManager,
        lektorModelManager = lektorModelManager,
        systemVoiceCatalog = systemVoiceCatalog,
        voiceTester = voiceTester,
        onDownloadError = { errorState ->
            val raw = errorState.rawMessage
            val toast = when {
                errorState.messageRes != 0 -> TextValue.StringResource(errorState.messageRes, errorState.formatArgs)
                !raw.isNullOrBlank() -> TextValue.DynamicString(raw)
                else -> TextValue.StringResource(R.string.error_unknown)
            }
            sendEffect(PlayerEffect.ShowToast(toast))
        },
    )

    private val nextEpisodeDelegate = PlayerNextEpisodeDelegate(
        settingsManager = settingsManager,
        videoUrlResolver = videoUrlResolver,
        stateProvider = { state.value },
        launchPrefetch = { onError, block -> launchHandled(onError = onError, block = block) },
    )

    private val sourceDelegate: PlayerSourceDelegate

    private val subtitleDelegate = PlayerSubtitleDelegate(
        subtitleRepository = subtitleRepository,
        stateProvider = { state.value },
        updateState = ::updateState,
        sendEffect = ::sendEffect,
        updateTvShowSettings = { transform -> sourceDelegate.updateTvShowSettings(transform) },
        refreshOverlayMenu = { initialMenuId -> refreshOverlayMenu(initialMenuId) },
        launchWork = { context, onError, block ->
            launchHandled(context = context, onError = onError, block = block)
        },
    )

    init {
        sourceDelegate = PlayerSourceDelegate(
            repository = repository,
            videoUrlResolver = videoUrlResolver,
            settingsManager = settingsManager,
            tvShowSettingsManager = tvShowSettingsManager,
            progressManager = progressManager,
            lektorModelManager = lektorModelManager,
            subtitleDelegate = subtitleDelegate,
            nextEpisodeDelegate = nextEpisodeDelegate,
            stateProvider = { state.value },
            updateState = ::updateState,
            refreshOverlayMenu = { refreshOverlayMenu() },
            handleError = ::handleError,
            launchWork = { context, onError, block ->
                launchHandled(context = context, onError = onError, block = block)
            },
        )

        launchHandled {
            settingsManager.subtitleStylePreferencesFlow.collect { preferences ->
                updateState { it.copy(subtitleStylePreferences = preferences) }
                if (state.value.shared.overlayMenuData != null) {
                    refreshOverlayMenu()
                }
            }
        }

        launchHandled {
            lektorController.state.collect { lektor ->
                updateState {
                    it.copy(
                        isLektorEnabled = lektor.isEnabled,
                        lektorHideSubtitles = lektor.hideSubtitles,
                        lektorVoiceId = lektor.voiceId,
                        systemVoices = lektor.systemVoices,
                        lektorDuckingVolume = lektor.duckingVolume,
                        lektorTimingOffsetMs = lektor.timingOffsetMs,
                        lektorEdgeRate = lektor.edgeRate,
                        lektorEdgePitch = lektor.edgePitch,
                        lektorEdgeVolume = lektor.edgeVolume,
                        lektorEdgeCalmMode = lektor.edgeCalmMode,
                        downloadedLektorVoices = lektor.downloadedVoices,
                        lektorModelDownloadProgress = lektor.modelDownloadProgress,
                        isLektorModelExtracting = lektor.isModelExtracting,
                    )
                }
                if (state.value.shared.overlayMenuData != null) {
                    refreshOverlayMenu()
                }
            }
        }

        viewModelScope.launch {
            nextEpisodeDelegate.observeUiState(state).collect { uiState ->
                updateState { it.copy(nextEpisodeButtonUIState = uiState) }
            }
        }
    }

    override fun getAuthErrorEffect(): PlayerEffect = PlayerEffect.NavigateToAuth

    override fun handleEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.LoadDetails -> sourceDelegate.loadDetails(event.url)
            is PlayerEvent.IsPlayingChanged -> updateState { it.copy(isPlaying = event.isPlaying) }
            is PlayerEvent.IsBufferingChanged -> updateState { it.copy(isBuffering = event.isBuffering) }
            is PlayerEvent.DurationProvided -> {
                updateState { it.copy(duration = event.duration) }
                nextEpisodeDelegate.checkPrefetchNextEpisode(state.value.currentPositionMs, event.duration)
            }

            is PlayerEvent.ControlsVisibilityChanged -> {
                updateState {
                    val shouldCancelTimer =
                        it.nextEpisodeButtonUIState.isSecondaryPhase && event.isVisible
                    it.copy(
                        areControlsVisible = event.isVisible,
                        isTimerCancelled = shouldCancelTimer || it.isTimerCancelled,
                    )
                }
            }

            is PlayerEvent.CurrentPositionChanged -> {
                updateState { it.copy(currentPositionMs = event.positionMs) }
                nextEpisodeDelegate.checkPrefetchNextEpisode(event.positionMs, state.value.duration)
            }

            is PlayerEvent.NextEpisodePromptDismissed -> handleNextEpisodePromptDismissed()
            is PlayerEvent.CancelNextEpisodeTimer -> updateState { it.copy(isTimerCancelled = true) }
            is PlayerEvent.NextEpisodeRequested -> sourceDelegate.loadNextEpisode()
            is PlayerEvent.SaveProgress -> sourceDelegate.saveProgress(event.url, event.positionMs)
            is PlayerEvent.OpenSettingsMenu -> openSettingsMenu(event.currentPositionMs, event.initialMenuId)
            is PlayerEvent.ChangeVideoSource -> sourceDelegate.changeVideoSource(event.source)
            is PlayerEvent.AudioTracksChanged -> subtitleDelegate.onAudioTracksChanged(event.audioTracks)
            is PlayerEvent.SelectAudioTrack -> {
                subtitleDelegate.selectAudioTrack(event.trackId)
                refreshOverlayMenu()
            }
            is PlayerEvent.ChangePlaybackSpeed -> {
                updateState { it.copy(playbackSpeed = event.speed) }
                sourceDelegate.updateTvShowSettings { it.copy(playbackSpeed = event.speed) }
                refreshOverlayMenu()
            }

            is PlayerEvent.UpdateSubtitleStyle -> settingsManager.setSubtitleStylePreferences(event.preferences)
            is PlayerEvent.ChangeAspectRatio -> {
                updateState { it.copy(aspectRatioMode = event.mode) }
                sourceDelegate.updateTvShowSettings { it.copy(aspectRatioMode = event.mode) }
                refreshOverlayMenu()
            }

            is PlayerEvent.SelectSubtitle -> {
                subtitleDelegate.selectSubtitle(event.subtitleUrl)
                refreshOverlayMenu()
            }
            is PlayerEvent.SetSubtitleOffset -> {
                subtitleDelegate.setSubtitleOffset(event.offsetMs)
                refreshOverlayMenu()
            }
            is PlayerEvent.AdjustSubtitleOffset -> {
                subtitleDelegate.adjustSubtitleOffset(event.deltaMs)
                refreshOverlayMenu()
            }
            is PlayerEvent.SetSubtitleFpsScale -> {
                subtitleDelegate.setSubtitleFpsScale(event.fpsScale)
                refreshOverlayMenu()
            }
            is PlayerEvent.AutoAlignCurrentSubtitles -> subtitleDelegate.autoAlignCurrentSubtitles()
            is PlayerEvent.AutoAlignSubtitles -> subtitleDelegate.autoAlignSubtitles(event.targetSubtitleUrl)
            is PlayerEvent.SearchSubtitles -> subtitleDelegate.searchSubtitles(event.language)
            is PlayerEvent.DownloadSubtitle -> subtitleDelegate.downloadSubtitle(event.candidate)
            is PlayerEvent.SearchOpenSubtitles -> subtitleDelegate.searchSubtitles(event.language)
            is PlayerEvent.DownloadOpenSubtitle -> subtitleDelegate.downloadOpenSubtitle(event.candidate)
            is PlayerEvent.SearchWyzieSubtitles -> subtitleDelegate.searchSubtitles(event.language)
            is PlayerEvent.PlayerError -> sourceDelegate.handlePlayerError()
            else -> handleLektorEvent(event)
        }
    }

    private fun handleLektorEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.ToggleLektor -> {
                lektorController.setEnabled(event.enabled)
                updateState { it.copy(isLektorEnabled = event.enabled) }
                if (event.enabled) subtitleDelegate.onLektorEnabled()
                refreshOverlayMenu()
            }

            is PlayerEvent.SelectLektorVoice -> {
                lektorController.selectVoice(event.voiceId)
                updateState { it.copy(lektorVoiceId = event.voiceId) }
                refreshOverlayMenu()
            }

            is PlayerEvent.SetLektorHideSubtitles -> {
                lektorController.setHideSubtitles(event.hide)
                updateState { it.copy(lektorHideSubtitles = event.hide) }
                refreshOverlayMenu()
            }

            is PlayerEvent.PreviewSystemVoice -> lektorController.previewSystemVoice(event.name)
            PlayerEvent.RefreshSystemVoices -> lektorController.refreshSystemVoices()
            PlayerEvent.TestVoice -> lektorController.testVoice()

            is PlayerEvent.DownloadLektorModel -> {
                lektorController.downloadModel(event.voiceId)
                updateState { it.copy(downloadedLektorVoices = lektorModelManager.getDownloadedVoices()) }
                refreshOverlayMenu()
            }

            is PlayerEvent.DeleteLektorModel -> {
                lektorController.deleteModel(event.voiceId)
                updateState { it.copy(downloadedLektorVoices = lektorModelManager.getDownloadedVoices()) }
                refreshOverlayMenu()
            }

            is PlayerEvent.ChangeLektorDuckingVolume -> {
                lektorController.setDuckingVolume(event.volume)
                updateState { it.copy(lektorDuckingVolume = event.volume) }
                refreshOverlayMenu()
            }

            is PlayerEvent.ChangeLektorTimingOffset -> {
                lektorController.setTimingOffsetMs(event.offsetMs)
                updateState { it.copy(lektorTimingOffsetMs = event.offsetMs) }
                refreshOverlayMenu()
            }

            is PlayerEvent.SetLektorEdgeRate -> {
                lektorController.setEdgeRate(event.rate)
                updateState { it.copy(lektorEdgeRate = event.rate) }
                refreshOverlayMenu()
            }

            is PlayerEvent.SetLektorEdgePitch -> {
                lektorController.setEdgePitch(event.pitch)
                updateState { it.copy(lektorEdgePitch = event.pitch) }
                refreshOverlayMenu()
            }

            is PlayerEvent.SetLektorEdgeVolume -> {
                lektorController.setEdgeVolume(event.volume)
                updateState { it.copy(lektorEdgeVolume = event.volume) }
                refreshOverlayMenu()
            }

            is PlayerEvent.SetLektorEdgeCalmMode -> {
                lektorController.setEdgeCalmMode(event.enabled)
                updateState { it.copy(lektorEdgeCalmMode = event.enabled) }
                refreshOverlayMenu()
            }

            else -> Unit
        }
    }

    private fun openSettingsMenu(
        currentPositionMs: Long,
        initialMenuId: String? = null,
    ) {
        updateState {
            it.copy(
                startPositionMs = currentPositionMs,
                downloadedLektorVoices = lektorModelManager.getDownloadedVoices(),
            )
        }
        refreshOverlayMenu(initialMenuId)
    }

    private fun refreshOverlayMenu(
        initialMenuId: String? = state.value.shared.overlayMenuData?.initialMenuId,
    ) {
        val menuData = PlayerMenuBuilder.buildOverlayMenuData(
            state = state.value,
            videoUrlResolver = videoUrlResolver,
            initialMenuId = initialMenuId,
            onEvent = ::onEvent,
        )
        updateSharedState { it.copy(overlayMenuData = menuData) }
    }

    private fun handleNextEpisodePromptDismissed() {
        val isSecondary = state.value.nextEpisodeButtonUIState.isSecondaryPhase
        if (isSecondary) {
            updateState { it.copy(isSecondaryPhaseDismissed = true) }
        } else {
            updateState { it.copy(isInitialPhaseDismissed = true) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        nextEpisodeDelegate.clear()
        voiceTester?.stop()
    }

    companion object {
        const val PREFETCH_LEAD_TIME_MS = 3 * 60 * 1000L // 3 minutes before end of episode
        const val MIN_PREFETCH_BEFORE_BUTTON_MS = 60 * 1000L // At least 1 minute before next episode button appears
    }
}
