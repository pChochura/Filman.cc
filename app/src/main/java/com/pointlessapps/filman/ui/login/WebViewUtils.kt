package com.pointlessapps.filman.ui.login

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebChromeClient
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
import com.pointlessapps.filman.config.FilmanConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONTokener
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri

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
                view?.evaluateJavascript(
                    """
                    (function() {
                        if (document.querySelector('input[name="password"]') !== null) {
                            var alert = document.querySelector('.alert.alert-danger');
                            if (alert) return alert.innerText.trim();
                            return 'false';
                        }
                        return 'true';
                    })();
                    """.trimIndent().replace("\n", " "),
                ) { result ->
                    val decodedResult = try {
                        JSONTokener(result).nextValue() as? String
                    } catch (e: Exception) {
                        result?.removeSurrounding("\"")
                    }

                    if (decodedResult == "true") {
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

        return false
    }
}

private const val FIND_V2_CHECKBOX_SCRIPT = """
    (function() {
        var recaptcha = document.querySelector('.g-recaptcha');
        if (recaptcha) {
            recaptcha.scrollIntoView({behavior: 'instant', block: 'center', inline: 'center'});
            var rect = recaptcha.getBoundingClientRect();
            return rect.left + (rect.width / 2) + ',' + (rect.top + (rect.height / 2));
        }
        return 'not_found';
    })();
"""

private const val SUBMIT_LOGIN_FORM_SCRIPT = """
    var submitBtn = document.querySelector('input[type="submit"], button[type="submit"], .btn-login');
    if (submitBtn) { 
        submitBtn.click(); 
    } else { 
        var form = document.querySelector('form');
        if (form) form.submit();
    }
"""

private const val CHECK_CHALLENGE_VISIBLE_SCRIPT = """
    (function() {
        var challenge = document.querySelector('iframe[title*="recaptcha challenge" i], iframe[name*="bframe" i], iframe[src*="bframe" i]');
        if (challenge) {
            var container = challenge.parentElement.parentElement;
            var style = window.getComputedStyle(container);
            if (style.visibility !== 'hidden' && style.display !== 'none' && style.opacity !== '0') {
                container.classList.add('captcha-container-tv');
                if (!document.getElementById('captcha-tv-style')) {
                    var s = document.createElement('style');
                    s.id = 'captcha-tv-style';
                    s.innerHTML = 'header, footer, #belt, #wrapper, .container, #cookies { display: none !important; } ' +
                        '.captcha-container-tv { position: fixed !important; top: 50% !important; left: 50% !important; ' +
                        'transform: translate(-50%, -50%) scale(1.4) !important; z-index: 2147483647 !important; } ' +
                        'body { background: #111 !important; height: 100vh !important; overflow: hidden !important; margin: 0 !important; }';
                    document.head.appendChild(s);
                }
                return 'visible';
            }
        }
        return 'hidden';
    })();
"""

private const val CHECK_STILL_VISIBLE_SCRIPT = """
    (function() {
        var challenge = document.querySelector('iframe[title*="recaptcha challenge" i], iframe[name*="bframe" i], iframe[src*="bframe" i]');
        if (challenge) {
            var style = window.getComputedStyle(challenge.parentElement.parentElement);
            if (style.visibility === 'hidden' || style.display === 'none' || style.opacity === '0') {
                return 'hidden';
            }
            return 'visible';
        }
        return 'hidden';
    })();
"""

private const val CHECK_TOKEN_FILLED_SCRIPT =
    "document.querySelector('.g-recaptcha-response') ? (document.querySelector('.g-recaptcha-response').value !== '' ? 'true' : 'false') : 'false'"

private const val CLEANUP_ISOLATION_SCRIPT =
    "var s = document.getElementById('captcha-tv-style'); if(s) s.remove();"

internal suspend fun WebView.bypassRecaptchaAndLogin(
    username: String,
    password: String,
    onRequiresManualSolve: () -> Unit,
) {
    fillCredentials(username, password)

    val hasCheckbox = clickV2CheckboxIfPresent()
    if (hasCheckbox) {
        handleV2CheckboxCaptcha(onRequiresManualSolve)
    } else {
        handleInvisibleCaptcha(onRequiresManualSolve)
    }
}

