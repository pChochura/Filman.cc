package com.example.filman

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Surface
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.local.SessionManager
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.ui.auth.AuthRoute
import com.example.filman.ui.auth.AuthViewModel
import com.example.filman.ui.details.MovieDetailsRoute
import com.example.filman.ui.details.MovieDetailsViewModel
import com.example.filman.ui.home.HomeRoute
import com.example.filman.ui.home.HomeViewModel
import com.example.filman.ui.player.PlayerRoute
import com.example.filman.ui.player.PlayerViewModel
import com.example.filman.ui.theme.FilmanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(this)
        val favoritesManager = FavoritesManager(this)
        val progressManager = ProgressManager(this)

        try {
            val userAgent = android.webkit.WebSettings.getDefaultUserAgent(this)
            sessionManager.saveUserAgent(userAgent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val scraper = FilmanScraper(sessionManager)

        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(
                        sessionManager,
                    ) as T

                    modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(
                        scraper,
                        favoritesManager,
                        progressManager,
                    ) as T

                    modelClass.isAssignableFrom(MovieDetailsViewModel::class.java) -> MovieDetailsViewModel(
                        scraper,
                        favoritesManager,
                        progressManager,
                    ) as T

                    modelClass.isAssignableFrom(PlayerViewModel::class.java) -> PlayerViewModel(
                        scraper,
                        sessionManager,
                        progressManager,
                    ) as T

                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }

        setContent {
            FilmanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FilmanApp(sessionManager, progressManager, factory)
                }
            }
        }
    }
}

@Composable
fun FilmanApp(
    sessionManager: SessionManager,
    progressManager: ProgressManager,
    factory: ViewModelProvider.Factory,
) {
    val navController = rememberNavController()

    val startDestination = if (sessionManager.hasCookie()) "home" else "auth"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("auth") {
            AuthRoute(
                viewModel = viewModel(factory = factory),
                onAuthSuccess = {
                    navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }
                },
            )
        }
        composable("home") {
            HomeRoute(
                viewModel = viewModel(factory = factory),
                onMovieClick = { url ->
                    val encodedUrl = android.util.Base64.encodeToString(
                        url.toByteArray(Charsets.UTF_8),
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
                    )
                    navController.navigate("details/$encodedUrl")
                },
                onAuthInvalid = {
                    sessionManager.clearCookie()
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    navController.navigate("auth") {
                        popUpTo("home") { inclusive = true }
                    }
                },
            )
        }
        composable("details/{url}") { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
            val url = try {
                String(
                    android.util.Base64.decode(
                        encodedUrl,
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
                    ),
                    Charsets.UTF_8,
                )
            } catch (e: Exception) {
                ""
            }
            MovieDetailsRoute(
                movieUrl = url,
                viewModel = viewModel(factory = factory),
                progressManager = progressManager,
                onPlayMovie = { mediaUrl ->
                    val playEncodedUrl = android.util.Base64.encodeToString(
                        mediaUrl.toByteArray(Charsets.UTF_8),
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
                    )
                    navController.navigate("player/$playEncodedUrl")
                },
                onAuthInvalid = {
                    sessionManager.clearCookie()
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    navController.navigate("auth") {
                        popUpTo("home") { inclusive = true }
                    }
                },
            )
        }
        composable("player/{url}") { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
            val url = try {
                String(
                    android.util.Base64.decode(
                        encodedUrl,
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
                    ),
                    Charsets.UTF_8,
                )
            } catch (e: Exception) {
                ""
            }
            PlayerRoute(
                mediaUrl = url,
                viewModel = viewModel(factory = factory),
            )
        }
    }
}
