package com.example.filman.data.scraper.extractors

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal abstract class WebViewExtractor : EmbedExtractor {

    @SuppressLint("SetJavaScriptEnabled")
    protected suspend fun interceptVideoUrl(
        embedUrl: String,
        context: Context,
        timeoutMs: Long = 15000L,
        urlInterceptor: (String) -> Boolean
    ): ExtractedVideo? = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<ExtractedVideo?>()
        val webView = WebView(context).apply {
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.userAgentString = "Mozilla/5.0 (iPad; CPU OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"
            
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val url = request.url.toString()
                    android.util.Log.d("WebViewExtractorReq", "Req: $url")
                    if (urlInterceptor(url)) {
                        android.util.Log.d("WebViewExtractor", "Intercepted video URL: $url")
                        val headers = request.requestHeaders ?: emptyMap()
                        if (!deferred.isCompleted) {
                            deferred.complete(ExtractedVideo(url, headers))
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(
                    view: WebView,
                    handler: android.webkit.SslErrorHandler,
                    error: android.net.http.SslError
                ) {
                    handler.proceed()
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    view.evaluateJavascript(
                        "(function() { " +
                                "setInterval(function() {" +
                                "  var clickEvents = ['click', 'mousedown', 'mouseup', 'touchstart', 'touchend'];" +
                                "  var elements = document.querySelectorAll('button, .play-button, .vjs-big-play-button, video, div');" +
                                "  for (var i = 0; i < elements.length; i++) {" +
                                "    if (elements[i].tagName.toLowerCase() === 'video') {" +
                                "       elements[i].play().catch(e => {});" +
                                "    }" +
                                "    for (var j = 0; j < clickEvents.length; j++) {" +
                                "      var event = new MouseEvent(clickEvents[j], {view: window, bubbles: true, cancelable: true});" +
                                "      elements[i].dispatchEvent(event);" +
                                "    }" +
                                "  }" +
                                "}, 500);" +
                                "})();", null
                    )
                }
            }
        }

        webView.loadUrl(embedUrl)

        val result = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        }

        // Clean up WebView on Main thread
        kotlinx.coroutines.delay(500)
        webView.stopLoading()
        webView.destroy()

        result
    }
}
