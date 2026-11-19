package com.pointlessapps.filman.player.lektor.normalizer

import java.util.Locale

internal data class DialogueFormatResult(
    val text: String,
    val isMultiSpeaker: Boolean,
)

internal object PolishProsodyConditioner {

    private val POLISH_LOCALE = Locale.forLanguageTag("pl-PL")

    private val DIALOGUE_END_PUNCTUATION = setOf('.', '!', '?', ',', ';', ':', '…', '"', '”', '\'')
    private val CLOSING_PUNCTUATION = setOf('"', '\'', '”', '’', ')', ']', '»')
    private val TERMINAL_PUNCTUATION = setOf('.', '!', '?', '…', ':', ';')

    private val CURRENCY_ZL = Regex("(\\d[\\d\\s,.]*\\d|\\d)\\s*zł\\b", RegexOption.IGNORE_CASE)
    private val CURRENCY_USD_PREFIX = Regex("\\$(\\d[\\d\\s,.]*\\d|\\d)")
    private val CURRENCY_USD_SUFFIX = Regex("(\\d[\\d\\s,.]*\\d|\\d)\\s*\\$")
    private val CURRENCY_EUR_PREFIX = Regex("€(\\d[\\d\\s,.]*\\d|\\d)")
    private val CURRENCY_EUR_SUFFIX = Regex("(\\d[\\d\\s,.]*\\d|\\d)\\s*€")
    private val CURRENCY_GBP_PREFIX = Regex("£(\\d[\\d\\s,.]*\\d|\\d)")
    private val CURRENCY_GBP_SUFFIX = Regex("(\\d[\\d\\s,.]*\\d|\\d)\\s*£")

    private val EDGE_OK_REGEX = Regex("\\b[Oo]\\.?[Kk]\\b(?:\\.(?=\\s*(?:\\p{Lu}|[,!?…:;]|$)))?")
    private val EDGE_MR_REGEX = Regex("\\bMr\\.?\\s+(?=\\p{Lu})")
    private val EDGE_MRS_MS_REGEX = Regex("\\bM(?:rs|s)\\.?\\s+(?=\\p{Lu})")
    private val EDGE_VS_REGEX = Regex("\\bvs\\.?(?=\\s)", RegexOption.IGNORE_CASE)
    private val EDGE_AMPERSAND_REGEX = Regex("(?<=\\p{L})\\s+&\\s+(?=\\p{L})")
    private val EDGE_FILLER_REGEX = Regex(
        "\\b(?:h+m{2,}|m{3,}|y{3,}|e{3,}|p+f{2,}|t+s{2,}|s+h{2,}|ć+ś+|c+ś+|u+g+h+)\\b(?:[,.…]+)?",
        RegexOption.IGNORE_CASE,
    )

    private val ROMAN_CENTURY_REGEX = Regex("(?:(\\b[Ww])\\s+)?\\b([IVX]+)\\b\\s+(wiek|wieku)\\b")
    private val ROMAN_WAR_REGEX = Regex("\\b([IVX]+)\\b\\s+(wojna|wojny|wojnie|wojną|wojnę)\\b")
    private val ROMAN_AFTER_MASC_NOUN_REGEX = Regex(
        "\\b(rozdział|akt|tom|sezon|odcinek|etap|poziom)\\s+([IVX]+)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val ROMAN_AFTER_FEM_NOUN_REGEX = Regex(
        "\\b(część|klasa|grupa|liga|kategoria)\\s+([IVX]+)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val ROMAN_AFTER_MASC_REGAL_REGEX = Regex(
        "\\b(Jan\\s+Paweł|Henryk|Ludwik|Karol|Filip|Aleksander|Mikołaj|Zygmunt|Władysław|" +
            "Bolesław|Kazimierz|Stanisław|August|Piotr|Fryderyk|Ryszard|Edward|Jerzy|Wilhelm|" +
            "Napoleon|Benedykt|Król|Papież|Cesarz|Car|Książę)\\s+([IVX]+)\\b",
    )
    private val ROMAN_AFTER_FEM_REGAL_REGEX = Regex(
        "\\b(Elżbieta|Katarzyna|Królowa)\\s+([IVX]+)\\b",
    )

