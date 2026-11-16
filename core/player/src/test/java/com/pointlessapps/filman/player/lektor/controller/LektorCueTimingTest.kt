package com.pointlessapps.filman.player.lektor.controller

import com.pointlessapps.filman.data.model.LektorTiming
import com.pointlessapps.filman.player.lektor.model.SubtitleCue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LektorCueTimingTest {
    private val cue = SubtitleCue(1, 5_000, 6_000, "Test")

    @Test
    fun `default starts half a second before subtitles`() {
        assertFalse(LektorCueTiming.isDue(cue, 4_499, LektorTiming.DEFAULT_OFFSET_MS))
        assertTrue(LektorCueTiming.isDue(cue, 4_500, LektorTiming.DEFAULT_OFFSET_MS))
        assertTrue(LektorCueTiming.isDue(cue, 5_800, LektorTiming.DEFAULT_OFFSET_MS))
        assertFalse(LektorCueTiming.isDue(cue, 5_801, LektorTiming.DEFAULT_OFFSET_MS))
    }

    @Test
    fun `zero and positive offsets preserve expected boundaries`() {
        assertFalse(LektorCueTiming.isDue(cue, 4_999, 0))
        assertTrue(LektorCueTiming.isDue(cue, 5_000, 0))
        assertFalse(LektorCueTiming.isDue(cue, 5_499, 500))
        assertTrue(LektorCueTiming.isDue(cue, 5_500, 500))
        assertTrue(LektorCueTiming.isDue(cue, 6_800, 500))
        assertFalse(LektorCueTiming.isDue(cue, 6_801, 500))
    }
}
