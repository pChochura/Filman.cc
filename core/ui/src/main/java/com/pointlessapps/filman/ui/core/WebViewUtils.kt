package com.pointlessapps.filman.ui.core

import android.os.Message
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.pointlessapps.filman.config.FilmanConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.time.Duration.Companion.milliseconds

fun WebViewClient(
    isLoginLoading: () -> Boolean,
    onCookiesFetched: (String) -> Unit,
    onAuthFailed: () -> Unit,
    onRequiresManualSolve: () -> Unit,
) = object : WebViewClient() {
    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        super.onPageFinished(view, url)
        CookieManager.getInstance().flush()

        val cookies = CookieManager.getInstance().getCookie(FilmanConfig.BASE_URL)
        val isLoginUrl = url?.contains(FilmanConfig.LOGIN_PATH) == true

        if (!isLoginUrl && !cookies.isNullOrBlank() && cookies.contains("PHPSESSID")) {
            onCookiesFetched(cookies)
            return
        }

        view?.evaluateJavascript(CHECK_PAGE_STATUS_SCRIPT) { result ->
            val statusObj =
                try {
                    val decoded = JSONTokener(result).nextValue()
                    if (decoded is String) JSONObject(decoded) else decoded as? JSONObject
                } catch (e: Exception) {
                    null
                }

            val status = statusObj?.optString("status")
            when (status) {
                "logged_in" -> {
                    if (!cookies.isNullOrBlank() && cookies.contains("PHPSESSID")) {
                        onCookiesFetched(cookies)
                    }
                }

                "challenge" -> {
                    onRequiresManualSolve()
                }

                "error" -> {
                    if (isLoginLoading()) {
                        onAuthFailed()
                    }
                }

                "login_form" -> {
                    if (isLoginLoading()) {
                        onAuthFailed()
                    }
                }
            }
        }
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        val url = request?.url?.toString() ?: return false

        // Block annoying ad overlays and mailto links
        if (url.startsWith("mailto:") || url.startsWith("intent:")) {
            return true // Block
        }

        if (request.isForMainFrame) {
            if (!url.contains("filman.cc") && !url.contains("google.com")) {
                return true // Block external click-jacking ads
            }
        }

        return false
    }
}

suspend fun WebView.bypassRecaptchaAndLogin(
    username: String,
    password: String,
    onRequiresManualSolve: () -> Unit,
) {
    fillCredentials(username, password)

    val challengeAlreadyVisible =
        evaluateJavascript(CHECK_CHALLENGE_VISIBLE_SCRIPT)?.removeSurrounding("\"") == "visible"
    if (challengeAlreadyVisible) {
        onRequiresManualSolve()
        return
    }

    val hasCheckbox = clickV2CheckboxIfPresent()
    if (hasCheckbox) {
        handleV2CheckboxCaptcha(onRequiresManualSolve)
    } else {
        handleInvisibleCaptcha(onRequiresManualSolve)
    }
}

private suspend fun WebView.fillCredentials(
    username: String,
    password: String,
) {
    val escapedUsername = JSONObject.quote(username)
    val escapedPassword = JSONObject.quote(password)
    val script =
        """
        (function() {
            var u = document.querySelector('input[name="login"]');
            var p = document.querySelector('input[name="password"]');
            if (u) {
                u.value = $escapedUsername;
                u.dispatchEvent(new Event('input', { bubbles: true }));
                u.dispatchEvent(new Event('change', { bubbles: true }));
            }
            if (p) {
                p.value = $escapedPassword;
                p.dispatchEvent(new Event('input', { bubbles: true }));
                p.dispatchEvent(new Event('change', { bubbles: true }));
            }
            var rem = document.querySelector('input[name="remember"]');
            if (rem) rem.checked = true;
        })();
        """.trimIndent()
    evaluateJavascript(script)
}

private suspend fun WebView.clickV2CheckboxIfPresent(): Boolean {
    val captchaResult = evaluateJavascript(FIND_V2_CHECKBOX_SCRIPT)?.removeSurrounding("\"")
    if (captchaResult != null && captchaResult != "not_found" && captchaResult != "null") {
        val parts = captchaResult.split(",")
        if (parts.size == 2) {
            val cx = parts[0].toFloatOrNull() ?: 0f
            val cy = parts[1].toFloatOrNull() ?: 0f
            val density = context.resources.displayMetrics.density

            delay(100.milliseconds)
            performClickAtCoordinates(
                webView = this,
                x = cx * density,
                y = cy * density,
            )
            return true
        }
    }
    return false
}

private suspend fun WebView.handleV2CheckboxCaptcha(onRequiresManualSolve: () -> Unit) {
    var isTokenFilled = false
    var challengeEmerged = false

    repeat(14) {
        delay(250.milliseconds)
        if (evaluateJavascript(CHECK_TOKEN_FILLED_SCRIPT)?.removeSurrounding("\"") == "true") {
            isTokenFilled = true
            return@repeat
        }

        if (evaluateJavascript(CHECK_CHALLENGE_VISIBLE_SCRIPT)?.removeSurrounding("\"") == "visible") {
            challengeEmerged = true
            return@repeat
        }
    }

    if (isTokenFilled) {
        evaluateJavascript(SUBMIT_LOGIN_FORM_SCRIPT)
        return
    }

    if (challengeEmerged) {
        onRequiresManualSolve()
        return
    }

    onRequiresManualSolve()
}

