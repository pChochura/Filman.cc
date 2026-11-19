package com.pointlessapps.filman.player.lektor.normalizer

import org.jsoup.parser.Parser
import java.util.Locale
import java.util.regex.Pattern

internal object PolishSubtitleCleaner {

    private val POLISH_LOCALE = Locale.forLanguageTag("pl-PL")

    private val HTML_TAG_REGEX = Pattern.compile("<[^>]+>")
    private val ASS_TAG_REGEX = Pattern.compile("\\{[^}]+\\}")
    private val INVISIBLE_UNICODE_REGEX = Regex("[\\u200B-\\u200F\\u00AD\\uFEFF]")
    private val BRACKET_SFX_REGEX = Pattern.compile("\\[[^\\]]*\\]")
    private val ASTERISK_SFX_REGEX = Pattern.compile("\\*[^*]+\\*")
    private val PARENTHESIS_SFX_REGEX = Pattern.compile(
        "\\((?:[\\p{L}\\s,'-]*?(?:muzyk[\\p{L}]*|westchn[\\p{L}]*|wzdych[\\p{L}]*|" +
            "śmiech[\\p{L}]*|śmiej[\\p{L}]*|chichot[\\p{L}]*|dźwięk[\\p{L}]*|" +
            "braw[\\p{L}]*|oklask[\\p{L}]*|szept[\\p{L}]*|krzyk[\\p{L}]*|krzycz[\\p{L}]*|" +
            "wrzask[\\p{L}]*|telefon|dzwon[\\p{L}]*|płacz[\\p{L}]*|szloch[\\p{L}]*|" +
            "kaszl[\\p{L}]*|kich[\\p{L}]*|strzał[\\p{L}]*|wystrzał[\\p{L}]*|pukan[\\p{L}]*|" +
            "syren[\\p{L}]*|cisz[\\p{L}]*|jęk[\\p{L}]*|stęk[\\p{L}]*|chrząk[\\p{L}]*|" +
            "oddech[\\p{L}]*|dyszy|sapie|gwizd[\\p{L}]*|wybuch[\\p{L}]*|eksplozj[\\p{L}]*|" +
            "krok[\\p{L}]*|trzask[\\p{L}]*|drzwi[\\p{L}]*|silnik[\\p{L}]*|klakson[\\p{L}]*|" +
            "alarm[\\p{L}]*|pisk[\\p{L}]*|szum[\\p{L}]*|grzmot[\\p{L}]*|deszcz[\\p{L}]*|" +
            "wiatr[\\p{L}]*|pies|szczek[\\p{L}]*|miau|ptak[\\p{L}]*|radio|telewiz[\\p{L}]*|" +
            "głośnik[\\p{L}]*|z\\s+offu|w\\s+tle|po\\s+angielsku|po\\s+francusku|" +
            "po\\s+niemiecku|po\\s+hiszpańsku|po\\s+rosyjsku|po\\s+włosku|" +
            "po\\s+japońsku|niezrozumiał[\\p{L}]*)[\\p{L}\\s,'-]*?)\\)",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
    )
    private val STANDALONE_PAREN_LINE = Regex("^([-—–]\\s*)?\\([^)]+\\)$")
    private val LEADING_COLON_AFTER_STRIP = Regex("^([-—–]\\s*)?:\\s+")
    private val SPEAKER_PREFIX_REGEX = Regex("^([-—–]\\s*)?([A-ZĄĆĘŁŃÓŚŹŻ][A-ZĄĆĘŁŃÓŚŹŻ0-9\\s]{1,22}):\\s+")

    private val CREDIT_LINE_REGEX = Regex(
        "^\\s*(?:tłumaczenie|korekta|napisy|synchro|synchronizacja|opracowanie|" +
            "tłum\\.|kor\\.|subtitles\\s+by|synced\\s+and\\s+corrected)\\s*[:\\-].*",
        setOf(RegexOption.IGNORE_CASE),
    )
    private val WATERMARK_REGEX = Regex(
        ".*(?:opensubtitles\\.(?:org|com)|napiprojekt(?:\\.pl)?|grupahatak(?:\\.pl)?|" +
            "napisy24\\.pl|subscene\\.com|addic7ed\\.com|podnapisi\\.net|" +
            "www\\.[a-z0-9\\-]+\\.[a-z]{2,}).*",
        setOf(RegexOption.IGNORE_CASE),
    )

    private val NON_SPEAKER_HEADERS = setOf(
        "UWAGA", "ROZDZIAŁ", "CZĘŚĆ", "AKT", "NOTATKA", "CEL", "BŁĄD",
        "STATUS", "GODZINA", "DATA", "MIEJSCE", "TYTUŁ",
    )

    private val ACRONYM_WHITELIST = setOf(
        "FBI", "CIA", "NSA", "DEA", "SWAT", "NYPD", "LAPD", "USA", "UK", "UE",
        "ONZ", "NATO", "NASA", "DNA", "RNA", "HIV", "AIDS", "VIP", "CEO", "CFO",
        "CTO", "HR", "PR", "IT", "AI", "TV", "DJ", "MC", "CD", "DVD", "USB",
        "SIM", "PIN", "GPS", "SMS", "MMS", "SOS", "PDF", "URL", "HTTP", "WWW",
        "ATM", "SUV", "BMW", "VW", "PKP", "PKS", "ZUS", "NFZ", "US", "CBŚ",
        "CBŚP", "ABW", "CBA", "BOR", "SOP", "GROM", "PSP", "OSP", "WOPR", "GOPR",
        "TOPR", "MON", "MSZ", "TVP", "TVN", "RMF", "PRL", "RP", "PESEL", "REGON",
        "PIT", "VAT", "KRS", "OK", "BBC", "CNN", "HBO", "KGB", "FSB", "SBU",
    )

