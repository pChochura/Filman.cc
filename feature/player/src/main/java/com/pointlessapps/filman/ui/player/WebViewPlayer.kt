package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.ui.core.pointerMovement
import com.pointlessapps.filman.ui.core.WebViewPointer
import com.pointlessapps.filman.ui.core.rememberWebViewPointerState
import com.pointlessapps.filman.ui.core.performClickAtCoordinates
import com.pointlessapps.filman.ui.core.WebViewClient
import com.pointlessapps.filman.ui.core.configureBrowserSession
import com.pointlessapps.filman.ui.core.PLAYER_PAUSE_SCRIPT
import com.pointlessapps.filman.ui.core.PLAYER_PLAY_SCRIPT
import com.pointlessapps.filman.ui.core.PLAYER_AUDIO_GATE_SCRIPT
import com.pointlessapps.filman.ui.core.PLAYER_AUDIO_LOCK_SCRIPT
import com.pointlessapps.filman.ui.core.PLAYER_AUDIO_UNLOCK_SCRIPT
import com.pointlessapps.filman.ui.core.getPlayerAspectRatioScript
import com.pointlessapps.filman.ui.core.getPlayerPlaybackSpeedScript
import com.pointlessapps.filman.ui.core.getPlayerResumeScript
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
    startPositionMs: Long,
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
    var pendingResumePositionMs by remember(videoUrl) { mutableStateOf(startPositionMs.coerceAtLeast(0)) }
    var lastPositionMs by remember(videoUrl) { mutableStateOf(startPositionMs.coerceAtLeast(0)) }
    var resumeAttemptInFlight by remember(videoUrl) { mutableStateOf(false) }
    var resumeSeekApplied by remember(videoUrl) { mutableStateOf(false) }
    var boxWidth by remember { mutableIntStateOf(0) }
    var boxHeight by remember { mutableIntStateOf(0) }
    val pointerState = rememberWebViewPointerState()

    LaunchedEffect(videoUrl, isVideoReady, isCaptchaShowing) {
        onLoadingStateChanged(!isVideoReady && !isCaptchaShowing)
        if (!isVideoReady && !isCaptchaShowing) {
            delay(45.seconds)
            Log.w("WebViewPlayer", "Timed out waiting for video playback")
            onPlayerError()
        }
    }

    LaunchedEffect(videoUrl, isVideoReady, pendingResumePositionMs) {
        if (isVideoReady && pendingResumePositionMs > 0) {
            delay(15.seconds)
            Log.w("WebViewPlayer", "Timed out restoring playback position")
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
                    state = pointerState,
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
                    configureBrowserSession(getPlayerUserAgent(context))
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.databaseEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
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
                                    if (this@apply.url == pageUrl) {
                                        isVideoReady = false
                                        resumeSeekApplied = false
                                    }
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
                                        val resumePosition = pendingResumePositionMs
                                        if (resumePosition > 0) {
                                            if (resumeSeekApplied && isResumePositionReached(currentTime, duration, resumePosition)) {
                                                pendingResumePositionMs = 0
                                                resumeSeekApplied = false
                                            } else if (!resumeSeekApplied && !resumeAttemptInFlight) {
                                                resumeAttemptInFlight = true
                                                evaluateJavascript(getPlayerResumeScript(resumePosition)) { result ->
                                                    resumeAttemptInFlight = false
                                                    if (this@apply.url == pageUrl && isVideoReady &&
                                                        pendingResumePositionMs == resumePosition && result == "true"
                                                    ) {
                                                        resumeSeekApplied = true
                                                    }
                                                }
                                            }
                                            if (pendingResumePositionMs > 0) return@post
                                        }
                                        if (currentTime.isFinite() && currentTime >= 0) {
                                            lastPositionMs = (currentTime * 1000).toLong()
                                            onCurrentPositionChanged(lastPositionMs)
                                        }
                                        if (duration.isFinite() && duration > 0) {
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
                            fun onCaptchaStateChanged(isShowing: Boolean, pageUrl: String) {
                                this@apply.post {
                                    if (this@apply.url != pageUrl) return@post
                                    isCaptchaShowing = isShowing
                                    onCaptchaStateChanged(isShowing)
                                    if (isShowing) {
                                        isVideoReady = false
                                        resumeSeekApplied = false
                                        evaluateJavascript(PLAYER_AUDIO_LOCK_SCRIPT, null)
                                        onIsBufferingChanged(false)
                                    }
                                }
                            }

                            @Suppress("Unused")
                            @JavascriptInterface
                            fun onCloudflareCleared(pageUrl: String) {
                                this@apply.post {
                                    if (this@apply.url != pageUrl) return@post
                                    CookieManager.getInstance().flush()
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
                                if (isVideoReady) pendingResumePositionMs = lastPositionMs
                                resumeAttemptInFlight = false
                                resumeSeekApplied = false
                                isVideoReady = false
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
        WebViewPointer(pointerState, enabled = isCaptchaShowing)

    }
}

internal fun isResumePositionReached(currentTime: Double, duration: Double, positionMs: Long): Boolean {
    if (!currentTime.isFinite() || !duration.isFinite() || duration <= 0) return false
    val target = minOf(positionMs / 1000.0, (duration - 1).coerceAtLeast(0.0))
    return currentTime >= (target - 1).coerceAtLeast(0.0)
}
