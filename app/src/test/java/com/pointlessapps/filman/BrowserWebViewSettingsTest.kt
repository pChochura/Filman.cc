package com.pointlessapps.filman

import android.webkit.WebSettings
import android.webkit.WebView
import com.pointlessapps.filman.ui.core.configureBrowserSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class BrowserWebViewSettingsTest {
    @Test
    fun `browser session enables scripts and storage with the supplied user agent`() {
        val settings = mockk<WebSettings>(relaxed = true)
        val webView = mockk<WebView> {
            every { getSettings() } returns settings
        }

        webView.configureBrowserSession("WebView user agent")

        verify(exactly = 1) { settings.javaScriptEnabled = true }
        verify(exactly = 1) { settings.domStorageEnabled = true }
        verify(exactly = 1) { settings.userAgentString = "WebView user agent" }
    }
}
