package com.pointlessapps.filman

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance
import com.pointlessapps.filman.data.model.SourcePriorityConfig
import com.pointlessapps.filman.ui.actor.ActorScreen
import com.pointlessapps.filman.ui.components.FilmanButton
import com.pointlessapps.filman.ui.components.FilmanNavigationBar
import com.pointlessapps.filman.ui.components.FilmanNavigationItem
import com.pointlessapps.filman.ui.components.FilmanOverlayClickScope
import com.pointlessapps.filman.ui.components.FilmanOverlayMenu
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.components.FilmanScaffold
import com.pointlessapps.filman.ui.core.Event
import com.pointlessapps.filman.ui.core.Event.ScrollToTopEvent
import com.pointlessapps.filman.ui.core.EventDispatcher
import com.pointlessapps.filman.ui.core.LocalEventDispatcher
import com.pointlessapps.filman.ui.core.LocalIsPlaying
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.ui.core.WebViewPointer
import com.pointlessapps.filman.ui.core.configureBrowserSession
import com.pointlessapps.filman.ui.core.performClickAtCoordinates
import com.pointlessapps.filman.ui.core.pointerMovement
import com.pointlessapps.filman.ui.core.rememberWebViewPointerState
import com.pointlessapps.filman.ui.details.MovieDetailsScreen
import com.pointlessapps.filman.ui.forkids.ForKidsScreen
import com.pointlessapps.filman.ui.home.HomeScreen
import com.pointlessapps.filman.ui.login.LoginScreen
import com.pointlessapps.filman.ui.movies.MoviesScreen
import com.pointlessapps.filman.ui.player.PlayerScreen
import com.pointlessapps.filman.ui.screensaver.ScreensaverScreen
import com.pointlessapps.filman.ui.search.SearchScreen
import com.pointlessapps.filman.ui.theme.spacing
import com.pointlessapps.filman.ui.tvshows.TvShowsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONException
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun FilmanApp(
    viewModel: MainViewModel,
    activity: MainActivity,
) {
    val backStack = viewModel.backStack
    val currentRoute = backStack.lastOrNull()
    val showSettingsOverlay by viewModel.showSettingsOverlay.collectAsState()
    val isTelevision = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION

    val transitionFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val eventDispatcher = remember { EventDispatcher() }

    val handleNavigateTo: (Route?) -> Unit =
        remember(backStack, transitionFocusRequester, viewModel) {
            {
                route ->
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

    LaunchedEffect(lastInteraction, isScreensaverEnabled, screensaverInactivityTime) {
        if (!showScreensaver && isScreensaverEnabled) {
            delay(screensaverInactivityTime.milliseconds)
            if (!isPlayingLocal.value) {
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
    ) {
        FilmanScaffold(
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
        val sourcesPriority by viewModel.sourcesPriority.collectAsState()
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
        val downloadedLektorVoices by viewModel.downloadedLektorVoices.collectAsState()
        val lektorModelDownloadProgress by viewModel.lektorModelDownloadProgress.collectAsState()
        val isLektorModelExtracting by viewModel.isLektorModelExtracting.collectAsState()
        val systemVoices by viewModel.systemVoices.collectAsState()
        val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        AppOverlayMenu(
            isLoggedIn = isLoggedIn,
            sourcesPriority = sourcesPriority,
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
            downloadedLektorVoices = downloadedLektorVoices,
            lektorModelDownloadProgress = lektorModelDownloadProgress,
            isLektorModelExtracting = isLektorModelExtracting,
            systemVoices = systemVoices,
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
            onDownloadLektorModel = viewModel::downloadLektorModel,
            onDeleteLektorModel = viewModel::deleteLektorModel,
            onPreviewSystemVoice = viewModel::previewSystemVoice,
            onRefreshSystemVoices = viewModel::refreshSystemVoices,
            onTestVoice = viewModel::testVoice,
            onLogoutClicked = viewModel::onLogoutClicked,
            onLoginClicked = {
                handleNavigateTo(Route.Login())
                viewModel.setShowSettingsOverlay(false)
            },
            onMoveSourceUp = viewModel::onMoveSourceUp,
            onMoveSourceDown = viewModel::onMoveSourceDown,
            onToggleSource = viewModel::onToggleSource,
            onMoveExtractorUp = viewModel::onMoveExtractorUp,
            onMoveExtractorDown = viewModel::onMoveExtractorDown,
            onToggleExtractor = viewModel::onToggleExtractor,
            onPreferredQualitySelected = viewModel::setPreferredQuality,
            onAutoPlayNextEpisodeToggled = viewModel::setAutoPlayNext,
            onClearCacheClicked = viewModel::clearCache,
            onClearWatchHistoryClicked = viewModel::clearWatchHistory,
            onClearSearchHistoryClicked = viewModel::clearSearchHistory,
            onWatchHistoryClicked = {
                handleNavigateTo(Route.WatchHistory)
                viewModel.setShowSettingsOverlay(false)
            },
        )
    }

    val userAgent by viewModel.userAgent.collectAsState()
    val activeChallenges by viewModel.activeChallenges.collectAsState()
    val visibleChallengeDomain by viewModel.visibleChallengeDomain.collectAsState()

    if (activeChallenges.isNotEmpty() && userAgent.isNotEmpty()) {
        val anyDialogVisible = visibleChallengeDomain != null
        BackHandler(enabled = anyDialogVisible) {
            viewModel.cancelChallenge(visibleChallengeDomain)
        }

        if (!anyDialogVisible) {
            val toastMessage = when {
                activeChallenges.keys.any { it.contains("zaluknij") } && activeChallenges.keys.any { it.contains("cda") } ->
                    stringResource(R.string.search_verifying_cloudflare)
                activeChallenges.keys.any { it.contains("zaluknij") } ->
                    stringResource(R.string.verifying_cloudflare_zaluknij)
                activeChallenges.keys.any { it.contains("cda") } ->
                    stringResource(R.string.verifying_cloudflare_cda_hd)
                else ->
                    stringResource(R.string.verifying_cloudflare_filman)
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = MaterialTheme.spacing.extraLarge)
                        .zIndex(20f),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier =
                        Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                                shape = MaterialTheme.shapes.medium,
                            ).padding(
                                horizontal = MaterialTheme.spacing.medium,
                                vertical = MaterialTheme.spacing.small,
                            ),
                ) {
                    Text(
                        text = toastMessage,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        activeChallenges.values.forEach { currentChallenge ->
            key(currentChallenge.targetUrl) {
                val targetUrl = currentChallenge.targetUrl
                val challengeTitle = currentChallenge.title
                val latestChallenge by rememberUpdatedState(currentChallenge)
                val isChallengeDialogVisible = visibleChallengeDomain == targetUrl
                val latestVisibleChallengeDomain by rememberUpdatedState(visibleChallengeDomain)

                var webViewInstance by remember(targetUrl) { mutableStateOf<WebView?>(null) }
                var hasPageFinished by remember(targetUrl) { mutableStateOf(false) }
                var isCompleted by remember(targetUrl) { mutableStateOf(false) }
                var needsWebView by remember(targetUrl) { mutableStateOf(false) }
                var challengeCentered by remember(targetUrl) { mutableStateOf(false) }
                var showSlowVerificationHint by remember(targetUrl) { mutableStateOf(false) }
                var pageGeneration by remember(targetUrl) { mutableIntStateOf(0) }
                val pageEvents = remember(targetUrl) { Channel<VerificationPageEvent>(Channel.CONFLATED) }
                var boxWidth by remember { mutableIntStateOf(0) }
                var boxHeight by remember { mutableIntStateOf(0) }
                val challengeFocusRequester = remember { FocusRequester() }
                val skipButtonFocusRequester = remember { FocusRequester() }
                val pointerState = rememberWebViewPointerState()

                val completeChallenge: suspend (String) -> Unit = remember(targetUrl) {
                    { cookies ->
                        if (!isCompleted) {
                            isCompleted = true
                            CookieManager.getInstance().flush()
                            if (viewModel.onChallengeSolved(targetUrl, cookies) && !latestChallenge.isStartup) {
                                eventDispatcher.tryDispatch(Event.ReloadData)
                            }
                        }
                    }
                }

                LaunchedEffect(isChallengeDialogVisible) {
                    showSlowVerificationHint = false
                    if (isChallengeDialogVisible) {
                        delay(45_000L)
                        showSlowVerificationHint = true
                    }
                }

                LaunchedEffect(isChallengeDialogVisible, needsWebView) {
                    if (isChallengeDialogVisible && needsWebView) {
                        androidx.compose.runtime.withFrameNanos { }
                        if (!challengeFocusRequester.requestFocus()) {
                            Log.w("FilmanApp", "Could not focus the verification controls for $targetUrl")
                        } else if (isTelevision && !challengeFocusRequester.captureFocus()) {
                            Log.w("FilmanApp", "Could not retain verification focus for $targetUrl")
                        }
                    }
                }

                LaunchedEffect(targetUrl) {
                    var nextVerificationAt = 0L
                    var nextCookieProbeAt = 0L
                    var waitingForOtherDialog = 0L
                    var lastDomStatus: String? = null
                    var stage = "native probe"
                    var clearanceTracker: ClearanceCookieTracker? = null
                    if (BuildConfig.DEBUG) Log.d("FilmanApp", "Verification started: $targetUrl, startup=${currentChallenge.isStartup}")
                    if (probeExistingProviderSession(
                            verify = { verifyCurrentProviderSession({ latestChallenge }, viewModel::verifyChallenge) },
                            onVerified = { completeChallenge("") },
                            onWebViewRequired = {
                                clearanceTracker = ClearanceCookieTracker(CookieManager.getInstance().getCookie(targetUrl))
                                needsWebView = true
                            },
                        )
                    ) {
                        return@LaunchedEffect
                    }
                    stage = "WebView creation"
                    val startTime = SystemClock.elapsedRealtime()
                    var previousTime = startTime
                    while (!isCompleted) {
                        val now = SystemClock.elapsedRealtime()
                        if (latestVisibleChallengeDomain != null && latestVisibleChallengeDomain != targetUrl) {
                            waitingForOtherDialog += now - previousTime
                        }
                        previousTime = now
                        val elapsed = now - startTime - waitingForOtherDialog
                        try {
                            val webView = webViewInstance
                            if (webView != null) {
                                val tracker = clearanceTracker
                                if (tracker != null && now >= nextCookieProbeAt &&
                                    tracker.hasNewClearance(CookieManager.getInstance().getCookie(targetUrl))
                                ) {
                                    stage = "native cookie probe"
                                    val verifiedCookies = verifyNewClearance(
                                        tracker = tracker,
                                        readCookies = { CookieManager.getInstance().getCookie(targetUrl) },
                                        verify = { verifyCurrentProviderSession({ latestChallenge }, viewModel::verifyChallenge) },
                                    )
                                    if (verifiedCookies != null) {
                                        completeChallenge(verifiedCookies)
                                        break
                                    }
                                    nextCookieProbeAt = SystemClock.elapsedRealtime() + 3_000L
                                    nextVerificationAt = nextCookieProbeAt
                                    Log.w("FilmanApp", "New provider clearance was not accepted by the native probe: $targetUrl")
                                }
                                stage = "WebView status"
                                val domStatus = withTimeoutOrNull(2_000L) { webView.awaitChallengeStatus() } ?: "loading"
                                if (BuildConfig.DEBUG && domStatus != lastDomStatus) {
                                    Log.d("FilmanApp", "Verification state: $targetUrl, dom=$domStatus, pageFinished=$hasPageFinished")
                                    webView.logVerificationDiagnostics(targetUrl, "state")
                                }
                                if (domStatus == "clear" && lastDomStatus != "clear") nextVerificationAt = 0L
                                lastDomStatus = domStatus
                                if (shouldShowChallengeDialog(domStatus, elapsed) && latestVisibleChallengeDomain == null) {
                                    viewModel.setChallengeDialogVisible(true, targetUrl)
                                }
                                if (latestVisibleChallengeDomain == targetUrl && !challengeCentered && domStatus != "clear") {
                                    val generation = pageGeneration
                                    val centered = withTimeoutOrNull(2_000L) { webView.centerChallengeControl() } == true
                                    if (generation == pageGeneration) challengeCentered = centered
                                }
                                if (hasPageFinished && domStatus == "clear" && now >= nextVerificationAt) {
                                    nextVerificationAt = now + 3_000L
                                    stage = "native probe"
                                    if (BuildConfig.DEBUG) Log.d("FilmanApp", "Native verification started: $targetUrl")
                                    val verified = verifyCurrentProviderSession({ latestChallenge }, viewModel::verifyChallenge)
                                    if (BuildConfig.DEBUG) Log.d("FilmanApp", "Native verification result: $targetUrl, verified=$verified")
                                    if (verified) {
                                        completeChallenge(CookieManager.getInstance().getCookie(targetUrl).orEmpty())
                                        break
                                    }
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w("FilmanApp", "Challenge verification failed", e)
                        }

                        if (latestVisibleChallengeDomain == null && elapsed > CHALLENGE_TOTAL_TIMEOUT_MS && webViewInstance == null) {
                            Log.w("FilmanApp", "Provider verification timed out at $stage: $targetUrl")
                            viewModel.onChallengeTimedOut(targetUrl)
                            if (!latestChallenge.isStartup) {
                                Toast.makeText(
                                    activity,
                                    activity.getString(
                                        R.string.cloudflare_verification_timed_out,
                                        targetUrl.removePrefix("https://").trimEnd('/'),
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            break
                        }
                        if (withTimeoutOrNull(1_000L) { pageEvents.receive() } == VerificationPageEvent.PAGE) {
                            nextVerificationAt = 0L
                        }
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .zIndex(if (isChallengeDialogVisible) 10f else -1f)
                            .alpha(if (isChallengeDialogVisible) 1f else 0.01f)
                            .then(
                                if (isChallengeDialogVisible) {
                                    Modifier.background(MaterialTheme.colorScheme.surface)
                                } else {
                                    Modifier
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            if (isChallengeDialogVisible) {
                                Modifier
                                    .fillMaxSize()
                                    .padding(MaterialTheme.spacing.medium)
                            } else {
                                Modifier.fillMaxSize()
                            },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (isChallengeDialogVisible) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = challengeTitle,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.titleMedium,
                                                )
                                                Spacer(modifier = Modifier.size(MaterialTheme.spacing.extraSmall))
                                                Text(
                                                    text = stringResource(R.string.cloudflare_verification_remote_hint),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                                if (showSlowVerificationHint) {
                                                    Spacer(modifier = Modifier.size(MaterialTheme.spacing.extraSmall))
                                                    Text(
                                                        text = stringResource(R.string.cloudflare_verification_slow_hint),
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                }
                                            }
                                            FilmanButton(
                                                modifier =
                                                    Modifier
                                                        .focusRequester(skipButtonFocusRequester)
                                                        .onPreviewKeyEvent { event ->
                                                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                                                if (challengeFocusRequester.requestFocus() && isTelevision) {
                                                                    challengeFocusRequester.captureFocus()
                                                                }
                                                                true
                                                            } else {
                                                                false
                                                            }
                                                        },
                                                text = stringResource(R.string.cloudflare_verification_skip),
                                                iconRes = null,
                                                onClick = { viewModel.cancelChallenge(targetUrl) },
                                            )
                                        }
                                        Spacer(modifier = Modifier.size(MaterialTheme.spacing.small))
                                    }
                                }
                            }
                            key(targetUrl) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .onSizeChanged { size ->
                                                boxWidth = size.width
                                                boxHeight = size.height
                                            }.pointerMovement(
                                                state = pointerState,
                                                boxWidthProvider = { boxWidth },
                                                boxHeightProvider = { boxHeight },
                                                onScrollRequested = { webViewInstance?.scrollBy(0, it) },
                                                onClickRequested = { x, y -> performClickAtCoordinates(webViewInstance, x, y) },
                                                enabled = isChallengeDialogVisible,
                                                onNavigateUp = {
                                                    if (isChallengeDialogVisible) {
                                                        if (isTelevision) challengeFocusRequester.freeFocus()
                                                        runCatching { skipButtonFocusRequester.requestFocus() }
                                                    }
                                                },
                                            ).focusRequester(challengeFocusRequester)
                                            .focusable(enabled = isChallengeDialogVisible),
                                ) {
                                    if (needsWebView) AndroidView(
                                        modifier = Modifier.fillMaxSize().then(
                                            if (isChallengeDialogVisible) Modifier.background(Color.White) else Modifier,
                                        ),
                                        factory = { ctx ->
                                            WebView(ctx).apply {
                                                layoutParams =
                                                    android.view.ViewGroup.LayoutParams(
                                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                    )
                                                configureBrowserSession(userAgent)
                                                @Suppress("DEPRECATION")
                                                settings.databaseEnabled = true
                                                settings.useWideViewPort = true
                                                settings.loadWithOverviewMode = true
                                                settings.textZoom = 120
                                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                                isFocusable = isChallengeDialogVisible && !isTelevision
                                                isFocusableInTouchMode = isChallengeDialogVisible && !isTelevision
                                                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                                                CookieManager.getInstance().setAcceptCookie(true)
                                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                                if (BuildConfig.DEBUG) {
                                                    Log.d("FilmanApp", "Verification WebView: $targetUrl, javascript=${settings.javaScriptEnabled}, domStorage=${settings.domStorageEnabled}, cookies=${CookieManager.getInstance().acceptCookie()}, thirdPartyCookies=${CookieManager.getInstance().acceptThirdPartyCookies(this)}")
                                                }
                                                addJavascriptInterface(object {
                                                    @JavascriptInterface
                                                    fun onPageChanged() {
                                                        pageEvents.trySend(VerificationPageEvent.DOM)
                                                    }
                                                }, "FilmanVerification")

                                                setOnKeyListener { _, keyCode, keyEvent ->
                                                    if (isChallengeDialogVisible && keyCode == KeyEvent.KEYCODE_BACK && keyEvent.action == KeyEvent.ACTION_UP) {
                                                        viewModel.cancelChallenge(targetUrl)
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                }

                                                webViewClient =
                                                    object : WebViewClient() {
                                                        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                                                            Log.w("FilmanApp", "Verification onRenderProcessGone for $targetUrl")
                                                            return true
                                                        }

                                                        override fun onPageStarted(
                                                            view: WebView?,
                                                            url: String?,
                                                            favicon: Bitmap?,
                                                        ) {
                                                            super.onPageStarted(view, url, favicon)
                                                            if (BuildConfig.DEBUG) Log.d("FilmanApp", "Verification page started: $targetUrl")
                                                            hasPageFinished = false
                                                            challengeCentered = false
                                                            pageGeneration++
                                                            pageEvents.trySend(VerificationPageEvent.PAGE)
                                                        }

                                                        override fun onPageFinished(
                                                            view: WebView,
                                                            url: String,
                                                        ) {
                                                            super.onPageFinished(view, url)
                                                            hasPageFinished = true
                                                            view.evaluateJavascript(CHALLENGE_OBSERVER_SCRIPT, null)
                                                            if (BuildConfig.DEBUG) view.logVerificationDiagnostics(targetUrl, "page-finished")
                                                            pageEvents.trySend(VerificationPageEvent.PAGE)
                                                            if (BuildConfig.DEBUG) Log.d("FilmanApp", "Verification page finished: $targetUrl")
                                                        }

                                                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                                            super.onReceivedError(view, request, error)
                                                            if (BuildConfig.DEBUG && request.isForMainFrame) {
                                                                Log.w("FilmanApp", "Verification main-frame error: $targetUrl, code=${error.errorCode}")
                                                            }
                                                        }

                                                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                                            super.onReceivedHttpError(view, request, response)
                                                            if (BuildConfig.DEBUG && request.isForMainFrame) {
                                                                Log.d("FilmanApp", "Verification main-frame HTTP: $targetUrl, status=${response.statusCode}")
                                                            }
                                                        }

                                                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                                            if (BuildConfig.DEBUG) {
                                                                val host = Uri.parse(error.url).host.orEmpty()
                                                                val category = when {
                                                                    host == Uri.parse(targetUrl).host -> "provider"
                                                                    host.endsWith(".dnstest.challenges.cloudflare.com") -> "challenge-dns-test"
                                                                    host == "challenges.cloudflare.com" -> "challenge"
                                                                    else -> "other"
                                                                }
                                                                Log.w("FilmanApp", "Verification TLS error: $targetUrl, category=$category, code=${error.primaryError}")
                                                            }
                                                            super.onReceivedSslError(view, handler, error)
                                                        }
                                                    }
                                                val request = latestChallenge.verificationRequest
                                                loadUrl(verificationPageUrl(targetUrl, request))
                                                webViewInstance = this
                                                pageEvents.trySend(VerificationPageEvent.PAGE)
                                            }
                                        },
                                        update = { view ->
                                            view.isFocusable = isChallengeDialogVisible && !isTelevision
                                            view.isFocusableInTouchMode = isChallengeDialogVisible && !isTelevision
                                        },
                                        onRelease = { view ->
                                            view.stopLoading()
                                            view.destroy()
                                            webViewInstance = null
                                        },
                                    )
                                    WebViewPointer(pointerState, enabled = isChallengeDialogVisible)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val BACKGROUND_CHALLENGE_GRACE_MS = 6_000L
private const val UNINSPECTABLE_CHALLENGE_GRACE_MS = 12_000L
private const val CHALLENGE_TOTAL_TIMEOUT_MS = 35_000L
private enum class VerificationPageEvent { PAGE, DOM }

private fun WebView.logVerificationDiagnostics(targetUrl: String, event: String) {
    evaluateJavascript(
        """
        (function() {
            var navigation = performance.getEntriesByType('navigation')[0];
            return [
                document.readyState === 'complete',
                document.visibilityState === 'visible',
                window.isSecureContext === true,
                navigator.cookieEnabled === true,
                !!window._cf_chl_opt || !!document.querySelector('#challenge-form, #challenge-stage, #cf-challenge-running'),
                !!document.querySelector('iframe[src*="challenges.cloudflare.com"], .cf-turnstile'),
                !!document.querySelector('#challenge-error-text'),
                !!navigation && navigation.type === 'reload'
            ];
        })();
        """.trimIndent(),
    ) { result ->
        if (result == null || result.length > 512) {
            Log.w("FilmanApp", "Verification diagnostics unavailable: $targetUrl, event=$event")
            return@evaluateJavascript
        }
        try {
            val values = JSONArray(result)
            if (values.length() != 8 || (0 until values.length()).any { values.opt(it) !is Boolean }) {
                Log.w("FilmanApp", "Verification diagnostics unavailable: $targetUrl, event=$event")
            } else {
                Log.d("FilmanApp", "Verification diagnostics: $targetUrl, event=$event, ready=${values.getBoolean(0)}, visible=${values.getBoolean(1)}, secure=${values.getBoolean(2)}, cookiesEnabled=${values.getBoolean(3)}, challenge=${values.getBoolean(4)}, recognizedChallengeFrame=${values.getBoolean(5)}, errorElement=${values.getBoolean(6)}, reload=${values.getBoolean(7)}")
            }
        } catch (_: JSONException) {
            Log.w("FilmanApp", "Verification diagnostics unavailable: $targetUrl, event=$event")
        }
    }
}

private suspend fun WebView.centerChallengeControl(): Boolean =
    suspendCancellableCoroutine { continuation ->
        evaluateJavascript(
            """
            (function() {
                var controls = document.querySelectorAll(
                    '#challenge-stage input[type="checkbox"], #challenge-form input[type="checkbox"], ' +
                    '#cf-challenge-running input[type="checkbox"], ' +
                    'iframe[title*="verify you are human" i], iframe[title*="verify you are a human" i], ' +
                    'iframe[title*="verification checkbox" i], iframe[src*="challenges.cloudflare.com"]'
                );
                for (var i = 0; i < controls.length; i++) {
                    var control = controls[i];
                    var style = window.getComputedStyle(control);
                    var rect = control.getBoundingClientRect();
                    if (style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0) {
                        control.scrollIntoView({block: 'center', inline: 'center'});
                        return true;
                    }
                }
                return false;
            })();
            """.trimIndent(),
        ) { result ->
            if (continuation.isActive) continuation.resumeWith(Result.success(result == "true"))
        }
    }

private const val CHALLENGE_OBSERVER_SCRIPT = """
    (function() {
        if (window.__filmanVerificationObserver || !document.documentElement) return;
        var timer = null;
        window.__filmanVerificationObserver = new MutationObserver(function() {
            if (timer !== null) return;
            timer = setTimeout(function() {
                timer = null;
                if (window.FilmanVerification) window.FilmanVerification.onPageChanged();
            }, 400);
        });
        window.__filmanVerificationObserver.observe(document.documentElement, {
            childList: true, subtree: true
        });
    })();
"""

internal fun shouldShowChallengeDialog(domStatus: String, elapsedMs: Long): Boolean =
    (domStatus == "interactive" && elapsedMs >= BACKGROUND_CHALLENGE_GRACE_MS) ||
        (domStatus == "challenge" && elapsedMs >= UNINSPECTABLE_CHALLENGE_GRACE_MS) ||
        elapsedMs >= CHALLENGE_TOTAL_TIMEOUT_MS

private suspend fun WebView.awaitChallengeStatus(): String =
    suspendCancellableCoroutine { continuation ->
        evaluateJavascript(
            """
            (function() {
                var title = (document.title || '').toLowerCase();
                function visible(element) {
                    if (!element) return false;
                    var style = window.getComputedStyle(element);
                    var rect = element.getBoundingClientRect();
                    return style.display !== 'none' && style.visibility !== 'hidden' &&
                        rect.width > 0 && rect.height > 0;
                }
                var checkbox = document.querySelector(
                    '#challenge-stage input[type="checkbox"], #challenge-form input[type="checkbox"], ' +
                    '#cf-challenge-running input[type="checkbox"]'
                );
                var frame = document.querySelector(
                    'iframe[title*="verify you are human" i], iframe[title*="verify you are a human" i], ' +
                    'iframe[title*="verification checkbox" i]'
                );
                if (visible(checkbox) || visible(frame)) return 'interactive';
                var managedChallenge = title.indexOf('just a moment') !== -1 ||
                    title.indexOf('attention required') !== -1 || title.indexOf('cloudflare') !== -1 ||
                    window._cf_chl_opt ||
                    document.querySelector('#challenge-stage, #challenge-form, #cf-challenge-running, #challenge-error-text');
                if (managedChallenge) {
                    var buttons = document.querySelectorAll('button, input[type="button"], input[type="submit"]');
                    for (var i = 0; i < buttons.length; i++) {
                        if (visible(buttons[i])) return 'interactive';
                    }
                    return 'challenge';
                }
                if (document.querySelector('iframe[src*="challenges.cloudflare.com"], .cf-turnstile')) return 'challenge';
                if (document.readyState === 'complete' && document.body && document.body.innerText.trim().length > 0) {
                    return 'clear';
                }
                return 'loading';
            })();
            """.trimIndent(),
        ) { result ->
            if (continuation.isActive) {
                continuation.resumeWith(Result.success(result?.removeSurrounding("\"") ?: "loading"))
            }
        }
    }

@Composable
internal fun AppNavigationBar(
    currentRoute: Route?,
    onRouteChanged: (Route) -> Unit,
    onScrollToTopRequested: () -> Unit,
    onBackClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    contentFocusRequester: FocusRequester,
) {
    if (currentRoute?.showNavigationBar == true || currentRoute?.showBackButton == true) {
        FilmanNavigationBar(
            currentRouteProvider = { currentRoute },
            onRouteChanged = onRouteChanged,
            onScrollToTopRequested = onScrollToTopRequested,
            items =
                if (currentRoute.showBackButton) {
                    listOf(FilmanNavigationItem.Back)
                } else {
                    listOf(
                        FilmanNavigationItem.Icon(
                            icon = R.drawable.ic_search,
                            contentDescription = R.string.common_search,
                            route = Route.Search,
                        ),
                        FilmanNavigationItem.Text(
                            title = R.string.common_tab_home,
                            route = Route.Home,
                        ),
                        FilmanNavigationItem.Text(
                            title = R.string.common_tab_movies,
                            route = Route.Movies,
                        ),
                        FilmanNavigationItem.Text(
                            title = R.string.common_tab_series,
                            route = Route.TvShows,
                        ),
                        FilmanNavigationItem.Text(
                            title = R.string.common_tab_kids,
                            route = Route.ForKids,
                        ),
                    )
                },
            onItemClicked = {
                when {
                    it === FilmanNavigationItem.Back -> onBackClicked()
                    it === FilmanNavigationItem.Settings -> onSettingsClicked()
                    else -> runCatching { contentFocusRequester.requestFocus() }
                }
            },
            contentFocusRequester = contentFocusRequester,
            showSettingsItem = !currentRoute.showBackButton,
        )
    }
}

@Composable
internal fun AppContent(
    backStack: List<Any>,
    transitionFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    onNavigateTo: (Route?) -> Unit,
    paddingValues: PaddingValues,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .size(1.dp)
                    .focusRequester(transitionFocusRequester)
                    .focusable(),
        )

        NavDisplay(
            backStack = backStack,
            onBack = { onNavigateTo(null) },
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider =
                entryProvider {
                    entry<Route.Login> { route ->
                        LoginScreen(
                            returnRoute = route.returnRoute,
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                        )
                    }
                    entry<Route.Home> {
                        HomeScreen(
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.Search> {
                        SearchScreen(
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.Movies> {
                        MoviesScreen(
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.TvShows> {
                        TvShowsScreen(
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.ForKids> {
                        ForKidsScreen(
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.WatchHistory> {
                        com.pointlessapps.filman.ui.watchhistory.WatchHistoryScreen(
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.Details> { route ->
                        MovieDetailsScreen(
                            request = route.request,
                            autoPlay = route.autoPlay,
                            episodeUrl = route.episodeUrl,
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.Actor> { route ->
                        ActorScreen(
                            actorUrl = route.url,
                            onNavigateTo = onNavigateTo,
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.Player> { route ->
                        PlayerScreen(
                            url = route.url,
                            onNavigateTo = onNavigateTo,
                        )
                    }
                },
        )
    }
}

@Composable
internal fun AppOverlayMenu(
    isLoggedIn: Boolean,
    sourcesPriority: List<SourcePriorityConfig>,
    preferredQuality: String,
    autoPlayNextEpisode: Boolean,
    appVersion: String,
    initialAppearanceType: NextEpisodeAppearance,
    initialAppearanceOffset: Long,
    secondaryAppearanceType: NextEpisodeAppearance,
    secondaryAppearanceOffset: Long,
    secondaryTimerAmount: Long,
    initialAppearancePercentage: Long,
    secondaryAppearancePercentage: Long,
    isScreensaverEnabled: Boolean,
    screensaverInactivityTime: Long,
    screensaverSlideDuration: Long,
    isLektorEnabled: Boolean,
    lektorHideSubtitles: Boolean,
    lektorVoiceId: String,
    lektorDuckingVolume: Float,
    lektorTimingOffsetMs: Int,
    lektorEdgeRate: String = com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions.DEFAULT_RATE,
    lektorEdgePitch: String = com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions.DEFAULT_PITCH,
    lektorEdgeVolume: String = com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions.DEFAULT_VOLUME,
    downloadedLektorVoices: Set<String>,
    lektorModelDownloadProgress: Float?,
    isLektorModelExtracting: Boolean,
    systemVoices: com.pointlessapps.filman.player.lektor.engine.SystemVoiceList,
    onDismissRequest: () -> Unit,
    onInitialAppearanceTypeToggled: (NextEpisodeAppearance) -> Unit,
    onInitialAppearanceOffsetToggled: (Long) -> Unit,
    onSecondaryAppearanceTypeToggled: (NextEpisodeAppearance) -> Unit,
    onSecondaryAppearanceOffsetToggled: (Long) -> Unit,
    onSecondaryTimerAmountToggled: (Long) -> Unit,
    onInitialAppearancePercentageToggled: (Long) -> Unit,
    onSecondaryAppearancePercentageToggled: (Long) -> Unit,
    onScreensaverEnabledToggled: (Boolean) -> Unit,
    onScreensaverInactivityTimeChanged: (Long) -> Unit,
    onScreensaverSlideDurationChanged: (Long) -> Unit,
    onToggleLektor: (Boolean) -> Unit,
    onSetLektorHideSubtitles: (Boolean) -> Unit,
    onSelectLektorVoice: (String) -> Unit,
    onChangeLektorDuckingVolume: (Float) -> Unit,
    onChangeLektorTimingOffset: (Int) -> Unit,
    onChangeLektorEdgeRate: (String) -> Unit = {},
    onChangeLektorEdgePitch: (String) -> Unit = {},
    onChangeLektorEdgeVolume: (String) -> Unit = {},
    onDownloadLektorModel: (String) -> Unit,
    onDeleteLektorModel: (String) -> Unit,
    onPreviewSystemVoice: (String?) -> Unit,
    onRefreshSystemVoices: () -> Unit,
    onTestVoice: () -> Unit = {},
    onLogoutClicked: () -> Unit,
    onLoginClicked: () -> Unit,
    onMoveSourceUp: (Int) -> Unit,
    onMoveSourceDown: (Int) -> Unit,
    onToggleSource: (Int) -> Unit,
    onMoveExtractorUp: (Int, Int) -> Unit,
    onMoveExtractorDown: (Int, Int) -> Unit,
    onToggleExtractor: (Int, Int) -> Unit,
    onPreferredQualitySelected: (String) -> Unit,
    onAutoPlayNextEpisodeToggled: (Boolean) -> Unit,
    onClearCacheClicked: () -> Unit,
    onClearWatchHistoryClicked: () -> Unit,
    onClearSearchHistoryClicked: () -> Unit,
    onWatchHistoryClicked: FilmanOverlayClickScope.() -> Unit,
) {
    val items = mutableListOf<FilmanOverlayMenuItem>()

    items.addAll(
        getPlaybackSettings(
            sourcesPriority,
            preferredQuality,
            autoPlayNextEpisode,
            initialAppearanceType,
            initialAppearanceOffset,
            secondaryAppearanceType,
            secondaryAppearanceOffset,
            secondaryTimerAmount,
            initialAppearancePercentage,
            secondaryAppearancePercentage,
            isLektorEnabled,
            lektorHideSubtitles,
            lektorVoiceId,
            lektorDuckingVolume,
            lektorTimingOffsetMs,
            lektorEdgeRate,
            lektorEdgePitch,
            lektorEdgeVolume,
            downloadedLektorVoices,
            lektorModelDownloadProgress,
            isLektorModelExtracting,
            systemVoices,
            onInitialAppearanceTypeToggled,
            onInitialAppearanceOffsetToggled,
            onSecondaryAppearanceTypeToggled,
            onSecondaryAppearanceOffsetToggled,
            onSecondaryTimerAmountToggled,
            onInitialAppearancePercentageToggled,
            onSecondaryAppearancePercentageToggled,
            onMoveSourceUp,
            onMoveSourceDown,
            onToggleSource,
            onMoveExtractorUp,
            onMoveExtractorDown,
            onToggleExtractor,
            onPreferredQualitySelected,
            onAutoPlayNextEpisodeToggled,
            onToggleLektor,
            onSetLektorHideSubtitles,
            onSelectLektorVoice,
            onChangeLektorDuckingVolume,
            onChangeLektorTimingOffset,
            onChangeLektorEdgeRate,
            onChangeLektorEdgePitch,
            onChangeLektorEdgeVolume,
            onDownloadLektorModel,
            onDeleteLektorModel,
            onPreviewSystemVoice,
            onRefreshSystemVoices,
            onTestVoice,
        ),
    )

    items.add(
        FilmanOverlayMenuItem.Header(
            id = "data_header",
            label = TextValue.StringResource(R.string.overlay_menu_header_data),
        ),
    )

    items.add(
        FilmanOverlayMenuItem.Button(
            id = "watch_history",
            label = TextValue.StringResource(R.string.overlay_menu_watch_history),
            onClick = onWatchHistoryClicked,
        ),
    )

    items.add(
        FilmanOverlayMenuItem.NestedMenu(
            id = "clear_cache",
            label = TextValue.StringResource(R.string.overlay_menu_clear_cache),
            value = null,
            items =
                listOf(
                    FilmanOverlayMenuItem.Header(
                        id = "clear_cache_header",
                        label = TextValue.StringResource(R.string.overlay_menu_are_you_sure),
                    ),
                    FilmanOverlayMenuItem.Button(
                        id = "clear_cache_yes",
                        label = TextValue.StringResource(R.string.overlay_menu_yes),
                        onClick = {
                            onClearCacheClicked()
                            popBack()
                        },
                    ),
                    FilmanOverlayMenuItem.Button(
                        id = "clear_cache_no",
                        label = TextValue.StringResource(R.string.overlay_menu_no),
                        onClick = { popBack() },
                    ),
                ),
        ),
    )

    items.add(
        FilmanOverlayMenuItem.NestedMenu(
            id = "clear_watch_history",
            label = TextValue.StringResource(R.string.overlay_menu_clear_watch_history),
            value = null,
            items =
                listOf(
                    FilmanOverlayMenuItem.Header(
                        id = "clear_watch_history_header",
                        label = TextValue.StringResource(R.string.overlay_menu_are_you_sure),
                    ),
                    FilmanOverlayMenuItem.Button(
                        id = "clear_watch_history_yes",
                        label = TextValue.StringResource(R.string.overlay_menu_yes),
                        onClick = {
                            onClearWatchHistoryClicked()
                            popBack()
                        },
                    ),
                    FilmanOverlayMenuItem.Button(
                        id = "clear_watch_history_no",
                        label = TextValue.StringResource(R.string.overlay_menu_no),
                        onClick = { popBack() },
                    ),
                ),
        ),
    )

    items.add(
        FilmanOverlayMenuItem.NestedMenu(
            id = "clear_search_history",
            label = TextValue.StringResource(R.string.overlay_menu_clear_search_history),
            value = null,
            items =
                listOf(
                    FilmanOverlayMenuItem.Header(
                        id = "clear_search_history_header",
                        label = TextValue.StringResource(R.string.overlay_menu_are_you_sure),
                    ),
                    FilmanOverlayMenuItem.Button(
                        id = "clear_search_history_yes",
                        label = TextValue.StringResource(R.string.overlay_menu_yes),
                        onClick = {
                            onClearSearchHistoryClicked()
                            popBack()
                        },
                    ),
                    FilmanOverlayMenuItem.Button(
                        id = "clear_search_history_no",
                        label = TextValue.StringResource(R.string.overlay_menu_no),
                        onClick = { popBack() },
                    ),
                ),
        ),
    )

    items.add(
        FilmanOverlayMenuItem.Header(
            id = "other_header",
            label = TextValue.StringResource(R.string.overlay_menu_header_other),
        ),
    )

    items.buildScreensaverSettings(
        isScreensaverEnabled,
        screensaverInactivityTime,
        screensaverSlideDuration,
        onScreensaverEnabledToggled,
        onScreensaverInactivityTimeChanged,
        onScreensaverSlideDurationChanged,
    )

    if (isLoggedIn) {
        items.add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "logout",
                label = TextValue.StringResource(R.string.overlay_menu_logout),
                value = null,
                items =
                    listOf(
                        FilmanOverlayMenuItem.Header(
                            id = "logout_header",
                            label = TextValue.StringResource(R.string.overlay_menu_are_you_sure),
                        ),
                        FilmanOverlayMenuItem.Button(
                            id = "logout_yes",
                            label = TextValue.StringResource(R.string.overlay_menu_yes),
                            onClick = {
                                onLogoutClicked()
                                popBack()
                            },
                        ),
                        FilmanOverlayMenuItem.Button(
                            id = "logout_no",
                            label = TextValue.StringResource(R.string.overlay_menu_no),
                            onClick = { popBack() },
                        ),
                    ),
            ),
        )
    } else {
        items.add(
            FilmanOverlayMenuItem.Button(
                id = "login",
                label = TextValue.StringResource(R.string.overlay_menu_login),
                onClick = { onLoginClicked() },
            ),
        )
    }

    val versionText = stringResource(R.string.overlay_menu_app_version)
    items.add(
        FilmanOverlayMenuItem.Footer(
            id = "footer_version",
            label = TextValue.DynamicString("$versionText $appVersion"),
        ),
    )

    FilmanOverlayMenu(
        title = TextValue.StringResource(R.string.overlay_menu_settings),
        onDismissRequest = onDismissRequest,
        items = items,
    )
}
