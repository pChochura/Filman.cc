package com.pointlessapps.filman.lite

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.ImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.pointlessapps.filman.lite.ui.SearchScreen
import io.ktor.client.HttpClient
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

@OptIn(ExperimentalCoilApi::class)
@Composable
fun LiteApp() {
    KoinApplication(application = { modules(liteModule()) }) {
        val client = koinInject<HttpClient>()
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .components { add(KtorNetworkFetcherFactory(client)) }
                .build()
        }
        MaterialTheme(colorScheme = darkColorScheme()) {
            SearchScreen()
        }
    }
}
