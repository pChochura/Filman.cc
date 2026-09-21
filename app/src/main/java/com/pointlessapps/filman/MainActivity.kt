package com.pointlessapps.filman

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.pointlessapps.filman.config.ZaluknijConfig
import com.pointlessapps.filman.config.ZaluknijConfig.CLOUDFLARE_COOKIE
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance
import com.pointlessapps.filman.ui.actor.ActorScreen
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
import com.pointlessapps.filman.ui.details.MovieDetailsScreen
import com.pointlessapps.filman.ui.forkids.ForKidsScreen
import com.pointlessapps.filman.ui.home.HomeScreen
import com.pointlessapps.filman.ui.login.LoginScreen
import com.pointlessapps.filman.ui.movies.MoviesScreen
import com.pointlessapps.filman.ui.player.PlayerScreen
import com.pointlessapps.filman.ui.screensaver.ScreensaverScreen
import com.pointlessapps.filman.ui.search.SearchScreen
import com.pointlessapps.filman.ui.theme.FilmanTheme
import com.pointlessapps.filman.ui.theme.spacing
import com.pointlessapps.filman.ui.tvshows.TvShowsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModel()

    private val _lastInteractionTime = MutableStateFlow(System.currentTimeMillis())
    val lastInteractionTime = _lastInteractionTime.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        viewModel.setUserAgent(this)
        viewModel.initBackStack(Route.Home)
        viewModel.handleIntent(intent)

        setContent {
            FilmanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FilmanApp(viewModel = viewModel, activity = this@MainActivity)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIntent(intent)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        _lastInteractionTime.value = System.currentTimeMillis()
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        _lastInteractionTime.value = System.currentTimeMillis()
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        _lastInteractionTime.value = System.currentTimeMillis()
        return super.dispatchGenericMotionEvent(ev)
    }
}

@Composable
private fun FilmanApp(
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
            { route ->
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

    LaunchedEffect(lastInteraction) {
        showScreensaver = false
        delay(2.minutes)
        if (!isPlayingLocal.value) {
            showScreensaver = true
        }
    }

    if (showScreensaver) {
        ScreensaverScreen(
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
        val extractorsPriority by viewModel.extractorsPriority.collectAsState()
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
            extractorsPriority = extractorsPriority,
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
            onDismissRequest = { viewModel.setShowSettingsOverlay(false) },
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
            onMoveExtractorUp = viewModel::onMoveExtractorUp,
            onMoveExtractorDown = viewModel::onMoveExtractorDown,
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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = MaterialTheme.spacing.huge),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                            MaterialTheme.shapes.medium,
                        )
                        .padding(
                            horizontal = MaterialTheme.spacing.medium,
                            vertical = MaterialTheme.spacing.small,
                        ),
            ) {
                Text(
                    text = stringResource(R.string.verifying_cloudflare_zaluknij),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .size(1.dp)
                    .graphicsLayer { alpha = 0.01f },
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        @SuppressLint("SetJavaScriptEnabled")
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = userAgent
                        webViewClient =
                            object : WebViewClient() {
                                override fun onPageFinished(
                                    view: WebView,
                                    url: String,
                                ) {
                                    super.onPageFinished(view, url)
                                    val cookies =
                                        CookieManager
                                            .getInstance()
                                            .getCookie(ZaluknijConfig.BASE_URL)
                                    if (cookies?.contains(CLOUDFLARE_COOKIE) == true) {
                                        viewModel.onZaluknijChallengeSolved(cookies)
                                    }
                                }
                            }
                        loadUrl(ZaluknijConfig.BASE_URL)
                    }
                },
            )
        }
    }
}

@Composable
private fun AppNavigationBar(
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
                            contentDescription = R.string.home_search,
                            route = Route.Search,
                        ),
                        FilmanNavigationItem.Text(
                            title = R.string.home_tab_home,
                            route = Route.Home,
                        ),
                        FilmanNavigationItem.Text(
                            title = R.string.home_tab_movies,
                            route = Route.Movies,
                        ),
                        FilmanNavigationItem.Text(
                            title = R.string.home_tab_series,
                            route = Route.TvShows,
                        ),
                        FilmanNavigationItem.Text(
                            title = R.string.home_tab_kids,
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
private fun AppContent(
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
private fun AppOverlayMenu(
    isLoggedIn: Boolean,
    extractorsPriority: List<String>,
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
    onDismissRequest: () -> Unit,
    onInitialAppearanceTypeToggled: (NextEpisodeAppearance) -> Unit,
    onInitialAppearanceOffsetToggled: (Long) -> Unit,
    onSecondaryAppearanceTypeToggled: (NextEpisodeAppearance) -> Unit,
    onSecondaryAppearanceOffsetToggled: (Long) -> Unit,
    onSecondaryTimerAmountToggled: (Long) -> Unit,
    onInitialAppearancePercentageToggled: (Long) -> Unit,
    onSecondaryAppearancePercentageToggled: (Long) -> Unit,
    onLogoutClicked: () -> Unit,
    onLoginClicked: () -> Unit,
    onMoveExtractorUp: (Int) -> Unit,
    onMoveExtractorDown: (Int) -> Unit,
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
            extractorsPriority,
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
            onMoveExtractorUp,
            onMoveExtractorDown,
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
