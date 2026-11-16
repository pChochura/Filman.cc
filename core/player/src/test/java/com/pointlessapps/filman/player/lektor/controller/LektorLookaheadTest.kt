package com.pointlessapps.filman.player.lektor.controller

import com.pointlessapps.filman.player.lektor.model.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LektorLookaheadTest {
    private fun cue(index: Int, startMs: Long, length: Int = 20) =
        SubtitleCue(index, startMs, startMs + 2_000, "a".repeat(length))

    @Test
    fun `slow synthesis skips cues that cannot be ready in time`() {
        val cues = listOf(cue(1, 5_000), cue(2, 20_000), cue(3, 35_000))

        assertEquals(3, LektorLookahead.nextCue(cues, 0, 1_000.0) { false }?.index)
    }

    @Test
    fun `cached cues do not prevent scheduling later cues`() {
        val cues = listOf(cue(1, 20_000), cue(2, 30_000), cue(3, 40_000), cue(4, 50_000))

        assertEquals(3, LektorLookahead.nextCue(cues, 0, 100.0) { it <= 2 }?.index)
        assertNull(LektorLookahead.nextCue(cues, 0, 100.0) { it <= 3 })
    }

    @Test
    fun `when no cue can be ready on time choose the cue with most lead time`() {
        val cues = listOf(cue(1, 5_000), cue(2, 10_000))

        assertEquals(2, LektorLookahead.nextCue(cues, 0, 1_000.0) { false }?.index)
    }

    @Test
    fun `ignore expired cues and cues beyond lookahead horizon`() {
        val cues = listOf(cue(1, 1_000), cue(2, 30_000), cue(3, 150_000))

        assertEquals(2, LektorLookahead.nextCue(cues, 5_000, null) { false }?.index)
        assertNull(LektorLookahead.nextCue(listOf(cues[0], cues[2]), 5_000, null) { false })
    }

    @Test
    fun `slow inference selects cues that can still be played`() {
        val cues = (1..30).map { cue(it, it * 4_000L) }
        val cached = mutableSetOf<Int>()
        var positionMs = 0L
        var playable = 0

        repeat(5) {
            val selected = requireNotNull(LektorLookahead.nextCue(cues, positionMs, 750.0) { it in cached })
            positionMs += 15_000L
            if (positionMs <= selected.startMs) playable++
            cached.add(selected.index)
        }

        assertEquals(5, playable)
    }
}
