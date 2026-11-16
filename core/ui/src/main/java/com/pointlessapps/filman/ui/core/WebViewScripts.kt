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
            lower.contains("play.ekino.link") || lower.contains("ekino.ws") -> EKINO_INTERMEDIATE_SCRIPT

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
    val adHosts = playerAdHosts.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    return "(function() {\nvar filmanAdHosts = $adHosts;\n$PLAYER_BASE_SCRIPT\n$providerScript\n})();\n"
}

const val PLAYER_AUDIO_GATE_SCRIPT = """
            (function() {
                var proto = HTMLMediaElement.prototype;
                var muted = Object.getOwnPropertyDescriptor(proto, 'muted');
                var play = proto.play;
                var unlocked = false;
                function allowed(media) {
                    return unlocked && media.tagName === 'VIDEO' &&
                        media.classList.contains('filman-content-video');
                }
                Object.defineProperty(proto, 'muted', {
                    configurable: muted.configurable,
                    enumerable: muted.enumerable,
                    get: muted.get,
                    set: function(value) { muted.set.call(this, allowed(this) ? value : true); }
                });
                proto.play = function() {
                    if (!allowed(this)) muted.set.call(this, true);
                    return play.apply(this, arguments);
                };
                function silence(media) {
                    if (media instanceof HTMLMediaElement && !allowed(media)) muted.set.call(media, true);
                }
                document.addEventListener('play', function(event) { silence(event.target); }, true);
                new MutationObserver(function() {
                    document.querySelectorAll('audio, video').forEach(silence);
                }).observe(document, { childList: true, subtree: true });

                var contexts = [];
                function trackAudioContext(name) {
                    var Original = window[name];
                    if (!Original) return;
                    function GuardedAudioContext() {
                        var context = new Original(...arguments);
                        var resume = context.resume.bind(context);
                        context.resume = function() {
                            if (!unlocked) return Promise.resolve();
                            return resume();
                        };
                        contexts.push({ context: context, resume: resume });
                        context.suspend().catch(function(error) { console.warn('Audio suspension failed', error); });
                        return context;
                    }
                    GuardedAudioContext.prototype = Original.prototype;
                    Object.setPrototypeOf(GuardedAudioContext, Original);
                    window[name] = GuardedAudioContext;
                }
                trackAudioContext('AudioContext');
                trackAudioContext('webkitAudioContext');
                window.__filmanUnlockAudio = function() {
                    unlocked = true;
                    document.querySelectorAll('video.filman-content-video').forEach(function(video) {
                        muted.set.call(video, false);
                        if (video.volume === 0) video.volume = 1;
                    });
                    contexts.forEach(function(entry) {
                        if (entry.context.state !== 'closed') entry.resume().catch(function(error) { console.warn('Audio resume failed', error); });
                    });
                };
                window.__filmanLockAudio = function() {
                    unlocked = false;
                    document.querySelectorAll('audio, video').forEach(function(media) { muted.set.call(media, true); });
                    contexts.forEach(function(entry) {
                        if (entry.context.state !== 'closed') entry.context.suspend().catch(function(error) { console.warn('Audio suspension failed', error); });
                    });
                };
            })();
        """

const val PLAYER_AUDIO_UNLOCK_SCRIPT =
            "window.__filmanAudioAllowed = true; if (window.__filmanUnlockAudio) window.__filmanUnlockAudio();"

const val PLAYER_AUDIO_LOCK_SCRIPT =
            "window.__filmanAudioAllowed = false; if (window.__filmanLockAudio) window.__filmanLockAudio();"

/**
 * Common base script injected for ALL providers.
 * Real Player Isolation:
 * - Styles <video> to fill screen with black background
 * - Identifies player iframes without exposing them before playback
 * - Hides distracting headers, sidebars, footers, popups, and banners
 * - Detects Cloudflare Turnstile / challenge and ensures it is centered and fully visible
 * - Coordinates with AndroidBridge for captcha detection and cookie persistence
 */
