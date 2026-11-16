package com.pointlessapps.filman.player.lektor.parser

import com.pointlessapps.filman.player.lektor.model.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Test

class LektorSentenceGrouperTest {
    private fun cue(index: Int, start: Long, end: Long, text: String) =
        SubtitleCue(index, start, end, text)

    @Test
    fun `combines split sentences into one timed utterance`() {
        val result = LektorSentenceGrouper.group(
            listOf(
                cue(1, 1_000, 2_000, "Kiedy wrócę"),
                cue(2, 2_200, 3_000, "do domu,"),
                cue(3, 3_100, 4_000, "porozmawiamy."),
                cue(4, 4_100, 5_000, "Dobrze?"),
            ),
        )
        assertEquals(
            listOf(
                cue(1, 1_000, 4_000, "Kiedy wrócę do domu, porozmawiamy."),
                cue(2, 4_100, 5_000, "Dobrze?"),
            ),
            result,
        )
    }

    @Test
    fun `handles srt formatting normalization and quoted endings`() {
        val raw = """
            1
            00:00:01,000 --> 00:00:02,000
            <i>To jest np.</i>

            2
            00:00:02,100 --> 00:00:03,000
            "przykład".

            3
            00:00:03,100 --> 00:00:04,000
            Następne zdanie!
        """.trimIndent()
        val result = LektorSentenceGrouper.group(SrtParser.parse(raw, normalizePolish = false))
        assertEquals(2, result.size)
        assertEquals("To jest na przykład \"przykład\".", result[0].text)
        assertEquals(3_000L, result[0].endMs)
        assertEquals("Następne zdanie!", result[1].text)
    }

    @Test
    fun `does not join separate dialogue speakers or long gaps`() {
        val result = LektorSentenceGrouper.group(
            listOf(
                cue(1, 0, 1_000, "- Nie wiem"),
                cue(2, 1_100, 2_000, "<i>- Ja też nie</i>"),
                cue(3, 4_000, 5_000, "Może później"),
            ),
        )
        assertEquals(listOf("Nie wiem", "Ja też nie", "Może później"), result.map { it.text })
    }

    @Test
    fun `silence and sound effects break a group`() {
        val result = LektorSentenceGrouper.group(
            listOf(
                cue(1, 0, 1_000, "Czekaj"),
                cue(2, 1_000, 1_200, "[muzyka]"),
                cue(3, 1_300, 2_000, "Wracaj"),
            ),
        )
        assertEquals(listOf("Czekaj", "Wracaj"), result.map { it.text })
    }

    @Test
    fun `ellipsis and overlapping cues continue within bounded group`() {
        val result = LektorSentenceGrouper.group(
            listOf(
                cue(1, 0, 2_000, "Jeżeli..."),
                cue(2, 1_900, 2_500, "to prawda,"),
                cue(3, 2_400, 3_000, "chodźmy."),
            ),
        )
        assertEquals(listOf(cue(1, 0, 3_000, "Jeżeli... to prawda, chodźmy.")), result)
    }

    @Test
    fun `unterminated subtitles are bounded and retain final fragment`() {
        val cues = (0..9).map { cue(it + 1, it * 1_000L, it * 1_000L + 900, "słowo") }
        val result = LektorSentenceGrouper.group(cues)
        assertEquals(2, result.size)
        assertEquals(8, result[0].text.split(" ").size)
        assertEquals(2, result[1].text.split(" ").size)
        assertEquals(7_900L, result[0].endMs)
        assertEquals(8_000L, result[1].startMs)
    }

    @Test
    fun `duration and text limits keep long unpunctuated passages bounded`() {
        val durationLimited = LektorSentenceGrouper.group(
            listOf(cue(1, 0, 10_000, "Początek"), cue(2, 10_000, 20_001, "koniec")),
        )
        assertEquals(2, durationLimited.size)
        val lengthLimited = LektorSentenceGrouper.group(
            listOf(cue(1, 0, 1_000, "a".repeat(599)), cue(2, 1_000, 2_000, "b")),
        )
        assertEquals(2, lengthLimited.size)
    }

    @Test
    fun `sentence ending with closing quotation stays separate`() {
        val result = LektorSentenceGrouper.group(
            listOf(cue(1, 0, 1_000, "„Dobrze!”"), cue(2, 1_000, 2_000, "Następne")),
        )
        assertEquals(2, result.size)
    }
}
