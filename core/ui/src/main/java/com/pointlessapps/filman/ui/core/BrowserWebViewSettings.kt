package com.pointlessapps.filman.ui.core

import android.annotation.SuppressLint
import android.webkit.WebView

@SuppressLint("SetJavaScriptEnabled")
fun WebView.configureBrowserSession(userAgent: String) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.userAgentString = userAgent
}