private suspend fun WebView.fillCredentials(username: String, password: String) {
    val script = """
        document.querySelector('input[name="login"]').value = '$username';
        document.querySelector('input[name="password"]').value = '$password';
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
    var challengeEmerged = false

    repeat(40) {
        delay(250.milliseconds)
        if (evaluateJavascript(CHECK_TOKEN_FILLED_SCRIPT)?.removeSurrounding("\"") == "true") {
            return@repeat
        }

        if (evaluateJavascript(CHECK_CHALLENGE_VISIBLE_SCRIPT)?.removeSurrounding("\"") == "visible") {
            challengeEmerged = true
            return@repeat
        }
    }

    if (challengeEmerged) {
        onRequiresManualSolve()
        waitForChallengeToDisappear()
    }

    evaluateJavascript(SUBMIT_LOGIN_FORM_SCRIPT)
}

private suspend fun WebView.handleInvisibleCaptcha(onRequiresManualSolve: () -> Unit) {
    evaluateJavascript(SUBMIT_LOGIN_FORM_SCRIPT)

    var challengeEmerged = false
    repeat(20) {
        delay(250.milliseconds)
        if (evaluateJavascript(CHECK_CHALLENGE_VISIBLE_SCRIPT)?.removeSurrounding("\"") == "visible") {
            challengeEmerged = true
            return@repeat
        }
    }

    if (challengeEmerged) {
        onRequiresManualSolve()
        waitForChallengeToDisappear()
        evaluateJavascript(SUBMIT_LOGIN_FORM_SCRIPT)
    }
}

private suspend fun WebView.waitForChallengeToDisappear() {
    var isSolved = false
    while (!isSolved) {
        delay(500.milliseconds)
        if (evaluateJavascript(CHECK_STILL_VISIBLE_SCRIPT)?.removeSurrounding("\"") == "hidden") {
            isSolved = true
            evaluateJavascript(CLEANUP_ISOLATION_SCRIPT)
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

internal fun playerWebViewClient(videoUrl: String) = object : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        view.evaluateJavascript(PLAYER_INJECTION_SCRIPT, null)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return !url.contains(videoUrl.toUri().host.orEmpty())
    }
}

internal fun playerWebChromeClient() = object : WebChromeClient() {
    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message?,
    ): Boolean {
        return false // block popups
    }
}

internal const val PLAYER_INJECTION_SCRIPT = """
(function() {
    // 1. Inject a black curtain over everything.
    // pointer-events: none allows the auto-clicker to click through it.
    var curtain = document.createElement('div');
    curtain.style.cssText = 'position:fixed; top:0; left:0; width:100vw; height:100vh; background:black; z-index:2147483647; pointer-events:none;';
    if (document.body) document.body.appendChild(curtain);
    
    // 2. Instantly inject CSS for the video and background
    var style = document.createElement('style');
    style.innerHTML = 'html, body { background: black !important; overflow: hidden !important; margin: 0 !important; padding: 0 !important; width: 100vw !important; height: 100vh !important; } video { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; z-index: 2147483646 !important; background: black !important; object-fit: contain !important; }';
    document.head.appendChild(style);

    function hookVideo(video) {
        if (video._hooked) return;
        video._hooked = true;

        // Also ensure all ancestors of the video have no clipping
        var el = video.parentElement;
        while (el && el !== document.body) {
            el.style.setProperty('overflow', 'visible', 'important');
            el.style.setProperty('position', 'static', 'important');
            el = el.parentElement;
        }

        video.removeAttribute('controls');
        video.removeAttribute('poster'); // Remove the thumbnail image
        
        video.addEventListener('timeupdate', function() {
            AndroidBridge.onTimeUpdate(video.currentTime, video.duration);
        });
        video.addEventListener('play', function() { AndroidBridge.onPlayStateChanged(true); });
        video.addEventListener('pause', function() { AndroidBridge.onPlayStateChanged(false); });
        video.play();
    }

    // Check if a video already exists
    var existing = document.querySelector('video');
    if (existing) {
        hookVideo(existing);
    }

    // Otherwise, watch for it to appear
    var observer = new MutationObserver(function(mutations) {
        var video = document.querySelector('video');
        if (video) {
            observer.disconnect();
            hookVideo(video);
        }
    });
    observer.observe(document.body || document.documentElement, { childList: true, subtree: true });

    // Auto-clicker to bypass the bot-check overlay as soon as it appears
    var autoClickInterval = setInterval(function() {
        var video = document.querySelector('video');
        // Stop clicking and remove curtain once the video is actually playing
        if (video && !video.paused && video.currentTime > 0) {
            if (curtain.parentNode) curtain.parentNode.removeChild(curtain);
            clearInterval(autoClickInterval);
            return;
        }
        
        // Simulate a click at the center of the viewport
        var clickEvent = new MouseEvent('click', {
            view: window,
            bubbles: true,
            cancelable: true,
            clientX: window.innerWidth / 2,
            clientY: window.innerHeight / 2
        });
        var el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
        if (el) el.dispatchEvent(clickEvent);
        else document.body.dispatchEvent(clickEvent);
        
        if (video) video.play();
    }, 500);
})();
"""

internal const val PLAYER_PLAY_SCRIPT = """
var clickEvent = new MouseEvent('click', {
    view: window,
    bubbles: true,
    cancelable: true,
    clientX: window.innerWidth / 2,
    clientY: window.innerHeight / 2
});
var el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
if (el) el.dispatchEvent(clickEvent);
else document.body.dispatchEvent(clickEvent);

if(document.querySelector('video')) document.querySelector('video').play();
"""

internal const val PLAYER_PAUSE_SCRIPT =
    "if(document.querySelector('video')) document.querySelector('video').pause();"

internal const val PLAYER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

internal fun getPlayerSeekScript(timeInSeconds: Double) =
    "if(document.querySelector('video')) document.querySelector('video').currentTime = $timeInSeconds;"
