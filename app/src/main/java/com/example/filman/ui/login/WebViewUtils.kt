package com.example.filman.ui.login

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
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
import com.example.filman.config.FilmanConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal fun WebViewClient(
    isLoginLoading: () -> Boolean,
    onCookiesFetched: (String) -> Unit,
    onAuthFailed: () -> Unit,
) = object : WebViewClient() {
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        CookieManager.getInstance().flush()

        val cookies =
            CookieManager.getInstance().getCookie(FilmanConfig.BASE_URL)

        val isLoginUrl = url?.contains(FilmanConfig.LOGIN_PATH) == true

        if (cookies != null && cookies.contains("PHPSESSID")) {
            if (!isLoginUrl) {
                onCookiesFetched(cookies)
            } else {
                view?.evaluateJavascript("document.querySelector('input[name=\"password\"]') !== null") { result ->
                    if (result == "false") {
                        onCookiesFetched(cookies)
                    } else if (isLoginLoading()) {
                        onAuthFailed()
                    }
                }
            }
        } else if (isLoginLoading() && isLoginUrl) {
            onAuthFailed()
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

        return false // Allow normal navigation
    }
}

internal suspend fun WebView.bypassRecaptchaAndLogin(
    username: String,
    password: String,
    onRequiresManualSolve: () -> Unit,
) {
    val recaptchaScript = """
        document.querySelector('input[name="login"]').value = '$username';
        document.querySelector('input[name="password"]').value = '$password';
        var recaptcha = document.querySelector('.g-recaptcha, iframe[title*="recaptcha" i]');
        if (recaptcha) {
            recaptcha.scrollIntoView({behavior: 'instant', block: 'center', inline: 'center'});
            var rect = recaptcha.getBoundingClientRect();
            rect.left + (rect.width / 2) + ',' + (rect.top + (rect.height / 2));
        } else {
            'not_found';
        }
    """.trimIndent()
    val loginScript = """
        var submitBtn = document.querySelector('input[type="submit"], button[type="submit"], .btn-login');
        if (submitBtn) { 
            submitBtn.click(); 
        } else { 
            var form = document.querySelector('form');
            if (form) form.submit();
        }
    """.trimIndent()

    val captchaResult = evaluateJavascript(recaptchaScript)
    val cleanResult = captchaResult?.removeSurrounding("\"")
    if (cleanResult != null && cleanResult != "not_found" && cleanResult != "null") {
        val parts = cleanResult.split(",")
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

            delay(1.seconds)

            val challengeVisibleScript = """
                (function() {
                    var challenge = document.querySelector('iframe[title*="recaptcha challenge" i]');
                    if (challenge) {
                        var style = window.getComputedStyle(challenge.parentElement.parentElement);
                        if (style.visibility !== 'hidden' && style.display !== 'none' && style.opacity !== '0') {
                            return 'visible';
                        }
                    }
                    var bframe = document.querySelector('iframe[name*="bframe" i]');
                    if (bframe) {
                        var style = window.getComputedStyle(bframe.parentElement.parentElement);
                         if (style.visibility !== 'hidden' && style.display !== 'none' && style.opacity !== '0') {
                            return 'visible';
                        }
                    }
                    return 'hidden';
                })();
            """.trimIndent()

            val isChallengeVisible =
                evaluateJavascript(challengeVisibleScript)?.removeSurrounding("\"") == "visible"

            if (isChallengeVisible) {
                onRequiresManualSolve()
            } else {
                delay(1.seconds)
                evaluateJavascript(loginScript)
            }
        }
    }
}

private suspend fun WebView.evaluateJavascript(script: String) =
    suspendCancellableCoroutine { continuation ->
        evaluateJavascript(script) {
            continuation.resumeWith(Result.success(it))
        }
    }

@Composable
internal fun Modifier.pointerMovement(
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
                        -> onClickRequested(pointerX.floatValue, pointerY.floatValue)

                    else -> consumed = false
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

internal fun performClickAtCoordinates(webView: WebView?, x: Float, y: Float) {
    val downTime = SystemClock.uptimeMillis()
    val motionEventDown = MotionEvent.obtain(
        downTime,
        downTime,
        MotionEvent.ACTION_DOWN,
        x,
        y,
        0,
    )
    webView?.dispatchTouchEvent(motionEventDown)
    motionEventDown.recycle()

    val motionEventUp = MotionEvent.obtain(
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
