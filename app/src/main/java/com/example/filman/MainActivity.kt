package com.example.filman

import android.os.Bundle
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.Surface
import com.example.filman.data.local.SessionManager
import com.example.filman.ui.auth.AuthRoute
import com.example.filman.ui.details.MovieDetailsRoute
import com.example.filman.ui.home.HomeScreen
import com.example.filman.ui.player.PlayerRoute
import com.example.filman.ui.theme.FilmanTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    private val sessionManager: SessionManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        runCatching {
            sessionManager.saveUserAgent(WebSettings.getDefaultUserAgent(this))
        }

        setContent {
            FilmanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FilmanApp(
                        startDestination = Route.Home.Home,
//                        startDestination = if (sessionManager.hasCookie()) {
//                            Route.Home
//                        } else {
//                            Route.Auth
//                        },
                    )
                }
            }
        }
    }
}

@Composable
fun FilmanApp(startDestination: Route) {
    val backStack = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { mutableStateListOf(*it.toTypedArray()) },
        ),
    ) {
        mutableStateListOf(startDestination)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Route.Auth> {
                AuthRoute(
                    viewModel = koinViewModel(),
                    onAuthSuccess = {
                        backStack.clear()
                        backStack.add(Route.Home.Home)
                    },
                )
            }
            entry<Route.Home.Home> {
                HomeScreen(
                    onNavigateTo = {
                        backStack.add(it)
                    },
                )
            }
            entry<Route.Details> { route ->
                MovieDetailsRoute(
                    movieUrl = route.url,
                    viewModel = koinViewModel(),
                    onPlayMovie = { mediaUrl ->
                        backStack.add(Route.Player(mediaUrl))
                    },
                    onAuthInvalid = {
                        backStack.clear()
                        backStack.add(Route.Auth)
                    },
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