    private val SPACE_BEFORE_PUNCT_REGEX = Regex("[ \\t]+([?!,.;:…])")
    private val TRAILING_INTERRUPTED_DASH = Regex("(?<=\\p{L})--+(?=\\s|$)")
    private val TRAILING_SINGLE_DASH = Regex("(?<=\\p{L})[ \\t]+-(?=[ \\t]*$)")
    private val MULTI_DOTS_FOUR_PLUS = Regex("\\.{4,}")
    private val DOUBLE_DOTS = Regex("(?<!\\.)\\.{2}(?!\\.)")
    private val MULTI_QUESTION = Regex("\\?{2,}")
    private val MULTI_EXCLAMATION = Regex("!{2,}")
    private val MIXED_QUESTION_EXCL = Regex("(?:\\?!|!\\?)+")
    private val SPACE_AFTER_PUNCT = Regex("([,;:?!])(\\p{L})")
    private val HORIZONTAL_SPACES = Regex("[ \\t]+")

    private val SUBORDINATE_COMMA_REGEX = Regex(
        "(\\p{L}+)[ \\t]+(że|żeby|ponieważ|gdyż|chociaż|" +
            "który|która|które|którego|której|którym|których|którą|którzy)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val NO_COMMA_BEFORE_SUBORDINATE = setOf(
        "chyba", "mimo", "pomimo", "dlatego", "zwłaszcza", "tak", "bardziej",
        "to", "tylko", "jako", "zamiast", "ani", "lub", "albo", "i", "oraz",
        "w", "we", "z", "ze", "o", "na", "do", "od", "po", "pod", "nad",
        "przed", "przez", "przy", "dla", "bez", "u", "ku", "za", "między", "wśród",
    )

    private val EXCLAMATION_SENTENCE_REGEX = Regex("([^.!?\n]+!)")
    private val WORD_REGEX = Regex("\\p{L}+")
    private val CALM_MILD_EXCLAMATIONS = setOf(
        "cześć", "hej", "dzień dobry", "dobry wieczór", "dobranoc", "do widzenia",
        "dziękuję", "dzięki", "proszę", "przepraszam", "dobrze", "dobra", "jasne",
        "świetnie", "super", "chodź", "chodźmy", "spokojnie", "oczywiście", "zgoda",
        "okej", "witaj", "witam", "witajcie",
    )

    private val ROMAN_VALUES = mapOf(
        "I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5,
        "VI" to 6, "VII" to 7, "VIII" to 8, "IX" to 9, "X" to 10,
        "XI" to 11, "XII" to 12, "XIII" to 13, "XIV" to 14, "XV" to 15,
        "XVI" to 16, "XVII" to 17, "XVIII" to 18, "XIX" to 19, "XX" to 20, "XXI" to 21,
    )

    private val ORD_MASC_NOM = arrayOf(
        "", "pierwszy", "drugi", "trzeci", "czwarty", "piąty", "szósty", "siódmy", "ósmy",
        "dziewiąty", "dziesiąty", "jedenasty", "dwunasty", "trzynasty", "czternasty",
        "piętnasty", "szesnasty", "siedemnasty", "osiemnasty", "dziewiętnasty",
        "dwudziesty", "dwudziesty pierwszy",
    )
    private val ORD_MASC_GEN = arrayOf(
        "", "pierwszego", "drugiego", "trzeciego", "czwartego", "piątego", "szóstego",
        "siódmego", "ósmego", "dziewiątego", "dziesiątego", "jedenastego", "dwunastego",
        "trzynastego", "czternastego", "piętnastego", "szesnastego", "siedemnastego",
        "osiemnastego", "dziewiętnastego", "dwudziestego", "dwudziestego pierwszego",
    )
    private val ORD_MASC_LOC = arrayOf(
        "", "pierwszym", "drugim", "trzecim", "czwartym", "piątym", "szóstym",
        "siódmym", "ósmym", "dziewiątym", "dziesiątym", "jedenastym", "dwunastym",
        "trzynastym", "czternastym", "piętnastym", "szesnastym", "siedemnastym",
        "osiemnastym", "dziewiętnastym", "dwudziestym", "dwudziestym pierwszym",
    )
    private val ORD_FEM_NOM = arrayOf(
        "", "pierwsza", "druga", "trzecia", "czwarta", "piąta", "szósta", "siódma", "ósma",
        "dziewiąta", "dziesiąta", "jedenasta", "dwunasta", "trzynasta", "czternasta",
        "piętnasta", "szesnasta", "siedemnasta", "osiemnasta", "dziewiętnasta",
        "dwudziesta", "dwudziesta pierwsza",
    )
    private val ORD_FEM_GEN = arrayOf(
        "", "pierwszej", "drugiej", "trzeciej", "czwartej", "piątej", "szóstej",
        "siódmej", "ósmej", "dziewiątej", "dziesiątej", "jedenastej", "dwunastej",
        "trzynastej", "czternastej", "piętnastej", "szesnastej", "siedemnastej",
        "osiemnastej", "dziewiętnastej", "dwudziestej", "dwudziestej pierwszej",
    )
    private val ORD_FEM_ACC = arrayOf(
        "", "pierwszą", "drugą", "trzecią", "czwartą", "piątą", "szóstą", "siódmą", "ósmą",
        "dziewiątą", "dziesiątą", "jedenastą", "dwunastą", "trzynastą", "czternastą",
        "piętnastą", "szesnastą", "siedemnastą", "osiemnastą", "dziewiętnastą",
        "dwudziestą", "dwudziestą pierwszą",
    )

