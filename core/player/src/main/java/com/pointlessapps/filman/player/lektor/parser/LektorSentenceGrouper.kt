package com.pointlessapps.filman.player.lektor.parser

import com.pointlessapps.filman.player.lektor.model.SubtitleCue
import com.pointlessapps.filman.player.lektor.normalizer.PolishNormalizationMode
import com.pointlessapps.filman.player.lektor.normalizer.PolishNormalizationOptions
import com.pointlessapps.filman.player.lektor.normalizer.PolishProsodyConditioner
import com.pointlessapps.filman.player.lektor.normalizer.PolishTextNormalizer
import java.util.Locale

internal object LektorSentenceGrouper {
    private const val OFFLINE_MAX_GAP_MS = 1_500L
    private const val NEURAL_MAX_GAP_MS = 750L
    private const val MAX_DURATION_MS = 20_000L
    private const val MAX_CUES = 8
    private const val MAX_TEXT_LENGTH = 600
    private const val MICRO_MERGE_MAX_GAP_MS = 250L
    private const val MICRO_MERGE_MAX_DURATION_MS = 4_500L
    private const val MICRO_MERGE_MAX_LENGTH = 150
    private const val MICRO_MERGE_MAX_CUES = 3
    private const val PAUSE_COMMA_MIN_GAP_MS = 350L
    private const val HESITATION_BREAK_MIN_GAP_MS = 450L

    private val POLISH_LOCALE = Locale.forLanguageTag("pl-PL")
    private val leadingFormatting = Regex("^(?:\\s|<[^>]*>|\\{[^}]*\\})*")
    private val closingPunctuation = setOf('"', '\'', '”', '’', ')', ']', '»')
    private val openingPunctuation = setOf('"', '\'', '„', '“', '(', '[', '«')
    private val horizontalWhitespace = Regex("[ \\t]+")
    private val allWhitespace = Regex("\\s+")
    private val joinSkipPunctuation = setOf(',', ';', ':', '…', '.', '!', '?', '—')
    private val trailingEllipsisRegex = Regex("(?:\\.{3}|…)\\s*$")
    private val leadingEllipsisRegex = Regex("^\\s*(?:\\.{3}|…)\\s*")

    private val TRAILING_NO_COMMA_WORDS = setOf(
        "w", "we", "z", "ze", "do", "na", "po", "od", "o", "u", "ku", "za",
        "pod", "nad", "przed", "przez", "przy", "dla", "bez", "i", "a", "oraz",
        "lub", "albo", "czy", "ani", "nie", "się", "to", "ten", "ta", "te",
        "mój", "moja", "moje", "twój", "jego", "jej", "ich", "nasz", "wasz",
    )
    private val MANDATORY_COMMA_NEXT_WORDS = setOf(
        "że", "żeby", "aby", "iż", "bo", "ponieważ", "gdyż", "ale", "lecz",
        "więc", "zatem", "jednak", "chociaż", "choć", "skoro", "dopóki", "zanim",
        "który", "która", "które", "którego", "której", "którym", "których",
        "którymi", "którą", "którzy",
    )
    private val NO_COMMA_BEFORE_CONJUNCTION = setOf(
        "chyba", "mimo", "pomimo", "dlatego", "tak", "tylko", "jako", "zamiast",
        "w", "we", "z", "ze", "o", "na", "do", "od", "po", "pod", "nad",
        "przed", "przez", "przy", "dla", "bez", "u", "ku", "za",
    )

    private class GroupAccumulator(private val isNeural: Boolean) {
        var first: SubtitleCue? = null
        var endMs = 0L
        var count = 0
        var isMultiSpeaker = false
        val text = StringBuilder()

        fun flushTo(result: MutableList<SubtitleCue>) {
            val start = first ?: return
            val finalText = if (isNeural) {
                PolishProsodyConditioner.ensureTerminalPunctuation(text.toString())
            } else {
                text.toString()
            }
            result.add(SubtitleCue(result.size + 1, start.startMs, endMs, finalText))
            first = null
            text.clear()
            count = 0
            isMultiSpeaker = false
        }

        fun startNew(cue: SubtitleCue, normalizedText: String, multiSpeaker: Boolean) {
            first = cue
            text.append(normalizedText)
            endMs = cue.endMs
            count = 1
            isMultiSpeaker = multiSpeaker
        }
    }

