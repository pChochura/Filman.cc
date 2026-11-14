package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.ui.core.pointerMovement
import com.pointlessapps.filman.ui.core.performClickAtCoordinates
import com.pointlessapps.filman.ui.core.WebViewClient
import com.pointlessapps.filman.ui.core.PLAYER_PAUSE_SCRIPT
import com.pointlessapps.filman.ui.core.PLAYER_PLAY_SCRIPT
import com.pointlessapps.filman.ui.core.PLAYER_AUDIO_GATE_SCRIPT
import com.pointlessapps.filman.ui.core.PLAYER_AUDIO_LOCK_SCRIPT
import com.pointlessapps.filman.ui.core.PLAYER_AUDIO_UNLOCK_SCRIPT
import com.pointlessapps.filman.ui.core.getPlayerAspectRatioScript
import com.pointlessapps.filman.ui.core.getPlayerPlaybackSpeedScript
import com.pointlessapps.filman.ui.core.getPlayerSetSubtitleScript
import com.pointlessapps.filman.ui.core.getPlayerUserAgent
import com.pointlessapps.filman.ui.core.playerWebChromeClient
import com.pointlessapps.filman.ui.core.playerWebViewClient

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Log
import android.view.ViewGroup
import android.view.KeyEvent
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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.pointlessapps.filman.data.api.NetworkClient










import kotlinx.coroutines.delay
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
    onLoadingStateChanged: (Boolean) -> Unit = {},
    onCaptchaStateChanged: (Boolean) -> Unit = {},
    onCancelCaptcha: () -> Unit = {},
    overlayFocusRequester: FocusRequester? = null,
    pointerFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadedVideoUrl by remember { mutableStateOf<String?>(null) }
    var isVideoReady by remember { mutableStateOf(false) }
    var isCaptchaShowing by remember { mutableStateOf(false) }
    var boxWidth by remember { mutableIntStateOf(0) }
    var boxHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(videoUrl, isVideoReady, isCaptchaShowing) {
        onLoadingStateChanged(!isVideoReady && !isCaptchaShowing)
        if (!isVideoReady && !isCaptchaShowing) {
            delay(45.seconds)
            Log.w("WebViewPlayer", "Timed out waiting for video playback")
            onPlayerError()
        }
    }

    LaunchedEffect(selectedSubtitleUrl, webView) {
        val webView = webView ?: return@LaunchedEffect
        webView.evaluateJavascript(getPlayerSetSubtitleScript(selectedSubtitleUrl), null)
    }

    LaunchedEffect(isCaptchaShowing) {
        if (isCaptchaShowing) {
            onIsBufferingChanged(false)
            delay(100.milliseconds)
            if (overlayFocusRequester != null) {
                overlayFocusRequester.requestFocus()
            } else {
                pointerFocusRequester.requestFocus()
            }
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
                    onNavigateUp = {
                        overlayFocusRequester?.requestFocus()
                    },
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
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString = getPlayerUserAgent(context)
                    setBackgroundColor(Color.BLACK)

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    addJavascriptInterface(
                        object {
                            @Suppress("Unused")
                            @JavascriptInterface
                            fun onVideoReady(pageUrl: String) {
                                this@apply.post {
                                    if (!isVideoReady && this@apply.url == pageUrl && !isCaptchaShowing) {
                                        isVideoReady = true
                                        evaluateJavascript(PLAYER_AUDIO_UNLOCK_SCRIPT, null)
                                        onIsBufferingChanged(false)
                                        onIsPlayingChanged(true)
                                    }
                                }
                            }

                            @Suppress("Unused")
                            @JavascriptInterface
                            fun onAdDetected(pageUrl: String) {
                                this@apply.post {
                                    if (this@apply.url == pageUrl) isVideoReady = false
                                }
                            }

                            @Suppress("Unused")
                            @JavascriptInterface
                            fun onTimeUpdate(
                                currentTime: Double,
                                duration: Double,
                                pageUrl: String,
                            ) {
                                this@apply.post {
                                    if (isVideoReady && this@apply.url == pageUrl) {
                                        onIsBufferingChanged(false)
                                        onCurrentPositionChanged((currentTime * 1000).toLong())
                                        if (duration.isFinite()) {
                                            onDurationProvided((duration * 1000).toLong())
                                        }
                                    }
                                }
                            }

                            @Suppress("Unused")
                            @JavascriptInterface
                            fun onPlayStateChanged(playing: Boolean, pageUrl: String) {
                                this@apply.post {
                                    if (isVideoReady && this@apply.url == pageUrl) onIsPlayingChanged(playing)
                                }
                            }

                            @Suppress("Unused")
                            @JavascriptInterface
                            fun onBufferingChanged(buffering: Boolean, pageUrl: String) {
                                this@apply.post {
                                    if (isVideoReady && this@apply.url == pageUrl) onIsBufferingChanged(buffering)
                                }
                            }

                            @Suppress("Unused")
                            @JavascriptInterface
                            fun onError(pageUrl: String) {
                                this@apply.post {
                                    if (this@apply.url == pageUrl) onPlayerError()
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
                                        isVideoReady = false
                                        evaluateJavascript(PLAYER_AUDIO_LOCK_SCRIPT, null)
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
                                CookieManager.getInstance().flush()
                                this@apply.post {
                                    isCaptchaShowing = false
                                    onCaptchaStateChanged(false)
                                    onIsBufferingChanged(false)
                                }
                            }
                        },
                        "AndroidBridge",
                    )

                    webChromeClient = playerWebChromeClient()
                    val supportsAudioGate = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
                    if (supportsAudioGate) {
                        WebViewCompat.addDocumentStartJavaScript(this, PLAYER_AUDIO_GATE_SCRIPT, setOf("*"))
                    } else {
                        Log.e("WebViewPlayer", "Cannot safely mute this WebView during loading")
                    }
                    webViewClient =
                        playerWebViewClient(
                            url = videoUrl,
                            onPlayerError = onPlayerError,
                            onPageLoading = {
                                isVideoReady = false
                                isCaptchaShowing = false
                                onCaptchaStateChanged(false)
                                evaluateJavascript(PLAYER_AUDIO_LOCK_SCRIPT, null)
                            },
                        )

                    // Pre-seed cookies from NetworkClient before loading
                    NetworkClient.preSeedCookiesForUrl(videoUrl)

                    setOnKeyListener { _, keyCode, event ->
                        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                            if (isCaptchaShowing) {
                                onCancelCaptcha()
                                return@setOnKeyListener true
                            }
                        }
                        false
                    }

                    loadedVideoUrl = videoUrl
                    webView = this
                    onWebViewProvided(WeakReference(this))
                    if (supportsAudioGate) {
                        loadUrl(videoUrl)
                    } else {
                        post { onPlayerError() }
                    }
                }
            },
            update = { view ->
                // ONLY reload if the videoUrl parameter itself changed from the outside,
                // NEVER if view.url internally redirected / navigated through Cloudflare or player hosts!
                if (videoUrl != loadedVideoUrl) {
                    loadedVideoUrl = videoUrl
                    isVideoReady = false
                    isCaptchaShowing = false
                    onCaptchaStateChanged(false)
                    view.evaluateJavascript(PLAYER_AUDIO_LOCK_SCRIPT, null)
                    NetworkClient.preSeedCookiesForUrl(videoUrl)
                    view.loadUrl(videoUrl)
                }
            },
            onRelease = { view ->
                view.destroy()
                webView = null
            },
        )

    }
}
