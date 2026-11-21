package com.pointlessapps.filman

import android.content.Intent
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.data.api.StreamApiLinkSource
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SearchHistoryManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsConstants.Quality.AUTO
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.model.ContentCategory
import com.pointlessapps.filman.data.model.ContentRule
import com.pointlessapps.filman.data.model.ContentRulesPreferences
import com.pointlessapps.filman.data.sync.SyncManager
import com.pointlessapps.filman.player.lektor.controller.LektorControllerDelegate
import com.pointlessapps.filman.player.lektor.engine.LektorModelManager
import com.pointlessapps.filman.player.lektor.engine.LektorVoiceTester
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceCatalog
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    private val lektorController = LektorControllerDelegate(
        scope = viewModelScope,
        settingsManager = settingsManager,
        lektorModelManager = lektorModelManager,
        systemVoiceCatalog = systemVoiceCatalog,
        voiceTester = voiceTester,
    )

    val isLektorEnabled = eagerState(settingsManager.isLektorEnabled, false)
    val lektorVoiceId = eagerState(settingsManager.lektorVoiceId, "edge_marek")
    val lektorDuckingVolume = eagerState(
        settingsManager.lektorDuckingVolume,
        com.pointlessapps.filman.data.model.LektorDucking.DEFAULT_VOLUME,
    )
    val lektorTimingOffsetMs = settingsManager.lektorTimingOffsetMs
    val lektorHideSubtitles = settingsManager.lektorHideSubtitles
    val lektorEdgeRate = settingsManager.lektorEdgeRate
    val lektorEdgePitch = settingsManager.lektorEdgePitch
    val lektorEdgeVolume = settingsManager.lektorEdgeVolume
    val lektorEdgeCalmMode = settingsManager.lektorEdgeCalmMode
    val contentRules = eagerState(settingsManager.contentRulesFlow, ContentRulesPreferences())

    fun setContentRule(category: ContentCategory, rule: ContentRule) {
        settingsManager.setContentRule(category, rule)
    }

    val downloadedLektorVoices: StateFlow<Set<String>> =
        eagerState(lektorController.state.map { it.downloadedVoices }, lektorModelManager.getDownloadedVoices())
    val lektorModelDownloadProgress: StateFlow<Float?> =
        eagerState(lektorController.state.map { it.modelDownloadProgress }, null)
    val isLektorModelExtracting: StateFlow<Boolean> =
        eagerState(lektorController.state.map { it.isModelExtracting }, false)
    val systemVoices: StateFlow<SystemVoiceList> =
        systemVoiceCatalog?.voices ?: MutableStateFlow(SystemVoiceList(loading = false, failed = true))

    val preferredQuality = eagerState(settingsManager.preferredQualityFlow, AUTO)
    val autoPlayNextEpisode = eagerState(settingsManager.autoPlayNextFlow, true)
    val initialAppearanceType = eagerState(
        settingsManager.initialAppearanceTypeFlow,
        SettingsConstants.NextEpisodeAppearance.SHOW_IN_OVERLAY,
    )
    val initialAppearanceOffset = eagerState(settingsManager.initialAppearanceOffsetFlow, 100L)
    val secondaryAppearanceType = eagerState(
        settingsManager.secondaryAppearanceTypeFlow,
        SettingsConstants.NextEpisodeAppearance.SHOW_WITH_TIMER,
    )
    val secondaryAppearanceOffset = eagerState(settingsManager.secondaryAppearanceOffsetFlow, 45L)
    val secondaryTimerAmount = eagerState(settingsManager.secondaryTimerAmountFlow, 10L)
    val initialAppearancePercentage = eagerState(settingsManager.initialAppearancePercentageFlow, 2L)
    val secondaryAppearancePercentage = eagerState(settingsManager.secondaryAppearancePercentageFlow, 100L)

    val isScreensaverEnabled = eagerState(settingsManager.isScreensaverEnabled, true)
    val screensaverInactivityTime = eagerState(settingsManager.screensaverInactivityTime, 120_000L)
    val screensaverSlideDuration = eagerState(settingsManager.screensaverSlideDuration, 10_000L)

    val isLoggedIn = eagerState(
        sessionManager.authTokenFlow.map { !it.isNullOrEmpty() },
        sessionManager.isLoggedIn(),
    )
    val loggedInUsername = sessionManager.usernameFlow

    private fun <T> eagerState(flow: Flow<T>, initialValue: T): StateFlow<T> =
        flow.stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = initialValue)

    fun onToggleLektor(enabled: Boolean) = lektorController.setEnabled(enabled)
    fun onSelectLektorVoice(voiceId: String) = lektorController.selectVoice(voiceId)
    fun previewSystemVoice(name: String?) = lektorController.previewSystemVoice(name)
    fun refreshSystemVoices() = lektorController.refreshSystemVoices()
    fun onChangeLektorDuckingVolume(volume: Float) = lektorController.setDuckingVolume(volume)
    fun onChangeLektorTimingOffset(offsetMs: Int) = lektorController.setTimingOffsetMs(offsetMs)
    fun onSetLektorHideSubtitles(hide: Boolean) = lektorController.setHideSubtitles(hide)
    fun onChangeLektorEdgeRate(rate: String) = lektorController.setEdgeRate(rate)
    fun onChangeLektorEdgePitch(pitch: String) = lektorController.setEdgePitch(pitch)
    fun onChangeLektorEdgeVolume(volume: String) = lektorController.setEdgeVolume(volume)
    fun onChangeLektorEdgeCalmMode(enabled: Boolean) = lektorController.setEdgeCalmMode(enabled)
    fun downloadLektorModel(voiceId: String) = lektorController.downloadModel(voiceId)
    fun deleteLektorModel(voiceId: String) = lektorController.deleteModel(voiceId)
    fun testVoice() = lektorController.testVoice()

    fun onSyncNowClicked() {
        syncManager?.triggerSync()
    }

    fun onResume() {
        syncManager?.triggerSync()
    }

    fun initBackStack(startDestination: Route) {
        if (backStack.isEmpty()) {
            backStack.add(startDestination)
        }
    }

    fun handleNavigateTo(route: Route?) = backStack.navigateToRoute(route)

    fun navigateToTab(route: Route) = backStack.navigateToTabRoute(route)

    fun handleIntent(intent: Intent?) {
        val deepLink = parseDeepLinkIntent(intent) ?: return
        if (deepLink.shouldResetToHome) {
            _showSettingsOverlay.update { false }
            backStack.removeAll { it != Route.Home }
            while (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
            if (backStack.isEmpty()) {
                backStack.add(Route.Home)
            }
        }
        backStack.add(deepLink.route)
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

    fun setPreferredQuality(quality: String) = settingsManager.setPreferredQuality(quality)
    fun setAutoPlayNext(enabled: Boolean) = settingsManager.setAutoPlayNext(enabled)
    fun setInitialAppearanceType(type: SettingsConstants.NextEpisodeAppearance) =
        settingsManager.setInitialAppearanceType(type)
    fun setInitialAppearanceOffset(offset: Long) = settingsManager.setInitialAppearanceOffset(offset)
    fun setSecondaryAppearanceType(type: SettingsConstants.NextEpisodeAppearance) =
        settingsManager.setSecondaryAppearanceType(type)
    fun setSecondaryAppearanceOffset(offset: Long) = settingsManager.setSecondaryAppearanceOffset(offset)
    fun setSecondaryTimerAmount(amount: Long) = settingsManager.setSecondaryTimerAmount(amount)
    fun setInitialAppearancePercentage(percentage: Long) = settingsManager.setInitialAppearancePercentage(percentage)
    fun setSecondaryAppearancePercentage(percentage: Long) = settingsManager.setSecondaryAppearancePercentage(percentage)

    fun clearCache() {
        viewModelScope.launch { streamApiLinkSource.clearCache() }
    }

    fun clearWatchHistory() = progressManager.clearAll()
    fun clearSearchHistory() = searchHistoryManager.clearAll()
    fun onScreensaverEnabledToggled(enabled: Boolean) = settingsManager.setScreensaverEnabled(enabled)
    fun onScreensaverInactivityTimeChanged(timeMs: Long) = settingsManager.setScreensaverInactivityTime(timeMs)
    fun onScreensaverSlideDurationChanged(timeMs: Long) = settingsManager.setScreensaverSlideDuration(timeMs)

    override fun onCleared() {
        super.onCleared()
        voiceTester?.stop()
    }
}