    fun group(
        rawCues: List<SubtitleCue>,
        options: PolishNormalizationOptions = PolishNormalizationOptions(),
    ): List<SubtitleCue> {
        val isNeural = options.mode == PolishNormalizationMode.NEURAL
        val result = mutableListOf<SubtitleCue>()
        val acc = GroupAccumulator(isNeural)

        for (cue in rawCues) {
            val normalized = PolishTextNormalizer.normalizeWithMetadata(cue.text, options)
            val normalizedText = prepareCueText(normalized.text, isNeural)
            if (normalizedText.isBlank()) {
                acc.flushTo(result)
                continue
            }

            val nextIsDialogue = cue.text.replaceFirst(leadingFormatting, "")
                .startsWithAny("-", "–", "—", "•")
            val currentText = acc.text.toString()
            val hasPairedEllipsis = acc.first != null &&
                endsWithEllipsis(currentText) &&
                startsWithEllipsis(normalizedText)

            val canJoin = canAppendToAccumulator(
                acc = acc,
                currentText = currentText,
                cue = cue,
                nextText = normalizedText,
                nextIsDialogue = nextIsDialogue,
                nextIsMultiSpeaker = normalized.isMultiSpeaker,
                hasPairedEllipsis = hasPairedEllipsis,
                isNeural = isNeural,
            )
            if (canJoin) {
                appendJoinedCue(acc.text, normalizedText, cue.startMs - acc.endMs, hasPairedEllipsis, isNeural)
                acc.endMs = maxOf(acc.endMs, cue.endMs)
                acc.count++
            } else {
                acc.flushTo(result)
                acc.startNew(cue, normalizedText, normalized.isMultiSpeaker)
            }
        }
        acc.flushTo(result)
        return result
    }

    private fun canAppendToAccumulator(
        acc: GroupAccumulator,
        currentText: String,
        cue: SubtitleCue,
        nextText: String,
        nextIsDialogue: Boolean,
        nextIsMultiSpeaker: Boolean,
        hasPairedEllipsis: Boolean,
        isNeural: Boolean,
    ): Boolean {
        val start = acc.first ?: return false
        return shouldJoin(
            start = start,
            endMs = acc.endMs,
            count = acc.count,
            currentText = currentText,
            groupIsMultiSpeaker = acc.isMultiSpeaker,
            cue = cue,
            nextText = nextText,
            nextIsDialogue = nextIsDialogue,
            nextIsMultiSpeaker = nextIsMultiSpeaker,
            hasPairedEllipsis = hasPairedEllipsis,
            isNeural = isNeural,
        )
    }

    private fun prepareCueText(text: String, isNeural: Boolean): String =
        if (isNeural) {
            text.replace(horizontalWhitespace, " ").trim()
        } else {
            text.replace(allWhitespace, " ").trim()
        }

    private fun shouldJoin(
        start: SubtitleCue,
        endMs: Long,
        count: Int,
        currentText: String,
        groupIsMultiSpeaker: Boolean,
        cue: SubtitleCue,
        nextText: String,
        nextIsDialogue: Boolean,
        nextIsMultiSpeaker: Boolean,
        hasPairedEllipsis: Boolean,
        isNeural: Boolean,
    ): Boolean {
        if (groupIsMultiSpeaker || nextIsDialogue || nextIsMultiSpeaker) return false
        if (cue.startMs < start.startMs) return false

        val gapMs = cue.startMs - endMs
        val hesitationBreak = isNeural &&
            !hasPairedEllipsis &&
            isEllipsisHesitationBreak(currentText, nextText, gapMs)
        val currentEnded = endsSentence(currentText) || hesitationBreak

        if (currentEnded) {
            return isNeural && canMicroMerge(start, endMs, count, currentText, cue, nextText)
        }

        val maxGapMs = if (isNeural) NEURAL_MAX_GAP_MS else OFFLINE_MAX_GAP_MS
        if (gapMs > maxGapMs) return false
        if (cue.endMs - start.startMs > MAX_DURATION_MS) return false
        if (count >= MAX_CUES) return false
        return currentText.length + 1 + nextText.length <= MAX_TEXT_LENGTH
    }

