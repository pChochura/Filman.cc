package com.pointlessapps.filman.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchModeTest {

    @Test
    fun `default state resolves to ORIGINAL watch mode and hasDubbing false`() {
        val state = PlayerState()
        assertEquals(WatchMode.ORIGINAL, state.currentWatchMode)
        assertFalse(state.hasDubbing)
    }

    @Test
    fun `selected subtitles resolves to SUBTITLES watch mode`() {
        val state = PlayerState(
            selectedSubtitleUrl = "https://example.com/subs.srt",
        )
        assertEquals(WatchMode.SUBTITLES, state.currentWatchMode)
    }

    @Test
    fun `isLektorEnabled resolves to LEKTOR watch mode even if subtitles selected`() {
        val state = PlayerState(
            selectedSubtitleUrl = "https://example.com/subs.srt",
            isLektorEnabled = true,
        )
        assertEquals(WatchMode.LEKTOR, state.currentWatchMode)
    }

    @Test
    fun `lektor audio track resolves to LEKTOR watch mode`() {
        val state = PlayerState(
            audioTracks = listOf(
                PlayerAudioTrack(id = "1", label = "English Original", isSelected = false),
                PlayerAudioTrack(id = "2", label = "Polski Lektor", isSelected = true),
            ),
            selectedAudioTrackId = "2",
        )
        assertEquals(WatchMode.LEKTOR, state.currentWatchMode)
    }

    @Test
    fun `dubbing audio track resolves to DUBBING watch mode and hasDubbing true`() {
        val state = PlayerState(
            audioTracks = listOf(
                PlayerAudioTrack(id = "1", label = "English Original", isSelected = false),
                PlayerAudioTrack(id = "2", label = "Polski Dubbing", isSelected = true),
            ),
            selectedAudioTrackId = "2",
        )
        assertEquals(WatchMode.DUBBING, state.currentWatchMode)
        assertTrue(state.hasDubbing)
    }
}
