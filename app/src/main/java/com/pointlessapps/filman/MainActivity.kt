package com.pointlessapps.filman

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.remember
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
import androidx.tv.material3.Surface
import com.pointlessapps.filman.config.ZaluknijConfig
import com.pointlessapps.filman.config.ZaluknijConfig.CLOUDFLARE_COOKIE
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance
import com.pointlessapps.filman.ui.actor.ActorScreen
import com.pointlessapps.filman.ui.components.FilmanNavigationBar
import com.pointlessapps.filman.ui.components.FilmanNavigationItem
import com.pointlessapps.filman.ui.components.FilmanOverlayMenu
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.components.FilmanScaffold
import com.pointlessapps.filman.ui.core.Event
import com.pointlessapps.filman.ui.core.Event.ScrollToTopEvent
import com.pointlessapps.filman.ui.core.EventDispatcher
import com.pointlessapps.filman.ui.core.LocalEventDispatcher
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.ui.details.MovieDetailsScreen
import com.pointlessapps.filman.ui.forkids.ForKidsScreen
import com.pointlessapps.filman.ui.home.HomeScreen
import com.pointlessapps.filman.ui.login.LoginScreen
import com.pointlessapps.filman.ui.movies.MoviesScreen
import com.pointlessapps.filman.ui.player.PlayerScreen
import com.pointlessapps.filman.ui.search.SearchScreen
import com.pointlessapps.filman.ui.theme.FilmanTheme
import com.pointlessapps.filman.ui.tvshows.TvShowsScreen
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        viewModel.setUserAgent(this)
        viewModel.initBackStack(Route.Home)
        viewModel.handleIntent(intent)

        setContent {
            FilmanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FilmanApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.handleIntent(intent)
    }
}

