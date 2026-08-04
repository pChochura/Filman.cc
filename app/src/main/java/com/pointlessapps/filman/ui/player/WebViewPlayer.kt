package com.pointlessapps.filman.ui.player

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.pointlessapps.filman.ui.login.PLAYER_PAUSE_SCRIPT
import com.pointlessapps.filman.ui.login.PLAYER_PLAY_SCRIPT
import com.pointlessapps.filman.ui.login.PLAYER_USER_AGENT
import com.pointlessapps.filman.ui.login.getPlayerAspectRatioScript
import com.pointlessapps.filman.ui.login.getPlayerPlaybackSpeedScript
import com.pointlessapps.filman.ui.login.playerWebChromeClient
import com.pointlessapps.filman.ui.login.playerWebViewClient
import java.lang.ref.WeakReference

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebViewPlayer(
    videoUrl: String,
    isPlaying: Boolean,
    playbackSpeed: Float,
    aspectRatioMode: Int,
    onIsPlayingChanged: (Boolean) -> Unit,
    onIsBufferingChanged: (Boolean) -> Unit,
    onDurationProvided: (Long) -> Unit,
    onCurrentPositionChanged: (Long) -> Unit,
    onWebViewProvided: (WeakReference<WebView>) -> Unit,
    onPlayerError: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(isPlaying, webView) {
        val webView = webView ?: return@LaunchedEffect
        if (isPlaying) {
            webView.evaluateJavascript(PLAYER_PLAY_SCRIPT, null)
        } else {
            webView.evaluateJavascript(PLAYER_PAUSE_SCRIPT, null)
        }
    }

    LaunchedEffect(playbackSpeed, webView) {
        val webView = webView ?: return@LaunchedEffect
        webView.evaluateJavascript(getPlayerPlaybackSpeedScript(playbackSpeed), null)
    }

    LaunchedEffect(aspectRatioMode, webView) {
        val webView = webView ?: return@LaunchedEffect
        webView.evaluateJavascript(getPlayerAspectRatioScript(aspectRatioMode), null)
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                isFocusable = false
                isFocusableInTouchMode = false
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.userAgentString = PLAYER_USER_AGENT
                setBackgroundColor(Color.BLACK)

                addJavascriptInterface(
                    object {
                        @Suppress("Unused")
                        @JavascriptInterface
                        fun onTimeUpdate(currentTime: Double, duration: Double) {
                            onIsBufferingChanged(false)
                            onCurrentPositionChanged((currentTime * 1000).toLong())
                            if (!duration.isNaN()) {
                                onDurationProvided((duration * 1000).toLong())
                            }
                        }

                        @Suppress("Unused")
                        @JavascriptInterface
                        fun onPlayStateChanged(playing: Boolean) {
                            onIsPlayingChanged(playing)
                        }

                        @Suppress("Unused")
                        @JavascriptInterface
                        fun onBufferingChanged(buffering: Boolean) {
                            onIsBufferingChanged(buffering)
                        }

                        @Suppress("Unused")
                        @JavascriptInterface
                        fun onError() {
                            onPlayerError()
                        }
                    },
                    "AndroidBridge",
                )

                webChromeClient = playerWebChromeClient()
                webViewClient = playerWebViewClient(
                    onPlayerError = onPlayerError,
                )

                loadUrl(videoUrl)
                webView = this
                onWebViewProvided(WeakReference(this))
            }
        },
        update = { view ->
            if (view.url != videoUrl) {
                view.loadUrl(videoUrl)
            }
        },
        onRelease = { view ->
            view.destroy()
            webView = null
        },
    )
}
