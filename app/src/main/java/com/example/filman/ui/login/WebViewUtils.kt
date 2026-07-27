package com.example.filman.ui.login

import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
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

        val cookies =
            CookieManager.getInstance().getCookie(FilmanConfig.BASE_URL)
        if (cookies != null && cookies.contains("PHPSESSID")) {
            if (url?.removeSuffix("/") == FilmanConfig.BASE_URL) {
                onCookiesFetched(cookies)
            }
        } else if (isLoginLoading() && url == FilmanConfig.LOGIN_URL) {
            onAuthFailed()
        }
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
            
            val isChallengeVisible = evaluateJavascript(challengeVisibleScript)?.removeSurrounding("\"") == "visible"
            
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

private fun performClickAtCoordinates(webView: WebView?, x: Float, y: Float) {
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
