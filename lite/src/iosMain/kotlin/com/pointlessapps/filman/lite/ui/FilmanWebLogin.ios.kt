package com.pointlessapps.filman.lite.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

private const val LOGIN_PATH = "/logowanie"
private const val SITE = "filman.cc"

actual fun clearFilmanWebSession() {
    WKWebsiteDataStore.defaultDataStore().removeDataOfTypes(
        WKWebsiteDataStore.allWebsiteDataTypes(),
        modifiedSince = NSDate(timeIntervalSinceReferenceDate = 0.0),
        completionHandler = {},
    )
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun FilmanWebLogin(
    onLoggedIn: (cookie: String, userAgent: String) -> Unit,
    modifier: Modifier,
) {
    val callback by rememberUpdatedState(onLoggedIn)
    val delegate = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                val url = webView.URL?.absoluteString.orEmpty()
                if (!url.contains(SITE) || url.contains(LOGIN_PATH)) return
                webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { cookies ->
                    val filman = cookies.orEmpty().filterIsInstance<NSHTTPCookie>()
                        .filter { it.domain.contains(SITE) }
                    if (filman.none { it.name == "PHPSESSID" }) return@getAllCookies
                    val header = filman.joinToString("; ") { "${it.name}=${it.value}" }
                    webView.evaluateJavaScript("navigator.userAgent") { result, _ ->
                        callback(header, result as? String ?: "")
                    }
                }
            }
        }
    }
    UIKitView(
        modifier = modifier,
        factory = {
            WKWebView(frame = CGRectZero.readValue(), configuration = WKWebViewConfiguration()).apply {
                navigationDelegate = delegate
                loadRequest(NSURLRequest.requestWithURL(NSURL.URLWithString(FILMAN_LOGIN_URL)!!))
            }
        },
    )
}
