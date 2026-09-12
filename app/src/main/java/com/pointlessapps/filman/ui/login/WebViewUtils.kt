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
import com.pointlessapps.filman.ui.player.PlayerConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.time.Duration.Companion.milliseconds

internal fun WebViewClient(
    isLoginLoading: () -> Boolean,
    onCookiesFetched: (String) -> Unit,
    onAuthFailed: () -> Unit,
    onRequiresManualSolve: () -> Unit,
) = object : WebViewClient() {
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        CookieManager.getInstance().flush()

        val cookies = CookieManager.getInstance().getCookie(FilmanConfig.BASE_URL)
        val isLoginUrl = url?.contains(FilmanConfig.LOGIN_PATH) == true

        if (!isLoginUrl && !cookies.isNullOrBlank() && cookies.contains("PHPSESSID")) {
            onCookiesFetched(cookies)
            return
        }

        view?.evaluateJavascript(CHECK_PAGE_STATUS_SCRIPT) { result ->
            val statusObj = try {
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

private const val CHECK_PAGE_STATUS_SCRIPT = """
    (function() {
        try {
            var html = (document.documentElement ? document.documentElement.innerHTML : '').toLowerCase();
            if (html.includes('challenges.cloudflare.com') || html.includes('cf-turnstile') || html.includes('just a moment') || document.title.toLowerCase().includes('just a moment')) {
                return JSON.stringify({ status: 'challenge' });
            }
            var alert = document.querySelector('.alert.alert-danger, #flash .alert, .alert');
            if (alert && alert.innerText && alert.innerText.trim().length > 0) {
                return JSON.stringify({ status: 'error', message: alert.innerText.trim() });
            }
            var isGuest = (typeof window.config !== 'undefined' && typeof window.config.guest !== 'undefined') ? window.config.guest : null;
            var hasLogout = document.querySelector('a[href*="/wyloguj"], a[href*="/logout"], a[href*="logout"], a[href*="/profil"]') !== null;
            var hasLoginForm = document.querySelector('input[name="password"]') !== null || document.querySelector('input[name="login"]') !== null;

            if (isGuest === false || hasLogout || (!hasLoginForm && !alert)) {
                return JSON.stringify({ status: 'logged_in' });
            }
            if (hasLoginForm) {
                return JSON.stringify({ status: 'login_form' });
            }
            return JSON.stringify({ status: 'unknown' });
        } catch(e) {
            return JSON.stringify({ status: 'unknown' });
        }
    })();
"""

private const val FIND_V2_CHECKBOX_SCRIPT = """
    (function() {
        var iframe = document.querySelector('.g-recaptcha iframe[src*="recaptcha"], iframe[src*="recaptcha/api2/anchor"]');
        if (iframe) {
            iframe.scrollIntoView({behavior: 'instant', block: 'center', inline: 'center'});
            var rect = iframe.getBoundingClientRect();
            var cx = rect.left + 28;
            var cy = rect.top + (rect.height / 2);
            return cx + ',' + cy;
        }
        var recaptcha = document.querySelector('.g-recaptcha');
        if (recaptcha) {
            recaptcha.scrollIntoView({behavior: 'instant', block: 'center', inline: 'center'});
            var rect = recaptcha.getBoundingClientRect();
            var cx = rect.left + 28;
            var cy = rect.top + 39;
            return cx + ',' + cy;
        }
        return 'not_found';
    })();
"""

private const val SUBMIT_LOGIN_FORM_SCRIPT = """
    (function() {
        var submitBtn = document.querySelector('button[type="submit"], input[type="submit"], .btn-primary, .btn-login');
        if (submitBtn) {
            submitBtn.click();
        } else {
            var form = document.querySelector('form#signin-form, form');
            if (form) {
                if (typeof form.submit === 'function') {
                    form.submit();
                } else {
                    HTMLFormElement.prototype.submit.call(form);
                }
            }
        }
    })();
"""

private const val CHECK_CHALLENGE_VISIBLE_SCRIPT = """
    (function() {
        var challenge = document.querySelector('iframe[title*="recaptcha challenge" i], iframe[name*="bframe" i], iframe[src*="bframe" i]');
        if (challenge) {
            var style = window.getComputedStyle(challenge.parentElement || challenge);
            if (style.visibility !== 'hidden' && style.display !== 'none' && style.opacity !== '0') {
                return 'visible';
            }
        }
        var cf = document.querySelector('.cf-turnstile, iframe[src*="challenges.cloudflare.com"], #challenge-stage');
        if (cf) {
            return 'visible';
        }
        return 'hidden';
    })();
"""

private const val CHECK_TOKEN_FILLED_SCRIPT =
    "document.querySelector('.g-recaptcha-response') ? (document.querySelector('.g-recaptcha-response').value !== '' ? 'true' : 'false') : 'false'"

internal suspend fun WebView.bypassRecaptchaAndLogin(
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

private suspend fun WebView.fillCredentials(username: String, password: String) {
    val escapedUsername = JSONObject.quote(username)
    val escapedPassword = JSONObject.quote(password)
    val script = """
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

internal fun playerWebViewClient(
    url: String,
    onPlayerError: () -> Unit,
    onCookiesUpdated: (String) -> Unit = {},
) = object : WebViewClient() {
    override fun onPageFinished(view: WebView, pageUrl: String) {
        super.onPageFinished(view, pageUrl)
        onCookiesUpdated(pageUrl)
        view.evaluateJavascript(getPlayerInjectionScript(pageUrl), null)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: android.webkit.WebResourceError?,
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            onPlayerError()
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: android.webkit.WebResourceResponse?,
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        val headers = errorResponse?.responseHeaders ?: emptyMap()
        val isCloudflare = headers.entries.any {
            it.key.startsWith("cf-", ignoreCase = true) ||
                    (it.key.equals("Server", ignoreCase = true) && it.value.contains("cloudflare", ignoreCase = true))
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
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return url.startsWith("intent:") || url.startsWith("mailto:") || url.startsWith("market:")
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

// ============================================================================
// Provider-specific injection scripts
// ============================================================================

/**
 * Returns the appropriate injection script for the given URL.
 * Each provider gets a tailored script that knows how to isolate its player.
 */
internal fun getPlayerInjectionScript(url: String): String {
    val lower = url.lowercase()
    val providerScript = when {
        lower.contains("play.ekino.link") || lower.contains("ekino.ws/watch/") -> EKINO_INTERMEDIATE_SCRIPT
        lower.contains("dood") || lower.contains("d0o0d") || lower.contains("myvidplay") -> DOODSTREAM_SCRIPT
        lower.contains("vidmoly") || lower.contains("luluvdo") || lower.contains("lulustream") -> VIDMOLY_SCRIPT
        Regex("""https?://(?:sb[a-zA-Z0-9]*|pelistop|cloudemb|vidgomunime|keephealth|streamsss|lvturbo|ssbstream)\.[a-z]+/.*""").containsMatchIn(lower) ||
                lower.contains("streamsb") || lower.contains("cloudemb") || lower.contains("byse") -> STREAMSB_SCRIPT
        lower.contains("onlystream") || lower.contains("savefiles") || lower.contains("vidara") ||
                lower.contains("upzone") -> GENERIC_IFRAME_SCRIPT
        else -> GENERIC_FALLBACK_SCRIPT
    }

    return "(function() {\n$PLAYER_BASE_SCRIPT\n$providerScript\n})();\n"
}

/**
 * Common base script injected for ALL providers.
 * Real Player Isolation:
 * - Styles <video> to fill screen with black background
 * - Styles primary player <iframe> to fill screen if no video element
 * - Hides distracting headers, sidebars, footers, popups, and banners
 * - Detects Cloudflare Turnstile / challenge and ensures it is centered and fully visible
 * - Coordinates with AndroidBridge for auto-clicks, captcha detection, and cookie persistence
 */
private const val PLAYER_BASE_SCRIPT = """
    // --- CLOUDFLARE CHALLENGE DETECTION HELPERS ---
    function checkIsCloudflare() {
        var title = (document.title || '').toLowerCase();
        if (title.includes('just a moment') || title.includes('attention required') || title.includes('cloudflare')) {
            return true;
        }
        if (document.querySelector('iframe[src*="challenges.cloudflare.com"], iframe[src*="turnstile"], .cf-turnstile')) {
            return true;
        }
        if (document.getElementById('challenge-stage') ||
            document.getElementById('challenge-form') ||
            document.getElementById('challenge-running')) {
            return true;
        }
        return false;
    }

    function removePlayerStyle() {
        var s = document.getElementById('filman_video_style');
        if (s && s.parentNode) s.parentNode.removeChild(s);
    }

    function applyPlayerStyle() {
        if (checkIsCloudflare()) return;
        var style = document.getElementById('filman_video_style');
        if (!style) {
            style = document.createElement('style');
            style.id = 'filman_video_style';
            style.innerHTML = `
                html, body {
                    background: black !important;
                    overflow: hidden !important;
                    margin: 0 !important;
                    padding: 0 !important;
                    width: 100vw !important;
                    height: 100vh !important;
                }
                /* Hide general site clutter */
                header, footer, nav, .header, .footer, .navbar, .ad, .ads, .advertisement,
                .banner, .alert:not(.alert-challenge), #refresh_btn, #belt, #cookies, .cookie-notice,
                .top-bar, .side-bar, .sidebar, #header, #footer {
                    display: none !important;
                }
                /* Stretched video element */
                video {
                    position: fixed !important;
                    top: 0 !important;
                    left: 0 !important;
                    width: 100vw !important;
                    height: 100vh !important;
                    background: black !important;
                    object-fit: contain !important;
                    z-index: 2147483640 !important;
                    visibility: visible !important;
                }
                /* Stretched iframe player when no direct video exists */
                iframe.filman-player-frame {
                    position: fixed !important;
                    top: 0 !important;
                    left: 0 !important;
                    width: 100vw !important;
                    height: 100vh !important;
                    border: none !important;
                    margin: 0 !important;
                    padding: 0 !important;
                    background: black !important;
                    z-index: 2147483640 !important;
                    visibility: visible !important;
                }
                /* Intermediate button (Ekino "Przejdź do odtwarzacza") */
                .buttonprch {
                    position: fixed !important;
                    top: 50% !important;
                    left: 50% !important;
                    transform: translate(-50%, -50%) !important;
                    z-index: 2147483645 !important;
                    display: inline-block !important;
                    visibility: visible !important;
                }
                .warning_ch {
                    margin: 0 !important;
                    padding: 0 !important;
                    background: black !important;
                    width: 100vw !important;
                    height: 100vh !important;
                }
            `;
            document.head.appendChild(style);
        }
    }

    // Only apply player style if NOT on a Cloudflare challenge
    if (!checkIsCloudflare()) {
        applyPlayerStyle();
    } else {
        removePlayerStyle();
    }

    // --- VIDEO HOOKING ---
    function hookVideo(video) {
        if (checkIsCloudflare()) return;
        if (video._hooked) return;
        video._hooked = true;

        var el = video.parentElement;
        while (el && el !== document.body) {
            el.style.setProperty('overflow', 'visible', 'important');
            el.style.setProperty('position', 'static', 'important');
            el = el.parentElement;
        }

        video.removeAttribute('controls');
        video.removeAttribute('poster');

        if (typeof window.filmanPlaybackSpeed !== 'undefined') {
            video.playbackRate = window.filmanPlaybackSpeed;
        }
        if (typeof window.filmanAspectRatio !== 'undefined') {
            video.style.setProperty('object-fit', window.filmanAspectRatio, 'important');
        }

        video.addEventListener('timeupdate', function() {
            AndroidBridge.onTimeUpdate(video.currentTime, video.duration);
        });
        video.addEventListener('play', function() { AndroidBridge.onPlayStateChanged(true); AndroidBridge.onBufferingChanged(false); });
        video.addEventListener('pause', function() { AndroidBridge.onPlayStateChanged(false); });
        video.addEventListener('waiting', function() { AndroidBridge.onBufferingChanged(true); });
        video.addEventListener('playing', function() { AndroidBridge.onBufferingChanged(false); });
        video.addEventListener('canplay', function() { AndroidBridge.onBufferingChanged(false); });
        tryPlay(video);
    }

    function tryPlay(video) {
        if (typeof jwplayer === 'function') {
            try { jwplayer().play(); } catch(e) {}
        }
        if (video) {
            try {
                var p = video.play();
                if (p && typeof p.catch === 'function') {
                    p.catch(function(err) {});
                }
            } catch(e) {}
        }
    }

    // --- SMART VIDEO SELECTION ---
    function findBestVideo() {
        var videos = document.querySelectorAll('video');
        if (videos.length === 0) return null;
        if (videos.length === 1) return videos[0];

        var best = null;
        var bestArea = 0;
        for (var i = 0; i < videos.length; i++) {
            var v = videos[i];
            if (v.muted && v.autoplay) continue;
            var rect = v.getBoundingClientRect();
            var area = rect.width * rect.height;
            var hasSrc = v.src || v.querySelector('source');
            if (area > bestArea || (hasSrc && area >= bestArea * 0.5)) {
                best = v;
                bestArea = area;
            }
        }
        return best || videos[0];
    }

    // --- IFRAME PLAYER ISOLATION ---
    // If there is no <video> element on the page, tag the main player iframe so it fills the screen
    function isolateIframePlayer() {
        if (checkIsCloudflare()) return;
        if (document.querySelector('video')) return;
        var iframes = document.querySelectorAll('iframe');
        for (var i = 0; i < iframes.length; i++) {
            var f = iframes[i];
            var s = f.src || f.getAttribute('data-src') || '';
            if (s && !s.includes('challenges.cloudflare.com') && !s.includes('turnstile') && !s.includes('google.com/recaptcha') && !s.includes('doubleclick')) {
                f.classList.add('filman-player-frame');
                var p = f.parentElement;
                while (p && p !== document.body) {
                    p.style.setProperty('overflow', 'visible', 'important');
                    p.style.setProperty('position', 'static', 'important');
                    p = p.parentElement;
                }
                break;
            }
        }
    }

    // Check for existing video or iframe player
    if (!checkIsCloudflare()) {
        var existingVideo = findBestVideo();
        if (existingVideo) {
            hookVideo(existingVideo);
        } else {
            isolateIframePlayer();
        }
    }

    // MutationObserver to catch dynamically injected videos or player iframes
    var videoObserver = new MutationObserver(function(mutations) {
        if (checkIsCloudflare()) return;
        var video = findBestVideo();
        if (video && !video._hooked) {
            hookVideo(video);
        } else {
            isolateIframePlayer();
        }
    });
    videoObserver.observe(document.body || document.documentElement, { childList: true, subtree: true });

    // --- CONTINUOUS AUTOPLAY RETRY ---
    var baseAutoPlayInterval = setInterval(function() {
        if (window._hasCaptchaFlag || checkIsCloudflare()) return;
        var video = findBestVideo();
        if (video) {
            if (!video._hooked) {
                hookVideo(video);
            }
            if (!video.paused && video.currentTime > 0) {
                clearInterval(baseAutoPlayInterval);
                return;
            }
            tryPlay(video);
        }
    }, 500);

    // --- DEAD VIDEO DETECTION ---
    var checkDeadVideoInterval = setInterval(function() {
        if (window._hasCaptchaFlag || checkIsCloudflare()) return;
        var bodyText = document.body ? document.body.innerText.toLowerCase() : '';
        if (bodyText.includes('video not found') || bodyText.includes('file was deleted') ||
            bodyText.includes('no longer available') || bodyText.includes('file not found') ||
            bodyText.includes('deleted by the owner') || bodyText.includes('video has been flagged') ||
            bodyText.includes('this video has been removed')) {
            clearInterval(checkDeadVideoInterval);
            AndroidBridge.onError();
        }
    }, 1000);

    // --- VIDEO TIMEOUT (35 seconds) ---
    var startTime = Date.now();
    var videoTimeoutInterval = setInterval(function() {
        if (window._hasCaptchaFlag || checkIsCloudflare()) {
            startTime = Date.now(); // Do not time out while challenge is active
            return;
        }
        var video = findBestVideo();
        if (video) {
            clearInterval(videoTimeoutInterval);
            return;
        }
        if (Date.now() - startTime > 35000) {
            clearInterval(videoTimeoutInterval);
            AndroidBridge.onError();
        }
    }, 1000);

    // --- CLOUDFLARE CHALLENGE DETECTION ---
    window._hasCaptchaFlag = checkIsCloudflare();
    if (window._hasCaptchaFlag) {
        removePlayerStyle();
        if (typeof AndroidBridge !== 'undefined' && AndroidBridge.onCaptchaStateChanged) {
            AndroidBridge.onCaptchaStateChanged(true);
        }
    }

    var captchaInterval = setInterval(function() {
        var hasCaptcha = checkIsCloudflare();

        if (hasCaptcha) {
            if (!window._hasCaptchaFlag) {
                window._hasCaptchaFlag = true;
                removePlayerStyle();
                if (typeof AndroidBridge !== 'undefined' && AndroidBridge.onCaptchaStateChanged) {
                    AndroidBridge.onCaptchaStateChanged(true);
                }
            }

            // Look specifically for the rendered Turnstile challenge iframe
            var turnstileIframe = document.querySelector('iframe[src*="challenges.cloudflare.com"], iframe[src*="turnstile"]');
            if (turnstileIframe) {
                var rect = turnstileIframe.getBoundingClientRect();
                if (rect.width >= 100 && rect.height >= 30) {
                    if (!window._captchaClicked) {
                        window._captchaClicked = true;
                        setTimeout(function() { window._captchaClicked = false; }, 4000);
                        turnstileIframe.scrollIntoView({behavior: 'instant', block: 'center', inline: 'center'});
                        // Turnstile checkbox is located on the left side of the iframe (~28px from left edge)
                        var cx = rect.left + 28;
                        var cy = rect.top + (rect.height / 2);
                        if (typeof AndroidBridge !== 'undefined' && AndroidBridge.onCaptchaFound) {
                            AndroidBridge.onCaptchaFound(cx, cy);
                        }
                    }
                }
            }
        } else {
            if (window._hasCaptchaFlag) {
                window._hasCaptchaFlag = false;
                startTime = Date.now(); // Reset timeout once challenge is cleared
                applyPlayerStyle();
                if (typeof AndroidBridge !== 'undefined') {
                    if (AndroidBridge.onCaptchaStateChanged) AndroidBridge.onCaptchaStateChanged(false);
                    if (AndroidBridge.onCloudflareCleared) AndroidBridge.onCloudflareCleared(window.location.hostname, document.cookie);
                }
            }
        }
    }, 600);
"""

/**
 * Ekino intermediate pages (/watch/f/ and play.ekino.link).
 * These pages show a "Przejdź do odtwarzacza" button or embed an iframe.
 * We navigate through the chain to reach the actual video host.
 */
private const val EKINO_INTERMEDIATE_SCRIPT = """
    var ekinoNavInterval = setInterval(function() {
        if (window._hasCaptchaFlag || (typeof checkIsCloudflare === 'function' && checkIsCloudflare())) return;

        // Step 1: Click the "Przejdź do odtwarzacza" button if present on ekino.ws
        // NOTE: On play.ekino.link, there is an <a href="" class="buttonprch"> ("Wróć na stronę")
        // whose empty href resolves to current page URL. We must NEVER follow buttonprch on play.ekino.link!
        if (!window.location.href.includes('play.ekino.link')) {
            var ekinoBtn = document.querySelector('a.buttonprch');
            if (ekinoBtn) {
                var btnHref = ekinoBtn.getAttribute('href');
                if (btnHref && btnHref !== '' && btnHref !== '#' && ekinoBtn.href !== window.location.href) {
                    clearInterval(ekinoNavInterval);
                    window.location.href = ekinoBtn.href;
                    return;
                }
            }
        }

        // Step 2: If we're on play.ekino.link (or any page containing the player iframe), find the real iframe and navigate to it
        var iframes = document.querySelectorAll('iframe');
        for (var i = 0; i < iframes.length; i++) {
            var src = iframes[i].src || iframes[i].getAttribute('data-src');
            if (src && (src.startsWith('http') || src.startsWith('//')) &&
                !src.includes('challenges.cloudflare.com') && !src.includes('google.com/recaptcha')) {
                clearInterval(ekinoNavInterval);
                if (src.includes('dood') && src.includes('/d/')) {
                    src = src.replace('/d/', '/e/');
                } else if (src.includes('onlystream') && !src.includes('/e/')) {
                    src = src.replace('onlystream.tv/', 'onlystream.tv/e/');
                }
                if (src.startsWith('//')) src = 'https:' + src;
                if (window.location.href !== src && window.location.href !== src + '/') {
                    window.location.href = src;
                }
                return;
            }
        }
    }, 400);

    // Auto-clicker for any remaining play overlays
    var autoClickInterval = setInterval(function() {
        var video = findBestVideo();
        if (video && !video.paused && video.currentTime > 0) {
            clearInterval(autoClickInterval);
            clearInterval(ekinoNavInterval);
            return;
        }
        if (window._hasCaptchaFlag || (typeof checkIsCloudflare === 'function' && checkIsCloudflare())) return;

        if (typeof jwplayer === 'function') {
            try { jwplayer().play(); } catch(e) {}
        }

        var playBtn = document.querySelector('.jw-icon-display') ||
                      document.querySelector('.vjs-big-play-button') ||
                      document.querySelector('.plyr__control--overlaid') ||
                      document.querySelector('.play-btn') ||
                      document.querySelector('.jw-display-icon-container');
        if (playBtn) playBtn.click();
        if (video) {
            try { video.play(); } catch(e) {}
            video.click();
        }
    }, 500);
"""

/**
 * Doodstream/d0o0d/myvidplay-specific script.
 * These sites have a specific play button overlay that must be clicked.
 * The video element only appears after clicking the overlay.
 */
private const val DOODSTREAM_SCRIPT = """
    var doodClickInterval = setInterval(function() {
        var video = findBestVideo();
        if (video && !video.paused && video.currentTime > 0) {
            clearInterval(doodClickInterval);
            return;
        }
        if (window._hasCaptchaFlag || (typeof checkIsCloudflare === 'function' && checkIsCloudflare())) return;

        // Doodstream has an overlay play button
        var playBtn = document.querySelector('.plyr__control--overlaid') ||
                      document.querySelector('[data-plyr="play"]') ||
                      document.querySelector('.play-btn') ||
                      document.querySelector('.icon--pressed');
        if (playBtn) {
            playBtn.click();
            return;
        }

        // Fallback: simulate click at center
        var clickEvent = new MouseEvent('click', {
            view: window, bubbles: true, cancelable: true,
            clientX: window.innerWidth / 2, clientY: window.innerHeight / 2
        });
        var el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
        if (el && el.tagName !== 'IFRAME') el.dispatchEvent(clickEvent);

        if (video) video.play();
    }, 700);
"""

/**
 * Vidmoly / luluvdo / lulustream-specific script.
 * These use JWPlayer or similar and have the video inside #vplayer or .jw-video.
 */
private const val VIDMOLY_SCRIPT = """
    var vidmolyClickInterval = setInterval(function() {
        var video = document.querySelector('#vplayer video') ||
                    document.querySelector('.jw-video') ||
                    document.querySelector('.vjs-tech') ||
                    findBestVideo();
        if (video && !video._hooked) {
            hookVideo(video);
        }
        if (video && !video.paused && video.currentTime > 0) {
            clearInterval(vidmolyClickInterval);
            return;
        }
        if (window._hasCaptchaFlag || (typeof checkIsCloudflare === 'function' && checkIsCloudflare())) return;

        var playBtn = document.querySelector('.jw-icon-display') ||
                      document.querySelector('.vjs-big-play-button') ||
                      document.querySelector('.play-btn');
        if (playBtn) {
            playBtn.click();
            return;
        }

        var clickEvent = new MouseEvent('click', {
            view: window, bubbles: true, cancelable: true,
            clientX: window.innerWidth / 2, clientY: window.innerHeight / 2
        });
        var el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
        if (el) el.dispatchEvent(clickEvent);

        if (video) video.play();
    }, 500);
"""

/**
 * StreamSB / cloudemb / sbani / lvturbo / byse-specific script.
 * These use Video.js or custom React players (or JWPlayer).
 */
private const val STREAMSB_SCRIPT = """
    var sbClickInterval = setInterval(function() {
        var video = document.querySelector('.jw-video') ||
                    document.querySelector('.vjs-tech') ||
                    document.querySelector('video[id*="player"]') ||
                    findBestVideo();
        if (video && !video._hooked) {
            hookVideo(video);
        }
        if (video && !video.paused && video.currentTime > 0) {
            clearInterval(sbClickInterval);
            return;
        }
        if (window._hasCaptchaFlag || (typeof checkIsCloudflare === 'function' && checkIsCloudflare())) return;

        if (typeof jwplayer === 'function') {
            try { jwplayer().play(); } catch(e) {}
        }

        var playBtn = document.querySelector('.jw-icon-display') ||
                      document.querySelector('.vjs-big-play-button') ||
                      document.querySelector('.play-btn') ||
                      document.querySelector('#play') ||
                      document.querySelector('[data-play]') ||
                      document.querySelector('[data-plyr="play"]') ||
                      document.querySelector('.plyr__control--overlaid') ||
                      document.querySelector('.player-play') ||
                      document.querySelector('.jw-display-icon-container');
        if (playBtn) playBtn.click();

        if (video) {
            try { video.play(); } catch(e) {}
            video.click();
        } else {
            var clickEvent = new MouseEvent('click', {
                view: window, bubbles: true, cancelable: true,
                clientX: window.innerWidth / 2, clientY: window.innerHeight / 2
            });
            var el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
            if (el) el.dispatchEvent(clickEvent);
        }
    }, 500);
"""

/**
 * Generic iframe host script (onlystream, savefiles, vidara, upzone).
 */
private const val GENERIC_IFRAME_SCRIPT = """
    var genericClickInterval = setInterval(function() {
        var video = findBestVideo();
        if (video && !video._hooked) {
            hookVideo(video);
        }
        if (video && !video.paused && video.currentTime > 0) {
            clearInterval(genericClickInterval);
            return;
        }
        if (window._hasCaptchaFlag || (typeof checkIsCloudflare === 'function' && checkIsCloudflare())) return;

        if (typeof jwplayer === 'function') {
            try { jwplayer().play(); } catch(e) {}
        }

        var playBtn = document.querySelector('.jw-icon-display') ||
                      document.querySelector('.vjs-big-play-button') ||
                      document.querySelector('.play-btn') ||
                      document.querySelector('[data-plyr="play"]') ||
                      document.querySelector('.jw-display-icon-container');
        if (playBtn) playBtn.click();

        var clickEvent = new MouseEvent('click', {
            view: window, bubbles: true, cancelable: true,
            clientX: window.innerWidth / 2, clientY: window.innerHeight / 2
        });
        var el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
        if (el) el.dispatchEvent(clickEvent);

        if (video) {
            try { video.play(); } catch(e) {}
            video.click();
        }
    }, 500);
"""

/**
 * Generic fallback for unknown providers.
 */
private const val GENERIC_FALLBACK_SCRIPT = """
    var intermediateNavInterval = setInterval(function() {
        if (window._hasCaptchaFlag || (typeof checkIsCloudflare === 'function' && checkIsCloudflare())) return;

        if (!window.location.href.includes('play.ekino.link')) {
            var ekinoBtn = document.querySelector('a.buttonprch');
            if (ekinoBtn) {
                var btnHref = ekinoBtn.getAttribute('href');
                if (btnHref && btnHref !== '' && btnHref !== '#' && ekinoBtn.href !== window.location.href) {
                    clearInterval(intermediateNavInterval);
                    window.location.href = ekinoBtn.href;
                    return;
                }
            }
        }

        var iframes = document.querySelectorAll('iframe');
        for (var i = 0; i < iframes.length; i++) {
            var src = iframes[i].src || iframes[i].getAttribute('data-src');
            if (src && (src.startsWith('http') || src.startsWith('//')) &&
                !src.includes('challenges.cloudflare.com') && !src.includes('google.com/recaptcha')) {
                clearInterval(intermediateNavInterval);
                if (src.includes('dood') && src.includes('/d/')) {
                    src = src.replace('/d/', '/e/');
                } else if (src.includes('onlystream') && !src.includes('/e/')) {
                    src = src.replace('onlystream.tv/', 'onlystream.tv/e/');
                }
                if (src.startsWith('//')) src = 'https:' + src;
                if (window.location.href !== src && window.location.href !== src + '/') {
                    window.location.href = src;
                }
                return;
            }
        }
    }, 800);

    var autoClickInterval = setInterval(function() {
        var video = findBestVideo();
        if (video && !video.paused && video.currentTime > 0) {
            clearInterval(autoClickInterval);
            clearInterval(captchaInterval);
            if (typeof intermediateNavInterval !== 'undefined') clearInterval(intermediateNavInterval);
            return;
        }

        if (window._hasCaptchaFlag || (typeof checkIsCloudflare === 'function' && checkIsCloudflare())) return;

        if (typeof jwplayer === 'function') {
            try { jwplayer().play(); } catch(e) {}
        }

        var playBtn = document.querySelector('.jw-icon-display') ||
                      document.querySelector('.vjs-big-play-button') ||
                      document.querySelector('.plyr__control--overlaid') ||
                      document.querySelector('.play-btn') ||
                      document.querySelector('[data-plyr="play"]') ||
                      document.querySelector('.jw-display-icon-container');
        if (playBtn) playBtn.click();

        var clickEvent = new MouseEvent('click', {
            view: window, bubbles: true, cancelable: true,
            clientX: window.innerWidth / 2, clientY: window.innerHeight / 2
        });
        var el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
        if (el) el.dispatchEvent(clickEvent);
        else document.body.dispatchEvent(clickEvent);

        if (video) {
            try { video.play(); } catch(e) {}
            video.click();
        }
    }, 500);
"""

// ============================================================================
// Player control scripts (used by WebViewPlayer for play/pause/seek/speed)
// ============================================================================

internal const val PLAYER_PLAY_SCRIPT = """
if (typeof jwplayer === 'function') {
    try { jwplayer().play(); } catch(e) {}
}

var playBtn = document.querySelector('.jw-icon-display') ||
              document.querySelector('.vjs-big-play-button') ||
              document.querySelector('.play-btn') ||
              document.querySelector('.jw-display-icon-container');
if (playBtn) playBtn.click();

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

if (document.querySelector('video')) {
    try { document.querySelector('video').play(); } catch(e) {}
}
"""

internal const val PLAYER_PAUSE_SCRIPT = """
if (typeof jwplayer === 'function') {
    try { jwplayer().pause(); } catch(e) {}
}
if (document.querySelector('video')) {
    try { document.querySelector('video').pause(); } catch(e) {}
}
"""

internal const val PLAYER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

internal fun getPlayerUserAgent(context: android.content.Context): String {
    return try {
        val defaultUa = android.webkit.WebSettings.getDefaultUserAgent(context)
        defaultUa.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
    } catch (_: Exception) {
        PLAYER_USER_AGENT
    }
}

internal fun getPlayerSeekScript(timeInSeconds: Double) =
    "if (typeof jwplayer === 'function') { try { jwplayer().seek($timeInSeconds); } catch(e){} } if (document.querySelector('video')) document.querySelector('video').currentTime = $timeInSeconds;"

internal fun getPlayerPlaybackSpeedScript(speed: Float) =
    "window.filmanPlaybackSpeed = $speed; if (typeof jwplayer === 'function') { try { jwplayer().setPlaybackRate($speed); } catch(e){} } if (document.querySelector('video')) document.querySelector('video').playbackRate = $speed;"

internal fun getPlayerAspectRatioScript(mode: Int): String {
    val objectFit = when (mode) {
        PlayerConstants.AspectRatio.CROP -> "cover"
        PlayerConstants.AspectRatio.STRETCH -> "fill"
        else -> "contain"
    }

    return "window.filmanAspectRatio = '$objectFit'; if(document.querySelector('video')) document.querySelector('video').style.setProperty('object-fit', '$objectFit', 'important');"
}