const val PLAYER_BASE_SCRIPT = """
    if (window.__filmanPlayerInstalled) return;
    window.__filmanPlayerInstalled = true;
    try { window.open = function() { return null; }; } catch(e) {}

    // --- CLOUDFLARE CHALLENGE DETECTION HELPERS ---
    function isManagedCloudflareChallenge() {
        return !!(window._cf_chl_opt ||
            document.querySelector('script[src*="/cdn-cgi/challenge-platform/"]') ||
            document.getElementById('challenge-error-text') ||
            document.getElementById('cf-challenge-running'));
    }

    function checkIsCloudflare() {
        if (isManagedCloudflareChallenge()) return true;
        // If response token is already generated, Turnstile / captcha is solved!
        var cfResp = document.querySelector('input[name="cf-turnstile-response"], input[name="g-recaptcha-response"]');
        if (cfResp && cfResp.value && cfResp.value.trim().length > 0) {
            return false;
        }

        var title = (document.title || '').toLowerCase();
        if (title.includes('just a moment') || title.includes('attention required') || title.includes('cloudflare')) {
            return true;
        }
        if (document.getElementById('challenge-stage') ||
            document.getElementById('challenge-form') ||
            document.getElementById('challenge-running')) {
            return true;
        }
        var turnstileIframe = document.querySelector('iframe[src*="challenges.cloudflare.com"], iframe[src*="turnstile"]');
        if (turnstileIframe) {
            return true;
        }
        // Ekino Turnstile challenge container
        var hasValidEkinoBtn = document.querySelector('a.buttonprch[href]:not([href=""]):not([href="#"])') !== null;
        if (!hasValidEkinoBtn) {
            var gcap = document.querySelector('#gcaptcha, div[id*="gcaptcha"]');
            if (gcap && gcap.children.length > 0) {
                return true;
            }
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
                body * {
                    visibility: hidden !important;
                }
                /* Hide general site clutter */
                header, footer, nav, .header, .footer, .navbar, .ad, .ads, .advertisement,
                .banner, .alert:not(.alert-challenge), #refresh_btn, #belt, #cookies, .cookie-notice,
                .top-bar, .side-bar, .sidebar, #header, #footer {
                    display: none !important;
                }
                /* Stretched video element */
                video.filman-content-video {
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
                a.buttonprch[href]:not([href=""]):not([href="#"]):not([href*="javascript"]) {
                    position: fixed !important;
                    top: 50% !important;
                    left: 50% !important;
                    transform: translate(-50%, -50%) !important;
                    z-index: 2147483645 !important;
                    display: inline-block !important;
                    visibility: hidden !important;
                }
                a.buttonprch[href=""], a.buttonprch[href="#"], .header .buttonprch, header .buttonprch {
                    display: none !important;
                    visibility: hidden !important;
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
        if (!video || !window.__filmanAudioAllowed || isAdVideo(video)) return;
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
        video.style.setProperty('visibility', video._filmanReady ? 'visible' : 'hidden', 'important');
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
    function isAdVideo(video) {
        if (!video) return true;
        if (video.closest('.vjs-ad-playing, .jw-flag-ads, .jw-flag-ad-playing, .ima-ad-container, [data-ad-container]')) return true;
        var src = (video.currentSrc || video.src || '').toLowerCase();
        try {
            var host = new URL(src, location.href).hostname.toLowerCase();
            if (isAdHost(host)) return true;
        } catch(e) {}
        return false;
    }

    function isAdHost(host) {
        host = host.toLowerCase().replace(/\.+$/, '');
        return filmanAdHosts.some(function(adHost) { return host === adHost || host.endsWith('.' + adHost); });
    }

    function isPlayerFrameSource(src) {
        try {
            var url = new URL(src, location.href);
            return (url.protocol === 'https:' || url.protocol === 'http:') &&
                !isAdHost(url.hostname.toLowerCase()) &&
                !url.hostname.toLowerCase().endsWith('challenges.cloudflare.com') &&
                !url.href.toLowerCase().includes('recaptcha');
        } catch(e) { return false; }
    }

    function reportVideoReady(video) {
        if (checkIsCloudflare() || isAdVideo(video) || video.paused || video.readyState < 2 ||
            video.videoWidth <= 0 || video.videoHeight <= 0 || video.currentTime < 0.25) return;
        if (!video._filmanReady && typeof AndroidBridge !== 'undefined' && AndroidBridge.onVideoReady) {
            document.querySelectorAll('video.filman-content-video').forEach(function(other) {
                if (other !== video) {
                    other.classList.remove('filman-content-video');
                    other.style.setProperty('visibility', 'hidden', 'important');
                }
            });
            video._filmanReady = true;
            video.classList.add('filman-content-video');
            uncoverVideo(video);
            if (AndroidBridge.onContentVideoChanged) AndroidBridge.onContentVideoChanged(location.href);
        }
        if (typeof AndroidBridge !== 'undefined' && AndroidBridge.onVideoReady) AndroidBridge.onVideoReady(location.href);
    }

    function reportAd(video) {
        if (!isAdVideo(video)) return false;
        window.__filmanAudioAllowed = false;
        if (window.__filmanLockAudio) window.__filmanLockAudio();
        video._filmanReady = false;
        video.classList.remove('filman-content-video');
        video.style.setProperty('visibility', 'hidden', 'important');
        if (typeof AndroidBridge !== 'undefined' && AndroidBridge.onAdDetected) AndroidBridge.onAdDetected(location.href);
        return true;
    }

    function clickPlayerButton(button) {
        if (!button || button._filmanClicked || button.closest('a, .ad, .ads, .advertisement, [data-ad-container]')) return;
        button._filmanClicked = true;
        button.click();
    }

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
            if (reportAd(video)) return;
            uncoverVideo(video);
            reportVideoReady(video);
            AndroidBridge.onTimeUpdate(video.currentTime, video.duration, location.href);
        });
        video.addEventListener('play', function() {
            if (reportAd(video)) return;
            ensureUnmuted(video);
            uncoverVideo(video);
            AndroidBridge.onPlayStateChanged(true, location.href);
            AndroidBridge.onBufferingChanged(false, location.href);
        });
        video.addEventListener('pause', function() { if (!isAdVideo(video)) AndroidBridge.onPlayStateChanged(false, location.href); });
        video.addEventListener('waiting', function() { if (!reportAd(video)) AndroidBridge.onBufferingChanged(true, location.href); });
        video.addEventListener('playing', function() {
            if (reportAd(video)) return;
            ensureUnmuted(video);
            uncoverVideo(video);
            AndroidBridge.onBufferingChanged(false, location.href);
        });
        video.addEventListener('canplay', function() {
            if (reportAd(video)) return;
            uncoverVideo(video);
            AndroidBridge.onBufferingChanged(false, location.href);
        });
        video.addEventListener('volumechange', function() {
            if (window.__filmanAudioAllowed && video._filmanReady && !isAdVideo(video) &&
                (video.muted || video.volume === 0)) {
                video.muted = false;
                video.volume = 1.0;
            }
        });
        tryPlay(video);
    }

    function tryPlay(video) {
        if (typeof jwplayer === 'function') {
            try {
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
        if (videos.length === 1) return isAdVideo(videos[0]) ? null : videos[0];

        var best = null;
        var bestArea = 0;
        for (var i = 0; i < videos.length; i++) {
            var v = videos[i];
            if (isAdVideo(v)) continue;
            var rect = v.getBoundingClientRect();
            var area = rect.width * rect.height;
            if (v.muted && v.autoplay && area < 10000 && videos.length > 1) continue;
            var hasSrc = v.src || v.querySelector('source');
            if (area > bestArea || (hasSrc && area >= bestArea * 0.5)) {
                best = v;
                bestArea = area;
            }
        }
        return best;
    }

    // --- IFRAME PLAYER ISOLATION ---
    // Identify a player iframe for navigation; an iframe alone is not proof of playback.
    function isolateIframePlayer() {
        if (checkIsCloudflare()) return;
        if (findBestVideo()) return;
        var iframes = document.querySelectorAll('#playcontainer iframe, .source-box iframe, #dooplay_player_response iframe, .playex iframe, .movieplay iframe, #streaming .player2 iframe, .player iframe, #player iframe, #vplayer iframe');
        for (var i = 0; i < iframes.length; i++) {
            var f = iframes[i];
            var s = f.getAttribute('src') || f.getAttribute('data-src') || '';
            if (s && !f.closest('.ad, .ads, .advertisement, [data-ad-container]') && isPlayerFrameSource(s)) {
                f.classList.add('filman-player-frame');
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
            AndroidBridge.onError(location.href);
        }
    }, 1000);

    // --- CLOUDFLARE CHALLENGE DETECTION ---
    window._hasCaptchaFlag = checkIsCloudflare();
    if (window._hasCaptchaFlag) {
        removePlayerStyle();
        if (typeof AndroidBridge !== 'undefined' && AndroidBridge.onCaptchaStateChanged) {
            AndroidBridge.onCaptchaStateChanged(true, location.href);
        }
    } else {
        if (typeof AndroidBridge !== 'undefined' && AndroidBridge.onCaptchaStateChanged) {
            AndroidBridge.onCaptchaStateChanged(false, location.href);
        }
    }

    var captchaInterval = setInterval(function() {
        var hasCaptcha = checkIsCloudflare();

        if (hasCaptcha) {
            if (!window._hasCaptchaFlag) {
                window._hasCaptchaFlag = true;
                removePlayerStyle();
                if (typeof AndroidBridge !== 'undefined' && AndroidBridge.onCaptchaStateChanged) {
                    AndroidBridge.onCaptchaStateChanged(true, location.href);
                }
            }

            // Check if response token appeared
            var resp = document.querySelector('input[name="cf-turnstile-response"], input[name="g-recaptcha-response"]');
            if (!isManagedCloudflareChallenge() && resp && resp.value && resp.value.trim().length > 0) {
                window._hasCaptchaFlag = false;
                startTime = Date.now();
                applyPlayerStyle();
                if (typeof AndroidBridge !== 'undefined') {
                    if (AndroidBridge.onCaptchaStateChanged) AndroidBridge.onCaptchaStateChanged(false, location.href);
                    if (AndroidBridge.onCloudflareCleared) AndroidBridge.onCloudflareCleared(location.href);
                }
                var form = resp.closest('form');
                if (form && !window._cfFormSubmitted) {
                    window._cfFormSubmitted = true;
                    var submitBtn = form.querySelector('button[type="submit"], input[type="submit"], a.buttonprch, .btn');
                    if (submitBtn) {
                        submitBtn.click();
                    } else {
                        form.submit();
                    }
                }
                return;
            }

        } else {
            if (window._hasCaptchaFlag) {
                window._hasCaptchaFlag = false;
                startTime = Date.now(); // Reset timeout once challenge is cleared
                applyPlayerStyle();
                if (typeof AndroidBridge !== 'undefined') {
                    if (AndroidBridge.onCaptchaStateChanged) AndroidBridge.onCaptchaStateChanged(false, location.href);
                    if (AndroidBridge.onCloudflareCleared) AndroidBridge.onCloudflareCleared(location.href);
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
    // Hide dummy back button on play.ekino.link
    try {
        var dummyBtns = document.querySelectorAll('a.buttonprch[href=""], a.buttonprch[href="#"], .header .buttonprch');
        dummyBtns.forEach(function(b) { b.style.setProperty('display', 'none', 'important'); });
    } catch(e) {}

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
        var iframes = document.querySelectorAll('iframe.filman-player-frame');
        for (var i = 0; i < iframes.length; i++) {
            var src = iframes[i].getAttribute('src') || iframes[i].getAttribute('data-src');
            if (src && isPlayerFrameSource(src)) {
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
                jwplayer().play();
            } catch(e) {}
        }

        var playBtn = document.querySelector('.jw-icon-display') ||
                      document.querySelector('.vjs-big-play-button') ||
                      document.querySelector('.plyr__control--overlaid') ||
                      document.querySelector('.play-btn') ||
                      document.querySelector('.jw-display-icon-container');
        if (!video) clickPlayerButton(playBtn);
        if (video) {
            ensureUnmuted(video);
            try { video.play(); } catch(e) {}
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
        if (playBtn && !video) {
            clickPlayerButton(playBtn);
            return;
        }

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
        if (isAdVideo(video)) video = null;
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
        if (playBtn && !video) {
            clickPlayerButton(playBtn);
            return;
        }

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
        if (isAdVideo(video)) video = null;
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
        if (!video) clickPlayerButton(playBtn);

        if (video) {
            ensureUnmuted(video);
            try { video.play(); } catch(e) {}
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
                jwplayer().play();
            } catch(e) {}
        }

        var playBtn = document.querySelector('.jw-icon-display') ||
                      document.querySelector('.vjs-big-play-button') ||
                      document.querySelector('.play-btn') ||
                      document.querySelector('[data-plyr="play"]') ||
                      document.querySelector('.jw-display-icon-container');
        if (!video) clickPlayerButton(playBtn);

        if (video) {
            ensureUnmuted(video);
            try { video.play(); } catch(e) {}
        }
    }, 500);
"""

