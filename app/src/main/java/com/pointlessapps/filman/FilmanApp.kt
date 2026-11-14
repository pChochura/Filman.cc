package com.pointlessapps.filman

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.config.ZaluknijConfig.CLOUDFLARE_COOKIE
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
import com.pointlessapps.filman.ui.core.performClickAtCoordinates
import com.pointlessapps.filman.ui.core.pointerMovement
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
import kotlinx.coroutines.suspendCancellableCoroutine
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
                val isChallengeDialogVisible = visibleChallengeDomain == targetUrl
                val latestVisibleChallengeDomain by rememberUpdatedState(visibleChallengeDomain)

                var webViewInstance by remember(targetUrl) { mutableStateOf<WebView?>(null) }
                var hasPageFinished by remember(targetUrl) { mutableStateOf(false) }
                var isCompleted by remember(targetUrl) { mutableStateOf(false) }
                var boxWidth by remember { mutableIntStateOf(0) }
                var boxHeight by remember { mutableIntStateOf(0) }
                val challengeFocusRequester = remember { FocusRequester() }
                val skipButtonFocusRequester = remember { FocusRequester() }

                val completeChallenge: suspend (String) -> Unit = remember(targetUrl) {
                    { cookies ->
                        if (!isCompleted) {
                            isCompleted = true
                            CookieManager.getInstance().flush()
                            if (viewModel.onChallengeSolved(targetUrl, cookies)) {
                                eventDispatcher.tryDispatch(Event.ReloadData)
                            }
                        }
                    }
                }

                val cancelAndReload: () -> Unit = remember(targetUrl) {
                    {
                        if (!isCompleted) {
                            isCompleted = true
                            viewModel.cancelChallenge(targetUrl)
                            eventDispatcher.tryDispatch(Event.ReloadData)
                        }
                    }
                }

                LaunchedEffect(isChallengeDialogVisible) {
                    if (isChallengeDialogVisible) {
                        runCatching { challengeFocusRequester.requestFocus() }
                    }
                }

                LaunchedEffect(targetUrl) {
                    val startTime = SystemClock.elapsedRealtime()
                    var nextVerificationAt = 0L
                    var previousTime = startTime
                    var waitingForOtherDialog = 0L
                    while (!isCompleted) {
                        delay(500)
                        val now = SystemClock.elapsedRealtime()
                        if (latestVisibleChallengeDomain != null && latestVisibleChallengeDomain != targetUrl) {
                            waitingForOtherDialog += now - previousTime
                        }
                        previousTime = now
                        val elapsed = now - startTime - waitingForOtherDialog
                        try {
                            val webView = webViewInstance
                            val currentUrl = webView?.url ?: targetUrl
                            val cookies = CookieManager.getInstance().getCookie(targetUrl)
                            val title = webView?.title?.lowercase().orEmpty()
                            val isChallengeTitle =
                                title.contains("just a moment") ||
                                    title.contains("attention required") ||
                                    title.contains("cloudflare")

                            if (hasPageFinished && !isChallengeTitle && webView != null) {
                                val isFilmanLoginPage =
                                    !targetUrl.contains("zaluknij") &&
                                        !targetUrl.contains("cda") &&
                                        currentUrl.contains(FilmanConfig.LOGIN_PATH)
                                if (isFilmanLoginPage) {
                                    if (cookies.isNullOrBlank() || !cookies.contains(CLOUDFLARE_COOKIE)) {
                                        cancelAndReload()
                                        break
                                    }
                                }

                                val domStatus = webView.awaitChallengeStatus()
                                if (domStatus == "clear" && now >= nextVerificationAt) {
                                    nextVerificationAt = now + 3_000L
                                    if (viewModel.verifyChallenge(currentChallenge)) {
                                        completeChallenge(cookies.orEmpty())
                                        break
                                    }
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w("FilmanApp", "Challenge verification failed", e)
                        }

                        if (latestVisibleChallengeDomain == null && elapsed >= BACKGROUND_CHALLENGE_GRACE_MS) {
                            viewModel.setChallengeDialogVisible(true, targetUrl)
                        }

                        if (elapsed > CHALLENGE_TOTAL_TIMEOUT_MS) {
                            viewModel.cancelChallenge(targetUrl)
                            break
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
                                    Modifier.background(Color.Black.copy(alpha = 0.7f))
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
                                    .fillMaxWidth(0.92f)
                                    .fillMaxHeight(0.92f)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.shapes.medium,
                                    ).padding(MaterialTheme.spacing.medium)
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
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.titleMedium,
                                                )
                                                Spacer(modifier = Modifier.size(MaterialTheme.spacing.extraSmall))
                                                Text(
                                                    text = "Naciśnij WSTECZ na pilocie, aby pominąć",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            FilmanButton(
                                                modifier =
                                                    Modifier
                                                        .focusRequester(skipButtonFocusRequester)
                                                        .onPreviewKeyEvent { event ->
                                                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                                                runCatching { challengeFocusRequester.requestFocus() }
                                                                true
                                                            } else {
                                                                false
                                                            }
                                                        },
                                                text = "Pomiń",
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
                                                boxWidthProvider = { boxWidth },
                                                boxHeightProvider = { boxHeight },
                                                onScrollRequested = { webViewInstance?.scrollBy(0, it) },
                                                onClickRequested = { x, y -> performClickAtCoordinates(webViewInstance, x, y) },
                                                enabled = isChallengeDialogVisible,
                                                onNavigateUp = {
                                                    if (isChallengeDialogVisible) {
                                                        runCatching { skipButtonFocusRequester.requestFocus() }
                                                    }
                                                },
                                            ).focusRequester(challengeFocusRequester)
                                            .focusable(enabled = isChallengeDialogVisible),
                                ) {
                                    AndroidView(
                                        modifier = Modifier.fillMaxSize(),
                                        factory = { ctx ->
                                            WebView(ctx).apply {
                                                layoutParams =
                                                    android.view.ViewGroup.LayoutParams(
                                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                    )
                                                @SuppressLint("SetJavaScriptEnabled")
                                                settings.javaScriptEnabled = true
                                                settings.domStorageEnabled = true
                                                @Suppress("DEPRECATION")
                                                settings.databaseEnabled = true
                                                settings.useWideViewPort = false
                                                settings.loadWithOverviewMode = false
                                                settings.textZoom = 120
                                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                                isFocusable = isChallengeDialogVisible
                                                isFocusableInTouchMode = isChallengeDialogVisible
                                                settings.userAgentString = userAgent
                                                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                                                CookieManager.getInstance().setAcceptCookie(true)
                                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

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
                                                        override fun onPageStarted(
                                                            view: WebView?,
                                                            url: String?,
                                                            favicon: Bitmap?,
                                                        ) {
                                                            super.onPageStarted(view, url, favicon)
                                                            hasPageFinished = false
                                                        }

                                                        override fun onPageFinished(
                                                            view: WebView,
                                                            url: String,
                                                        ) {
                                                            super.onPageFinished(view, url)
                                                            hasPageFinished = true
                                                        }
                                                    }
                                                val request = currentChallenge.verificationRequest
                                                loadUrl(if (request?.method == "GET") request.url.toString() else targetUrl)
                                                webViewInstance = this
                                            }
                                        },
                                        update = { view ->
                                            view.isFocusable = isChallengeDialogVisible
                                            view.isFocusableInTouchMode = isChallengeDialogVisible
                                        },
                                        onRelease = { view ->
                                            view.stopLoading()
                                            view.destroy()
                                            webViewInstance = null
                                        },
                                    )
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
private const val CHALLENGE_TOTAL_TIMEOUT_MS = 35_000L

private suspend fun WebView.awaitChallengeStatus(): String =
    suspendCancellableCoroutine { continuation ->
        evaluateJavascript(
            """
            (function() {
                var title = (document.title || '').toLowerCase();
                if (title.indexOf('just a moment') !== -1 || title.indexOf('attention required') !== -1 || title.indexOf('cloudflare') !== -1) {
                    return 'challenge';
                }
                if (document.querySelector('#challenge-stage, #challenge-form, #cf-challenge-running')) {
                    return 'challenge';
                }
                if (document.querySelector('iframe[src*="challenges.cloudflare.com"], .cf-turnstile')) {
                    return 'interactive';
                }
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
