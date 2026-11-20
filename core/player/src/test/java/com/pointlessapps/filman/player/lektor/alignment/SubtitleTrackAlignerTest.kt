package com.pointlessapps.filman.player.lektor.alignment

import com.pointlessapps.filman.player.lektor.model.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTrackAlignerTest {

    @Test
    fun `detects exact offset between reference and target cues`() {
        val referenceCues = listOf(
            SubtitleCue(1, 10_000, 14_000, "Hello"),
            SubtitleCue(2, 20_000, 25_000, "World"),
            SubtitleCue(3, 40_000, 48_000, "Testing speech"),
            SubtitleCue(4, 60_000, 65_000, "Alignment test"),
            SubtitleCue(5, 80_000, 85_000, "Finished"),
        )

        val shiftMs = 3_500
        val targetCues = referenceCues.map { cue ->
            cue.copy(startMs = cue.startMs - shiftMs, endMs = cue.endMs - shiftMs)
        }

        val result = SubtitleTrackAligner.align(referenceCues, targetCues, maxLagMs = 10_000)
        assertNotNull(result)
        assertEquals(shiftMs, result!!.bestOffsetMs)
        assertEquals(1.0, result.bestFpsRatio, 0.001)
        assertTrue(result.confidence > 0.9)
    }

    @Test
    fun `detects 25 to 23_976 fps scaling and offset`() {
        val ratio = 25.0 / 23.976 // ~1.0427
        val shiftMs = 2_000

        val referenceCues = listOf(
            SubtitleCue(1, 10_000, 15_000, "Line 1"),
            SubtitleCue(2, 60_000, 65_000, "Line 2"),
            SubtitleCue(3, 120_000, 125_000, "Line 3"),
            SubtitleCue(4, 200_000, 205_000, "Line 4"),
            SubtitleCue(5, 300_000, 305_000, "Line 5"),
        )

        // Target was recorded at 23.976 fps instead of 25 fps
        val targetCues = referenceCues.map { cue ->
            cue.copy(
                startMs = ((cue.startMs - shiftMs) / ratio).toLong(),
                endMs = ((cue.endMs - shiftMs) / ratio).toLong(),
            )
        }

        val result = SubtitleTrackAligner.align(referenceCues, targetCues, maxLagMs = 10_000)
        assertNotNull(result)
        assertEquals(ratio, result!!.bestFpsRatio, 0.005)
        assertEquals(shiftMs.toDouble(), result.bestOffsetMs.toDouble(), 150.0)
        assertTrue(result.confidence > 0.8)
    }
}
