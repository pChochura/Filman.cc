package com.pointlessapps.filman.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerBufferingTest {
    @Test
    fun `webview loading shows buffering even when previous source was ready`() {
        assertTrue(playerIsBuffering(isBuffering = false, isWebView = true, isWebViewLoading = true))
    }

    @Test
    fun `ready webview follows playback buffering`() {
        assertFalse(playerIsBuffering(isBuffering = false, isWebView = true, isWebViewLoading = false))
        assertTrue(playerIsBuffering(isBuffering = true, isWebView = true, isWebViewLoading = false))
    }

    @Test
    fun `native player ignores webview loading state`() {
        assertFalse(playerIsBuffering(isBuffering = false, isWebView = false, isWebViewLoading = true))
        assertTrue(playerIsBuffering(isBuffering = true, isWebView = false, isWebViewLoading = false))
    }
}
