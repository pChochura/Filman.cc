package com.pointlessapps.filman.lite.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val LOGIN_PATH = "/logowanie"
private const val SITE = "https://filman.cc"

actual fun clearFilmanWebSession() {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun FilmanWebLogin(
    onLoggedIn: (cookie: String, userAgent: String) -> Unit,
    modifier: Modifier,
) {
    val callback by rememberUpdatedState(onLoggedIn)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).also { view ->
                view.settings.javaScriptEnabled = true
                view.settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        CookieManager.getInstance().flush()
                        val cookies = CookieManager.getInstance().getCookie(SITE).orEmpty()
                        val onFilman = url?.startsWith(SITE) == true
                        if (onFilman && !url.contains(LOGIN_PATH) && cookies.contains("PHPSESSID")) {
                            callback(cookies, view.settings.userAgentString)
                        }
                    }
                }
                view.loadUrl(FILMAN_LOGIN_URL)
            }
        },
        onRelease = { it.destroy() },
    )
}
