package com.pointlessapps.filman.ui.core

import android.content.Context
import android.webkit.WebSettings

const val CHECK_PAGE_STATUS_SCRIPT = """
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

const val FIND_V2_CHECKBOX_SCRIPT = """
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

const val SUBMIT_LOGIN_FORM_SCRIPT = """
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

const val CHECK_CHALLENGE_VISIBLE_SCRIPT = """
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

const val CHECK_TOKEN_FILLED_SCRIPT =
    "document.querySelector('.g-recaptcha-response') ? (document.querySelector('.g-recaptcha-response').value !== '' ? 'true' : 'false') : 'false'"

fun getPlayerInjectionScript(url: String): String {
    val lower = url.lowercase()
    val providerScript =
        when {
            lower.contains("play.ekino.link") || lower.contains("ekino.ws/watch/") -> EKINO_INTERMEDIATE_SCRIPT

            lower.contains("dood") || lower.contains("d0o0d") || lower.contains("myvidplay") -> DOODSTREAM_SCRIPT

            lower.contains("vidmoly") || lower.contains("luluvdo") || lower.contains("lulustream") -> VIDMOLY_SCRIPT

            Regex(
                """https?://(?:sb[a-zA-Z0-9]*|pelistop|cloudemb|vidgomunime|keephealth|streamsss|lvturbo|ssbstream)\.[a-z]+/.*""",
            ).containsMatchIn(lower) ||
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
const val PLAYER_BASE_SCRIPT = """
    try { window.open = function() { return null; }; } catch(e) {}

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
                    opacity: 1 !important;
                    display: block !important;
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
                    opacity: 1 !important;
                    display: block !important;
                }
                /* Hide cover, loader, poster, backdrop overlays that block the video */
                img[alt*="loader" i],
                img[alt*="poster" i],
                img[alt*="cover" i],
                img[src*="image.tmdb.org"],
                img[src*="wsrv.nl"],
                .mui-x5j1hq,
                .mui-5drya5,
                .jw-preview,
                .vjs-poster,
                .plyr__poster,
                [class*="backdrop" i]:not(video):not(body):not(html),
                [class*="poster" i]:not(video):not(body):not(html):not(button):not(a):not([role="button"]):not([class*="play" i]),
                [class*="loader" i]:not(video):not(body):not(html):not(button):not(a):not([role="button"]):not([class*="play" i]),
                [class*="cover" i]:not(video):not(body):not(html):not(button):not(a):not([role="button"]):not([class*="play" i]),
                [id*="poster" i]:not(video):not(body):not(html):not(button):not(a):not([role="button"]):not([class*="play" i]),
                [id*="loader" i]:not(video):not(body):not(html):not(button):not(a):not([role="button"]):not([class*="play" i]) {
                    display: none !important;
                    opacity: 0 !important;
                    visibility: hidden !important;
                    pointer-events: none !important;
                    z-index: -1 !important;
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

    // --- AUDIO / UNMUTING ---
    function ensureUnmuted(video) {
        if (!video) return;
        try {
            if (video.muted || video.volume === 0) {
                video.muted = false;
                video.volume = 1.0;
                video.dispatchEvent(new Event('volumechange'));
            }
        } catch(e) {}
        if (typeof jwplayer === 'function') {
            try {
                if (jwplayer().getMute && jwplayer().getMute()) {
                    jwplayer().setMute(false);
                    jwplayer().setVolume(100);
                }
            } catch(e) {}
        }
        var muteBtn = document.getElementById('mute') ||
                      document.querySelector('.jw-icon-volume[aria-label*="unmute" i]') ||
                      document.querySelector('.jw-off') ||
                      document.querySelector('.vjs-vol-muted') ||
                      document.querySelector('.plyr__control--muted');
        if (muteBtn && video.muted) {
            try { muteBtn.click(); } catch(e) {}
        }
    }

    // --- COVER / OVERLAY SUPPRESSION ---
    function dismissCoverOverlays(video) {
        var selectors = [
            'img[alt*="loader" i]',
            'img[alt*="poster" i]',
            'img[alt*="cover" i]',
            'img[src*="image.tmdb.org"]',
            'img[src*="wsrv.nl"]',
            '.mui-x5j1hq',
            '.mui-5drya5',
            '.jw-preview',
            '.vjs-poster',
            '.plyr__poster',
            '[class*="backdrop" i]',
            '[class*="poster" i]',
            '[class*="loader" i]',
            '[class*="cover" i]',
            '[id*="poster" i]',
            '[id*="loader" i]'
        ];
        selectors.forEach(function(sel) {
            try {
                document.querySelectorAll(sel).forEach(function(el) {
                    if (!el || el === document.body || el === document.documentElement) return;
                    if (el.tagName === 'VIDEO') return;
                    if (video && el.contains && el.contains(video)) return;
                    var role = el.getAttribute('role') || '';
                    var tag = el.tagName.toLowerCase();
                    var className = (typeof el.className === 'string') ? el.className.toLowerCase() : '';
                    if (tag === 'button' || tag === 'a' || role === 'button' || className.includes('play')) return;
                    el.style.setProperty('display', 'none', 'important');
                    el.style.setProperty('opacity', '0', 'important');
                    el.style.setProperty('visibility', 'hidden', 'important');
                    el.style.setProperty('pointer-events', 'none', 'important');
                    el.style.setProperty('z-index', '-1', 'important');
                });
            } catch(e) {}
        });
    }

    function uncoverVideo(video) {
        if (!video) return;
        video.style.setProperty('position', 'fixed', 'important');
        video.style.setProperty('top', '0', 'important');
        video.style.setProperty('left', '0', 'important');
        video.style.setProperty('width', '100vw', 'important');
        video.style.setProperty('height', '100vh', 'important');
        video.style.setProperty('background', 'black', 'important');
        video.style.setProperty('z-index', '2147483640', 'important');
        video.style.setProperty('visibility', 'visible', 'important');
        video.style.setProperty('opacity', '1', 'important');
        video.style.setProperty('display', 'block', 'important');
        if (typeof window.filmanAspectRatio !== 'undefined') {
            video.style.setProperty('object-fit', window.filmanAspectRatio, 'important');
        }
        if (video.hasAttribute('poster')) {
            video.removeAttribute('poster');
            video.poster = '';
        }

        var el = video.parentElement;
        while (el && el !== document.body && el !== document.documentElement) {
            el.style.setProperty('display', 'block', 'important');
            el.style.setProperty('opacity', '1', 'important');
            el.style.setProperty('visibility', 'visible', 'important');
            el.style.setProperty('overflow', 'visible', 'important');
            el.style.setProperty('position', 'static', 'important');
            el.style.setProperty('transform', 'none', 'important');
            el.style.setProperty('filter', 'none', 'important');
            el.style.setProperty('clip', 'auto', 'important');
            el.style.setProperty('clip-path', 'none', 'important');
            el = el.parentElement;
        }

        dismissCoverOverlays(video);
    }

    // --- VIDEO HOOKING ---
    function hookVideo(video) {
        if (checkIsCloudflare()) return;
        if (video._hooked) {
            uncoverVideo(video);
            return;
        }
        video._hooked = true;

        uncoverVideo(video);

        video.removeAttribute('controls');
        video.removeAttribute('poster');

        ensureUnmuted(video);

        if (typeof window.filmanPlaybackSpeed !== 'undefined') {
            video.playbackRate = window.filmanPlaybackSpeed;
        }
        if (typeof window.filmanAspectRatio !== 'undefined') {
            video.style.setProperty('object-fit', window.filmanAspectRatio, 'important');
        }

        video.addEventListener('timeupdate', function() {
            uncoverVideo(video);
            AndroidBridge.onTimeUpdate(video.currentTime, video.duration);
        });
        video.addEventListener('play', function() {
            ensureUnmuted(video);
            uncoverVideo(video);
            AndroidBridge.onPlayStateChanged(true);
            AndroidBridge.onBufferingChanged(false);
        });
        video.addEventListener('pause', function() { AndroidBridge.onPlayStateChanged(false); });
        video.addEventListener('waiting', function() { AndroidBridge.onBufferingChanged(true); });
        video.addEventListener('playing', function() {
            ensureUnmuted(video);
            uncoverVideo(video);
            AndroidBridge.onBufferingChanged(false);
        });
        video.addEventListener('canplay', function() {
            uncoverVideo(video);
            AndroidBridge.onBufferingChanged(false);
        });
        video.addEventListener('volumechange', function() {
            if (video.muted || video.volume === 0) {
                video.muted = false;
                video.volume = 1.0;
            }
        });
        tryPlay(video);
    }

    function tryPlay(video) {
        if (typeof jwplayer === 'function') {
            try {
                jwplayer().setMute(false);
                jwplayer().setVolume(100);
                jwplayer().play();
            } catch(e) {}
        }
        if (video) {
            ensureUnmuted(video);
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
            var rect = v.getBoundingClientRect();
            var area = rect.width * rect.height;
            if (v.muted && v.autoplay && area < 10000 && videos.length > 1) continue;
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
                f.style.setProperty('position', 'fixed', 'important');
                f.style.setProperty('top', '0', 'important');
                f.style.setProperty('left', '0', 'important');
                f.style.setProperty('width', '100vw', 'important');
                f.style.setProperty('height', '100vh', 'important');
                f.style.setProperty('z-index', '2147483640', 'important');
                f.style.setProperty('visibility', 'visible', 'important');
                f.style.setProperty('opacity', '1', 'important');
                f.style.setProperty('display', 'block', 'important');
                var p = f.parentElement;
                while (p && p !== document.body && p !== document.documentElement) {
                    p.style.setProperty('overflow', 'visible', 'important');
                    p.style.setProperty('position', 'static', 'important');
                    p.style.setProperty('display', 'block', 'important');
                    p.style.setProperty('opacity', '1', 'important');
                    p.style.setProperty('visibility', 'visible', 'important');
                    p = p.parentElement;
                }
                dismissCoverOverlays(null);
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
        if (video) {
            uncoverVideo(video);
            if (!video._hooked) {
                hookVideo(video);
            }
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
            uncoverVideo(video);
            if (!video._hooked) {
                hookVideo(video);
            }
            ensureUnmuted(video);
            if (!video.paused && video.currentTime > 0 && !video.muted && video.volume > 0) {
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

    // --- VIDEO TIMEOUT (15 seconds) ---
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
        if (Date.now() - startTime > 15000) {
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
const val EKINO_INTERMEDIATE_SCRIPT = """
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
        if (video) ensureUnmuted(video);
        if (video && !video.paused && video.currentTime > 0 && !video.muted && video.volume > 0) {
            clearInterval(autoClickInterval);
            clearInterval(ekinoNavInterval);
            return;
        }
        if (window._hasCaptchaFlag || (typeof checkIsCloudflare === 'function' && checkIsCloudflare())) return;

        if (typeof jwplayer === 'function') {
            try {
                jwplayer().setMute(false);
                jwplayer().setVolume(100);
                jwplayer().play();
            } catch(e) {}
        }

        var playBtn = document.querySelector('.jw-icon-display') ||
                      document.querySelector('.vjs-big-play-button') ||
                      document.querySelector('.plyr__control--overlaid') ||
                      document.querySelector('.play-btn') ||
                      document.querySelector('.jw-display-icon-container');
        if (playBtn) playBtn.click();
        if (video) {
            ensureUnmuted(video);
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
const val DOODSTREAM_SCRIPT = """
    var doodClickInterval = setInterval(function() {
        var video = findBestVideo();
        if (video) {
            if (typeof uncoverVideo === 'function') uncoverVideo(video);
            ensureUnmuted(video);
        }
        if (video && !video.paused && video.currentTime > 0 && !video.muted && video.volume > 0) {
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

        if (video) {
            ensureUnmuted(video);
            video.play();
        }
    }, 700);
"""

/**
 * Vidmoly / luluvdo / lulustream-specific script.
 * These use JWPlayer or similar and have the video inside #vplayer or .jw-video.
 */
const val VIDMOLY_SCRIPT = """
    var vidmolyClickInterval = setInterval(function() {
        var video = document.querySelector('#vplayer video') ||
                    document.querySelector('.jw-video') ||
                    document.querySelector('.vjs-tech') ||
                    findBestVideo();
        if (video) {
            if (typeof uncoverVideo === 'function') uncoverVideo(video);
            if (!video._hooked) {
                hookVideo(video);
            }
            ensureUnmuted(video);
        }
        if (video && !video.paused && video.currentTime > 0 && !video.muted && video.volume > 0) {
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

        if (video) {
            ensureUnmuted(video);
            video.play();
        }
    }, 500);
"""

/**
 * StreamSB / cloudemb / sbani / lvturbo / byse-specific script.
 * These use Video.js or custom React players (or JWPlayer).
 */
const val STREAMSB_SCRIPT = """
    var sbClickInterval = setInterval(function() {
        var video = document.querySelector('.jw-video') ||
                    document.querySelector('.vjs-tech') ||
                    document.querySelector('video[id*="player"]') ||
                    findBestVideo();
        if (video) {
            if (typeof uncoverVideo === 'function') uncoverVideo(video);
            if (!video._hooked) {
                hookVideo(video);
            }
            ensureUnmuted(video);
        }
        if (video && !video.paused && video.currentTime > 0 && !video.muted && video.volume > 0) {
            clearInterval(sbClickInterval);
            return;
        }
        if (window._hasCaptchaFlag || (typeof checkIsCloudflare === 'function' && checkIsCloudflare())) return;

        if (typeof jwplayer === 'function') {
            try {
                jwplayer().setMute(false);
                jwplayer().setVolume(100);
                jwplayer().play();
            } catch(e) {}
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
            ensureUnmuted(video);
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
const val GENERIC_IFRAME_SCRIPT = """
    var genericClickInterval = setInterval(function() {
        var video = findBestVideo();
        if (video) {
            if (typeof uncoverVideo === 'function') uncoverVideo(video);
            if (!video._hooked) {
                hookVideo(video);
            }
            ensureUnmuted(video);
        }
        if (video && !video.paused && video.currentTime > 0 && !video.muted && video.volume > 0) {
            clearInterval(genericClickInterval);
            return;
        }
        if (window._hasCaptchaFlag || (typeof checkIsCloudflare === 'function' && checkIsCloudflare())) return;

        if (typeof jwplayer === 'function') {
            try {
                jwplayer().setMute(false);
                jwplayer().setVolume(100);
                jwplayer().play();
            } catch(e) {}
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
            ensureUnmuted(video);
            try { video.play(); } catch(e) {}
            video.click();
        }
    }, 500);
"""

/**
 * Generic fallback for unknown providers.
 */
const val GENERIC_FALLBACK_SCRIPT = """
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
        if (video) {
            if (typeof uncoverVideo === 'function') uncoverVideo(video);
            ensureUnmuted(video);
        }
        if (video && !video.paused && video.currentTime > 0 && !video.muted && video.volume > 0) {
            clearInterval(autoClickInterval);
            clearInterval(captchaInterval);
            if (typeof intermediateNavInterval !== 'undefined') clearInterval(intermediateNavInterval);
            return;
        }

        if (window._hasCaptchaFlag || (typeof checkIsCloudflare === 'function' && checkIsCloudflare())) return;

        if (typeof jwplayer === 'function') {
            try {
                jwplayer().setMute(false);
                jwplayer().setVolume(100);
                jwplayer().play();
            } catch(e) {}
        }

        var playBtn = document.getElementById('bigPlay') ||
                      document.querySelector('.jw-bigplay') ||
                      document.querySelector('.jw-icon-display') ||
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
            ensureUnmuted(video);
            try { video.play(); } catch(e) {}
            video.click();
        }
    }, 500);
