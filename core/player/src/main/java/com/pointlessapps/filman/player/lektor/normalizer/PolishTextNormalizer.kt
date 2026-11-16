package com.pointlessapps.filman.player.lektor.normalizer

import java.util.regex.Pattern

object PolishTextNormalizer {

    private val HTML_TAG_REGEX = Pattern.compile("<[^>]+>")
    private val ASS_TAG_REGEX = Pattern.compile("\\{[^}]+\\}")
    private val BRACKET_SFX_REGEX = Pattern.compile("\\[[^\\]]*\\]")
    private val ASTERISK_SFX_REGEX = Pattern.compile("\\*[^*]+\\*")
    private val PARENTHESIS_SFX_REGEX = Pattern.compile(
        "\\((?:[\\p{L}\\s]*?(?:muzyk[a-z]*|westchni[a-z]*|śmiech[a-z]*|dźwięk[a-z]*|braw[a-z]*|szept[a-z]*|krzyk[a-z]*|telefon|dzwonek)[\\p{L}\\s]*?)\\)",
        Pattern.CASE_INSENSITIVE,
    )

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

    private val UNITS = arrayOf(
        "", "jeden", "dwa", "trzy", "cztery", "pięć", "sześć", "siedem", "osiem", "dziewięć"
    )
    private val TEENS = arrayOf(
        "dziesięć", "jedenaście", "dwanaście", "trzynaście", "czternaście", "piętnaście",
        "szesnaście", "siedemnaście", "osiemnaście", "dziewiętnaście"
    )
    private val TENS = arrayOf(
        "", "dziesięć", "dwadzieścia", "trzydzieści", "czterdzieści", "pięćdziesiąt",
        "sześćdziesiąt", "siedemdziesiąt", "osiemdziesiąt", "dziewięćdziesiąt"
    )
    private val HUNDREDS = arrayOf(
        "", "sto", "dwieście", "trzysta", "czterysta", "pięćset",
        "sześćset", "siedemset", "osiemset", "dziewięćset"
    )

    fun normalize(text: String): String {
        var result = text

        // 1. Strip formatting tags
        result = HTML_TAG_REGEX.matcher(result).replaceAll("")
        result = ASS_TAG_REGEX.matcher(result).replaceAll("")

        // 2. Strip SFX/Closed Caption noise
        result = BRACKET_SFX_REGEX.matcher(result).replaceAll("")
        result = ASTERISK_SFX_REGEX.matcher(result).replaceAll("")
        result = PARENTHESIS_SFX_REGEX.matcher(result).replaceAll("")

        // 3. Remove leading dialogue dashes/bullets and ensure pauses between dialogue lines
        val lines = result.lines()
        val processedLines = lines.mapIndexed { index, rawLine ->
            val isDialogueLine = rawLine.trimStart().startsWith("-") ||
                    rawLine.trimStart().startsWith("—") ||
                    rawLine.trimStart().startsWith("–")
            val trimmed = rawLine.trimStart('-', '—', '–', '•', ' ').trim()
            if (trimmed.isEmpty()) return@mapIndexed ""

            val endsWithPunctuation = trimmed.last() in listOf('.', '!', '?', ',', ';', ':', '…', '"', '”', '\'')
            if (isDialogueLine && !endsWithPunctuation && index < lines.size - 1) {
                "$trimmed."
            } else {
                trimmed
            }
        }.filter { it.isNotBlank() }
        result = processedLines.joinToString(" ")

        // 4. Currency and special symbols
        result = result.replace("%", " procent")
        result = result.replace("$", " dolarów ")
        result = result.replace("€", " euro ")
        result = result.replace("zł", " złotych ")

        // 5. Expand abbreviations
        for ((pattern, replacement) in ABBREVIATIONS) {
            result = pattern.matcher(result).replaceAll(replacement)
        }

        // 6. Expand numbers (up to 999 999)
        result = expandNumbers(result)

        // 7. Cleanup extra spaces and punctuation
        result = result.replace(Regex("\\s+"), " ").trim()

        return result
    }

    private fun expandNumbers(input: String): String {
        val numberPattern = Pattern.compile("\\b(\\d{1,6})\\b")
        val matcher = numberPattern.matcher(input)
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
