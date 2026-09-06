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
                        var html = document.documentElement.innerHTML.toLowerCase();
                        if (html.includes('challenges.cloudflare.com') || html.includes('cf-turnstile') || html.includes('just a moment')) {
                            return 'challenge';
                        }
                        if (document.querySelector('input[name="password"]') !== null || document.querySelector('input[name="login"]') !== null) {
                            var alert = document.querySelector('.alert.alert-danger');
                            if (alert) return alert.innerText.trim();
                            return 'false';
                        }
                        return 'challenge';
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
                    } else if (decodedResult == "challenge") {
                        onRequiresManualSolve()
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
                        'transform: translate(-50%, -50%) scale(1.0) !important; z-index: 2147483647 !important; } ' +
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
        val isCloudflare = errorResponse?.responseHeaders?.entries?.any {
            it.key.equals("Server", ignoreCase = true) && it.value.equals("cloudflare", ignoreCase = true)
        } == true
        val statusCode = errorResponse?.statusCode ?: 0
        // Cloudflare challenge pages often return 403 or 503 - do not abort on them
        if (
            request?.isForMainFrame == true &&
            (statusCode == 404 || statusCode == 500 || (statusCode == 403 && !isCloudflare))
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
    // --- CSS OVERRIDES FOR PLAYER ISOLATION ---
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
            /* Cloudflare challenge MUST always be visible and centered */
            .cf-turnstile, #challenge-stage, #challenge-form,
            iframe[src*="challenges.cloudflare.com"], iframe[src*="turnstile"],
            .g-recaptcha, iframe[src*="recaptcha"] {
                position: fixed !important;
                top: 50% !important;
                left: 50% !important;
                transform: translate(-50%, -50%) !important;
                z-index: 2147483647 !important;
                visibility: visible !important;
                display: block !important;
                opacity: 1 !important;
            }
        `;
        document.head.appendChild(style);
    }

    // --- VIDEO HOOKING ---
    function hookVideo(video) {
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
        video.play();
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
        if (document.querySelector('video')) return;
        var iframes = document.querySelectorAll('iframe');
        for (var i = 0; i < iframes.length; i++) {
            var f = iframes[i];
            var s = f.src || f.getAttribute('data-src') || '';
            if (s && !s.includes('challenges.cloudflare.com') && !s.includes('google.com/recaptcha') && !s.includes('doubleclick')) {
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
    var existingVideo = findBestVideo();
    if (existingVideo) {
        hookVideo(existingVideo);
    } else {
        isolateIframePlayer();
    }

    // MutationObserver to catch dynamically injected videos or player iframes
    var videoObserver = new MutationObserver(function(mutations) {
        var video = findBestVideo();
        if (video && !video._hooked) {
            hookVideo(video);
        } else {
            isolateIframePlayer();
        }
    });
    videoObserver.observe(document.body || document.documentElement, { childList: true, subtree: true });

    // --- DEAD VIDEO DETECTION ---
    var checkDeadVideoInterval = setInterval(function() {
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
        // Do not time out while captcha is active
        if (window._hasCaptchaFlag) return;
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
    window._hasCaptchaFlag = false;
    var captchaInterval = setInterval(function() {
        var isCaptchaPage = document.title.includes('Just a moment') ||
                            document.title.includes('Attention Required') ||
                            document.title.includes('Cloudflare');
        var widget = document.querySelector('.cf-turnstile') ||
                     document.getElementById('challenge-stage') ||
                     document.querySelector('iframe[src*="challenges.cloudflare.com"]') ||
                     document.querySelector('iframe[src*="turnstile"]');

        var isVisibleWidget = false;
        if (widget) {
            var rect = widget.getBoundingClientRect();
            isVisibleWidget = rect.width > 0 && rect.height > 0;
        }

        var hasCaptcha = isCaptchaPage || isVisibleWidget;

        if (hasCaptcha) {
            window._hasCaptchaFlag = true;
            if (typeof AndroidBridge !== 'undefined' && AndroidBridge.onCaptchaStateChanged) {
                AndroidBridge.onCaptchaStateChanged(true);
            }

            if (widget && !window._captchaClicked) {
                window._captchaClicked = true;
                setTimeout(function() { window._captchaClicked = false; }, 6000);
                widget.scrollIntoView({behavior: 'instant', block: 'center', inline: 'center'});
                var r = widget.getBoundingClientRect();
                var cx = r.left + r.width / 2;
                var cy = r.top + r.height / 2;
                if (r.width > 0 && r.height > 0 && typeof AndroidBridge !== 'undefined' && AndroidBridge.onCaptchaFound) {
                    AndroidBridge.onCaptchaFound(cx, cy);
                }
            }
        } else {
            if (window._hasCaptchaFlag) {
                window._hasCaptchaFlag = false;
                startTime = Date.now(); // Reset timeout once challenge is cleared
                if (typeof AndroidBridge !== 'undefined') {
                    if (AndroidBridge.onCaptchaStateChanged) AndroidBridge.onCaptchaStateChanged(false);
                    if (AndroidBridge.onCloudflareCleared) AndroidBridge.onCloudflareCleared(window.location.hostname, document.cookie);
                }
            }
        }
    }, 800);
"""

/**
 * Ekino intermediate pages (/watch/f/ and play.ekino.link).
 * These pages show a "Przejdź do odtwarzacza" button or embed an iframe.
 * We navigate through the chain to reach the actual video host.
 */
private const val EKINO_INTERMEDIATE_SCRIPT = """
    var ekinoNavInterval = setInterval(function() {
        if (window._hasCaptchaFlag) return;

        // Step 1: Click the "Przejdź do odtwarzacza" button if present
        var ekinoBtn = document.querySelector('a.buttonprch');
        if (ekinoBtn && ekinoBtn.href) {
            clearInterval(ekinoNavInterval);
            window.location.href = ekinoBtn.href;
            return;
        }

        // Step 2: If we're on play.ekino.link, find the real iframe and navigate to it
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
        if (window._hasCaptchaFlag) return;

        var playBtn = document.querySelector('.jw-icon-display') ||
                      document.querySelector('.vjs-big-play-button') ||
                      document.querySelector('.plyr__control--overlaid') ||
                      document.querySelector('.play-btn');
        if (playBtn) playBtn.click();
        if (video) video.play();
    }, 600);
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
        if (window._hasCaptchaFlag) return;

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
        if (window._hasCaptchaFlag) return;

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
 * These use Video.js or custom React players.
 */
private const val STREAMSB_SCRIPT = """
    var sbClickInterval = setInterval(function() {
        var video = document.querySelector('.vjs-tech') ||
                    document.querySelector('video[id*="player"]') ||
                    findBestVideo();
        if (video && !video._hooked) {
            hookVideo(video);
        }
        if (video && !video.paused && video.currentTime > 0) {
            clearInterval(sbClickInterval);
            return;
        }
        if (window._hasCaptchaFlag) return;

        var playBtn = document.querySelector('.vjs-big-play-button') ||
                      document.querySelector('.play-btn') ||
                      document.querySelector('#play') ||
                      document.querySelector('[data-play]') ||
                      document.querySelector('.player-play');
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
        if (window._hasCaptchaFlag) return;

        var playBtn = document.querySelector('.jw-icon-display') ||
                      document.querySelector('.vjs-big-play-button') ||
                      document.querySelector('.play-btn') ||
                      document.querySelector('[data-plyr="play"]');
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
 * Generic fallback for unknown providers.
 */
private const val GENERIC_FALLBACK_SCRIPT = """
    var intermediateNavInterval = setInterval(function() {
        if (window._hasCaptchaFlag) return;

        var ekinoBtn = document.querySelector('a.buttonprch');
        if (ekinoBtn && ekinoBtn.href) {
            clearInterval(intermediateNavInterval);
            window.location.href = ekinoBtn.href;
            return;
        }

        var iframes = document.querySelectorAll('iframe');
        for (var i = 0; i < iframes.length; i++) {
            var src = iframes[i].src || iframes[i].getAttribute('data-src');
            if (src && (src.startsWith('http') || src.startsWith('//')) &&
                !src.includes('challenges.cloudflare.com') && !src.includes('google.com/recaptcha')) {
                if (window.location.href.includes('play.ekino.link')) {
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

        if (window._hasCaptchaFlag) return;

        var playBtn = document.querySelector('.jw-icon-display') ||
                      document.querySelector('.vjs-big-play-button') ||
                      document.querySelector('.plyr__control--overlaid') ||
                      document.querySelector('.play-btn') ||
                      document.querySelector('[data-plyr="play"]');
        if (playBtn) {
            playBtn.click();
            if (video) video.play();
            return;
        }

        var clickEvent = new MouseEvent('click', {
            view: window, bubbles: true, cancelable: true,
            clientX: window.innerWidth / 2, clientY: window.innerHeight / 2
        });
        var el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
        if (el) el.dispatchEvent(clickEvent);
        else document.body.dispatchEvent(clickEvent);

        if (video) video.play();
    }, 500);
"""

// ============================================================================
// Player control scripts (used by WebViewPlayer for play/pause/seek/speed)
// ============================================================================

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

internal fun getPlayerPlaybackSpeedScript(speed: Float) =
    "window.filmanPlaybackSpeed = $speed; if(document.querySelector('video')) document.querySelector('video').playbackRate = $speed;"

internal fun getPlayerAspectRatioScript(mode: Int): String {
    val objectFit = when (mode) {
        PlayerConstants.AspectRatio.CROP -> "cover"
        PlayerConstants.AspectRatio.STRETCH -> "fill"
        else -> "contain"
    }

    return "window.filmanAspectRatio = '$objectFit'; if(document.querySelector('video')) document.querySelector('video').style.setProperty('object-fit', '$objectFit', 'important');"
}
