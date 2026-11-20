package com.pointlessapps.filman

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pointlessapps.filman.ui.components.TiwiScaffold
import com.pointlessapps.filman.ui.core.Event
import com.pointlessapps.filman.ui.core.Event.ScrollToTopEvent
import com.pointlessapps.filman.ui.core.EventDispatcher
import com.pointlessapps.filman.ui.core.LocalEventDispatcher
import com.pointlessapps.filman.ui.core.LocalInteractionReset
import com.pointlessapps.filman.ui.core.LocalIsPlaying
import com.pointlessapps.filman.ui.screensaver.ScreensaverScreen
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun TiwiApp(
    viewModel: MainViewModel,
    activity: MainActivity,
) {
    val backStack = viewModel.backStack
    val currentRoute = backStack.lastOrNull()
    val showSettingsOverlay by viewModel.showSettingsOverlay.collectAsState()

    val transitionFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val eventDispatcher = remember { EventDispatcher() }

    var wasSettingsOverlayOpen by remember { mutableStateOf(false) }
    LaunchedEffect(showSettingsOverlay) {
        if (wasSettingsOverlayOpen && !showSettingsOverlay) {
            runCatching { contentFocusRequester.requestFocus() }
        }
        wasSettingsOverlayOpen = showSettingsOverlay
    }

    val handleNavigateTo: (Route?) -> Unit =
        remember(backStack, transitionFocusRequester, viewModel) {
            { route ->
                runCatching { transitionFocusRequester.requestFocus() }
                viewModel.handleNavigateTo(route)
            }
        }

    LaunchedEffect(Unit) {
        eventDispatcher.dispatch(Event.FocusOnContent)
    }

    val lastInteraction by activity.lastInteractionTime.collectAsState()
    var showScreensaver by remember { mutableStateOf(false) }
    val isPlayingLocal = remember { mutableStateOf(false) }

    val isScreensaverEnabled by viewModel.isScreensaverEnabled.collectAsStateWithLifecycle()
    val screensaverInactivityTime by viewModel.screensaverInactivityTime.collectAsStateWithLifecycle()
    val screensaverSlideDuration by viewModel.screensaverSlideDuration.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        showScreensaver = false
        activity.resetInteractionTime()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        showScreensaver = false
    }

    LaunchedEffect(lastInteraction, isScreensaverEnabled, screensaverInactivityTime, showSettingsOverlay) {
        if (!showScreensaver && isScreensaverEnabled) {
            delay(screensaverInactivityTime.milliseconds)
            if (!isPlayingLocal.value && !showSettingsOverlay) {
                showScreensaver = true
            }
        } else if (!isScreensaverEnabled) {
            showScreensaver = false
        }
    }

    if (showScreensaver && isScreensaverEnabled) {
        ScreensaverScreen(
            slideDuration = screensaverSlideDuration,
            onDismiss = {
                showScreensaver = false
                activity.dispatchTouchEvent(
                    MotionEvent.obtain(
                        SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_DOWN,
                        0f, 0f, 0,
                    ),
                )
            },
        )
    }

    CompositionLocalProvider(
        LocalEventDispatcher provides eventDispatcher,
        LocalIsPlaying provides isPlayingLocal,
        LocalInteractionReset provides activity::resetInteractionTime,
    ) {
        TiwiScaffold(
            navigationTopBar = {
                AppNavigationBar(
                    currentRoute = currentRoute,
                    onRouteChanged = { route ->
                        if (currentRoute != route) {
                            viewModel.navigateToTab(route)
                        }
                    },
                    onScrollToTopRequested = {
                        eventDispatcher.tryDispatch(ScrollToTopEvent)
                    },
                    onBackClicked = { handleNavigateTo(null) },
                    onSettingsClicked = { viewModel.setShowSettingsOverlay(true) },
                    contentFocusRequester = contentFocusRequester,
                )
            },
        ) { paddingValues ->
            AppContent(
                backStack = backStack,
                transitionFocusRequester = transitionFocusRequester,
                contentFocusRequester = contentFocusRequester,
                onNavigateTo = handleNavigateTo,
                paddingValues = paddingValues,
            )
        }
    }

    if (showSettingsOverlay) {
        val isLoggedIn by viewModel.isLoggedIn.collectAsState()
        val preferredQuality by viewModel.preferredQuality.collectAsState()
        val autoPlayNextEpisode by viewModel.autoPlayNextEpisode.collectAsState()
        val initialAppearanceType by viewModel.initialAppearanceType.collectAsState()
        val initialAppearanceOffset by viewModel.initialAppearanceOffset.collectAsState()
        val secondaryAppearanceType by viewModel.secondaryAppearanceType.collectAsState()
        val secondaryAppearanceOffset by viewModel.secondaryAppearanceOffset.collectAsState()
        val secondaryTimerAmount by viewModel.secondaryTimerAmount.collectAsState()
        val initialAppearancePercentage by viewModel.initialAppearancePercentage.collectAsState()
        val secondaryAppearancePercentage by viewModel.secondaryAppearancePercentage.collectAsState()
        val isLektorEnabled by viewModel.isLektorEnabled.collectAsState()
        val lektorHideSubtitles by viewModel.lektorHideSubtitles.collectAsState()
        val lektorVoiceId by viewModel.lektorVoiceId.collectAsState()
        val lektorDuckingVolume by viewModel.lektorDuckingVolume.collectAsState()
        val lektorTimingOffsetMs by viewModel.lektorTimingOffsetMs.collectAsState()
        val lektorEdgeRate by viewModel.lektorEdgeRate.collectAsState()
        val lektorEdgePitch by viewModel.lektorEdgePitch.collectAsState()
        val lektorEdgeVolume by viewModel.lektorEdgeVolume.collectAsState()
        val lektorEdgeCalmMode by viewModel.lektorEdgeCalmMode.collectAsState()
        val downloadedLektorVoices by viewModel.downloadedLektorVoices.collectAsState()
        val lektorModelDownloadProgress by viewModel.lektorModelDownloadProgress.collectAsState()
        val isLektorModelExtracting by viewModel.isLektorModelExtracting.collectAsState()
        val systemVoices by viewModel.systemVoices.collectAsState()
        val contentRules by viewModel.contentRules.collectAsState()
        val loggedInUsername by viewModel.loggedInUsername.collectAsState()
        val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        AppOverlayMenu(
            isLoggedIn = isLoggedIn,
            loggedInUsername = loggedInUsername,
            preferredQuality = preferredQuality,
            autoPlayNextEpisode = autoPlayNextEpisode,
            appVersion = appVersion,
            initialAppearanceType = initialAppearanceType,
            initialAppearanceOffset = initialAppearanceOffset,
            secondaryAppearanceType = secondaryAppearanceType,
            secondaryAppearanceOffset = secondaryAppearanceOffset,
            secondaryTimerAmount = secondaryTimerAmount,
            initialAppearancePercentage = initialAppearancePercentage,
            secondaryAppearancePercentage = secondaryAppearancePercentage,
            isScreensaverEnabled = isScreensaverEnabled,
            screensaverInactivityTime = screensaverInactivityTime,
            screensaverSlideDuration = screensaverSlideDuration,
            isLektorEnabled = isLektorEnabled,
            lektorHideSubtitles = lektorHideSubtitles,
            lektorVoiceId = lektorVoiceId,
            lektorDuckingVolume = lektorDuckingVolume,
            lektorTimingOffsetMs = lektorTimingOffsetMs,
            lektorEdgeRate = lektorEdgeRate,
            lektorEdgePitch = lektorEdgePitch,
            lektorEdgeVolume = lektorEdgeVolume,
            lektorEdgeCalmMode = lektorEdgeCalmMode,
            downloadedLektorVoices = downloadedLektorVoices,
            lektorModelDownloadProgress = lektorModelDownloadProgress,
            isLektorModelExtracting = isLektorModelExtracting,
            systemVoices = systemVoices,
            contentRules = contentRules,
            onContentRuleChanged = viewModel::setContentRule,
            onDismissRequest = { viewModel.setShowSettingsOverlay(false) },
            onScreensaverEnabledToggled = viewModel::onScreensaverEnabledToggled,
            onScreensaverInactivityTimeChanged = viewModel::onScreensaverInactivityTimeChanged,
            onScreensaverSlideDurationChanged = viewModel::onScreensaverSlideDurationChanged,
            onInitialAppearanceTypeToggled = viewModel::setInitialAppearanceType,
            onInitialAppearanceOffsetToggled = viewModel::setInitialAppearanceOffset,
            onSecondaryAppearanceTypeToggled = viewModel::setSecondaryAppearanceType,
            onSecondaryAppearanceOffsetToggled = viewModel::setSecondaryAppearanceOffset,
            onSecondaryTimerAmountToggled = viewModel::setSecondaryTimerAmount,
            onInitialAppearancePercentageToggled = viewModel::setInitialAppearancePercentage,
            onSecondaryAppearancePercentageToggled = viewModel::setSecondaryAppearancePercentage,
            onToggleLektor = viewModel::onToggleLektor,
            onSetLektorHideSubtitles = viewModel::onSetLektorHideSubtitles,
            onSelectLektorVoice = viewModel::onSelectLektorVoice,
            onChangeLektorDuckingVolume = viewModel::onChangeLektorDuckingVolume,
            onChangeLektorTimingOffset = viewModel::onChangeLektorTimingOffset,
            onChangeLektorEdgeRate = viewModel::onChangeLektorEdgeRate,
            onChangeLektorEdgePitch = viewModel::onChangeLektorEdgePitch,
            onChangeLektorEdgeVolume = viewModel::onChangeLektorEdgeVolume,
            onChangeLektorEdgeCalmMode = viewModel::onChangeLektorEdgeCalmMode,
            onDownloadLektorModel = viewModel::downloadLektorModel,
            onDeleteLektorModel = viewModel::deleteLektorModel,
            onPreviewSystemVoice = viewModel::previewSystemVoice,
            onRefreshSystemVoices = viewModel::refreshSystemVoices,
            onTestVoice = viewModel::testVoice,
            onLogoutClicked = viewModel::onLogoutClicked,
            onSyncNowClicked = viewModel::onSyncNowClicked,
            onLoginClicked = {
                handleNavigateTo(Route.Login())
                viewModel.setShowSettingsOverlay(false)
            },
            onPreferredQualitySelected = viewModel::setPreferredQuality,
            onAutoPlayNextEpisodeToggled = viewModel::setAutoPlayNext,
            onWatchHistoryClicked = {
                handleNavigateTo(Route.WatchHistory)
                viewModel.setShowSettingsOverlay(false)
            },
            onFavoritesClicked = {
                handleNavigateTo(Route.Favorites)
                viewModel.setShowSettingsOverlay(false)
            },
        )
    }
}

@Composable
internal fun FilmanApp(
    viewModel: MainViewModel,
    activity: MainActivity,
) = TiwiApp(viewModel, activity)