@Composable
private fun FilmanApp(viewModel: MainViewModel) {
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

    CompositionLocalProvider(LocalEventDispatcher provides eventDispatcher) {
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
            onMoveExtractorUp = viewModel::onMoveExtractorUp,
            onMoveExtractorDown = viewModel::onMoveExtractorDown,
            onPreferredQualitySelected = viewModel::setPreferredQuality,
            onAutoPlayNextEpisodeToggled = viewModel::setAutoPlayNext,
            onClearCacheClicked = viewModel::clearCache,
            onClearWatchHistoryClicked = viewModel::clearWatchHistory,
            onClearSearchHistoryClicked = viewModel::clearSearchHistory,
        )
    }

    val userAgent by viewModel.userAgent.collectAsState()
    val isZaluknijChallengeRequested by viewModel.isZaluknijChallengeRequested.collectAsState()
    if (isZaluknijChallengeRequested && userAgent.isNotEmpty()) {
        Box(
            modifier = Modifier
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
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                val cookies = CookieManager.getInstance()
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
            items = if (currentRoute.showBackButton) {
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
            modifier = Modifier
                .size(1.dp)
                .focusRequester(transitionFocusRequester)
                .focusable(),
        )

        NavDisplay(
            backStack = backStack,
            onBack = { onNavigateTo(null) },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
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
                entry<Route.Details> { route ->
                    MovieDetailsScreen(
                        movieUrl = route.url,
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
    onMoveExtractorUp: (Int) -> Unit,
    onMoveExtractorDown: (Int) -> Unit,
    onPreferredQualitySelected: (String) -> Unit,
    onAutoPlayNextEpisodeToggled: (Boolean) -> Unit,
    onClearCacheClicked: () -> Unit,
    onClearWatchHistoryClicked: () -> Unit,
    onClearSearchHistoryClicked: () -> Unit,
) {
    val items = mutableListOf<FilmanOverlayMenuItem>()

    items.add(
        FilmanOverlayMenuItem.Header(
            id = "playback_header",
            label = TextValue.StringResource(R.string.overlay_menu_header_playback),
        ),
    )

    if (extractorsPriority.isNotEmpty()) {
        val extractorsItems = extractorsPriority.mapIndexed { index, extractor ->
            FilmanOverlayMenuItem.ReorderableOption(
                id = extractor,
                label = TextValue.DynamicString(extractor),
                onMoveUp = if (index > 0) {
                    { onMoveExtractorUp(index) }
                } else {
                    null
                },
                onMoveDown = if (index < extractorsPriority.size - 1) {
                    { onMoveExtractorDown(index) }
                } else {
                    null
                },
            )
        }
        items.add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "extractors_priority",
                label = TextValue.StringResource(R.string.overlay_menu_sources_priority),
                value = null,
                items = extractorsItems,
            ),
        )
    }

    val qualityOptions = SettingsConstants.Quality.ALL
    val qualityItems = qualityOptions.map { quality ->
        FilmanOverlayMenuItem.Option(
            id = "quality_$quality",
            label = if (quality == SettingsConstants.Quality.AUTO) {
                TextValue.StringResource(R.string.overlay_menu_quality_auto)
            } else {
                TextValue.DynamicString(quality)
            },
            isSelected = quality == preferredQuality,
            onClick = { onPreferredQualitySelected(quality) },
        )
    }

    items.add(
        FilmanOverlayMenuItem.NestedMenu(
            id = "preferred_quality",
            label = TextValue.StringResource(R.string.overlay_menu_preferred_quality),
            value = if (preferredQuality == SettingsConstants.Quality.AUTO) {
                stringResource(R.string.overlay_menu_quality_auto)
            } else {
                preferredQuality
            },
            items = qualityItems,
        ),
    )

    val initialTypeItems = listOf(
        NextEpisodeAppearance.SHOW,
        NextEpisodeAppearance.SHOW_IN_OVERLAY,
        NextEpisodeAppearance.HIDE,
    ).map { type ->
        FilmanOverlayMenuItem.Option(
            id = "initial_type_$type",
            label = TextValue.StringResource(
                when (type) {
                    NextEpisodeAppearance.SHOW -> R.string.next_episode_appearance_show
                    NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.next_episode_appearance_show_in_overlay
                    NextEpisodeAppearance.HIDE -> R.string.next_episode_appearance_dont_show
                    else -> R.string.next_episode_appearance_show
                },
            ),
            isSelected = initialAppearanceType == type,
            onClick = { onInitialAppearanceTypeToggled(type) },
        )
    }

    val initialOffsetItems = listOf(60L, 80L, 100L, 120L, 150L).map { offset ->
        FilmanOverlayMenuItem.Option(
            id = "initial_offset_$offset",
            label = TextValue.StringResource(R.string.next_episode_seconds_format, offset),
            isSelected = initialAppearanceOffset == offset,
            onClick = { onInitialAppearanceOffsetToggled(offset) },
        )
    }

    val secondaryTypeItems = listOf(
        NextEpisodeAppearance.SHOW_WITH_TIMER,
        NextEpisodeAppearance.SHOW,
        NextEpisodeAppearance.SHOW_IN_OVERLAY,
        NextEpisodeAppearance.HIDE,
    ).map { type ->
        FilmanOverlayMenuItem.Option(
            id = "secondary_type_$type",
            label = TextValue.StringResource(
                when (type) {
                    NextEpisodeAppearance.SHOW_WITH_TIMER -> R.string.next_episode_appearance_show_with_timer
                    NextEpisodeAppearance.SHOW -> R.string.next_episode_appearance_just_show
                    NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.next_episode_appearance_show_in_overlay
                    NextEpisodeAppearance.HIDE -> R.string.next_episode_appearance_dont_show
                },
            ),
            isSelected = secondaryAppearanceType == type,
            onClick = { onSecondaryAppearanceTypeToggled(type) },
        )
    }

    val secondaryOffsetItems = listOf(30L, 45L, 60L, 90L).map { offset ->
        FilmanOverlayMenuItem.Option(
            id = "secondary_offset_$offset",
            label = TextValue.StringResource(R.string.next_episode_seconds_format, offset),
            isSelected = secondaryAppearanceOffset == offset,
            onClick = { onSecondaryAppearanceOffsetToggled(offset) },
        )
    }

    val timerAmountOptions = listOf(5L, 10L, 15L, 20L)
    val secondaryTimerAmountItems = timerAmountOptions.map { amount ->
        FilmanOverlayMenuItem.Option(
            id = "secondary_timer_$amount",
            label = TextValue.StringResource(R.string.next_episode_seconds_format, amount),
            isSelected = secondaryTimerAmount == amount,
            onClick = { onSecondaryTimerAmountToggled(amount) },
        )
    }

    val percentageOptions = listOf(1L, 2L, 3L, 4L, 5L)
    val initialPercentageItems = percentageOptions.map { percentage ->
        FilmanOverlayMenuItem.Option(
            id = "initial_percentage_$percentage",
            label = TextValue.StringResource(R.string.next_episode_percentage_format, percentage),
            isSelected = initialAppearancePercentage == percentage,
            onClick = { onInitialAppearancePercentageToggled(percentage) },
        )
    }
    val secondaryPercentageItems = percentageOptions.map { percentage ->
        FilmanOverlayMenuItem.Option(
            id = "secondary_percentage_$percentage",
            label = TextValue.StringResource(R.string.next_episode_percentage_format, percentage),
            isSelected = secondaryAppearancePercentage == percentage,
            onClick = { onSecondaryAppearancePercentageToggled(percentage) },
        )
    }

    val nextEpisodeNestedItems = listOf(
        FilmanOverlayMenuItem.NestedMenu(
            id = "autoplay_next_episode",
            label = TextValue.StringResource(R.string.overlay_menu_autoplay_next),
            value = stringResource(
                if (autoPlayNextEpisode) {
                    R.string.overlay_menu_autoplay_enabled
                } else {
                    R.string.overlay_menu_autoplay_disabled
                },
            ),
            items = listOf(
                FilmanOverlayMenuItem.Option(
                    id = "autoplay_true",
                    label = TextValue.StringResource(R.string.overlay_menu_autoplay_enabled),
                    isSelected = autoPlayNextEpisode,
                    onClick = { onAutoPlayNextEpisodeToggled(true) },
                ),
                FilmanOverlayMenuItem.Option(
                    id = "autoplay_false",
                    label = TextValue.StringResource(R.string.overlay_menu_autoplay_disabled),
                    isSelected = !autoPlayNextEpisode,
                    onClick = { onAutoPlayNextEpisodeToggled(false) },
                ),
            ),
        ),
        FilmanOverlayMenuItem.NestedMenu(
            id = "initial_appearance_type",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_type),
            value = stringResource(
                when (initialAppearanceType) {
                    NextEpisodeAppearance.SHOW -> R.string.next_episode_appearance_show
                    NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.next_episode_appearance_show_in_overlay
                    NextEpisodeAppearance.HIDE -> R.string.next_episode_appearance_dont_show
                    else -> R.string.next_episode_appearance_show
                },
            ),

            items = initialTypeItems,
        ),
        FilmanOverlayMenuItem.NestedMenu(
            id = "initial_appearance_offset",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_offset),
            value = stringResource(R.string.next_episode_seconds_format, initialAppearanceOffset),
            items = initialOffsetItems,
        ),
        FilmanOverlayMenuItem.NestedMenu(
            id = "initial_percentage",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_percentage),
            value = stringResource(
                R.string.next_episode_percentage_format,
                initialAppearancePercentage,
            ),
            items = initialPercentageItems,
        ),
        FilmanOverlayMenuItem.NestedMenu(
            id = "secondary_appearance_type",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_secondary_type),
            value = stringResource(
                when (secondaryAppearanceType) {
                    NextEpisodeAppearance.SHOW_WITH_TIMER -> R.string.next_episode_appearance_show_with_timer
                    NextEpisodeAppearance.SHOW -> R.string.next_episode_appearance_just_show
                    NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.next_episode_appearance_show_in_overlay
                    NextEpisodeAppearance.HIDE -> R.string.next_episode_appearance_dont_show
                },
            ),
            items = secondaryTypeItems,
        ),
        FilmanOverlayMenuItem.NestedMenu(
            id = "secondary_appearance_offset",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_secondary_offset),
            value = stringResource(R.string.next_episode_seconds_format, secondaryAppearanceOffset),
            items = secondaryOffsetItems,
        ),
        FilmanOverlayMenuItem.NestedMenu(
            id = "secondary_percentage",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_secondary_percentage),
            value = stringResource(
                R.string.next_episode_percentage_format,
                secondaryAppearancePercentage,
            ),
            items = secondaryPercentageItems,
        ),
        FilmanOverlayMenuItem.NestedMenu(
            id = "secondary_timer_amount",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_secondary_timer),
            value = stringResource(R.string.next_episode_seconds_format, secondaryTimerAmount),
            items = secondaryTimerAmountItems,
        ),
    )

    items.add(
        FilmanOverlayMenuItem.NestedMenu(
            id = "next_episode_settings",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_settings),
            value = null,
            items = nextEpisodeNestedItems,
        ),
    )

    items.add(
        FilmanOverlayMenuItem.Header(
            id = "data_header",
            label = TextValue.StringResource(R.string.overlay_menu_header_data),
        ),
    )

    items.add(
        FilmanOverlayMenuItem.NestedMenu(
            id = "clear_cache",
            label = TextValue.StringResource(R.string.overlay_menu_clear_cache),
            value = null,
            items = listOf(
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
            items = listOf(
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
            items = listOf(
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

    if (isLoggedIn) {
        items.add(
            FilmanOverlayMenuItem.Header(
                id = "other_header",
                label = TextValue.StringResource(R.string.overlay_menu_header_other),
            ),
        )

        items.add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "logout",
                label = TextValue.StringResource(R.string.overlay_menu_logout),
                value = null,
                items = listOf(
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
