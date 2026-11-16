package com.pointlessapps.filman.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleDiagnosticsTest {
    private fun cues(times: List<Int>): String = times.mapIndexed { index, seconds ->
        val minutes = seconds / 60
        val second = seconds % 60
        "${index + 1}\n00:%02d:%02d,000 --> 00:%02d:%02d,500\nDialogue".format(minutes, second, minutes, second)
    }.joinToString("\n\n")

    @Test
    fun `reports unreadable and sparse tracks`() {
        assertEquals(SubtitleWarning.UNREADABLE, diagnoseSubtitle("not subtitles", 0))
        assertEquals(SubtitleWarning.POSSIBLY_INCOMPLETE, diagnoseSubtitle(cues(listOf(1, 2)), 0))
    }

    @Test
    fun `reports long gaps and tracks ending early`() {
        assertEquals(SubtitleWarning.POSSIBLY_INCOMPLETE, diagnoseSubtitle(cues((0..10).toList() + 700), 0))
        assertEquals(SubtitleWarning.POSSIBLY_INCOMPLETE, diagnoseSubtitle(cues((0..10).map { it * 60 }), 3_600_000))
    }

    @Test
    fun `accepts normal coverage and does not infer ending without duration`() {
        val content = cues((0..10).map { it * 60 })
        assertNull(diagnoseSubtitle(content, 0))
        assertNull(diagnoseSubtitle(content, 650_000))
    }
}