    fun formatDialogueLines(cleanedText: String, preserveTurnBreak: Boolean): DialogueFormatResult {
        val rawLines = cleanedText.lines()
        val nonBlankEntries = rawLines.mapNotNull { raw ->
            val trimmedStart = raw.trimStart()
            val hasDash = trimmedStart.startsWith("-") ||
                trimmedStart.startsWith("—") ||
                trimmedStart.startsWith("–") ||
                trimmedStart.startsWith("•")
            val body = raw.trimStart('-', '—', '–', '•', ' ').trim()
            if (body.isEmpty()) null else hasDash to body
        }
        if (nonBlankEntries.isEmpty()) return DialogueFormatResult("", isMultiSpeaker = false)

        val isMultiSpeaker = nonBlankEntries.size >= 2 && nonBlankEntries.drop(1).any { it.first }
        val sb = StringBuilder()
        for (i in nonBlankEntries.indices) {
            val (_, body) = nonBlankEntries[i]
            val nextIsDialogue = i + 1 < nonBlankEntries.size && nonBlankEntries[i + 1].first
            if (i > 0) {
                val sep = if (preserveTurnBreak && nonBlankEntries[i].first) '\n' else ' '
                sb.append(sep)
            }
            sb.append(body)
            if (nextIsDialogue && body.last() !in DIALOGUE_END_PUNCTUATION) {
                sb.append('.')
            }
        }
        return DialogueFormatResult(sb.toString(), isMultiSpeaker)
    }

    fun fixCurrencyAndSymbols(text: String): String {
        var result = CURRENCY_ZL.replace(text) { m ->
            val num = m.groupValues[1].trim()
            val unit = polishCurrencyUnit(num, "złoty", "złote", "złotych", "złotego")
            "$num $unit"
        }
        result = replaceCurrencyPair(
            text = result,
            prefixRegex = CURRENCY_USD_PREFIX,
            suffixRegex = CURRENCY_USD_SUFFIX,
            sg = "dolar",
            plNom = "dolary",
            plGen = "dolarów",
            decGen = "dolara",
        )
        result = CURRENCY_EUR_PREFIX.replace(result) { m -> "${m.groupValues[1].trim()} euro" }
        result = CURRENCY_EUR_SUFFIX.replace(result) { m -> "${m.groupValues[1].trim()} euro" }
        result = replaceCurrencyPair(
            text = result,
            prefixRegex = CURRENCY_GBP_PREFIX,
            suffixRegex = CURRENCY_GBP_SUFFIX,
            sg = "funt",
            plNom = "funty",
            plGen = "funtów",
            decGen = "funta",
        )

        result = result.replace("$", " dolarów ")
        result = result.replace("€", " euro ")
        result = result.replace("£", " funtów ")
        return result
    }

