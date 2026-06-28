package com.example.filman

import android.os.Bundle
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.entryProvider
import androidx.tv.material3.Surface
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.local.SessionManager
import com.example.filman.ui.auth.AuthRoute
import com.example.filman.ui.details.MovieDetailsRoute
import com.example.filman.ui.home.HomeRoute
import com.example.filman.ui.player.PlayerRoute
import com.example.filman.ui.theme.FilmanTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    private val sessionManager: SessionManager by inject()
    private val progressManager: ProgressManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        runCatching {
            sessionManager.saveUserAgent(WebSettings.getDefaultUserAgent(this))
        }

        setContent {
            FilmanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FilmanApp(sessionManager, progressManager)
                }
            }
        }
    }
}

@Composable
fun FilmanApp(
    sessionManager: SessionManager,
    progressManager: ProgressManager,
) {
    val startDestination = if (sessionManager.hasCookie()) Home else Auth
    val backStack = remember { mutableStateListOf(startDestination) }

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Auth> {
                AuthRoute(
                    viewModel = koinViewModel(),
                    onAuthSuccess = {
                        backStack.clear()
                        backStack.add(Home)
                    },
                )
            }
            entry<Home> {
                HomeRoute(
                    viewModel = koinViewModel(),
                    onMovieClick = { url ->
                        backStack.add(Details(url))
                    },
                    onAuthInvalid = {
                        sessionManager.clearCookie()
                        android.webkit.CookieManager.getInstance().removeAllCookies(null)
                        backStack.clear()
                        backStack.add(Auth)
                    },
                )
            }
            entry<Details> { route ->
                MovieDetailsRoute(
                    movieUrl = route.url,
                    viewModel = koinViewModel(),
                    progressManager = progressManager,
                    onPlayMovie = { mediaUrl ->
                        backStack.add(Player(mediaUrl))
                    },
                    onAuthInvalid = {
                        sessionManager.clearCookie()
                        android.webkit.CookieManager.getInstance().removeAllCookies(null)
                        backStack.clear()
                        backStack.add(Auth)
                    },
                )
            }
            entry<Player> { route ->
                PlayerRoute(
                    mediaUrl = route.url,
                    viewModel = koinViewModel(),
                )
            }
        }
    )
}
