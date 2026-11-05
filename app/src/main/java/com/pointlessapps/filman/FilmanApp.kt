package com.pointlessapps.filman

import android.annotation.SuppressLint
import android.os.SystemClock
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.config.ZaluknijConfig
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
                transitionFocusRequester.requestFocus()
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
    val isZaluknijChallengeRequested by viewModel.isZaluknijChallengeRequested.collectAsState()
    if (isZaluknijChallengeRequested && userAgent.isNotEmpty()) {
        BackHandler {
            viewModel.cancelChallenge()
        }

        var webViewInstance by remember { mutableStateOf<WebView?>(null) }
        var boxWidth by remember { mutableIntStateOf(0) }
        var boxHeight by remember { mutableIntStateOf(0) }
        val challengeFocusRequester = remember { FocusRequester() }
        val skipButtonFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            challengeFocusRequester.requestFocus()
        }

        val targetUrl = ZaluknijConfig.BASE_URL
        val initialCookies = remember {
            CookieManager.getInstance().getCookie(targetUrl).orEmpty()
        }

        LaunchedEffect(targetUrl) {
            val startTime = System.currentTimeMillis()
            while (true) {
                delay(500)
                try {
                    val currentUrl = webViewInstance?.url ?: targetUrl
                    val cookies = CookieManager.getInstance().getCookie(currentUrl)
                        ?: CookieManager.getInstance().getCookie(targetUrl)
                    val title = webViewInstance?.title?.lowercase().orEmpty()
                    val isChallenge = title.contains("just a moment") ||
                            title.contains("attention required") ||
                            title.contains("cloudflare")
                    if (!isChallenge && !cookies.isNullOrBlank() && cookies.contains(CLOUDFLARE_COOKIE) && cookies != initialCookies) {
                        CookieManager.getInstance().flush()
                        viewModel.onChallengeSolved(targetUrl, cookies)
                        break
                    }
                } catch (_: Exception) {
                }
                if (System.currentTimeMillis() - startTime > 30_000) {
                    viewModel.cancelChallenge()
                    break
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.92f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.shapes.medium,
                    )
                    .padding(MaterialTheme.spacing.medium),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Zaluknij.cc — Weryfikacja Cloudflare",
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
                            modifier = Modifier
                                .focusRequester(skipButtonFocusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                        challengeFocusRequester.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                },
                            text = "Pomiń",
                            iconRes = null,
                            onClick = { viewModel.cancelChallenge() },
                        )
                    }
                    Spacer(modifier = Modifier.size(MaterialTheme.spacing.small))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .onSizeChanged { size ->
                                boxWidth = size.width
                                boxHeight = size.height
                            }
                            .pointerMovement(
                                boxWidthProvider = { boxWidth },
                                boxHeightProvider = { boxHeight },
                                onScrollRequested = { webViewInstance?.scrollBy(0, it) },
                                onClickRequested = { x, y -> performClickAtCoordinates(webViewInstance, x, y) },
                                enabled = true,
                                onNavigateUp = { skipButtonFocusRequester.requestFocus() },
                            )
                            .focusRequester(challengeFocusRequester)
                            .focusable(),
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                    @SuppressLint("SetJavaScriptEnabled")
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    settings.useWideViewPort = false
                                    settings.loadWithOverviewMode = false
                                    settings.textZoom = 120
                                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    isFocusable = true
                                    isFocusableInTouchMode = true
                                    settings.userAgentString = userAgent
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                    setOnKeyListener { _, keyCode, keyEvent ->
                                        if (keyCode == KeyEvent.KEYCODE_BACK && keyEvent.action == KeyEvent.ACTION_UP) {
                                            viewModel.cancelChallenge()
                                            true
                                        } else {
                                            false
                                        }
                                    }

                                    webViewClient =
                                        object : WebViewClient() {
                                            override fun onPageFinished(
                                                view: WebView,
                                                url: String,
                                            ) {
                                                super.onPageFinished(view, url)
                                                val title = view.title?.lowercase().orEmpty()
                                                val isChallenge = title.contains("just a moment") ||
                                                        title.contains("attention required") ||
                                                        title.contains("cloudflare")
                                                if (!isChallenge) {
                                                    val cookies =
                                                        CookieManager
                                                            .getInstance()
                                                            .getCookie(url)
                                                            ?: CookieManager
                                                                .getInstance()
                                                                .getCookie(targetUrl)
                                                    if (cookies?.contains(CLOUDFLARE_COOKIE) == true && cookies != initialCookies) {
                                                        CookieManager.getInstance().flush()
                                                        viewModel.onChallengeSolved(targetUrl, cookies)
                                                    }
                                                }
                                            }
                                        }
                                    loadUrl(targetUrl)
                                    webViewInstance = this
                                }
                            },
                        )
                    }
                }
            }
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
                    else -> contentFocusRequester.requestFocus()
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
