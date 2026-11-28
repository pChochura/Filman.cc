package com.pointlessapps.filman.player.lektor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTimelineTest {

    @Test
    fun `detectDialogueEndMs filters trailing credit cues and returns accurate credit start`() {
        val cues = listOf(
            SubtitleCue(index = 1, startMs = 10_000, endMs = 15_000, text = "Cześć, jak się masz?"),
            SubtitleCue(index = 2, startMs = 2_400_000, endMs = 2_500_000, text = "To koniec naszej przygody."),
            SubtitleCue(index = 3, startMs = 2_510_000, endMs = 2_520_000, text = "Tłumaczenie: Jan Kowalski"),
            SubtitleCue(index = 4, startMs = 2_525_000, endMs = 2_530_000, text = "Napisy pobrano z opensubtitles.org"),
        )
        val durationMs = 2_700_000L // 45 minutes
        val timeline = SubtitleTimeline(cues = cues)

        val detectedStart = timeline.detectDialogueEndMs(durationMs)
        // Last dialogue cue ends at 2_500_000. Buffer is +2000ms -> 2_502_000.
        assertEquals(2_502_000L, detectedStart)
    }

    @Test
    fun `detectDialogueEndMs returns null if last dialogue cue is too early`() {
        val cues = listOf(
            SubtitleCue(index = 1, startMs = 10_000, endMs = 15_000, text = "Tylko początek filmu"),
            SubtitleCue(index = 2, startMs = 500_000, endMs = 600_000, text = "I potem cisza"),
        )
        val durationMs = 2_700_000L // 600k is ~22%, way below 70%
        val timeline = SubtitleTimeline(cues = cues)

        assertNull(timeline.detectDialogueEndMs(durationMs))
    }

    @Test
    fun `hasSpokenDialogueAt distinguishes dialogue from metadata`() {
        val cues = listOf(
            SubtitleCue(index = 1, startMs = 10_000, endMs = 20_000, text = "Ważna kwestia aktora"),
            SubtitleCue(index = 2, startMs = 30_000, endMs = 40_000, text = "Napisy: Grupa Hatak"),
        )
        val timeline = SubtitleTimeline(cues = cues)

        assertTrue(timeline.hasSpokenDialogueAt(15_000))
        assertFalse(timeline.hasSpokenDialogueAt(25_000))
        assertFalse(timeline.hasSpokenDialogueAt(35_000)) // metadata cue ignored as spoken dialogue
    }
}
