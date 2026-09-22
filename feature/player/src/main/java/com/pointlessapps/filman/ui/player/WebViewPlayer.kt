package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.ui.core.pointerMovement
import com.pointlessapps.filman.ui.core.performClickAtCoordinates
import com.pointlessapps.filman.ui.core.WebViewClient
import com.pointlessapps.filman.ui.core.PLAYER_PAUSE_SCRIPT
import com.pointlessapps.filman.ui.core.PLAYER_PLAY_SCRIPT
import com.pointlessapps.filman.ui.core.getPlayerAspectRatioScript
import com.pointlessapps.filman.ui.core.getPlayerPlaybackSpeedScript
import com.pointlessapps.filman.ui.core.getPlayerSetSubtitleScript
import com.pointlessapps.filman.ui.core.getPlayerUserAgent
import com.pointlessapps.filman.ui.core.playerWebChromeClient
import com.pointlessapps.filman.ui.core.playerWebViewClient

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import com.pointlessapps.filman.data.scraper.NetworkClient
import com.pointlessapps.filman.ui.components.FilmanFullscreenLoader










import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPlayer(
    videoUrl: String,
    isPlaying: Boolean,
    playbackSpeed: Float,
    aspectRatioMode: Int,
    selectedSubtitleUrl: String?,
    onIsPlayingChanged: (Boolean) -> Unit,
    onIsBufferingChanged: (Boolean) -> Unit,
    onDurationProvided: (Long) -> Unit,
    onCurrentPositionChanged: (Long) -> Unit,
    onWebViewProvided: (WeakReference<WebView>) -> Unit,
    onPlayerError: () -> Unit,
    onCloudflareCleared: (String, String) -> Unit,
    onCaptchaStateChanged: (Boolean) -> Unit = {},
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadedVideoUrl by remember { mutableStateOf<String?>(null) }
    var isVideoReady by remember { mutableStateOf(false) }
    var isCaptchaShowing by remember { mutableStateOf(false) }
    var boxWidth by remember { mutableIntStateOf(0) }
    var boxHeight by remember { mutableIntStateOf(0) }
    val pointerFocusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedSubtitleUrl, webView) {
        val webView = webView ?: return@LaunchedEffect
        webView.evaluateJavascript(getPlayerSetSubtitleScript(selectedSubtitleUrl), null)
    }

    LaunchedEffect(isCaptchaShowing) {
        if (isCaptchaShowing) {
            onIsBufferingChanged(false)
            delay(100.milliseconds)
            pointerFocusRequester.requestFocus()
        }
    }

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

    

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    boxWidth = size.width
                    boxHeight = size.height
                }.pointerMovement(
                    boxWidthProvider = { boxWidth },
                    boxHeightProvider = { boxHeight },
                    onScrollRequested = { webView?.scrollBy(0, it) },
                    onClickRequested = { x, y -> performClickAtCoordinates(webView, x, y) },
                    enabled = isCaptchaShowing,
                ).focusRequester(pointerFocusRequester)
                .focusable(isCaptchaShowing),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isVideoReady || isCaptchaShowing) 1f else 0f),
            factory = { context ->
                WebView(context).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    isFocusable = true
                    isFocusableInTouchMode = true
                    settings.javaScriptEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString = getPlayerUserAgent(context)
                    setBackgroundColor(Color.BLACK)

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    addJavascriptInterface(
                        object {
                            @Suppress("Unused")
                            @JavascriptInterface
                            fun onTimeUpdate(
                                currentTime: Double,
                                duration: Double,
                            ) {
                                this@apply.post {
                                    isVideoReady = true
                                    onIsBufferingChanged(false)
                                    onCurrentPositionChanged((currentTime * 1000).toLong())
                                    if (!duration.isNaN()) {
                                        onDurationProvided((duration * 1000).toLong())
                                    }
                                }
                            }

                            @Suppress("Unused")
                            @JavascriptInterface
                            fun onPlayStateChanged(playing: Boolean) {
                                this@apply.post {
                                    isVideoReady = true
                                    onIsPlayingChanged(playing)
                                }
                            }

                            @Suppress("Unused")
                            @JavascriptInterface
                            fun onBufferingChanged(buffering: Boolean) {
                                this@apply.post {
                                    onIsBufferingChanged(buffering)
                                }
                            }

                            @Suppress("Unused")
                            @JavascriptInterface
                            fun onError() {
                                this@apply.post {
                                    onPlayerError()
                                }
                            }

                            @Suppress("Unused")
                            @JavascriptInterface
                            fun onCaptchaFound(
                                x: Float,
                                y: Float,
                            ) {
                                val density = context.resources.displayMetrics.density
                                this@apply.post {
                                    onIsBufferingChanged(false)
                                    performClickAtCoordinates(this@apply, x * density, y * density)
                                }
                            }

                            @Suppress("Unused")
                            @JavascriptInterface
                            fun onCaptchaStateChanged(isShowing: Boolean) {
                                this@apply.post {
                                    isCaptchaShowing = isShowing
                                    onCaptchaStateChanged(isShowing)
                                    if (isShowing) {
                                        onIsBufferingChanged(false)
                                    }
                                }
                            }

                            @Suppress("Unused")
                            @JavascriptInterface
                            fun onCloudflareCleared(
                                domain: String,
                                jsCookies: String,
                            ) {
                                val currentUrl = this@apply.url ?: "https://$domain"
                                val fullCookies =
                                    CookieManager.getInstance().getCookie(currentUrl)
                                        ?: CookieManager.getInstance().getCookie("https://$domain")
                                        ?: jsCookies
                                CookieManager.getInstance().flush()
                                this@apply.post {
                                    isCaptchaShowing = false
                                    onCaptchaStateChanged(false)
                                    onIsBufferingChanged(false)
                                    onCloudflareCleared(domain, fullCookies)
                                }
                            }
                        },
                        "AndroidBridge",
                    )

                    webChromeClient = playerWebChromeClient()
                    webViewClient =
                        playerWebViewClient(
                            url = videoUrl,
                            onPlayerError = onPlayerError,
                            onCookiesUpdated = { pageUrl ->
                                val host = pageUrl.toHttpUrlOrNull()?.host
                                if (!host.isNullOrBlank()) {
                                    val cookies = CookieManager.getInstance().getCookie(pageUrl)
                                    if (!cookies.isNullOrBlank()) {
                                        CookieManager.getInstance().flush()
                                        onCloudflareCleared(host, cookies)
                                    }
                                }
                            },
                        )

                    // Pre-seed cookies from NetworkClient before loading
                    NetworkClient.preSeedCookiesForUrl(videoUrl)

                    loadedVideoUrl = videoUrl
                    loadUrl(videoUrl)
                    webView = this
                    onWebViewProvided(WeakReference(this))
                }
            },
            update = { view ->
                // ONLY reload if the videoUrl parameter itself changed from the outside,
                // NEVER if view.url internally redirected / navigated through Cloudflare or player hosts!
                if (videoUrl != loadedVideoUrl) {
                    loadedVideoUrl = videoUrl
                    isVideoReady = false
                    NetworkClient.preSeedCookiesForUrl(videoUrl)
                    view.loadUrl(videoUrl)
                }
            },
            onRelease = { view ->
                view.destroy()
                webView = null
            },
        )

        FilmanFullscreenLoader(
            isVisibleProvider = { !isVideoReady && !isCaptchaShowing },
        )
    }
}
