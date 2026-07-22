package com.example.filman.ui.player

import android.app.Activity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.filman.Route
import com.example.filman.ui.base.BaseEvent
import com.example.filman.ui.components.FilmanFullscreenLoader
import com.example.filman.ui.components.FilmanOverlayMenu
import com.example.filman.ui.core.CollectEffect
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun PlayerScreen(
    url: String,
    onNavigateTo: (Route) -> Unit,
    contentFocusRequester: FocusRequester,
    viewModel: PlayerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(url) {
        viewModel.onEvent(PlayerEvent.LoadDetails(url))
    }

    val activityContext = LocalContext.current
    DisposableEffect(Unit) {
        val window = (activityContext as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is PlayerEffect.NavigateToAuth -> onNavigateTo(Route.Auth)
        }
    }

    AnimatedContent(
        targetState = state.isLoading,
        contentAlignment = Alignment.Center,
    ) { isLoading ->
        if (isLoading) {
            FilmanFullscreenLoader()
        } else {
            PlayerContent(
                state = state,
                onEvent = viewModel::onEvent,
                contentFocusRequester = contentFocusRequester,
            )
        }
    }

    state.overlayMenuData?.let { data ->
        FilmanOverlayMenu(
            title = data.title,
            items = data.items,
            onDismissRequest = { viewModel.onEvent(BaseEvent.CloseContextMenu) },
        )
    }
}

@Composable
private fun PlayerContent(
    state: PlayerState,
    onEvent: (PlayerEvent) -> Unit,
    contentFocusRequester: FocusRequester,
) {
    val currentPosition = remember { mutableLongStateOf(0) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        state.videoUrl?.let { url ->
            if (state.isWebView) {
                WebViewPlayer(url)
            } else {
                Player(
                    videoUrl = url,
                    headers = state.videoHeaders,
                    isPlaying = state.isPlaying,
                    onIsPlayingChanged = { onEvent(PlayerEvent.IsPlayingChanged(it)) },
                    onDurationProvided = { onEvent(PlayerEvent.DurationProvided(it)) },
                    onCurrentPositionChanged = { currentPosition.longValue = it },
                )
            }
        }

        if (!state.isWebView) {
            PlayerControls(
                detailedMedia = state.detailedMedia,
                duration = state.duration,
                currentPositionProvider = currentPosition::longValue,
                currentSeason = 1,
                currentEpisode = 3,
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Player(
    videoUrl: String,
    headers: Map<String, String>,
    isPlaying: Boolean,
    onIsPlayingChanged: (Boolean) -> Unit,
    onDurationProvided: (Long) -> Unit,
    onCurrentPositionChanged: (Long) -> Unit,
) {
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(player, isPlaying) {
        player?.let { player ->
            while (true) {
                if (player.duration != C.TIME_UNSET) {
                    onDurationProvided(player.duration)
                }
                onCurrentPositionChanged(player.currentPosition)
                delay(1.seconds)
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            PlayerView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )

                useController = false

                val dataSourceFactory =
                    DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true)
                        .setDefaultRequestProperties(headers)

                val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

                player = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build()
                    .apply {
                        addListener(
                            object : Player.Listener {
                                override fun onIsPlayingChanged(isPlaying: Boolean) {
                                    onIsPlayingChanged(isPlaying)
                                }
                            },
                        )

                        setMediaItem(MediaItem.fromUri(videoUrl))
                        prepare()
                        playWhenReady = true
                        onDurationProvided(duration)
                    }

                this.player = player
            }
        },
        onRelease = { view -> view.player?.release() },
    )
}

@Composable
private fun WebViewPlayer(url: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            android.webkit.WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                setBackgroundColor(android.graphics.Color.BLACK)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    
                    // Spoofing mobile UA just in case to get mobile layout/player
                    userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
                }

                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onReceivedSslError(
                        view: android.webkit.WebView?,
                        handler: android.webkit.SslErrorHandler?,
                        error: android.net.http.SslError?
                    ) {
                        handler?.proceed()
                    }

                    override fun shouldInterceptRequest(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null
                        if (request.method == "GET" && (url.contains("q8y5z.com/mae") || url.contains("boosteradx.online/e") || url.contains("streamlyplayero.online/e"))) {
                            try {
                                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                                request.requestHeaders?.forEach { (key, value) ->
                                    if (!key.equals("Accept-Encoding", ignoreCase = true)) {
                                        connection.setRequestProperty(key, value)
                                    }
                                }
                                if (connection.responseCode == 200) {
                                    var html = connection.inputStream.bufferedReader().readText()
                                    val style = "<style>video::-webkit-media-controls { display: none !important; } .jw-controls, .vjs-control-bar, .plyr__controls, .html5-video-controls { display: none !important; } * { -webkit-tap-highlight-color: transparent !important; }</style>"
                                    html = html.replace("</head>", "$style</head>")
                                    
                                    val mimeType = connection.contentType?.substringBefore(";") ?: "text/html"
                                    val encoding = "utf-8" // We read it as string, so we return UTF-8
                                    
                                    val inputStream = java.io.ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
                                    return android.webkit.WebResourceResponse(mimeType, encoding, inputStream)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Fallback injection for main frame
                        view?.evaluateJavascript(
                            "(function() { " +
                            "   var style = document.createElement('style');" +
                            "   style.innerHTML = '" +
                            "       video::-webkit-media-controls { display: none !important; } " +
                            "       .jw-controls, .vjs-control-bar, .plyr__controls, .html5-video-controls { display: none !important; } " +
                            "       * { -webkit-tap-highlight-color: transparent !important; } " +
                            "   ';" +
                            "   document.head.appendChild(style);" +
                            "})();", null
                        )
                    }
                }

                // Inject a click on document so the video can autoplay
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        if (newProgress > 80) {
                            view?.evaluateJavascript(
                                "(function() { " +
                                "   const elements = document.querySelectorAll('button, video, .play-button, [role=button], .jw-video');" +
                                "   elements.forEach(el => el.click());" +
                                "})();",
                                null
                            )
                        }
                    }
                }

                loadUrl(url)
            }
        },
        onRelease = { view ->
            view.destroy()
        }
    )
}
