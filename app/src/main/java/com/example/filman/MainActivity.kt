package com.example.filman

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Surface
import com.example.filman.data.local.SessionManager
import com.example.filman.ui.auth.AuthScreen
import com.example.filman.ui.home.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(this)
        val favoritesManager = com.example.filman.data.local.FavoritesManager(this)
        val progressManager = com.example.filman.data.local.ProgressManager(this)
        
        try {
            val userAgent = android.webkit.WebSettings.getDefaultUserAgent(this)
            sessionManager.saveUserAgent(userAgent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val scraper = com.example.filman.data.scraper.FilmanScraper(sessionManager)

        setContent {
            androidx.tv.material3.MaterialTheme(
                colorScheme = androidx.tv.material3.darkColorScheme()
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FilmanApp(sessionManager, favoritesManager, progressManager, scraper)
                }
            }
        }
    }
}

@Composable
fun FilmanApp(
    sessionManager: SessionManager, 
    favoritesManager: com.example.filman.data.local.FavoritesManager,
    progressManager: com.example.filman.data.local.ProgressManager,
    scraper: com.example.filman.data.scraper.FilmanScraper
) {
    val navController = rememberNavController()
    
    val startDestination = if (sessionManager.hasCookie()) "home" else "auth"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("auth") {
            AuthScreen(
                sessionManager = sessionManager,
                onAuthSuccess = {
                    navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }
        composable("home") {
            HomeScreen(
                scraper = scraper,
                favoritesManager = favoritesManager,
                progressManager = progressManager,
                onMovieClick = { url ->
                    val encodedUrl = android.util.Base64.encodeToString(url.toByteArray(Charsets.UTF_8), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                    navController.navigate("details/${encodedUrl}")
                },
                onAuthInvalid = {
                    sessionManager.clearCookie()
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    navController.navigate("auth") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
        composable("details/{url}") { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
            val url = try { String(android.util.Base64.decode(encodedUrl, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP), Charsets.UTF_8) } catch(e: Exception) { "" }
            println("LOG!, $url")
            com.example.filman.ui.details.MovieDetailsScreen(
                movieUrl = url,
                scraper = scraper,
                favoritesManager = favoritesManager,
                progressManager = progressManager,
                onPlayMovie = { mediaUrl ->
                    val playEncodedUrl = android.util.Base64.encodeToString(mediaUrl.toByteArray(Charsets.UTF_8), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                    navController.navigate("player/${playEncodedUrl}")
                },
                onAuthInvalid = {
                    sessionManager.clearCookie()
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    navController.navigate("auth") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
        composable("player/{url}") { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
            val url = try { String(android.util.Base64.decode(encodedUrl, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP), Charsets.UTF_8) } catch(e: Exception) { "" }
            com.example.filman.ui.player.PlayerScreen(
                mediaUrl = url,
                scraper = scraper,
                sessionManager = sessionManager,
                progressManager = progressManager
            )
        }
    }
}
