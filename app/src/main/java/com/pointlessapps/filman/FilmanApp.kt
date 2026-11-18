package com.pointlessapps.filman

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance
import com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
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
import com.pointlessapps.filman.ui.tvshows.TvShowsScreen
import kotlinx.coroutines.delay
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

    var wasSettingsOverlayOpen by remember { mutableStateOf(false) }
    LaunchedEffect(showSettingsOverlay) {
        if (wasSettingsOverlayOpen && !showSettingsOverlay) {
            runCatching { contentFocusRequester.requestFocus() }
        }
        wasSettingsOverlayOpen = showSettingsOverlay
    }

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
            onSyncNowClicked = viewModel::onSyncNowClicked,
            onLoginClicked = {
                handleNavigateTo(Route.Login())
                viewModel.setShowSettingsOverlay(false)
            },
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
    loggedInUsername: String? = null,
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
    lektorEdgeRate: String = LektorEdgeVoiceOptions.DEFAULT_RATE,
    lektorEdgePitch: String = LektorEdgeVoiceOptions.DEFAULT_PITCH,
    lektorEdgeVolume: String = LektorEdgeVoiceOptions.DEFAULT_VOLUME,
    downloadedLektorVoices: Set<String>,
    lektorModelDownloadProgress: Float?,
    isLektorModelExtracting: Boolean,
    systemVoices: SystemVoiceList,
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
    onSyncNowClicked: () -> Unit = {},
    onLoginClicked: () -> Unit,
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
        val userLabel = if (!loggedInUsername.isNullOrBlank()) {
            TextValue.StringResource(R.string.overlay_menu_logged_in_as, loggedInUsername)
        } else {
            TextValue.StringResource(R.string.overlay_menu_account)
        }
        items.add(
            FilmanOverlayMenuItem.Button(
                id = "sync_now",
                label = TextValue.StringResource(R.string.overlay_menu_sync_now),
                onClick = { onSyncNowClicked() },
            ),
        )
        items.add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "logout",
                label = TextValue.StringResource(R.string.overlay_menu_logout),
                value = userLabel,
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
