package com.pointlessapps.filman

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.Surface
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
        AppOverlayMenu(
            isLoggedIn = isLoggedIn,
            onDismissRequest = { viewModel.setShowSettingsOverlay(false) },
            onLogoutClicked = viewModel::onLogoutClicked,
        )
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
                        contentFocusRequester = contentFocusRequester,
                    )
                }
            },
        )
    }
}

@Composable
private fun AppOverlayMenu(
    isLoggedIn: Boolean,
    onDismissRequest: () -> Unit,
    onLogoutClicked: () -> Unit,
) {
    FilmanOverlayMenu(
        title = TextValue.StringResource(R.string.overlay_menu_settings),
        onDismissRequest = onDismissRequest,
        items = listOfNotNull(
            FilmanOverlayMenuItem.Button(
                label = TextValue.StringResource(R.string.overlay_menu_logout),
                onClick = onLogoutClicked,
            ).takeIf { isLoggedIn },
        ),
    )
}
