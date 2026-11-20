package com.pointlessapps.filman.player.lektor.alignment

import com.pointlessapps.filman.player.lektor.model.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioVadAlignerTest {

    @Test
    fun `alignWithSpeech accurately determines offset from detected speech intervals`() {
        val speechIntervals = listOf(
            SpeechInterval(startMs = 5000, endMs = 7000),
            SpeechInterval(startMs = 12000, endMs = 15000),
            SpeechInterval(startMs = 20000, endMs = 23000),
            SpeechInterval(startMs = 30000, endMs = 34000),
            SpeechInterval(startMs = 45000, endMs = 48000),
            SpeechInterval(startMs = 60000, endMs = 63000),
        )

        // Target subtitles are delayed by 2500ms
        val targetCues = listOf(
            SubtitleCue(index = 1, startMs = 7500, endMs = 9500, text = "Hello"),
            SubtitleCue(index = 2, startMs = 14500, endMs = 17500, text = "World"),
            SubtitleCue(index = 3, startMs = 22500, endMs = 25500, text = "How are you"),
            SubtitleCue(index = 4, startMs = 32500, endMs = 36500, text = "Fine thanks"),
            SubtitleCue(index = 5, startMs = 47500, endMs = 50500, text = "Nice day"),
            SubtitleCue(index = 6, startMs = 62500, endMs = 65500, text = "Goodbye"),
        )

        val result = AudioVadAligner.alignWithSpeech(
            speechIntervals = speechIntervals,
            targetCues = targetCues,
            maxLagMs = 10000,
        )

        assertNotNull(result)
        // Shift needed to move target to reference is -2500ms
        assertEquals(-2500.0, result!!.bestOffsetMs.toDouble(), 150.0)
        assertEquals(1.0, result.bestFpsRatio, 0.01)
        assertTrue(result.confidence > 0.8)
    }
}