private suspend fun WebView.handleInvisibleCaptcha(onRequiresManualSolve: () -> Unit) {
    evaluateJavascript(SUBMIT_LOGIN_FORM_SCRIPT)

    var challengeEmerged = false
    repeat(10) {
        delay(250.milliseconds)
        if (evaluateJavascript(CHECK_CHALLENGE_VISIBLE_SCRIPT)?.removeSurrounding("\"") == "visible") {
            challengeEmerged = true
            return@repeat
        }
    }

    if (challengeEmerged) {
        onRequiresManualSolve()
    }
}

private suspend fun WebView.evaluateJavascript(script: String) =
    suspendCancellableCoroutine { continuation ->
        evaluateJavascript(script) {
            continuation.resumeWith(Result.success(it))
        }
    }

@Composable
fun Modifier.pointerMovement(
    boxWidthProvider: () -> Int,
    boxHeightProvider: () -> Int,
    onScrollRequested: (Int) -> Unit,
    onClickRequested: (Float, Float) -> Unit,
    enabled: Boolean,
): Modifier {
    val size = 16.dp
    val borderWidth = 1.dp
    val color = MaterialTheme.colorScheme.primary
    val borderColor = MaterialTheme.colorScheme.surfaceVariant

    val pointerX = remember { mutableFloatStateOf(0f) }
    val pointerY = remember { mutableFloatStateOf(0f) }

    return this
        .onPreviewKeyEvent { event ->
            if (!enabled) return@onPreviewKeyEvent false

            val boxWidth = boxWidthProvider()
            val boxHeight = boxHeightProvider()

            val speed = 25f
            var consumed = true

            if (event.type == KeyEventType.KeyDown) {
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        pointerY.floatValue = (pointerY.floatValue - speed).coerceAtLeast(0f)
                        if (pointerY.floatValue < 50f) onScrollRequested(-50)
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        pointerY.floatValue =
                            (pointerY.floatValue + speed).coerceAtMost(boxHeight.toFloat())
                        if (pointerY.floatValue > boxHeight - 50f) onScrollRequested(50)
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        pointerX.floatValue = (pointerX.floatValue - speed).coerceAtLeast(0f)
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        pointerX.floatValue =
                            (pointerX.floatValue + speed).coerceAtMost(boxWidth.toFloat())
                    }

                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                        -> {
                        onClickRequested(pointerX.floatValue, pointerY.floatValue)
                    }

                    else -> {
                        consumed = false
                    }
                }
            }

            return@onPreviewKeyEvent consumed
        }
        .drawWithContent {
            drawContent()
            if (enabled) {
                drawCircle(
                    color = borderColor,
                    radius = size.toPx() / 2 + borderWidth.toPx(),
                    center = Offset(pointerX.floatValue, pointerY.floatValue),
                )
                drawCircle(
                    color = color,
                    radius = size.toPx() / 2,
                    center = Offset(pointerX.floatValue, pointerY.floatValue),
                )
            }
        }
}

fun performClickAtCoordinates(
    webView: WebView?,
    x: Float,
    y: Float,
) {
    val downTime = SystemClock.uptimeMillis()
    val motionEventDown =
        MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0,
        )
    webView?.dispatchTouchEvent(motionEventDown)
    motionEventDown.recycle()

    val motionEventUp =
        MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_UP,
            x,
            y,
            0,
        )
    webView?.dispatchTouchEvent(motionEventUp)
    motionEventUp.recycle()
}

fun playerWebViewClient(
    url: String,
    onPlayerError: () -> Unit,
    onCookiesUpdated: (String) -> Unit = {},
) = object : WebViewClient() {
    override fun onPageFinished(
        view: WebView,
        pageUrl: String,
    ) {
        super.onPageFinished(view, pageUrl)
        onCookiesUpdated(pageUrl)
        view.evaluateJavascript(getPlayerInjectionScript(pageUrl), null)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            onPlayerError()
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        val headers = errorResponse?.responseHeaders ?: emptyMap()
        val isCloudflare =
            headers.entries.any {
                it.key.startsWith("cf-", ignoreCase = true) ||
                        (it.key.equals(
                            "Server",
                            ignoreCase = true,
                        ) && it.value.contains("cloudflare", ignoreCase = true))
            }
        val statusCode = errorResponse?.statusCode ?: 0
        // Cloudflare challenge pages often return 403 or 503 - do not abort on them
        if (
            request?.isForMainFrame == true &&
            (statusCode == 404 || statusCode == 500 || ((statusCode == 403 || statusCode == 503) && !isCloudflare))
        ) {
            onPlayerError()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun shouldOverrideUrlLoading(
        view: WebView,
        url: String,
    ): Boolean = url.startsWith("intent:") || url.startsWith("mailto:") || url.startsWith("market:")
}

fun playerWebChromeClient() =
    object : WebChromeClient() {
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?,
        ): Boolean {
            return false // block popups
        }
    }

// ============================================================================
// Provider-specific injection scripts
// ============================================================================

/**
 * Returns the appropriate injection script for the given URL.
 * Each provider gets a tailored script that knows how to isolate its player.
 */
