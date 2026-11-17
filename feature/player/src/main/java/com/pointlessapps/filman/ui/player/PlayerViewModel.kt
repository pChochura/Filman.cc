package com.pointlessapps.filman.ui.player

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.HIDE
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.SHOW
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.SHOW_IN_OVERLAY
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.SHOW_WITH_TIMER
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.TvShowSettingsManager
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.model.SubtitleStylePreferences
import com.pointlessapps.filman.data.model.TvShowSourceSettings
import com.pointlessapps.filman.data.model.getTvShowKey
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.api.CloudflareChallengeDetected
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.api.NetworkClient
import okhttp3.Request
import com.pointlessapps.filman.data.api.OpenSubtitlesClient
import com.pointlessapps.filman.data.api.OpenSubtitleCandidate
import com.pointlessapps.filman.data.api.TmdbClient
import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.api.VideoSignatureExtractor
import com.pointlessapps.filman.data.api.OpenSubtitlesHasher
import com.pointlessapps.filman.data.api.VideoHasher
import com.pointlessapps.filman.data.api.WyzieSubsClient
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.data.api.extractors.Subtitle
import com.pointlessapps.filman.data.api.extractors.getExtractorForUrl
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.core.PlayerConstants
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel.AppearanceModel.Show
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel.AppearanceModel.ShowInOverlay
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel.AppearanceModel.ShowWithTimer
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonUIState
import com.pointlessapps.filman.player.lektor.engine.LektorModelManager
import com.pointlessapps.filman.player.lektor.engine.ModelDownloadState
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceCatalog
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface PlayerEvent : FilmanEvent {
    data class UpdateSubtitleStyle(val preferences: SubtitleStylePreferences) : PlayerEvent

    data class LoadDetails(
        val url: String,
    ) : PlayerEvent

    data class IsPlayingChanged(
        val isPlaying: Boolean,
    ) : PlayerEvent

    data class IsBufferingChanged(
        val isBuffering: Boolean,
    ) : PlayerEvent

    data class DurationProvided(
        val duration: Long,
    ) : PlayerEvent

    data object NextEpisodeRequested : PlayerEvent

    data class SaveProgress(
        val url: String,
        val positionMs: Long,
    ) : PlayerEvent

    data class OpenSettingsMenu(
        val currentPositionMs: Long,
        val initialMenuId: String? = null,
    ) : PlayerEvent

    data class ControlsVisibilityChanged(
        val isVisible: Boolean,
    ) : PlayerEvent

    data class CurrentPositionChanged(
        val positionMs: Long,
    ) : PlayerEvent

    data object NextEpisodePromptDismissed : PlayerEvent

    data object CancelNextEpisodeTimer : PlayerEvent

    data class ChangeVideoSource(
        val source: ExtractedVideo,
    ) : PlayerEvent

    data class AudioTracksChanged(
        val audioTracks: List<PlayerAudioTrack>,
    ) : PlayerEvent

    data class SelectAudioTrack(
        val trackId: String,
    ) : PlayerEvent

    data class SelectSubtitle(
        val subtitleUrl: String?,
    ) : PlayerEvent

    data class SearchOpenSubtitles(
        val language: String,
    ) : PlayerEvent

    data class DownloadOpenSubtitle(
        val candidate: OpenSubtitleCandidate,
    ) : PlayerEvent

    data class SearchWyzieSubtitles(
        val language: String,
    ) : PlayerEvent

    data class ChangePlaybackSpeed(
        val speed: Float,
    ) : PlayerEvent

    data class ChangeAspectRatio(
        val mode: Int,
    ) : PlayerEvent

    data class ToggleLektor(
        val enabled: Boolean,
    ) : PlayerEvent

    data class SetLektorHideSubtitles(val hide: Boolean) : PlayerEvent

    data class SelectLektorVoice(
        val voiceId: String,
    ) : PlayerEvent

    data class PreviewSystemVoice(val name: String?) : PlayerEvent
    data object RefreshSystemVoices : PlayerEvent

    data class ChangeLektorDuckingVolume(
        val volume: Float,
    ) : PlayerEvent

    data class ChangeLektorTimingOffset(val offsetMs: Int) : PlayerEvent

    data class DownloadLektorModel(
        val voiceId: String,
    ) : PlayerEvent

    data class DeleteLektorModel(
        val voiceId: String,
    ) : PlayerEvent

    data object PlayerError : PlayerEvent

}