    private val NON_ACRONYM_CONSONANTS = setOf(
        "DR", "NR", "ŚW", "ŚP", "WG", "NP", "MLN", "MLD", "TYS", "PKT", "ART", "UST",
    )

    private val ROMAN_NUMERALS_MULTI = setOf(
        "II", "III", "IV", "VI", "VII", "VIII", "IX", "XI", "XII",
        "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX", "XXI",
    )

    private val POLISH_VOWELS = setOf('A', 'Ą', 'E', 'Ę', 'I', 'O', 'Ó', 'U', 'Y')
    private val WORD_TOKEN_REGEX = Regex("\\p{L}+")

    fun clean(rawText: String): String {
        var result = HTML_TAG_REGEX.matcher(rawText).replaceAll("")
        result = ASS_TAG_REGEX.matcher(result).replaceAll("")
        result = Parser.unescapeEntities(result, false)
        result = result.replace('\u00A0', ' ')
        result = INVISIBLE_UNICODE_REGEX.matcherReplace(result)

        val cleanedLines = result.lines().mapNotNull { cleanLine(it) }
        return cleanedLines.joinToString("\n")
    }

    private fun Regex.matcherReplace(input: String): String = replace(input, "")

    private fun cleanLine(rawLine: String): String? {
        val trimmedRaw = rawLine.trim()
        if (trimmedRaw.isEmpty()) return null
        if (trimmedRaw.contains('♪') || trimmedRaw.contains('♫')) return null
        if (CREDIT_LINE_REGEX.matches(trimmedRaw) || WATERMARK_REGEX.matches(trimmedRaw)) return null

        var line = BRACKET_SFX_REGEX.matcher(trimmedRaw).replaceAll("")
        line = ASTERISK_SFX_REGEX.matcher(line).replaceAll("")
        line = PARENTHESIS_SFX_REGEX.matcher(line).replaceAll("")
        line = line.trim()
        if (line.isEmpty() || STANDALONE_PAREN_LINE.matches(line)) return null

        line = LEADING_COLON_AFTER_STRIP.replace(line) { match -> match.groupValues[1] }
        line = stripSpeakerPrefix(line).trim()
        if (line.isEmpty()) return null

        return decapitalizeAllCaps(line)
    }

    private fun stripSpeakerPrefix(line: String): String {
        val match = SPEAKER_PREFIX_REGEX.find(line) ?: return line
        val label = match.groupValues[2].trim()
        val labelBase = label.substringBefore(' ')
        if (label in NON_SPEAKER_HEADERS || labelBase in NON_SPEAKER_HEADERS || label in ACRONYM_WHITELIST) {
            return line
        }
        val dashPrefix = match.groupValues[1]
        return dashPrefix + line.substring(match.range.last + 1)
    }

    private fun decapitalizeAllCaps(line: String): String {
        var totalLetters = 0
        var upperLetters = 0
        for (ch in line) {
            if (ch.isLetter()) {
                totalLetters++
                if (ch.isUpperCase()) upperLetters++
            }
        }
        if (upperLetters == 0) return line

        val isShoutingLine = totalLetters >= 4 && upperLetters * 100 >= totalLetters * 85
        var seenSentenceStart = false
        return WORD_TOKEN_REGEX.replace(line) { match ->
            val token = match.value
            val atSentenceStart = !seenSentenceStart || isAfterSentenceBoundary(line, match.range.first)
            seenSentenceStart = true
            transformToken(token, isShoutingLine, atSentenceStart)
        }
    }

    private fun transformToken(token: String, isShoutingLine: Boolean, atSentenceStart: Boolean): String {
        val isAllUpper = token.length >= 2 && token.all { !it.isLetter() || it.isUpperCase() }
        val isSingleUpperInShouting = isShoutingLine && token.length == 1 && token[0].isUpperCase()
        if (!isAllUpper && !isSingleUpperInShouting) return token

        if (isPreservedAcronym(token)) return token

        val lowered = token.lowercase(POLISH_LOCALE)
        return if (atSentenceStart) {
            lowered.replaceFirstChar { it.titlecase(POLISH_LOCALE) }
        } else {
            lowered
        }
    }

    private fun isPreservedAcronym(token: String): Boolean {
        if (token in ACRONYM_WHITELIST || token in ROMAN_NUMERALS_MULTI) return true
        if (token in NON_ACRONYM_CONSONANTS) return false
        return token.length in 2..4 && token.none { it in POLISH_VOWELS }
    }

    private fun isAfterSentenceBoundary(line: String, tokenStart: Int): Boolean {
        var i = tokenStart - 1
        while (i >= 0 && (line[i].isWhitespace() || line[i] in setOf('"', '”', '„', '(', '[', '—', '–', '-'))) {
            if (line[i] in setOf('-', '—', '–') && i == 0) return true
            i--
        }
        return i < 0 || line[i] in setOf('.', '!', '?')
    }
}
