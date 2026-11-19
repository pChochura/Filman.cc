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

    @Test
    fun testZlSubstringDoesNotCorruptPolishWordsAndCurrencyFormatsCorrectly() {
        val neural = PolishNormalizationOptions(mode = PolishNormalizationMode.NEURAL)
        val offline = PolishNormalizationOptions(mode = PolishNormalizationMode.OFFLINE)

        val trickyWords = "Przyszłość poszła, a zły złodziej znalazł klucz."
        assertEquals(trickyWords, PolishTextNormalizer.normalize(trickyWords, neural))
        assertEquals(trickyWords, PolishTextNormalizer.normalize(trickyWords, offline))

        assertEquals("Mam 50 złotych.", PolishTextNormalizer.normalize("Mam 50 zł.", neural))
        assertEquals("Mam pięćdziesiąt złotych.", PolishTextNormalizer.normalize("Mam 50 zł.", offline))
        assertEquals("Kosztuje 1 złoty.", PolishTextNormalizer.normalize("Kosztuje 1 zł.", neural))
        assertEquals("Kosztuje 2 złote.", PolishTextNormalizer.normalize("Kosztuje 2 zł.", neural))
        assertEquals("To 50 dolarów.", PolishTextNormalizer.normalize("To $50.", neural))
        assertEquals("To pięćdziesiąt dolarów.", PolishTextNormalizer.normalize("To $50.", offline))
        assertEquals("Bilet za 20 euro.", PolishTextNormalizer.normalize("Bilet za €20.", neural))
    }

    @Test
    fun testNeuralModePreservesNumbersTimesDatesAndAbbreviations() {
        val neural = PolishNormalizationOptions(mode = PolishNormalizationMode.NEURAL)
        val input = "Spotkamy się o 15:30 przy ul. Długiej w 1999 r. w 21. wieku."
        assertEquals(input, PolishTextNormalizer.normalize(input, neural))
    }

    @Test
    fun testHtmlEntitiesInvisibleCharsAndPolishSdhCleanup() {
        val neural = PolishNormalizationOptions(mode = PolishNormalizationMode.NEURAL)
        val input = "&quot;Cześć&quot;&#39; \u200B&nbsp;(westchnęła)\n♪ La la la ♪\n" +
            "(śmieją się) [MAREK]: Co?\nTłumaczenie: Jan\nJAN: Chodź. (po angielsku)"
        assertEquals("\"Cześć\"' Co? Chodź.", PolishTextNormalizer.normalize(input, neural))
    }

    @Test
    fun testAllCapsDecapitalizationPreservesAcronyms() {
        val neural = PolishNormalizationOptions(mode = PolishNormalizationMode.NEURAL)
        assertEquals(
            "Trzy lata później agent FBI w USA i ONZ oraz CBŚ.",
            PolishTextNormalizer.normalize("TRZY LATA PÓŹNIEJ AGENT FBI W USA I ONZ ORAZ CBŚ.", neural),
        )
        assertEquals(
            "Powiedziałem nie! To nie jest DNA.",
            PolishTextNormalizer.normalize("Powiedziałem NIE! To nie jest DNA.", neural),
        )
    }

    @Test
    fun testEdgePhoneticsAndContextualRomanNumerals() {
        val neural = PolishNormalizationOptions(mode = PolishNormalizationMode.NEURAL)
        assertEquals(
            "Okej, mister Smith i pani Brown kontra Tom i Jerry.",
            PolishTextNormalizer.normalize("OK, Mr. Smith i Mrs. Brown vs. Tom & Jerry.", neural),
        )
        assertEquals("Co to jest?", PolishTextNormalizer.normalize("Hmm... co to jest?", neural))
        assertEquals(
            "Druga wojna światowa w dwudziestym wieku, Jan Paweł drugi, rozdział czwarty.",
            PolishTextNormalizer.normalize(
                "II wojna światowa w XX wieku, Jan Paweł II, rozdział IV.",
                neural,
            ),
        )
    }

    @Test
    fun testPunctuationCleanupMissingCommasAndDialogueTurns() {
        val neural = PolishNormalizationOptions(mode = PolishNormalizationMode.NEURAL)
        assertEquals("Co? Ale…", PolishTextNormalizer.normalize("Co ?! Ale--", neural))
        assertEquals(
            "Wiedziałem, że przyjdziesz.",
            PolishTextNormalizer.normalize("Wiedziałem że przyjdziesz.", neural),
        )
        assertEquals(
            "Chyba że zostaniesz mimo że padało.",
            PolishTextNormalizer.normalize("Chyba że zostaniesz mimo że padało.", neural),
        )
        assertEquals(
            "Poczekaj chwilę.\nNie mam czasu",
            PolishTextNormalizer.normalize("Poczekaj chwilę\n- Nie mam czasu", neural),
        )
    }

    @Test
    fun testCalmExclamationsSoftensMildAndLongLinesWhilePreservingUrgentShouts() {
        val calm = PolishNormalizationOptions(
            mode = PolishNormalizationMode.NEURAL,
            calmExclamations = true,
        )
        assertEquals("Cześć.", PolishTextNormalizer.normalize("Cześć!", calm))
        assertEquals(
            "To był naprawdę długi dzień.",
            PolishTextNormalizer.normalize("To był naprawdę długi dzień!", calm),
        )
        assertEquals("Uciekaj!", PolishTextNormalizer.normalize("Uciekaj!", calm))
    }
}
