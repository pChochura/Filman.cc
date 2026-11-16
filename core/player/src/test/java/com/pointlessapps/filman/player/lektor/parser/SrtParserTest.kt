package com.pointlessapps.filman.player.lektor.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtParserTest {

    @Test
    fun testParseStandardSrt() {
        val srtContent = """
            1
            00:00:01,000 --> 00:00:04,500
            Dzień dobry wszystkim!

            2
            00:00:05,200 --> 00:00:08,000
            - Jak się masz?
            - Całkiem nieźle.

            3
            00:00:09,000 --> 00:00:10,000
            [muzyka]
        """.trimIndent()

        val cues = SrtParser.parse(srtContent)
        // Note: cue 3 had only "[muzyka]", which normalizes to empty, so it should be skipped!
        assertEquals(2, cues.size)

        assertEquals(1, cues[0].index)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(4500L, cues[0].endMs)
        assertEquals(3500L, cues[0].durationMs)
        assertEquals("Dzień dobry wszystkim!", cues[0].text)

        assertEquals(2, cues[1].index)
        assertEquals(5200L, cues[1].startMs)
        assertEquals(8000L, cues[1].endMs)
        assertEquals("Jak się masz? Całkiem nieźle.", cues[1].text)
    }

    @Test
    fun testParseDotMillisecondsAndBom() {
        val srtContent = "\uFEFF1\n00:01:05.123 --> 00:01:08.456\nTest dot timestamp."
        val cues = SrtParser.parse(srtContent)
        assertEquals(1, cues.size)
        assertEquals(65123L, cues[0].startMs)
        assertEquals(68456L, cues[0].endMs)
        assertEquals("Test dot timestamp.", cues[0].text)
    }

    @Test
    fun testParseWebVttTwoPartTimestampsAndSettings() {
        val vttContent = """
            WEBVTT

            NOTE This is a test comment

            01:05.123 --> 01:08.456 position:10% line:80%
            <v Speaker>Witaj świecie!</v>
        """.trimIndent()
        val cues = SrtParser.parse(vttContent)
        assertEquals(1, cues.size)
        assertEquals(65123L, cues[0].startMs)
        assertEquals(68456L, cues[0].endMs)
        assertEquals("Witaj świecie!", cues[0].text)
    }
}
