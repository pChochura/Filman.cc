package com.pointlessapps.filman

import android.content.Intent
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.config.FilmanConfig.DEEP_LINK_PARAM_AUTOPLAY
import com.pointlessapps.filman.config.FilmanConfig.DEEP_LINK_PARAM_FROM_RECOMMENDATION
import com.pointlessapps.filman.config.FilmanConfig.DEEP_LINK_PARAM_VALUE_TRUE
import com.pointlessapps.filman.data.api.StreamApiLinkSource
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SearchHistoryManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsConstants.Quality.AUTO
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.sync.SyncManager
import com.pointlessapps.filman.player.lektor.engine.LektorModelManager
import com.pointlessapps.filman.player.lektor.engine.LektorVoiceTester
import com.pointlessapps.filman.player.lektor.engine.ModelDownloadState
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceCatalog
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class MainViewModel(
    private val sessionManager: SessionManager,
    private val settingsManager: SettingsManager,
    private val progressManager: ProgressManager,
    private val searchHistoryManager: SearchHistoryManager,
    private val streamApiLinkSource: StreamApiLinkSource,
    private val syncManager: SyncManager? = null,
    val lektorModelManager: LektorModelManager = LektorModelManager(),
    val systemVoiceCatalog: SystemVoiceCatalog? = null,
    val voiceTester: LektorVoiceTester? = null,
) : ViewModel() {
    val backStack = mutableStateListOf<Route>()

    private val _showSettingsOverlay = MutableStateFlow(false)
    val showSettingsOverlay = _showSettingsOverlay.asStateFlow()

    val isLektorEnabled =
        settingsManager.isLektorEnabled.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )
    val lektorVoiceId =
        settingsManager.lektorVoiceId.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = "darkman",
        )
    val lektorDuckingVolume =
        settingsManager.lektorDuckingVolume.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = com.pointlessapps.filman.data.model.LektorDucking.DEFAULT_VOLUME,
        )
    val lektorTimingOffsetMs = settingsManager.lektorTimingOffsetMs
    val lektorHideSubtitles = settingsManager.lektorHideSubtitles
    val lektorEdgeRate = settingsManager.lektorEdgeRate
    val lektorEdgePitch = settingsManager.lektorEdgePitch
    val lektorEdgeVolume = settingsManager.lektorEdgeVolume

    val downloadedLektorVoices = MutableStateFlow<Set<String>>(emptySet())
    val lektorModelDownloadProgress = MutableStateFlow<Float?>(null)
    val isLektorModelExtracting = MutableStateFlow(false)
    val systemVoices = systemVoiceCatalog?.voices ?: MutableStateFlow(SystemVoiceList(loading = false, failed = true))

    init {
        viewModelScope.launch {
            downloadedLektorVoices.value = lektorModelManager.getDownloadedVoices()
            lektorModelManager.downloadedVoices.collect { voices ->
                downloadedLektorVoices.value = voices
            }
        }
        viewModelScope.launch {
            lektorModelManager.downloadState.collect { downloadState ->
                when (downloadState) {
                    is ModelDownloadState.Downloading -> {
                        lektorModelDownloadProgress.value = downloadState.progress
                        isLektorModelExtracting.value = false
                    }
                    ModelDownloadState.Extracting -> {
                        lektorModelDownloadProgress.value = null
                        isLektorModelExtracting.value = true
                    }
                    ModelDownloadState.Ready -> {
                        lektorModelDownloadProgress.value = null
                        isLektorModelExtracting.value = false
                        downloadedLektorVoices.value = lektorModelManager.getDownloadedVoices()
                    }
                    is ModelDownloadState.Error -> {
                        lektorModelDownloadProgress.value = null
                        isLektorModelExtracting.value = false
                    }
                    ModelDownloadState.Idle -> {
                        lektorModelDownloadProgress.value = null
                        isLektorModelExtracting.value = false
                        downloadedLektorVoices.value = lektorModelManager.getDownloadedVoices()
                    }
                }
            }
        }
    }

    fun onToggleLektor(enabled: Boolean) {
        settingsManager.setLektorEnabled(enabled)
        if (enabled) {
            val selectedVoice = LektorVoice.fromId(lektorVoiceId.value)
            if (!lektorModelManager.isModelReady(selectedVoice)) {
                lektorModelManager.startDownload(selectedVoice)
            }
        }
    }

    fun onSelectLektorVoice(voiceId: String) {
        settingsManager.setLektorVoiceId(voiceId)
    }

    fun previewSystemVoice(name: String?) {
        systemVoiceCatalog?.preview(name)
    }

    fun refreshSystemVoices() {
        systemVoiceCatalog?.refresh()
    }

    fun onChangeLektorDuckingVolume(volume: Float) {
        settingsManager.setLektorDuckingVolume(volume)
    }

    fun onChangeLektorTimingOffset(offsetMs: Int) {
        settingsManager.setLektorTimingOffsetMs(offsetMs)
    }

    fun onSetLektorHideSubtitles(hide: Boolean) {
        settingsManager.setLektorHideSubtitles(hide)
    }

    fun onChangeLektorEdgeRate(rate: String) {
        settingsManager.setLektorEdgeRate(rate)
    }

    fun onChangeLektorEdgePitch(pitch: String) {
        settingsManager.setLektorEdgePitch(pitch)
    }

    fun onChangeLektorEdgeVolume(volume: String) {
        settingsManager.setLektorEdgeVolume(volume)
    }

    fun downloadLektorModel(voiceId: String) {
        val voice = LektorVoice.fromId(voiceId)
        lektorModelManager.startDownload(voice)
        downloadedLektorVoices.value = lektorModelManager.getDownloadedVoices()
    }

    fun deleteLektorModel(voiceId: String) {
        val voice = LektorVoice.fromId(voiceId)
        lektorModelManager.deleteModel(voice)
        downloadedLektorVoices.value = lektorModelManager.getDownloadedVoices()
    }

    fun testVoice() {
        if (voiceTester != null) {
            voiceTester.testVoice(
                voiceId = lektorVoiceId.value,
                edgeRate = lektorEdgeRate.value,
                edgePitch = lektorEdgePitch.value,
                edgeVolume = lektorEdgeVolume.value,
            )
        } else {
            val selectedVoice = LektorVoice.fromId(lektorVoiceId.value)
            if (selectedVoice.isSystem) {
                systemVoiceCatalog?.preview(LektorVoice.systemVoiceName(lektorVoiceId.value))
            }
        }
    }

    val preferredQuality =
        settingsManager.preferredQualityFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AUTO,
        )
    val autoPlayNextEpisode =
        settingsManager.autoPlayNextFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )
    val initialAppearanceType =
        settingsManager.initialAppearanceTypeFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsConstants.NextEpisodeAppearance.SHOW_IN_OVERLAY,
        )
    val initialAppearanceOffset =
        settingsManager.initialAppearanceOffsetFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 100L,
        )
    val secondaryAppearanceType =
        settingsManager.secondaryAppearanceTypeFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsConstants.NextEpisodeAppearance.SHOW_WITH_TIMER,
        )
    val secondaryAppearanceOffset =
        settingsManager.secondaryAppearanceOffsetFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 45L,
        )
    val secondaryTimerAmount =
        settingsManager.secondaryTimerAmountFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 10L,
        )
    val initialAppearancePercentage =
        settingsManager.initialAppearancePercentageFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 2L,
        )
    val secondaryAppearancePercentage =
        settingsManager.secondaryAppearancePercentageFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 100L,
        )

    val isScreensaverEnabled =
        settingsManager.isScreensaverEnabled.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )
    val screensaverInactivityTime =
        settingsManager.screensaverInactivityTime.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 120_000L,
        )
    val screensaverSlideDuration =
        settingsManager.screensaverSlideDuration.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 10_000L,
        )

    val isLoggedIn =
        sessionManager.authTokenFlow.map { !it.isNullOrEmpty() }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = sessionManager.isLoggedIn(),
        )

    val loggedInUsername = sessionManager.usernameFlow

    fun onSyncNowClicked() {
        syncManager?.triggerSync()
    }

    fun initBackStack(startDestination: Route) {
        if (backStack.isEmpty()) {
            backStack.add(startDestination)
        }
    }

    fun handleNavigateTo(route: Route?) {
        if (route == null) {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
        } else {
            var routeToAdd = route
            if (routeToAdd is Route.Login) {
                val currentRoute = backStack.lastOrNull()
                if (currentRoute is Route.Login) {
                    return
                }
                if (currentRoute != null) {
                    routeToAdd =
                        routeToAdd.copy(
                            returnRoute = routeToAdd.returnRoute ?: currentRoute,
                        )
                    if (routeToAdd.replaceCurrentRoute) {
                        backStack.removeLastOrNull()
                    }
                }
            } else if (backStack.lastOrNull() is Route.Login) {
                val loginRoute = backStack.last() as Route.Login
                backStack.removeLastOrNull()

                if (!loginRoute.replaceCurrentRoute && backStack.lastOrNull() == routeToAdd) {
                    return
                }
            }

            backStack.add(routeToAdd)
        }
    }

    fun navigateToTab(route: Route) {
        backStack.removeAll { it.showNavigationBar }
        backStack.add(route)
    }

    fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (
            data.scheme == FilmanConfig.DEEP_LINK_SCHEME &&
            data.host == FilmanConfig.DEEP_LINK_HOST_DETAILS
        ) {
            val url = data.getQueryParameter(FilmanConfig.DEEP_LINK_PARAM_URL)
            val episodeUrl = data.getQueryParameter(FilmanConfig.DEEP_LINK_PARAM_EPISODE_URL)
            val autoPlay =
                data.getQueryParameter(DEEP_LINK_PARAM_AUTOPLAY) == DEEP_LINK_PARAM_VALUE_TRUE ||
                    data.getQueryParameter("autoPlay") == DEEP_LINK_PARAM_VALUE_TRUE
            val fromRecommendation =
                data.getQueryParameter(DEEP_LINK_PARAM_FROM_RECOMMENDATION) == DEEP_LINK_PARAM_VALUE_TRUE

            if (url != null) {
                if (fromRecommendation || autoPlay) {
                    _showSettingsOverlay.update { false }
                    backStack.removeAll { it != Route.Home }
                    while (backStack.size > 1) {
                        backStack.removeLastOrNull()
                    }
                    if (backStack.isEmpty()) {
                        backStack.add(Route.Home)
                    }
                }
                backStack.add(
                    Route.Details(
                        request = DetailsRequest.Url(url),
                        autoPlay = autoPlay,
                        episodeUrl = episodeUrl,
                    ),
                )
            }
        }
    }

    fun setShowSettingsOverlay(show: Boolean) {
        if (show) refreshSystemVoices()
        _showSettingsOverlay.update { show }
    }

    fun onLogoutClicked() {
        viewModelScope.launch {
            sessionManager.clearSession()
        }
        _showSettingsOverlay.update { false }
    }

    fun setPreferredQuality(quality: String) {
        settingsManager.setPreferredQuality(quality)
    }

    fun setAutoPlayNext(enabled: Boolean) {
        settingsManager.setAutoPlayNext(enabled)
    }

    fun setInitialAppearanceType(type: SettingsConstants.NextEpisodeAppearance) {
        settingsManager.setInitialAppearanceType(type)
    }

    fun setInitialAppearanceOffset(offset: Long) {
        settingsManager.setInitialAppearanceOffset(offset)
    }

    fun setSecondaryAppearanceType(type: SettingsConstants.NextEpisodeAppearance) {
        settingsManager.setSecondaryAppearanceType(type)
    }

    fun setSecondaryAppearanceOffset(offset: Long) {
        settingsManager.setSecondaryAppearanceOffset(offset)
    }

    fun setSecondaryTimerAmount(amount: Long) {
        settingsManager.setSecondaryTimerAmount(amount)
    }

    fun setInitialAppearancePercentage(percentage: Long) {
        settingsManager.setInitialAppearancePercentage(percentage)
    }

    fun setSecondaryAppearancePercentage(percentage: Long) {
        settingsManager.setSecondaryAppearancePercentage(percentage)
    }

    fun clearCache() {
        viewModelScope.launch { streamApiLinkSource.clearCache() }
    }

    fun clearWatchHistory() {
        progressManager.clearAll()
    }

    fun clearSearchHistory() {
        searchHistoryManager.clearAll()
    }

    fun onScreensaverEnabledToggled(enabled: Boolean) {
        settingsManager.setScreensaverEnabled(enabled)
    }

    fun onScreensaverInactivityTimeChanged(timeMs: Long) {
        settingsManager.setScreensaverInactivityTime(timeMs)
    }

    fun onScreensaverSlideDurationChanged(timeMs: Long) {
        settingsManager.setScreensaverSlideDuration(timeMs)
    }

    override fun onCleared() {
        super.onCleared()
        voiceTester?.stop()
    }
}