@Immutable
data class PlayerState(
    val videoUrl: String? = null,
    val videoHeaders: Map<String, String> = emptyMap(),
    val detailedMedia: DetailedMedia? = null,
    val isPlaying: Boolean = true,
    val isBuffering: Boolean = true,
    val duration: Long = 0,
    val startPositionMs: Long = 0,
    val playbackSpeed: Float = PlayerConstants.PlaybackSpeed.X1_0,
    val aspectRatioMode: Int = PlayerConstants.AspectRatio.FIT,
    val audioTracks: List<PlayerAudioTrack> = emptyList(),
    val selectedAudioTrackId: String? = null,
    val subtitles: List<Subtitle> = emptyList(),
    val openSubtitles: List<Subtitle> = emptyList(),
    val openSubtitleCandidates: List<OpenSubtitleCandidate> = emptyList(),
    val selectedSubtitleUrl: String? = null,
    val rawSubtitleContent: String? = null,
    val isLektorEnabled: Boolean = false,
    val lektorHideSubtitles: Boolean = false,
    val lektorVoiceId: String = "meski",
    val systemVoices: SystemVoiceList = SystemVoiceList(),
    val lektorDuckingVolume: Float = com.pointlessapps.filman.data.model.LektorDucking.DEFAULT_VOLUME,
    val lektorTimingOffsetMs: Int = com.pointlessapps.filman.data.model.LektorTiming.DEFAULT_OFFSET_MS,
    val downloadedLektorVoices: Set<String> = emptySet(),
    val lektorModelDownloadProgress: Float? = null,
    val isLektorModelExtracting: Boolean = false,
    val isWebView: Boolean = false,
    val failedUrls: Set<String> = emptySet(),
    val alternativeSources: List<ExtractedVideo> = emptyList(),
    val currentPositionMs: Long = 0,
    val areControlsVisible: Boolean = false,
    val nextEpisodeButtonUIState: NextEpisodeButtonUIState = NextEpisodeButtonUIState(),
    val isInitialPhaseDismissed: Boolean = false,
    val isSecondaryPhaseDismissed: Boolean = false,
    val isTimerCancelled: Boolean = false,
    val autoPlayNextEpisode: Boolean = true,
    val subtitleStylePreferences: SubtitleStylePreferences = SubtitleStylePreferences(),
    override val shared: SharedState = SharedState(),
) : StateWithShared<PlayerState> {
    val showSubtitles: Boolean get() = !isLektorEnabled || !lektorHideSubtitles

    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

sealed interface PlayerEffect {
    data object NavigateToAuth : PlayerEffect

    data class ShowToast(
        val message: TextValue,
    ) : PlayerEffect
}

class PlayerViewModel(
    private val repository: FilmanRepository,
    private val videoUrlResolver: VideoUrlResolver,
    private val settingsManager: SettingsManager,
    private val tvShowSettingsManager: TvShowSettingsManager,
    private val sessionManager: SessionManager,
    private val zaluknijSessionManager: com.pointlessapps.filman.data.local.ZaluknijSessionManager,
    private val cdaHdSessionManager: com.pointlessapps.filman.data.local.CdaHdSessionManager,
    progressManager: ProgressManager,
    val lektorModelManager: LektorModelManager = LektorModelManager(),
    val systemVoiceCatalog: SystemVoiceCatalog? = null,
    val openSubtitlesClient: OpenSubtitlesClient = OpenSubtitlesClient(),
    val wyzieSubsClient: WyzieSubsClient = WyzieSubsClient(NetworkClient.okHttpClient, TmdbClient(NetworkClient.okHttpClient)),
    val openSubtitlesHasher: VideoHasher = OpenSubtitlesHasher,
) : BaseViewModel<PlayerState, PlayerEvent, PlayerEffect>(
    initialState = PlayerState(
        isLektorEnabled = settingsManager.isLektorEnabled.value,
        lektorHideSubtitles = settingsManager.lektorHideSubtitles.value,
        lektorVoiceId = settingsManager.lektorVoiceId.value,
        lektorDuckingVolume = settingsManager.lektorDuckingVolume.value,
        lektorTimingOffsetMs = settingsManager.lektorTimingOffsetMs.value,
        downloadedLektorVoices = lektorModelManager.getDownloadedVoices(),
    ),
    progressManager = progressManager,
) {
    override fun onCloudflareChallengeDetected(e: CloudflareChallengeDetected) {
        when {
            e.domain == "zaluknij.cc" -> zaluknijSessionManager.requestChallenge(e.request)
            e.domain == "cda-hd.cc" -> cdaHdSessionManager.requestChallenge(e.request)
            e.domain == FilmanConfig.DOMAIN -> sessionManager.requestChallenge(e.request)
        }
    }
    private var preferredSubtitleLanguage: String? = null
    private var preferredSubtitleLabel: String? = null
    private var preferredAudioLanguage: String? = null
    private var currentMediaUrl: String? = null


    init {
        systemVoiceCatalog?.let { catalog ->
            catalog.refresh()
            launchHandled {
                catalog.voices.collect { voices ->
                    updateState { it.copy(systemVoices = voices) }
                    if (state.value.shared.overlayMenuData != null) refreshOverlayMenu()
                }
            }
        }
        launchHandled {
            settingsManager.subtitleStylePreferencesFlow.collect { preferences ->
                updateState { it.copy(subtitleStylePreferences = preferences) }
                if (state.value.shared.overlayMenuData != null) {
                    refreshOverlayMenu()
                }
            }
        }
        launchHandled {
            lektorModelManager.downloadedVoices.collect { voices ->
                updateState { it.copy(downloadedLektorVoices = voices) }
                if (state.value.shared.overlayMenuData != null) {
                    refreshOverlayMenu()
                }
            }
        }
        launchHandled {
            lektorModelManager.downloadState.collect { downloadState ->
                when (downloadState) {
                    is ModelDownloadState.Downloading -> {
                        updateState {
                            it.copy(
                                lektorModelDownloadProgress = downloadState.progress,
                                isLektorModelExtracting = false,
                            )
                        }
                    }
                    ModelDownloadState.Extracting -> {
                        updateState {
                            it.copy(
                                lektorModelDownloadProgress = null,
                                isLektorModelExtracting = true,
                            )
                        }
                    }
                    ModelDownloadState.Ready -> {
                        updateState {
                            it.copy(
                                lektorModelDownloadProgress = null,
                                isLektorModelExtracting = false,
                                downloadedLektorVoices = lektorModelManager.getDownloadedVoices(),
                            )
                        }
                    }
                    is ModelDownloadState.Error -> {
                        updateState {
                            it.copy(
                                lektorModelDownloadProgress = null,
                                isLektorModelExtracting = false,
                            )
                        }
                        sendEffect(PlayerEffect.ShowToast(TextValue.DynamicString(downloadState.message)))
                    }
                    ModelDownloadState.Idle -> {
                        updateState {
                            it.copy(
                                lektorModelDownloadProgress = null,
                                isLektorModelExtracting = false,
                                downloadedLektorVoices = lektorModelManager.getDownloadedVoices(),
                            )
                        }
                    }
                }
                if (state.value.shared.overlayMenuData != null) {
                    refreshOverlayMenu()
                }
            }
        }
        launchHandled {
            combine(
                zaluknijSessionManager.verificationGeneration,
                cdaHdSessionManager.verificationGeneration,
                sessionManager.verificationGeneration,
            ) { zaluknijGeneration, cdaGeneration, filmanGeneration ->
                listOf(zaluknijGeneration, cdaGeneration, filmanGeneration)
            }.collect { generations ->
                if (generations.any { it > 0 }) {
                    refreshSourcesIfAvailable()
                }
            }
        }
        val initialModelFlow =
            combine(
                settingsManager.initialAppearanceTypeFlow,
                settingsManager.initialAppearanceOffsetFlow,
                settingsManager.initialAppearancePercentageFlow,
            ) { type, offset, percentage ->
                val percentageOffset = percentage / 100f
                val maxTimeOffset = offset * 1000L
                when (type) {
                    SHOW -> Show(percentageOffset, maxTimeOffset)
                    SHOW_IN_OVERLAY -> ShowInOverlay(percentageOffset, maxTimeOffset)
                    else -> null
                }
            }

        val secondaryModelFlow =
            combine(
                settingsManager.secondaryAppearanceTypeFlow,
                settingsManager.secondaryAppearanceOffsetFlow,
                settingsManager.secondaryAppearancePercentageFlow,
                settingsManager.secondaryTimerAmountFlow,
            ) { type, offset, percentage, timerAmount ->
                val percentageOffset = percentage / 100f
                val maxTimeOffset = offset * 1000L
                when (type) {
                    SHOW -> {
                        Show(percentageOffset, maxTimeOffset)
                    }

                    SHOW_IN_OVERLAY -> {
                        ShowInOverlay(percentageOffset, maxTimeOffset)
                    }

                    SHOW_WITH_TIMER -> {
                        ShowWithTimer(
                            percentageOffset,
                            maxTimeOffset,
                            timerAmount * 1000L,
                        )
                    }

                    HIDE -> {
                        null
                    }
                }
            }

        val baseModelFlow =
            combine(initialModelFlow, secondaryModelFlow) { initial, secondary ->
                NextEpisodeButtonModel(initial, secondary)
            }

        val dynamicUiStateFlow =
            combine(
                baseModelFlow,
                state,
            ) { model, currentState ->
                val duration = currentState.duration
                val currentPosition = currentState.currentPositionMs
                val areControlsVisible = currentState.areControlsVisible
                val isInitialDismissed = currentState.isInitialPhaseDismissed
                val isSecondaryDismissed = currentState.isSecondaryPhaseDismissed
                val isTimerCancelled = currentState.isTimerCancelled

                fun isPastThreshold(appearance: NextEpisodeButtonModel.AppearanceModel?): Boolean {
                    if (appearance == null || duration <= 0) return false
                    val threshold =
                        duration -
                                minOf(
                                    appearance.maxTimeOffset,
                                    (duration * appearance.percentageOffset).toLong(),
                                )
                    return currentPosition >= threshold
                }

                val isInitialPhase = isPastThreshold(model.initialAppearanceModel)
                val isSecondaryPhase = isPastThreshold(model.appearanceModel)

                val activeModel =
                    if (isSecondaryPhase) {
                        model.appearanceModel
                    } else if (isInitialPhase) {
                        model.initialAppearanceModel
                    } else {
                        null
                    }

                var isVisible = false
                var shouldRunTimer = false

                val autoPlayNextEpisode = settingsManager.autoPlayNextFlow.value

                when (activeModel) {
                    is Show -> {
                        isVisible = true
                    }

                    is ShowInOverlay -> {
                        isVisible = areControlsVisible
                    }

                    is ShowWithTimer -> {
                        isVisible = true
                        shouldRunTimer =
                            autoPlayNextEpisode && !areControlsVisible && !isTimerCancelled
                    }

                    null -> {
                        isVisible = false
                    }
                }

                if (isSecondaryPhase && isSecondaryDismissed) {
                    isVisible = false
                    shouldRunTimer = false
                } else if (isInitialPhase && !isSecondaryPhase && isInitialDismissed) {
                    isVisible = false
                    shouldRunTimer = false
                }

                if (isVisible && autoPlayNextEpisode) {
                    handleNextEpisodeBoxAppeared()
                }

                NextEpisodeButtonUIState(
                    isVisible = isVisible,
                    isSecondaryPhase = isSecondaryPhase,
                    isTimerRunning = shouldRunTimer,
                    timerDurationMs = (activeModel as? ShowWithTimer)?.timerDuration ?: 0L,
                )
            }

        viewModelScope.launch {
            dynamicUiStateFlow.collect { uiState ->
                updateState { it.copy(nextEpisodeButtonUIState = uiState) }
            }
        }

        viewModelScope.launch {
            settingsManager.lektorTimingOffsetMs.collect { offset ->
                updateState { it.copy(lektorTimingOffsetMs = offset) }
            }
        }

        viewModelScope.launch {
            settingsManager.lektorHideSubtitles.collect { hide ->
                updateState { it.copy(lektorHideSubtitles = hide) }
            }
        }

        viewModelScope.launch {
            combine(
                settingsManager.isLektorEnabled,
                settingsManager.lektorVoiceId,
                settingsManager.lektorDuckingVolume,
            ) { enabled, voiceId, duckingVolume ->
                Triple(enabled, voiceId, duckingVolume)
            }.collect { (enabled, voiceId, duckingVolume) ->
                updateState {
                    it.copy(
                        isLektorEnabled = enabled,
                        lektorVoiceId = voiceId,
                        lektorDuckingVolume = duckingVolume,
                    )
                }
            }
        }
    }

    override fun getAuthErrorEffect(): PlayerEffect = PlayerEffect.NavigateToAuth

    override fun handleEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.LoadDetails -> {
                loadDetails(event.url)
            }

            is PlayerEvent.IsPlayingChanged -> {
                updateState { it.copy(isPlaying = event.isPlaying) }
            }

            is PlayerEvent.IsBufferingChanged -> {
                updateState { it.copy(isBuffering = event.isBuffering) }
            }

            is PlayerEvent.DurationProvided -> {
                updateState { it.copy(duration = event.duration) }
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
                updateState {
                    it.copy(currentPositionMs = event.positionMs)
                }
            }

            is PlayerEvent.NextEpisodePromptDismissed -> {
                handleNextEpisodePromptDismissed()
            }

            is PlayerEvent.CancelNextEpisodeTimer -> {
                updateState { it.copy(isTimerCancelled = true) }
            }

            is PlayerEvent.NextEpisodeRequested -> {
                loadNextEpisode()
            }

            is PlayerEvent.SaveProgress -> {
                saveProgress(event.url, event.positionMs)
            }

            is PlayerEvent.OpenSettingsMenu -> {
                openSettingsMenu(
                    currentPositionMs = event.currentPositionMs,
                    initialMenuId = event.initialMenuId,
                )
            }

            is PlayerEvent.ChangeVideoSource -> {
                changeVideoSource(event.source)
            }

            is PlayerEvent.AudioTracksChanged -> {
                val tracks = event.audioTracks
                val preferredTrack =
                    if (state.value.selectedAudioTrackId == null && preferredAudioLanguage != null) {
                        tracks.find {
                            it.language.equals(
                                preferredAudioLanguage,
                                ignoreCase = true,
                            )
                        }
                    } else {
                        null
                    }

                val selectedId =
                    state.value.selectedAudioTrackId?.takeIf { id -> tracks.any { it.id == id } }
                        ?: preferredTrack?.id

                updateState {
                    it.copy(
                        audioTracks = tracks,
                        selectedAudioTrackId = selectedId,
                    )
                }
            }

            is PlayerEvent.SelectAudioTrack -> {
                val selectedTrack = state.value.audioTracks.find { it.id == event.trackId }
                preferredAudioLanguage = selectedTrack?.language
                updateState { it.copy(selectedAudioTrackId = event.trackId) }
            }

            is PlayerEvent.ChangePlaybackSpeed -> {
                updateState { it.copy(playbackSpeed = event.speed) }
                updateTvShowSettings { it.copy(playbackSpeed = event.speed) }
            }

            is PlayerEvent.UpdateSubtitleStyle -> {
                settingsManager.setSubtitleStylePreferences(event.preferences)
            }

            is PlayerEvent.ChangeAspectRatio -> {
                updateState { it.copy(aspectRatioMode = event.mode) }
                updateTvShowSettings { it.copy(aspectRatioMode = event.mode) }
            }

            is PlayerEvent.SelectSubtitle -> {
                val selectedSubtitle =
                    (state.value.subtitles + state.value.openSubtitles).find { it.url == event.subtitleUrl }
                preferredSubtitleLanguage = selectedSubtitle?.language
                preferredSubtitleLabel = selectedSubtitle?.label
                updateState { it.copy(selectedSubtitleUrl = event.subtitleUrl, rawSubtitleContent = null) }
                if (event.subtitleUrl != null) {
                    loadRawSubtitleContent(event.subtitleUrl)
                } else {
                    updateState { it.copy(rawSubtitleContent = null) }
                }
                updateTvShowSettings {
                    it.copy(
                        subtitlesEnabled = event.subtitleUrl != null,
                        subtitleLanguage = selectedSubtitle?.language,
                        subtitleLabel = selectedSubtitle?.label,
                    )
                }
            }

            is PlayerEvent.ToggleLektor -> {
                val newEnabled = event.enabled
                settingsManager.setLektorEnabled(newEnabled)
                updateState { it.copy(isLektorEnabled = newEnabled) }
                if (newEnabled) {
                    val selectedVoice = LektorVoice.fromId(state.value.lektorVoiceId)
                    if (!lektorModelManager.isModelReady(selectedVoice)) {
                        lektorModelManager.startDownload(selectedVoice)
                    }
                    val allSubs = state.value.subtitles + state.value.openSubtitles
                    val currentSub = allSubs.find { it.url == state.value.selectedSubtitleUrl }
                    if (currentSub == null || !isPolishSubtitle(currentSub)) {
                        val existingPolish = allSubs.find { isPolishSubtitle(it) }
                        if (existingPolish != null) {
                            handleEvent(PlayerEvent.SelectSubtitle(existingPolish.url))
                        } else {
                            downloadPolishSubtitles()
                        }
                    } else if (state.value.rawSubtitleContent == null && state.value.selectedSubtitleUrl != null) {
                        loadRawSubtitleContent(state.value.selectedSubtitleUrl!!)
                    }
                }
                refreshOverlayMenu()
            }

            is PlayerEvent.SelectLektorVoice -> {
                settingsManager.setLektorVoiceId(event.voiceId)
                updateState { it.copy(lektorVoiceId = event.voiceId) }
                refreshOverlayMenu()
            }

            is PlayerEvent.SetLektorHideSubtitles -> {
                settingsManager.setLektorHideSubtitles(event.hide)
                updateState { it.copy(lektorHideSubtitles = event.hide) }
                refreshOverlayMenu()
            }

            is PlayerEvent.PreviewSystemVoice -> systemVoiceCatalog?.preview(event.name)
            PlayerEvent.RefreshSystemVoices -> systemVoiceCatalog?.refresh()

            is PlayerEvent.DownloadLektorModel -> {
                val voice = LektorVoice.fromId(event.voiceId)
                lektorModelManager.startDownload(voice)
                updateState {
                    it.copy(
                        downloadedLektorVoices = lektorModelManager.getDownloadedVoices(),
                    )
                }
                refreshOverlayMenu()
            }

            is PlayerEvent.DeleteLektorModel -> {
                val voice = LektorVoice.fromId(event.voiceId)
                lektorModelManager.deleteModel(voice)
                updateState {
                    it.copy(
                        downloadedLektorVoices = lektorModelManager.getDownloadedVoices(),
                    )
                }
                refreshOverlayMenu()
            }

            is PlayerEvent.ChangeLektorDuckingVolume -> {
                settingsManager.setLektorDuckingVolume(event.volume)
                updateState { it.copy(lektorDuckingVolume = event.volume) }
                refreshOverlayMenu()
            }

            is PlayerEvent.ChangeLektorTimingOffset -> {
                settingsManager.setLektorTimingOffsetMs(event.offsetMs)
                updateState { it.copy(lektorTimingOffsetMs = event.offsetMs) }
                refreshOverlayMenu()
            }

            is PlayerEvent.SearchOpenSubtitles -> {
                searchOpenSubtitles(event.language)
            }

            is PlayerEvent.DownloadOpenSubtitle -> {
                downloadOpenSubtitle(event.candidate)
            }

            is PlayerEvent.SearchWyzieSubtitles -> {
                searchWyzieSubtitles(event.language)
            }

            is PlayerEvent.PlayerError -> {
                handlePlayerError()
            }

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

    private fun refreshOverlayMenu(initialMenuId: String? = state.value.shared.overlayMenuData?.initialMenuId) {
        val menuData = PlayerMenuBuilder.buildOverlayMenuData(
            state = state.value,
            videoUrlResolver = videoUrlResolver,
            initialMenuId = initialMenuId,
            onEvent = ::onEvent,
        )
        updateSharedState { it.copy(overlayMenuData = menuData) }
    }

    private fun getPreferredSubtitleUrl(subtitles: List<Subtitle>): String? {
        if (preferredSubtitleLanguage == null) return null

        return subtitles
            .find {
                it.language == preferredSubtitleLanguage && it.label == preferredSubtitleLabel
            }?.url ?: subtitles.find { it.language == preferredSubtitleLanguage }?.url
    }

    private fun changeVideoSource(source: ExtractedVideo) {
        val currentPos = state.value.currentPositionMs
        val preferredSub = getPreferredSubtitleUrl(source.subtitles)
        updateState {
            it.copy(
                videoUrl = source.url,
                videoHeaders = source.headers,
                audioTracks = emptyList(),
                selectedAudioTrackId = null,
                subtitles = source.subtitles,
                selectedSubtitleUrl = preferredSub,
                isWebView = source.isWebView,
                startPositionMs = currentPos,
                duration = 0,
                failedUrls = it.failedUrls - source.url,
                shared = it.shared.copy(errorMessage = null),
            )
        }
        if (preferredSub != null) {
            loadRawSubtitleContent(preferredSub)
        }
        val needsPolishSubtitles = state.value.isLektorEnabled ||
            (preferredSubtitleLanguage?.lowercase() in setOf("pl", "pol"))
        if (needsPolishSubtitles) {
            val allSubs = source.subtitles + state.value.openSubtitles
            val currentSub = allSubs.find { it.url == preferredSub }
            if (currentSub == null || !isPolishSubtitle(currentSub)) {
                val existingPolish = allSubs.find { isPolishSubtitle(it) }
                if (existingPolish != null) {
                    handleEvent(PlayerEvent.SelectSubtitle(existingPolish.url))
                } else {
                    downloadPolishSubtitles()
                }
            }
        }
        updateTvShowSettings {
            it.copy(
                serverName = source.serverName,
                version = source.version,
                quality = source.quality,
                sourceWebsite = source.sourceWebsite,
            )
        }
    }

    private fun updateTvShowSettings(transform: (TvShowSourceSettings) -> TvShowSourceSettings) {
        val movie = state.value.detailedMedia?.baseItem ?: return
        val showKey = movie.getTvShowKey() ?: return
        val current =
            tvShowSettingsManager.getSettingsForTvShowSync(showKey) ?: TvShowSourceSettings()
        val updated = transform(current)
        tvShowSettingsManager.saveSettingsForTvShow(showKey, updated)
    }

    private fun findMatchingSubtitle(
        subtitles: List<Subtitle>,
        settings: TvShowSourceSettings,
    ): String? {
        if (!settings.subtitlesEnabled || subtitles.isEmpty()) return null

        val lang = settings.subtitleLanguage
        val label = settings.subtitleLabel

        if (!lang.isNullOrBlank() && !label.isNullOrBlank()) {
            val match =
                subtitles.find {
                    it.language.equals(lang, ignoreCase = true) &&
                            it.label.equals(
                                label,
                                ignoreCase = true,
                            )
                }
            if (match != null) return match.url
        }

        if (!lang.isNullOrBlank()) {
            val match = subtitles.find { it.language.equals(lang, ignoreCase = true) }
            if (match != null) return match.url
        }

        if (!label.isNullOrBlank()) {
            val match = subtitles.find { it.label.equals(label, ignoreCase = true) }
            if (match != null) return match.url
        }

        return subtitles.firstOrNull()?.url
    }

    private fun handlePlayerError() {
        val currentUrl = state.value.videoUrl
        val alternatives = state.value.alternativeSources.ifEmpty {
            state.value.detailedMedia?.let { videoUrlResolver.getAlternativeUrls(it) }.orEmpty()
        }

        val newFailedUrls = state.value.failedUrls + listOfNotNull(currentUrl)
        updateState { it.copy(failedUrls = newFailedUrls) }

        val currentIndex = alternatives.indexOfFirst { it.url == currentUrl }
        val orderedAlternatives =
            if (currentIndex != -1) {
                alternatives.subList(currentIndex + 1, alternatives.size) + alternatives.subList(
                    0,
                    currentIndex + 1,
                )
            } else {
                alternatives
            }

        val nextSource = orderedAlternatives.firstOrNull { it.url !in newFailedUrls }

        if (nextSource != null) {
            changeVideoSource(nextSource)
        } else {
            updateSharedState {
                it.copy(errorMessage = TextValue.StringResource(R.string.player_all_sources_failed))
            }
        }
        if (state.value.shared.overlayMenuData != null) {
            refreshOverlayMenu()
        }
    }

    private fun handleNextEpisodePromptDismissed() {
        val isSecondary = state.value.nextEpisodeButtonUIState.isSecondaryPhase
        if (isSecondary) {
            updateState { it.copy(isSecondaryPhaseDismissed = true) }
        } else {
            updateState { it.copy(isInitialPhaseDismissed = true) }
        }
    }

    private fun handleNextEpisodeBoxAppeared() {
    }

    private fun saveProgress(
        url: String,
        positionMs: Long,
    ) {
        val detailedMedia = state.value.detailedMedia ?: return
        if (detailedMedia.baseItem.url != url) return
        val duration = state.value.duration
        val item = detailedMedia.baseItem

        progressManager?.saveProgress(item, positionMs, duration)
    }

    private fun loadNextEpisode() {
        val detailedMedia = state.value.detailedMedia ?: return
        val nextEpisodeUrl = detailedMedia.baseItem.nextEpisodeUrl ?: return

        progressManager?.markAsWatched(detailedMedia.baseItem)
        loadDetails(nextEpisodeUrl)
    }

    private fun loadDetails(url: String) {
        updateState {
            it.copy(
                shared =
                    it.shared.copy(
                        isLoading = true,
                        errorMessage = null,
                    ),
                detailedMedia = null,
                videoHeaders = emptyMap(),
                videoUrl = null,
                subtitles = emptyList(),
                selectedSubtitleUrl = null,
                rawSubtitleContent = null,
                isWebView = false,
                failedUrls = emptySet(),
                alternativeSources = emptyList(),
                audioTracks = emptyList(),
                selectedAudioTrackId = null,
                openSubtitles = emptyList(),
                openSubtitleCandidates = emptyList(),
                duration = 0,
                startPositionMs = 0,
                currentPositionMs = 0,
                isTimerCancelled = false,
                isInitialPhaseDismissed = false,
                isSecondaryPhaseDismissed = false,
                downloadedLektorVoices = lektorModelManager.getDownloadedVoices(),
            )
        }

        launchHandled(
            onError = { t ->
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = if (t is com.pointlessapps.filman.data.api.AuthException) null else TextValue.StringResource(R.string.player_no_playable_video),
                    )
                }
                handleError(t)
            }
        ) {
            val existingProgress = progressManager?.getProgressForUrlWhenLoaded(url)
            val startPos = (existingProgress as? ProgressItem.InProgress)?.progressMs ?: 0L
            updateState { it.copy(startPositionMs = startPos, currentPositionMs = startPos) }

            val isDirectYoutube =
                url.contains("youtube.com", ignoreCase = true) ||
                        url.contains("youtu.be", ignoreCase = true)

            if (isDirectYoutube) {
                updateState {
                    it.copy(
                        shared = it.shared.copy(isLoading = false),
                        detailedMedia = null,
                    )
                }

                val extractor = getExtractorForUrl(url)
                val extractedList = extractor?.extractVideo(url) ?: emptyList()

                if (extractedList.isNotEmpty()) {
                    val preferredQuality = settingsManager.preferredQualityFlow.first()
                    val bestExtracted =
                        if (preferredQuality == SettingsConstants.Quality.AUTO) {
                            extractedList.first()
                        } else {
                            extractedList.find {
                                it.quality.contains(preferredQuality, ignoreCase = true) ||
                                        it.version.contains(preferredQuality, ignoreCase = true)
                            } ?: extractedList.first()
                        }

                    updateState {
                        it.copy(
                            videoHeaders = bestExtracted.headers,
                            videoUrl = bestExtracted.url,
                            subtitles = bestExtracted.subtitles,
                            selectedSubtitleUrl = getPreferredSubtitleUrl(bestExtracted.subtitles),
                            startPositionMs = 0L,
                            isWebView = bestExtracted.isWebView,
                            alternativeSources = extractedList,
                        )
                    }
                } else {
                    updateSharedState {
                        it.copy(
                            isLoading = false,
                            errorMessage = TextValue.StringResource(R.string.player_no_playable_video),
                        )
                    }
                }
                return@launchHandled
            }

            currentMediaUrl = url
            val detailedMedia = repository.getMediaDetails(url)
            val details = detailedMedia?.baseItem
            if (details == null) {
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = TextValue.StringResource(R.string.error_media_not_found),
                    )
                }
                return@launchHandled
            }

            val tvShowKey = details.getTvShowKey()
            val savedSettings =
                if (tvShowKey != null) {
                    tvShowSettingsManager.getSettingsForTvShow(tvShowKey)
                } else {
                    null
                }

            val allSources = videoUrlResolver.resolveAll(url, detailedMedia = detailedMedia)
            val extracted = videoUrlResolver.findBest(allSources, targetSettings = savedSettings) ?: allSources.firstOrNull()

            if (extracted != null) {
                val selectedSubtitleUrl =
                    if (savedSettings != null) {
                        if (savedSettings.subtitlesEnabled) {
                            preferredSubtitleLanguage = savedSettings.subtitleLanguage
                            preferredSubtitleLabel = savedSettings.subtitleLabel
                            findMatchingSubtitle(extracted.subtitles, savedSettings)
                        } else {
                            preferredSubtitleLanguage = null
                            preferredSubtitleLabel = null
                            null
                        }
                    } else {
                        getPreferredSubtitleUrl(extracted.subtitles)
                    }

                val speed = savedSettings?.playbackSpeed ?: state.value.playbackSpeed
                val aspect = savedSettings?.aspectRatioMode ?: state.value.aspectRatioMode

                updateState {
                    it.copy(
                        shared = it.shared.copy(isLoading = false),
                        detailedMedia = detailedMedia,
                        videoHeaders = extracted.headers,
                        videoUrl = extracted.url,
                        subtitles = extracted.subtitles,
                        selectedSubtitleUrl = selectedSubtitleUrl,
                        startPositionMs = startPos,
                        playbackSpeed = speed,
                        aspectRatioMode = aspect,
                        isWebView = extracted.isWebView,
                        alternativeSources = allSources,
                    )
                }

                if (selectedSubtitleUrl != null) {
                    loadRawSubtitleContent(selectedSubtitleUrl)
                }
                val needsPolishSubtitles = state.value.isLektorEnabled ||
                    (savedSettings?.subtitlesEnabled == true &&
                        (savedSettings.subtitleLanguage?.lowercase() in setOf("pl", "pol") ||
                         savedSettings.subtitleLabel?.contains("pol", ignoreCase = true) == true))
                if (needsPolishSubtitles) {
                    val allSubs = extracted.subtitles + state.value.openSubtitles
                    val currentSub = allSubs.find { it.url == selectedSubtitleUrl }
                    if (currentSub == null || !isPolishSubtitle(currentSub)) {
                        val existingPolish = allSubs.find { isPolishSubtitle(it) }
                        if (existingPolish != null) {
                            handleEvent(PlayerEvent.SelectSubtitle(existingPolish.url))
                        } else {
                            downloadPolishSubtitles()
                        }
                    }
                }

                if (tvShowKey != null && savedSettings == null) {
                    val initialSettings =
                        TvShowSourceSettings(
                            serverName = extracted.serverName,
                            version = extracted.version,
                            quality = extracted.quality,
                            sourceWebsite = extracted.sourceWebsite,
                            subtitlesEnabled = selectedSubtitleUrl != null,
                            subtitleLanguage = extracted.subtitles.find { it.url == selectedSubtitleUrl }?.language,
                            subtitleLabel = extracted.subtitles.find { it.url == selectedSubtitleUrl }?.label,
                            playbackSpeed = speed,
                            aspectRatioMode = aspect,
                        )
                    tvShowSettingsManager.saveSettingsForTvShow(tvShowKey, initialSettings)
                }

                saveProgress(details.url, startPos)

                launchHandled {
                    var isFinished = false
                    var attempts = 0
                    var hasPromptedAuth = false
                    var hasPromptedCloudflare = false
                    var hasPromptedCdaCloudflare = false
                    while (!isFinished && attempts < 30 && currentMediaUrl == url) {
                        attempts++
                        val linksResponse = repository.getLinks(url, isTvShow = detailedMedia.baseItem.isTvShow)
                        if (linksResponse != null) {
                            val newSources = videoUrlResolver.sortVideos(
                                linksResponse.links.map {
                                    ExtractedVideo(
                                        url = it.url,
                                        serverName = it.serverName.ifEmpty { "${it.sourceWebsite} ${it.language}".trim() },
                                        version = it.language,
                                        quality = it.quality,
                                        sourceWebsite = it.sourceWebsite,
                                        isWebView = it.displayMode == "WEBVIEW",
                                        headers = it.headers,
                                        subtitles = it.subtitles.map { sub ->
                                            Subtitle(
                                                url = sub.url,
                                                label = sub.label,
                                                language = sub.language,
                                            )
                                        }
                                    )
                                }
                            )
                            updateState { it.copy(alternativeSources = newSources) }
                            if (state.value.shared.overlayMenuData != null) {
                                refreshOverlayMenu()
                            }
                            val currentSource = state.value.alternativeSources.find { it.url == state.value.videoUrl }
                            val currentIsTmdb = currentSource == null || currentSource.sourceWebsite.contains("tmdb", ignoreCase = true)
                            val bestSource = videoUrlResolver.findBest(newSources, targetSettings = savedSettings) ?: newSources.firstOrNull()
                            if (currentIsTmdb && bestSource != null && !bestSource.sourceWebsite.contains("tmdb", ignoreCase = true) &&
                                (state.value.currentPositionMs - state.value.startPositionMs < 15000 || !state.value.isPlaying)
                            ) {
                                changeVideoSource(bestSource)
                            }

                            if (linksResponse.requiresFilmanAuth && !hasPromptedAuth && !sessionManager.hasCookie()) {
                                hasPromptedAuth = true
                                sendEffect(PlayerEffect.NavigateToAuth)
                            }
                            if ("zaluknij" in linksResponse.challengeSources && !hasPromptedCloudflare && !zaluknijSessionManager.isChallengeActive.value && !zaluknijSessionManager.isChallengeCancelled.value) {
                                hasPromptedCloudflare = true
                                zaluknijSessionManager.requestChallenge()
                            }
                            if ("cda_hd" in linksResponse.challengeSources && !hasPromptedCdaCloudflare && !cdaHdSessionManager.isChallengeActive.value && !cdaHdSessionManager.isChallengeCancelled.value) {
                                hasPromptedCdaCloudflare = true
                                cdaHdSessionManager.requestChallenge()
                            }
                            if (linksResponse.status == "READY" || linksResponse.status == "ERROR" || linksResponse.status == "NEEDS_AUTHORIZATION") {
                                val cloudflareOk = linksResponse.challengeSources.none {
                                    when (it) {
                                        "zaluknij" -> zaluknijSessionManager.isChallengeActive.value
                                        "cda_hd" -> cdaHdSessionManager.isChallengeActive.value
                                        "filman" -> sessionManager.isChallengeActive.value
                                        else -> false
                                    }
                                }
                                val filmanOk = !linksResponse.requiresFilmanAuth || sessionManager.hasCookie() || hasPromptedAuth
                                isFinished = cloudflareOk && filmanOk
                            }
                        } else {
                            isFinished = true
                        }
                        if (!isFinished) kotlinx.coroutines.delay(2000)
                    }
                }
            } else {
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = TextValue.StringResource(R.string.stream_api_unavailable),
                    )
                }
            }
        }
    }

    private fun refreshSourcesIfAvailable() {
        val mediaUrl = currentMediaUrl ?: state.value.detailedMedia?.baseItem?.url ?: return
        val detailedMedia = state.value.detailedMedia ?: return
        val tvShowKey = detailedMedia.baseItem.getTvShowKey()

        launchHandled {
            val savedSettings = if (tvShowKey != null) tvShowSettingsManager.getSettingsForTvShow(tvShowKey) else null
            var linksResponse = repository.getLinks(mediaUrl, isTvShow = detailedMedia.baseItem.isTvShow)
            for (attempt in 0 until 20) {
                if (attempt > 0 && linksResponse?.status != "PENDING" && linksResponse?.status != "PARTIAL") break
                kotlinx.coroutines.delay(1000)
                linksResponse = repository.getLinks(mediaUrl, isTvShow = detailedMedia.baseItem.isTvShow)
            }
            val settledResponse = linksResponse
            if (settledResponse != null && settledResponse.links.isNotEmpty()) {
                val newSources = videoUrlResolver.sortVideos(
                    settledResponse.links.map {
                        ExtractedVideo(
                            url = it.url,
                            serverName = it.serverName.ifEmpty { "${it.sourceWebsite} ${it.language}".trim() },
                            version = it.language,
                            quality = it.quality,
                            sourceWebsite = it.sourceWebsite,
                            isWebView = it.displayMode == "WEBVIEW",
                            headers = it.headers,
                            subtitles = it.subtitles.map { sub ->
                                Subtitle(
                                    url = sub.url,
                                    label = sub.label,
                                    language = sub.language,
                                )
                            }
                        )
                    }
                )
                if (newSources.isNotEmpty()) {
                    updateState { it.copy(alternativeSources = newSources) }
                    if (state.value.shared.overlayMenuData != null) {
                        refreshOverlayMenu()
                    }
                    val currentSource = state.value.alternativeSources.find { it.url == state.value.videoUrl }
                    val currentIsTmdb = currentSource == null || currentSource.sourceWebsite.contains("tmdb", ignoreCase = true)
                    val bestSource = videoUrlResolver.findBest(newSources, targetSettings = savedSettings) ?: newSources.firstOrNull()
                    if (currentIsTmdb && bestSource != null && !bestSource.sourceWebsite.contains("tmdb", ignoreCase = true) && (state.value.currentPositionMs < 15000 || !state.value.isPlaying)) {
                        changeVideoSource(bestSource)
                    }
                }
            }
        }
    }

    private fun isPolishSubtitle(sub: Subtitle): Boolean {
        val lang = sub.language.lowercase()
        val label = sub.label.lowercase()
        return lang == "pl" || lang == "pol" || lang.contains("pol") ||
                label.contains("polsk") || label.contains("polish") ||
                label == "pl" || label.startsWith("pl ") || label.startsWith("pl-") ||
                label.endsWith("(pl)") || label.endsWith("pl")
    }

    private fun downloadPolishSubtitles() {
        val detailedMedia = state.value.detailedMedia ?: return
        val title = detailedMedia.baseItem.titleEn ?: detailedMedia.baseItem.titlePl

        launchHandled {
            var srtUrl: String? = null
            var subLabel = "OpenSubtitles (pl)"
            val season = detailedMedia.baseItem.seasonNumber
            val episode = detailedMedia.baseItem.episodeNumber

            var searchFailed = false
            fun recordFailure(error: Throwable) {
                if (error is CancellationException) throw error
                searchFailed = true
                android.util.Log.w("Subtitles", "Automatic subtitle search failed", error)
            }

            val videoUrl = state.value.videoUrl
            val videoHeaders = state.value.videoHeaders
            val videoFileName = VideoSignatureExtractor.extract(
                videoUrl = videoUrl,
                headers = videoHeaders,
            )
            val movieHash = if (videoUrl != null && !videoUrl.contains(".m3u8", ignoreCase = true) && !videoUrl.contains(".mpd", ignoreCase = true)) {
                openSubtitlesHasher.computeHash(videoUrl, videoHeaders)
            } else null

            var candidates = runCatching {
                openSubtitlesClient.search(
                    query = title,
                    language = "pl",
                    season = season,
                    episode = episode,
                    tmdbId = detailedMedia.tmdbId,
                    videoFileName = videoFileName,
                    movieHash = movieHash,
                )
            }.onFailure(::recordFailure).getOrNull() ?: emptyList()

            if (candidates.isEmpty() && (detailedMedia.tmdbId ?: 0) <= 0 &&
                detailedMedia.baseItem.titlePl != title
            ) {
                candidates = runCatching {
                    openSubtitlesClient.search(
                        query = detailedMedia.baseItem.titlePl,
                        language = "pl",
                        season = season,
                        episode = episode,
                        videoFileName = videoFileName,
                        movieHash = movieHash,
                    )
                }.onFailure(::recordFailure).getOrNull() ?: emptyList()
            }

            var chosenCandidate: OpenSubtitleCandidate? = null
            val primaryCandidate = candidates.firstOrNull()
            if (primaryCandidate != null) {
                chosenCandidate = primaryCandidate
                var chosenDownload = runCatching { openSubtitlesClient.download(primaryCandidate.fileId) }
                    .onFailure { recordFailure(it) }
                    .getOrNull()

                if (chosenDownload != null && candidates.size > 1 && state.value.duration > 0) {
                    val primaryUrl = subtitleUrlWithFormat(chosenDownload.link, chosenDownload.fileName.substringAfterLast('.', "srt"))
                    val primaryBody = runCatching {
                        val req = Request.Builder().url(primaryUrl).build()
                        NetworkClient.okHttpClient.newCall(req).execute().use { if (it.isSuccessful) it.body.string() else null }
                    }.getOrNull()
                    if (primaryBody != null && diagnoseSubtitle(primaryBody, state.value.duration) == SubtitleWarning.POSSIBLY_INCOMPLETE) {
                        val secondCandidate = candidates[1]
                        val secondDownload = runCatching { openSubtitlesClient.download(secondCandidate.fileId) }.getOrNull()
                        if (secondDownload != null) {
                            chosenCandidate = secondCandidate
                            chosenDownload = secondDownload
                        }
                    }
                }

                if (chosenDownload != null) {
                    srtUrl = subtitleUrlWithFormat(chosenDownload.link, chosenDownload.fileName.substringAfterLast('.', "srt"))
                    subLabel = "OpenSubtitles (pl) ${chosenCandidate.fileName}".trim()
                }
            } else {
                val year = detailedMedia.baseItem.year
                val wyzieSubs = runCatching {
                    wyzieSubsClient.searchSubtitles(title, year, season, episode, detailedMedia.tmdbId)
                }.getOrNull() ?: emptyList()
                val plSubs = wyzieSubs.filter { it.language.equals("pl", ignoreCase = true) }
                val plSub = if (videoFileName != null && plSubs.size > 1) {
                    val videoTokens = VideoSignatureExtractor.tokenize(videoFileName)
                    plSubs.maxByOrNull {
                        VideoSignatureExtractor.jaccardSimilarity(videoTokens, VideoSignatureExtractor.tokenize(it.display))
                    } ?: plSubs.firstOrNull()
                } else {
                    plSubs.firstOrNull()
                }
                if (plSub != null) {
                    srtUrl = subtitleUrlWithFormat(plSub.url, plSub.format)
                    subLabel = "Wyzie (${plSub.display})"
                }
            }

            if (srtUrl != null) {
                val newSubtitle =
                    Subtitle(
                        url = srtUrl,
                        label = subLabel,
                        language = "pl",
                    )
                val updatedOpenSubtitles = state.value.openSubtitles + newSubtitle
                updateState {
                    it.copy(
                        openSubtitles = updatedOpenSubtitles,
                        selectedSubtitleUrl = srtUrl,
                        rawSubtitleContent = null,
                    )
                }
                loadRawSubtitleContent(srtUrl)
            } else {
                sendEffect(
                    PlayerEffect.ShowToast(
                        TextValue.StringResource(
                            if (searchFailed) R.string.player_subtitle_search_failed else R.string.player_lektor_subtitles_not_found,
                        )
                    )
                )
            }
        }
    }

    private fun searchOpenSubtitles(language: String) {
        val detailedMedia = state.value.detailedMedia ?: return
        val title = detailedMedia.baseItem.titleEn ?: detailedMedia.baseItem.titlePl
        val season = detailedMedia.baseItem.seasonNumber
        val episode = detailedMedia.baseItem.episodeNumber

        launchHandled(onError = { error ->
            android.util.Log.e("Subtitles", "OpenSubtitles search failed", error)
            sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_search_failed)))
        }) {
            val videoUrl = state.value.videoUrl
            val videoHeaders = state.value.videoHeaders
            val videoFileName = VideoSignatureExtractor.extract(videoUrl, videoHeaders)
            val movieHash = if (videoUrl != null && !videoUrl.contains(".m3u8", ignoreCase = true) && !videoUrl.contains(".mpd", ignoreCase = true)) {
                openSubtitlesHasher.computeHash(videoUrl, videoHeaders)
            } else null
            val candidates = openSubtitlesClient.search(
                query = title,
                language = language,
                season = season,
                episode = episode,
                tmdbId = detailedMedia.tmdbId,
                videoFileName = videoFileName,
                movieHash = movieHash,
            )
            if (state.value.detailedMedia != detailedMedia) return@launchHandled
            updateState { it.copy(openSubtitleCandidates = candidates) }
            if (candidates.isNotEmpty()) {
                refreshOverlayMenu("menu_open_subtitle_results")
            } else {
                sendEffect(
                    PlayerEffect.ShowToast(
                        TextValue.StringResource(
                            R.string.player_no_open_subtitles_found,
                            language,
                        ),
                    ),
                )
            }
        }
    }

    private fun downloadOpenSubtitle(candidate: OpenSubtitleCandidate) {
        val detailedMedia = state.value.detailedMedia ?: return
        launchHandled(onError = { error ->
            android.util.Log.e("Subtitles", "OpenSubtitles download failed", error)
            sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_download_failed)))
        }) {
            val download = openSubtitlesClient.download(candidate.fileId)
            if (state.value.detailedMedia != detailedMedia) return@launchHandled
            val link = subtitleUrlWithFormat(download.link, download.fileName.substringAfterLast('.', "srt"))
            val subtitle = Subtitle(link, "OpenSubtitles (${candidate.language}) ${candidate.fileName}", candidate.language)
            updateState {
                it.copy(openSubtitles = (it.openSubtitles + subtitle).distinctBy { sub -> sub.url })
            }
            handleEvent(PlayerEvent.SelectSubtitle(link))
        }
    }

    private fun searchWyzieSubtitles(language: String) {
        val detailedMedia = state.value.detailedMedia ?: return
        val title = detailedMedia.baseItem.titleEn ?: detailedMedia.baseItem.titlePl
        val year = detailedMedia.baseItem.year
        val season = detailedMedia.baseItem.seasonNumber
        val episode = detailedMedia.baseItem.episodeNumber

        launchHandled {
            val subs = wyzieSubsClient.searchSubtitles(title, year, season, episode, detailedMedia.tmdbId)
            val filteredSubs = subs.filter { it.language.equals(language, ignoreCase = true) }

            if (filteredSubs.isNotEmpty()) {
                val newSubtitles =
                    filteredSubs.mapIndexed { index, sub ->
                        val srtLink = subtitleUrlWithFormat(sub.url, sub.format)
                        Subtitle(
                            url = srtLink,
                            label = "Wyzie (${sub.display}) ${if (index > 0) "#${index + 1}" else ""}".trim(),
                            language = sub.language,
                        )
                    }
                val updatedOpenSubtitles = state.value.openSubtitles + newSubtitles
                updateState {
                    it.copy(
                        openSubtitles = updatedOpenSubtitles,
                        selectedSubtitleUrl = newSubtitles.first().url,
                        rawSubtitleContent = null,
                    )
                }
                loadRawSubtitleContent(newSubtitles.first().url)
            } else {
                sendEffect(
                    PlayerEffect.ShowToast(
                        TextValue.StringResource(
                            R.string.player_no_wyzie_subtitles_found,
                            language,
                        ),
                    ),
                )
            }
        }
    }

    private fun subtitleUrlWithFormat(url: String, format: String): String {
        val extension = when (format.lowercase()) {
            "srt", "vtt", "ttml" -> format.lowercase()
            else -> "srt"
        }
        return if (url.contains("?")) "$url&ext=.$extension" else "$url?ext=.$extension"
    }

    private fun loadRawSubtitleContent(url: String) {
        launchHandled(Dispatchers.IO) {
            try {
                fun applyContent(body: String) {
                    if (state.value.selectedSubtitleUrl != url) return
                    val isDownloaded = state.value.openSubtitles.any { it.url == url }
                    val checkableFormat = url.contains(".srt", ignoreCase = true) ||
                        url.contains(".vtt", ignoreCase = true)
                    if (isDownloaded && checkableFormat) {
                        val warning = diagnoseSubtitle(body, state.value.duration)
                        val message = when (warning) {
                            SubtitleWarning.UNREADABLE -> R.string.player_subtitle_unreadable
                            SubtitleWarning.POSSIBLY_INCOMPLETE -> R.string.player_subtitle_incomplete
                            null -> null
                        }
                        if (message != null) {
                            sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(message)))
                        }
                    }
                    if (body.contains("-->")) {
                        updateState { if (it.selectedSubtitleUrl == url) it.copy(rawSubtitleContent = body) else it }
                    } else {
                        android.util.Log.w("Subtitles", "Subtitle content did not contain timestamps")
                    }
                }
                if (url.startsWith("file://") || url.startsWith("/")) {
                    val file = java.io.File(url.removePrefix("file://"))
                    if (file.exists()) {
                        val body = file.readText()
                        applyContent(body)
                        return@launchHandled
                    }
                }
                val requestBuilder = Request.Builder().url(url)
                val isDownloaded = state.value.openSubtitles.any { it.url == url }
                if (!isDownloaded) {
                    state.value.videoHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }
                }
                if (isDownloaded || state.value.videoHeaders.none { it.key.equals("User-Agent", ignoreCase = true) }) {
                    requestBuilder.header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36")
                }
                NetworkClient.okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        applyContent(response.body.string())
                    } else {
                        android.util.Log.w("Lektor", "Failed to download subtitle content, code=${response.code}")
                        if (isDownloaded && state.value.selectedSubtitleUrl == url) {
                            sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_download_failed)))
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("Lektor", "Error loading raw subtitle content", e)
                if (state.value.selectedSubtitleUrl == url && state.value.openSubtitles.any { it.url == url }) {
                    sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_download_failed)))
                }
            }
        }
    }
}