    private fun replaceCurrencyPair(
        text: String,
        prefixRegex: Regex,
        suffixRegex: Regex,
        sg: String,
        plNom: String,
        plGen: String,
        decGen: String,
    ): String {
        val afterPrefix = prefixRegex.replace(text) { m ->
            val num = m.groupValues[1].trim()
            "$num ${polishCurrencyUnit(num, sg, plNom, plGen, decGen)}"
        }
        return suffixRegex.replace(afterPrefix) { m ->
            val num = m.groupValues[1].trim()
            "$num ${polishCurrencyUnit(num, sg, plNom, plGen, decGen)}"
        }
    }

    private fun polishCurrencyUnit(
        numStr: String,
        sg: String,
        plNom: String,
        plGen: String,
        decGen: String,
    ): String {
        if (numStr.contains(',') || numStr.contains('.')) return decGen
        val n = numStr.filter { !it.isWhitespace() }.toLongOrNull() ?: return plGen
        val absN = kotlin.math.abs(n)
        val mod100 = (absN % 100L).toInt()
        val mod10 = (absN % 10L).toInt()
        return when {
            absN == 1L -> sg
            mod100 in 12..14 -> plGen
            mod10 in 2..4 -> plNom
            else -> plGen
        }
    }

    fun applyEdgePhonetics(text: String): String {
        var result = EDGE_OK_REGEX.replace(text) { m ->
            val atStart = isAtSentenceStart(text, m.range.first)
            val endsWithPeriod = m.value.endsWith(".")
            val word = if (atStart) "Okej" else "okej"
            if (endsWithPeriod && isFollowedBySentenceEndOrNewSentence(text, m.range.last + 1)) {
                "$word."
            } else {
                word
            }
        }
        result = EDGE_MR_REGEX.replace(result) { m ->
            if (isAtSentenceStart(result, m.range.first)) "Mister " else "mister "
        }
        result = EDGE_MRS_MS_REGEX.replace(result) { m ->
            if (isAtSentenceStart(result, m.range.first)) "Pani " else "pani "
        }
        result = EDGE_VS_REGEX.replace(result, "kontra")
        result = EDGE_AMPERSAND_REGEX.replace(result, " i ")
        result = expandContextualRomanNumerals(result)
        result = stripFillerVocalizations(result)
        return result
    }

