package com.pointlessapps.filman.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `isLowQualityPolishSubtitle flags mojibake and ASCII-stripped Polish while accepting valid Polish`() {
        assertTrue(isLowQualityPolishSubtitle("1\n00:00:01,000 --> 00:00:03,000\nCo\uFFFD nie tak"))

        val asciiPolish = (1..10).joinToString("\n\n") { i ->
            "$i\n00:00:${"%02d".format(i * 2)},000 --> 00:00:${"%02d".format(i * 2 + 1)},000\n" +
                "Przyszlosc poszla w zla strone, a zlodziej uciekl z miasta zrodlowego."
        }
        assertTrue(isLowQualityPolishSubtitle(asciiPolish))

        val properPolish = (1..10).joinToString("\n\n") { i ->
            "$i\n00:00:${"%02d".format(i * 2)},000 --> 00:00:${"%02d".format(i * 2 + 1)},000\n" +
                "Przyszłość poszła w złą stronę, a złodziej uciekł z miasta źródłowego."
        }
        assertFalse(isLowQualityPolishSubtitle(properPolish))

        val shortAscii = "1\n00:00:01,000 --> 00:00:03,000\nCzesc, jak sie masz?"
        assertFalse(isLowQualityPolishSubtitle(shortAscii))
    }
}
