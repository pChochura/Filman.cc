package com.pointlessapps.filman.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewResumeTest {
    @Test
    fun `ignores stale opening updates until playback reaches resume position`() {
        assertFalse(isResumePositionReached(0.5, 400.0, 120_000))
        assertFalse(isResumePositionReached(119.0, Double.NaN, 120_000))
        assertTrue(isResumePositionReached(120.0, 400.0, 120_000))
    }

    @Test
    fun `accepts position clamped to shorter video`() {
        assertFalse(isResumePositionReached(50.0, 100.0, 120_000))
        assertTrue(isResumePositionReached(99.0, 100.0, 120_000))
    }
}
