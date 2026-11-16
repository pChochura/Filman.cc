package com.pointlessapps.filman.player.lektor.normalizer

import org.junit.Assert.assertEquals
import org.junit.Test

class PolishTextNormalizerTest {

    @Test
    fun testStripHtmlAndAssTags() {
        val input = "<i>Witaj</i> w <b>Matrixie</b>{\\an8}!"
        val output = PolishTextNormalizer.normalize(input)
        assertEquals("Witaj w Matrixie!", output)
    }

    @Test
    fun testStripSoundEffectsAndNoise() {
        val input = "[muzyka dramatyczna] Co to było? (westchnienie) *śmiech*"
        val output = PolishTextNormalizer.normalize(input)
        assertEquals("Co to było?", output)
    }

    @Test
    fun testDialogueDashesRemoval() {
        val input = """
            - Cześć!
            - Cześć, jak się masz?
        """.trimIndent()
        val output = PolishTextNormalizer.normalize(input)
        assertEquals("Cześć! Cześć, jak się masz?", output)

        val unpunctuatedDialogue = """
            - Nie idź tam
            - Dlaczego?
        """.trimIndent()
        val unpunctuatedOutput = PolishTextNormalizer.normalize(unpunctuatedDialogue)
        assertEquals("Nie idź tam. Dlaczego?", unpunctuatedOutput)

        val multiLineSentence = """
            To jest bardzo długie zdanie
            które nie mieści się w jednej linii
        """.trimIndent()
        val multiLineOutput = PolishTextNormalizer.normalize(multiLineSentence)
        assertEquals("To jest bardzo długie zdanie które nie mieści się w jednej linii", multiLineOutput)
    }

    @Test
    fun testAbbreviationExpansion() {
        val input = "np. dr Jan Kowalski przy ul. Długiej w 1999 r."
        val output = PolishTextNormalizer.normalize(input)
        assertEquals("na przykład doktor Jan Kowalski przy ulica Długiej w tysiąc dziewięćset dziewięćdziesiąt dziewięć roku", output)
    }

    @Test
    fun testNumberToPolishWords() {
        assertEquals("zero", PolishTextNormalizer.numberToPolishWords(0))
        assertEquals("pięć", PolishTextNormalizer.numberToPolishWords(5))
        assertEquals("czternaście", PolishTextNormalizer.numberToPolishWords(14))
        assertEquals("czterdzieści dwa", PolishTextNormalizer.numberToPolishWords(42))
        assertEquals("sto dwadzieścia trzy", PolishTextNormalizer.numberToPolishWords(123))
        assertEquals("tysiąc", PolishTextNormalizer.numberToPolishWords(1000))
        assertEquals("dwa tysiące", PolishTextNormalizer.numberToPolishWords(2000))
        assertEquals("pięć tysięcy trzysta dwadzieścia jeden", PolishTextNormalizer.numberToPolishWords(5321))
    }
}