    private fun stripFillerVocalizations(text: String): String {
        if (!EDGE_FILLER_REGEX.containsMatchIn(text)) return text
        val stripped = EDGE_FILLER_REGEX.replace(text, "").trim()
        if (stripped.isEmpty()) return ""
        return stripped.lines().joinToString("\n") { line ->
            val clean = line.trimStart(' ', ',', ';', ':', '.', '…')
                .replace(Regex(",\\s*$"), ".")
                .trim()
            if (clean.isEmpty()) {
                ""
            } else {
                clean.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(POLISH_LOCALE) else ch.toString()
                }
            }
        }
    }

    private fun expandContextualRomanNumerals(text: String): String {
        var result = ROMAN_CENTURY_REGEX.replace(text) { m ->
            val wPrefix = m.groupValues[1]
            val ordIdx = ROMAN_VALUES[m.groupValues[2]] ?: return@replace m.value
            val noun = m.groupValues[3]
            val ordWord = when {
                noun.equals("wiek", ignoreCase = true) -> ORD_MASC_NOM[ordIdx]
                wPrefix.isNotEmpty() -> ORD_MASC_LOC[ordIdx]
                else -> ORD_MASC_GEN[ordIdx]
            }
            if (wPrefix.isNotEmpty()) {
                "$wPrefix $ordWord $noun"
            } else {
                val cased = capitalizeIfSentenceStart(text, m.range.first, ordWord)
                "$cased $noun"
            }
        }
        result = ROMAN_WAR_REGEX.replace(result) { m ->
            val ordIdx = ROMAN_VALUES[m.groupValues[1]] ?: return@replace m.value
            val noun = m.groupValues[2]
            val ordWord = when (noun.lowercase(POLISH_LOCALE)) {
                "wojna" -> ORD_FEM_NOM[ordIdx]
                "wojnę" -> ORD_FEM_ACC[ordIdx]
                else -> ORD_FEM_GEN[ordIdx]
            }
            "${capitalizeIfSentenceStart(result, m.range.first, ordWord)} $noun"
        }
        result = ROMAN_AFTER_MASC_NOUN_REGEX.replace(result) { m ->
            val ordIdx = ROMAN_VALUES[m.groupValues[2]] ?: return@replace m.value
            "${m.groupValues[1]} ${ORD_MASC_NOM[ordIdx]}"
        }
        result = ROMAN_AFTER_FEM_NOUN_REGEX.replace(result) { m ->
            val ordIdx = ROMAN_VALUES[m.groupValues[2]] ?: return@replace m.value
            "${m.groupValues[1]} ${ORD_FEM_NOM[ordIdx]}"
        }
        result = ROMAN_AFTER_MASC_REGAL_REGEX.replace(result) { m ->
            val ordIdx = ROMAN_VALUES[m.groupValues[2]] ?: return@replace m.value
            "${m.groupValues[1]} ${ORD_MASC_NOM[ordIdx]}"
        }
        return ROMAN_AFTER_FEM_REGAL_REGEX.replace(result) { m ->
            val ordIdx = ROMAN_VALUES[m.groupValues[2]] ?: return@replace m.value
            "${m.groupValues[1]} ${ORD_FEM_NOM[ordIdx]}"
        }
    }

    fun cleanPunctuation(text: String): String {
        var result = SPACE_BEFORE_PUNCT_REGEX.replace(text, "$1")
        result = TRAILING_INTERRUPTED_DASH.replace(result, "…")
        result = TRAILING_SINGLE_DASH.replace(result, "…")
        result = MULTI_DOTS_FOUR_PLUS.replace(result, "...")
        result = DOUBLE_DOTS.replace(result, "...")
        result = MIXED_QUESTION_EXCL.replace(result, "?")
        result = MULTI_QUESTION.replace(result, "?")
        result = MULTI_EXCLAMATION.replace(result, "!")
        result = SPACE_AFTER_PUNCT.replace(result, "$1 $2")
        return result.lines()
            .map { it.replace(HORIZONTAL_SPACES, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    fun restoreMissingCommas(text: String): String =
        SUBORDINATE_COMMA_REGEX.replace(text) { m ->
            val prevWord = m.groupValues[1]
            val conjunction = m.groupValues[2]
            if (prevWord.lowercase(POLISH_LOCALE) in NO_COMMA_BEFORE_SUBORDINATE) {
                m.value
            } else {
                "$prevWord, $conjunction"
            }
        }

    fun softenCalmExclamations(text: String): String =
        EXCLAMATION_SENTENCE_REGEX.replace(text) { m ->
            val segment = m.value
            val words = WORD_REGEX.findAll(segment).map { it.value }.toList()
            val normalizedBody = words.joinToString(" ").lowercase(POLISH_LOCALE)
            if (words.size >= 4 || normalizedBody in CALM_MILD_EXCLAMATIONS) {
                segment.dropLast(1) + "."
            } else {
                segment
            }
        }

    fun ensureTerminalPunctuation(text: String): String =
        text.lines()
            .mapNotNull { rawLine ->
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val inner = trimmed.trimEnd { it in CLOSING_PUNCTUATION }.trimEnd()
                if (inner.isEmpty() || inner.last() in TERMINAL_PUNCTUATION) {
                    trimmed
                } else {
                    "$trimmed."
                }
            }
            .joinToString("\n")

    private fun capitalizeIfSentenceStart(text: String, index: Int, word: String): String =
        if (isAtSentenceStart(text, index)) {
            word.replaceFirstChar { it.titlecase(POLISH_LOCALE) }
        } else {
            word
        }

    private fun isAtSentenceStart(text: String, index: Int): Boolean {
        var i = index - 1
        while (i >= 0 && text[i].isWhitespace()) i--
        return i < 0 || text[i] in setOf('.', '!', '?', '…', '\n', '„', '"')
    }

    private fun isFollowedBySentenceEndOrNewSentence(text: String, indexAfter: Int): Boolean {
        var i = indexAfter
        while (i < text.length && text[i] == ' ') i++
        return i >= text.length || text[i] == '\n' || text[i].isUpperCase()
    }
}
