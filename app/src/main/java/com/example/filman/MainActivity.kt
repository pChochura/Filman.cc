package com.example.filman

import android.content.Intent
import android.os.Bundle
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.Surface
import com.example.filman.config.FilmanConfig
import com.example.filman.data.local.SessionManager
import com.example.filman.ui.actor.ActorScreen
import com.example.filman.ui.auth.AuthRoute
import com.example.filman.ui.components.FilmanNavigationBar
import com.example.filman.ui.components.FilmanNavigationItem
import com.example.filman.ui.components.FilmanScaffold
import com.example.filman.ui.core.Event.ScrollToTopEvent
import com.example.filman.ui.core.EventDispatcher
import com.example.filman.ui.core.LocalEventDispatcher
import com.example.filman.ui.details.MovieDetailsScreen
import com.example.filman.ui.forkids.ForKidsScreen
import com.example.filman.ui.home.HomeScreen
import com.example.filman.ui.movies.MoviesScreen
import com.example.filman.ui.player.PlayerRoute
import com.example.filman.ui.search.SearchScreen
import com.example.filman.ui.theme.FilmanTheme
import com.example.filman.ui.tvshows.TvShowsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    private val sessionManager: SessionManager by inject()
    private val pendingIntent = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        pendingIntent.value = intent

        runCatching {
            sessionManager.saveUserAgent(WebSettings.getDefaultUserAgent(this))
        }

        setContent {
            FilmanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FilmanApp(
                        startDestination = Route.Home,
                        intentFlow = pendingIntent,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIntent.value = intent
    }
}


@Composable
private fun FilmanApp(
    startDestination: Route,
    intentFlow: StateFlow<Intent?>,
) {
    val backStack = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { mutableStateListOf(*it.toTypedArray()) },
        ),
    ) {
        mutableStateListOf(startDestination)
    }

    val currentRoute = backStack.lastOrNull()

    val currentIntent by intentFlow.collectAsState()
    LaunchedEffect(currentIntent) {
        currentIntent?.data?.let { data ->
            if (
                data.scheme == FilmanConfig.DEEP_LINK_SCHEME &&
                data.host == FilmanConfig.DEEP_LINK_HOST_DETAILS
            ) {
                val url = data.getQueryParameter(FilmanConfig.DEEP_LINK_PARAM_URL)
                val episodeUrl = data.getQueryParameter(FilmanConfig.DEEP_LINK_PARAM_EPISODE_URL)
                if (url != null) {
                    backStack.add(Route.Details(url = url, episodeUrl = episodeUrl))
                }
            }
        }
    }

    val contentFocusRequester = remember { FocusRequester() }
    val eventDispatcher = remember { EventDispatcher() }

    CompositionLocalProvider(LocalEventDispatcher provides eventDispatcher) {
        FilmanScaffold(
            navigationTopBar = {
                if (currentRoute?.showNavigationBar == true) {
                    FilmanNavigationBar(
                        currentRouteProvider = { currentRoute },
                        onRouteChanged = { route ->
                            if (currentRoute != route) {
                                backStack.removeAll { it.showNavigationBar }
                                backStack.add(route)
                            }
                        },
                        onScrollToTopRequested = {
                            eventDispatcher.dispatch(ScrollToTopEvent)
                        },
                        items = listOf(
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
                        ),
                        contentFocusRequester = contentFocusRequester,
                    )
                }
            },
        ) { paddingValues ->
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<Route.Auth> {
                        AuthRoute(
                            viewModel = koinViewModel(),
                            onAuthSuccess = {
                                backStack.clear()
                                backStack.add(Route.Home)
                            },
                        )
                    }
                    entry<Route.Home> {
                        HomeScreen(
                            onNavigateTo = { backStack.add(it) },
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.Search> {
                        SearchScreen(
                            onNavigateTo = { backStack.add(it) },
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.Movies> {
                        MoviesScreen(
                            onNavigateTo = { backStack.add(it) },
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.TvShows> {
                        TvShowsScreen(
                            onNavigateTo = { backStack.add(it) },
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.ForKids> {
                        ForKidsScreen(
                            onNavigateTo = { backStack.add(it) },
                            contentFocusRequester = contentFocusRequester,
                            paddingValues = paddingValues,
                        )
                    }
                    entry<Route.Details> { route ->
                        MovieDetailsScreen(
                            movieUrl = route.url,
                            onNavigateTo = { backStack.add(it) },
                            contentFocusRequester = contentFocusRequester,
                        )
                    }
                    entry<Route.Actor> { route ->
                        ActorScreen(
                            actorUrl = route.url,
                            onNavigateTo = { backStack.add(it) },
                            contentFocusRequester = contentFocusRequester,
                        )
                    }
                    entry<Route.Player> { route ->
                        PlayerRoute(
                            mediaUrl = route.url,
                            viewModel = koinViewModel(),
                        )
                    }
                },
            )
        }
    }
}
