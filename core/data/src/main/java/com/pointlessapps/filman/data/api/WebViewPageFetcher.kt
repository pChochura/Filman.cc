package com.pointlessapps.filman.data.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.pointlessapps.filman.data.api.dto.PageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Fetches a page from inside a WebView that already passed the Cloudflare challenge, so the
 * request carries a clearance valid for this device (IP, user agent and TLS fingerprint).
 * The raw body is meant to be forwarded to the server, which does all the parsing.
 */
/** [finalUrl] is where the request ended up after redirects. */
data class FetchedPage(val html: String, val finalUrl: String)

class WebViewPageFetcher(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetch(request: PageRequest, userAgent: String?): FetchedPage =
        withContext(Dispatchers.Main) {
            try { withTimeout(TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val url = request.url.toHttpUrl()
                    val origin = "${url.scheme}://${url.host}"
                    NetworkClient.preSeedCookiesForUrl(origin)

                    val webView = WebView(context)
                    val handler = Handler(Looper.getMainLooper())
                    fun finish(block: () -> Unit) {
                        handler.post {
                            webView.destroy()
                            if (cont.isActive) block()
                        }
                    }

                    webView.settings.javaScriptEnabled = true
                    if (!userAgent.isNullOrEmpty()) webView.settings.userAgentString = userAgent

                    webView.addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onResult(status: Int, body: String, finalUrl: String) = finish {
                                if (status == 403 || status == 503 || CHALLENGE_MARKERS.any(body::contains)) {
                                    cont.resumeWithException(
                                        CloudflareChallengeDetected(domain = url.host, url = request.url),
                                    )
                                } else {
                                    cont.resume(FetchedPage(body, finalUrl))
                                }
                            }

                            @JavascriptInterface
                            fun onError(message: String) = finish {
                                cont.resumeWithException(java.io.IOException(message))
                            }
                        },
                        BRIDGE,
                    )

                    var started = false
                    fun isChallenge(view: WebView) =
                        view.title?.contains("Just a moment", ignoreCase = true) == true

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, pageUrl: String?) {
                            if (started) return
                            if (isChallenge(view)) {
                                // Give non-interactive challenges a moment to clear themselves
                                handler.postDelayed({
                                    if (started || !cont.isActive) return@postDelayed
                                    if (isChallenge(webView)) {
                                        finish {
                                            cont.resumeWithException(
                                                CloudflareChallengeDetected(domain = url.host, url = request.url),
                                            )
                                        }
                                    } else {
                                        started = true
                                        webView.evaluateJavascript(buildScript(request), null)
                                    }
                                }, CHALLENGE_GRACE_MS)
                                return
                            }
                            started = true
                            view.evaluateJavascript(buildScript(request), null)
                        }
                    }
                    cont.invokeOnCancellation { handler.post { webView.destroy() } }
                    webView.loadUrl("$origin/")
                }
            } } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                throw java.io.IOException("Timed out fetching ${request.url}", e)
            }
        }

    private fun buildScript(request: PageRequest): String {
        val config = JSONObject().apply {
            put("method", request.method.uppercase())
            put("url", request.url)
            put("body", request.formFields.entries.joinToString("&") {
                "${java.net.URLEncoder.encode(it.key, "UTF-8")}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
            })
        }
        return """
            (async () => {
              try {
                const c = $config;
                const opts = { method: c.method, credentials: 'include', headers: { 'X-Requested-With': 'XMLHttpRequest' } };
                if (c.method === 'POST') {
                  opts.headers['Content-Type'] = 'application/x-www-form-urlencoded; charset=UTF-8';
                  opts.body = c.body;
                }
                const r = await fetch(c.url, opts);
                $BRIDGE.onResult(r.status, await r.text(), r.url);
              } catch (e) {
                $BRIDGE.onError(String(e));
              }
            })();
        """.trimIndent()
    }

    private companion object {
        const val BRIDGE = "FilmanBridge"
        const val TIMEOUT_MS = 30_000L
        const val CHALLENGE_GRACE_MS = 6_000L
        val CHALLENGE_MARKERS = listOf("Just a moment...", "cf-browser-verification", "Attention Required! | Cloudflare")
    }
}
