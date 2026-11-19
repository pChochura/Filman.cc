package com.pointlessapps.filman.player.lektor.normalizer

import java.util.regex.Pattern

internal data class NormalizedSubtitleText(
    val text: String,
    val isMultiSpeaker: Boolean,
)

object PolishTextNormalizer {

    private val ABBREVIATIONS = listOf(
        Pair(Pattern.compile("\\bnp\\.", Pattern.CASE_INSENSITIVE), "na przykład"),
        Pair(Pattern.compile("\\bm\\.in\\.", Pattern.CASE_INSENSITIVE), "między innymi"),
        Pair(Pattern.compile("\\btzw\\.", Pattern.CASE_INSENSITIVE), "tak zwany"),
        Pair(Pattern.compile("\\btzn\\.", Pattern.CASE_INSENSITIVE), "to znaczy"),
        Pair(Pattern.compile("\\bdr\\b", Pattern.CASE_INSENSITIVE), "doktor"),
        Pair(Pattern.compile("\\bprof\\.", Pattern.CASE_INSENSITIVE), "profesor"),
        Pair(Pattern.compile("\\bul\\.", Pattern.CASE_INSENSITIVE), "ulica"),
        Pair(Pattern.compile("(\\d+)\\s*r\\.", Pattern.CASE_INSENSITIVE), "$1 roku"),
        Pair(Pattern.compile("\\btys\\.", Pattern.CASE_INSENSITIVE), "tysięcy"),
        Pair(Pattern.compile("\\bmln\\b", Pattern.CASE_INSENSITIVE), "milionów"),
        Pair(Pattern.compile("\\bmld\\b", Pattern.CASE_INSENSITIVE), "miliardów"),
        Pair(Pattern.compile("\\bitd\\.", Pattern.CASE_INSENSITIVE), "i tak dalej"),
        Pair(Pattern.compile("\\bitp\\.", Pattern.CASE_INSENSITIVE), "i tym podobne"),
        Pair(Pattern.compile("\\bgodz\\.", Pattern.CASE_INSENSITIVE), "godzina"),
        Pair(Pattern.compile("\\bart\\.", Pattern.CASE_INSENSITIVE), "artykuł"),
        Pair(Pattern.compile("\\bust\\.", Pattern.CASE_INSENSITIVE), "ustęp"),
        Pair(Pattern.compile("\\bpkt\\b", Pattern.CASE_INSENSITIVE), "punkt"),
        Pair(Pattern.compile("\\bok\\.", Pattern.CASE_INSENSITIVE), "około"),
        Pair(Pattern.compile("\\bp\\.n\\.e\\.", Pattern.CASE_INSENSITIVE), "przed naszą erą"),
        Pair(Pattern.compile("\\bn\\.e\\.", Pattern.CASE_INSENSITIVE), "naszej ery"),
        Pair(Pattern.compile("\\bwg\\b", Pattern.CASE_INSENSITIVE), "według"),
    )

    private val NUMBER_PATTERN = Pattern.compile("\\b(\\d{1,6})\\b")

    private val UNITS = arrayOf(
        "", "jeden", "dwa", "trzy", "cztery", "pięć", "sześć", "siedem", "osiem", "dziewięć",
    )
    private val TEENS = arrayOf(
        "dziesięć", "jedenaście", "dwanaście", "trzynaście", "czternaście", "piętnaście",
        "szesnaście", "siedemnaście", "osiemnaście", "dziewiętnaście",
    )
    private val TENS = arrayOf(
        "", "dziesięć", "dwadzieścia", "trzydzieści", "czterdzieści", "pięćdziesiąt",
        "sześćdziesiąt", "siedemdziesiąt", "osiemdziesiąt", "dziewięćdziesiąt",
    )
    private val HUNDREDS = arrayOf(
        "", "sto", "dwieście", "trzysta", "czterysta", "pięćset",
        "sześćset", "siedemset", "osiemset", "dziewięćset",
    )

    fun normalize(
        text: String,
        options: PolishNormalizationOptions = PolishNormalizationOptions(),
    ): String = normalizeWithMetadata(text, options).text

    internal fun normalizeWithMetadata(
        text: String,
        options: PolishNormalizationOptions = PolishNormalizationOptions(),
    ): NormalizedSubtitleText {
        val cleaned = PolishSubtitleCleaner.clean(text)
        if (cleaned.isBlank()) return NormalizedSubtitleText("", isMultiSpeaker = false)

        val isNeural = options.mode == PolishNormalizationMode.NEURAL
        val dialogue = PolishProsodyConditioner.formatDialogueLines(cleaned, preserveTurnBreak = isNeural)
        var result = PolishProsodyConditioner.fixCurrencyAndSymbols(dialogue.text)

        result = when (options.mode) {
            PolishNormalizationMode.OFFLINE -> normalizeOffline(result)
            PolishNormalizationMode.NEURAL -> normalizeNeural(result, options.calmExclamations)
        }

        result = PolishProsodyConditioner.cleanPunctuation(result)
        return NormalizedSubtitleText(result, dialogue.isMultiSpeaker)
    }

    private fun normalizeOffline(text: String): String {
        var result = text.replace("%", " procent")
        for ((pattern, replacement) in ABBREVIATIONS) {
            result = pattern.matcher(result).replaceAll(replacement)
        }
        return expandNumbers(result)
    }

    private fun normalizeNeural(text: String, calmExclamations: Boolean): String {
        var result = PolishProsodyConditioner.applyEdgePhonetics(text)
        result = PolishProsodyConditioner.restoreMissingCommas(result)
        if (calmExclamations) {
            result = PolishProsodyConditioner.softenCalmExclamations(result)
        }
        return result
    }

    private fun expandNumbers(input: String): String {
        val matcher = NUMBER_PATTERN.matcher(input)
        val sb = StringBuffer()
        while (matcher.find()) {
            val numStr = matcher.group(1) ?: continue
            val num = numStr.toIntOrNull()
            if (num != null) {
                matcher.appendReplacement(sb, numberToPolishWords(num))
            } else {
                matcher.appendReplacement(sb, numStr)
            }
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    fun numberToPolishWords(n: Int): String {
        if (n == 0) return "zero"
        if (n < 0) return "minus " + numberToPolishWords(-n)

        val parts = mutableListOf<String>()

        val thousands = n / 1000
        val remainder = n % 1000

        if (thousands > 0) {
            if (thousands == 1) {
                parts.add("tysiąc")
            } else {
                parts.add(threeDigitsToWords(thousands))
                val tRem100 = thousands % 100
                val tRem10 = thousands % 10
                when {
                    tRem100 in 11..19 -> parts.add("tysięcy")
                    tRem10 in 2..4 -> parts.add("tysiące")
                    else -> parts.add("tysięcy")
                }
            }
        }

        if (remainder > 0) {
            parts.add(threeDigitsToWords(remainder))
        }

        return parts.joinToString(" ").trim()
    }

    private fun threeDigitsToWords(n: Int): String {
        val parts = mutableListOf<String>()
        val h = n / 100
        val t = (n % 100) / 10
        val u = n % 10

        if (h > 0) parts.add(HUNDREDS[h])

        if (t == 1) {
            parts.add(TEENS[u])
        } else {
            if (t > 1) parts.add(TENS[t])
            if (u > 0) parts.add(UNITS[u])
        }

        return parts.joinToString(" ").trim()
    }
}