"""

// ============================================================================
// Player control scripts (used by WebViewPlayer for play/pause/seek/speed)
// ============================================================================

const val PLAYER_PLAY_SCRIPT = """
if (typeof jwplayer === 'function') {
    try {
        jwplayer().setMute(false);
        jwplayer().setVolume(100);
        jwplayer().play();
    } catch(e) {}
}

var playBtn = document.getElementById('bigPlay') ||
              document.querySelector('.jw-bigplay') ||
              document.querySelector('.jw-icon-display') ||
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

var video = document.querySelector('video');
if (video) {
    try {
        if (typeof uncoverVideo === 'function') uncoverVideo(video);
        video.muted = false;
        video.volume = 1.0;
        video.play();
    } catch(e) {}
}
"""

const val PLAYER_PAUSE_SCRIPT = """
if (typeof jwplayer === 'function') {
    try { jwplayer().pause(); } catch(e) {}
}
if (document.querySelector('video')) {
    try { document.querySelector('video').pause(); } catch(e) {}
}
"""

const val PLAYER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

fun getPlayerUserAgent(context: Context): String =
    try {
        val defaultUa = WebSettings.getDefaultUserAgent(context)
        defaultUa.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
    } catch (_: Exception) {
        PLAYER_USER_AGENT
    }

fun getPlayerSeekScript(timeInSeconds: Double) =
    "if (typeof jwplayer === 'function') { try { jwplayer().seek($timeInSeconds); } catch(e){} } if (document.querySelector('video')) document.querySelector('video').currentTime = $timeInSeconds;"

fun getPlayerPlaybackSpeedScript(speed: Float) =
    "window.filmanPlaybackSpeed = $speed; if (typeof jwplayer === 'function') { try { jwplayer().setPlaybackRate($speed); } catch(e){} } if (document.querySelector('video')) document.querySelector('video').playbackRate = $speed;"

fun getPlayerAspectRatioScript(mode: Int): String {
    val objectFit =
        when (mode) {
            PlayerConstants.AspectRatio.CROP -> "cover"
            PlayerConstants.AspectRatio.STRETCH -> "fill"
            else -> "contain"
        }

    return "window.filmanAspectRatio = '$objectFit'; if(document.querySelector('video')) document.querySelector('video').style.setProperty('object-fit', '$objectFit', 'important');"
}

fun getPlayerSetSubtitleScript(url: String?): String {
    if (url == null) {
        return """
            var video = document.querySelector('video');
            if (video) {
                var existing = document.getElementById('filman-track');
                if (existing) existing.remove();
            }
            """.trimIndent()
    }

    return """
        fetch('$url')
            .then(res => res.text())
            .then(text => {
                var vtt = 'WEBVTT\n\n' + text.replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, '${'$'}1.${'$'}2');
                var blob = new Blob([vtt], {type: 'text/vtt'});
                var blobUrl = URL.createObjectURL(blob);
                
                var video = document.querySelector('video');
                if (video) {
                    var existing = document.getElementById('filman-track');
                    if (existing) existing.remove();
                    
                    var track = document.createElement('track');
                    track.id = 'filman-track';
                    track.kind = 'captions';
                    track.label = 'Custom';
                    track.srclang = 'en';
                    track.src = blobUrl;
                    track.default = true;
                    video.appendChild(track);
                    
                    if (video.textTracks) {
                        for (var i = 0; i < video.textTracks.length; i++) {
                            video.textTracks[i].mode = 'hidden';
                        }
                        video.textTracks[video.textTracks.length - 1].mode = 'showing';
                    }
                }
            }).catch(e => console.log('Subtitle fetch failed', e));
        """.trimIndent()
}