/**
 * Generic fallback for unknown providers.
 */
const val GENERIC_FALLBACK_SCRIPT = """
    // Hide dummy back button on play.ekino.link
    try {
        var dummyBtns = document.querySelectorAll('a.buttonprch[href=""], a.buttonprch[href="#"], .header .buttonprch');
        dummyBtns.forEach(function(b) { b.style.setProperty('display', 'none', 'important'); });
    } catch(e) {}

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

        var iframes = document.querySelectorAll('iframe.filman-player-frame');
        for (var i = 0; i < iframes.length; i++) {
            var src = iframes[i].getAttribute('src') || iframes[i].getAttribute('data-src');
            if (src && isPlayerFrameSource(src)) {
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
        if (!video) clickPlayerButton(playBtn);

        if (video) {
            ensureUnmuted(video);
            try { video.play(); } catch(e) {}
        }
    }, 500);
"""

// ============================================================================
// Player control scripts (used by WebViewPlayer for play/pause/seek/speed)
// ============================================================================

const val PLAYER_PLAY_SCRIPT = """
if (typeof jwplayer === 'function') {
    try {
        jwplayer().play();
    } catch(e) {}
}

var video = document.querySelector('video');
if (video) {
    try {
        if (typeof uncoverVideo === 'function') uncoverVideo(video);
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

fun getPlayerUserAgent(context: Context): String =
    WebSettings.getDefaultUserAgent(context)

fun getPlayerSeekScript(timeInSeconds: Double) =
    "if (typeof jwplayer === 'function') { try { jwplayer().seek($timeInSeconds); } catch(e){} } if (document.querySelector('video')) document.querySelector('video').currentTime = $timeInSeconds;"

private const val PLAYER_RESUME_SCRIPT = """
(function() {
    var video = document.querySelector('video.filman-content-video');
    if (!video || video.readyState < 1 || !Number.isFinite(video.duration) || video.duration <= 0) return false;
    var target = Math.min(__FILMAN_RESUME_SECONDS__, Math.max(0, video.duration - 1));
    try {
        if (video.seeking) return false;
        if (Math.abs(video.currentTime - target) < 1) return true;
        video.currentTime = target;
        return !video.seeking && Math.abs(video.currentTime - target) < 1;
    } catch (error) {
        console.warn('Unable to restore playback position', error);
        return false;
    }
})()
"""

fun getPlayerResumeScript(positionMs: Long): String =
    PLAYER_RESUME_SCRIPT.replace("__FILMAN_RESUME_SECONDS__", (positionMs.coerceAtLeast(0) / 1000.0).toString())

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

fun getPlayerSetSubtitleScript(url: String?, showSubtitles: Boolean = true): String {
    val encodedUrl = url?.let { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
    val urlExpression = encodedUrl?.let { "decodeURIComponent('$it')" } ?: "null"
    return """
        (function() {
            var url = $urlExpression;
            var show = $showSubtitles;
            var generation = window.filmanSubtitleGeneration = (window.filmanSubtitleGeneration || 0) + 1;
            var saved = window.filmanHiddenTextTracks || [];
            clearInterval(window.filmanSubtitleHideTimer);
            function removeTrack() {
                var existing = document.getElementById('filman-track');
                if (existing) {
                    if (existing._filmanBlobUrl) URL.revokeObjectURL(existing._filmanBlobUrl);
                    existing.remove();
                }
            }
            function hideTracks() {
                document.querySelectorAll('video').forEach(function(video) {
                    for (var i = 0; i < video.textTracks.length; i++) {
                        var track = video.textTracks[i];
                        if (!saved.some(function(entry) { return entry.track === track; })) {
                            saved.push({ track: track, mode: track.mode });
                        }
                        track.mode = 'hidden';
                    }
                });
            }
            if (window.filmanSubtitleUrl !== url) removeTrack();
            window.filmanSubtitleUrl = url;
            if (!show) {
                window.filmanHiddenTextTracks = saved;
                hideTracks();
                window.filmanSubtitleHideTimer = setInterval(hideTracks, 250);
                if (!url) removeTrack();
                return;
            }
            saved.forEach(function(entry) { entry.track.mode = entry.mode; });
            window.filmanHiddenTextTracks = [];
            if (!url) {
                removeTrack();
                return;
            }
            fetch(url)
            .then(res => {
                if (!res.ok) throw new Error('Subtitle HTTP ' + res.status);
                return res.text();
            })
            .then(text => {
                if (window.filmanSubtitleGeneration !== generation) return;
                var video = document.querySelector('video.filman-content-video') || document.querySelector('video');
                if (!video) return;
                var vtt = 'WEBVTT\n\n' + text.replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, '${'$'}1.${'$'}2');
                var blob = new Blob([vtt], {type: 'text/vtt'});
                var blobUrl = URL.createObjectURL(blob);
                    removeTrack();
                    var track = document.createElement('track');
                    track.id = 'filman-track';
                    track._filmanBlobUrl = blobUrl;
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
            }).catch(e => console.log('Subtitle fetch failed', e));
        })();
        """.trimIndent()
}