    private fun isEllipsisHesitationBreak(currentText: String, nextText: String, gapMs: Long): Boolean {
        if (gapMs <= HESITATION_BREAK_MIN_GAP_MS) return false
        return endsWithEllipsis(currentText) && startsWithUppercase(nextText)
    }

    private fun canMicroMerge(
        start: SubtitleCue,
        endMs: Long,
        count: Int,
        currentText: String,
        cue: SubtitleCue,
        nextText: String,
    ): Boolean {
        val gapMs = cue.startMs - endMs
        if (gapMs !in 0..MICRO_MERGE_MAX_GAP_MS) return false
        if (currentText.length > 45 && nextText.length > 35) return false
        if (currentText.length + 1 + nextText.length > MICRO_MERGE_MAX_LENGTH) return false
        if (cue.endMs - start.startMs > MICRO_MERGE_MAX_DURATION_MS) return false
        return count < MICRO_MERGE_MAX_CUES
    }

    private fun appendJoinedCue(
        text: StringBuilder,
        nextText: String,
        gapMs: Long,
        hasPairedEllipsis: Boolean,
        isNeural: Boolean,
    ) {
        if (hasPairedEllipsis) {
            val cleanedCurrent = trailingEllipsisRegex.replace(text.toString(), "").trimEnd()
            val cleanedNext = leadingEllipsisRegex.replace(nextText, "").trimStart()
            text.clear()
            text.append(cleanedCurrent)
            if (cleanedCurrent.isNotEmpty() && cleanedNext.isNotEmpty()) {
                val sep = if (gapMs >= PAUSE_COMMA_MIN_GAP_MS &&
                    cleanedCurrent.last() !in joinSkipPunctuation
                ) {
                    ", "
                } else {
                    " "
                }
                text.append(sep)
            }
            text.append(cleanedNext)
            return
        }

        val currentText = text.toString()
        val sep = if (isNeural && shouldInsertJoinComma(currentText, nextText, gapMs)) ", " else " "
        text.append(sep).append(nextText)
    }

    private fun shouldInsertJoinComma(currentText: String, nextText: String, gapMs: Long): Boolean {
        val trimmedCurrent = currentText.trimEnd()
        val lastChar = trimmedCurrent.lastOrNull() ?: return false
        if (lastChar in joinSkipPunctuation || endsSentence(trimmedCurrent)) return false

        val lastWord = extractLastWord(trimmedCurrent)?.lowercase(POLISH_LOCALE) ?: return false
        if (gapMs >= PAUSE_COMMA_MIN_GAP_MS && lastWord !in TRAILING_NO_COMMA_WORDS) {
            return true
        }
        val firstNextWord = extractFirstWord(nextText)?.lowercase(POLISH_LOCALE) ?: return false
        return firstNextWord in MANDATORY_COMMA_NEXT_WORDS && lastWord !in NO_COMMA_BEFORE_CONJUNCTION
    }

    private fun extractLastWord(text: String): String? =
        Regex("(\\p{L}+)$").find(text)?.value

    private fun extractFirstWord(text: String): String? =
        Regex("^(\\p{L}+)").find(text.trimStart())?.value

    private fun endsWithEllipsis(text: String): Boolean {
        val last = text.trimEnd().trimEnd { it in closingPunctuation }.trimEnd()
        return last.endsWith("...") || last.endsWith("…")
    }

    private fun startsWithEllipsis(text: String): Boolean {
        val first = text.trimStart().trimStart { it in openingPunctuation }.trimStart()
        return first.startsWith("...") || first.startsWith("…")
    }

    private fun startsWithUppercase(text: String): Boolean {
        val ch = text.trimStart().trimStart { it in openingPunctuation }.trimStart().firstOrNull() ?: return false
        return ch.isUpperCase()
    }

    private fun endsSentence(text: String): Boolean {
        val last = text.trimEnd().trimEnd { it in closingPunctuation }.trimEnd()
        if (last.endsWith("...") || last.endsWith("…")) return false
        return last.lastOrNull() in setOf('.', '!', '?')
    }

    private fun String.startsWithAny(vararg prefixes: String) = prefixes.any { startsWith(it) }
}
