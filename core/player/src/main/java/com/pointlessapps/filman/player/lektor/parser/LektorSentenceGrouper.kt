package com.pointlessapps.filman.player.lektor.parser

import com.pointlessapps.filman.player.lektor.model.SubtitleCue
import com.pointlessapps.filman.player.lektor.normalizer.PolishTextNormalizer

internal object LektorSentenceGrouper {
    private const val MAX_GAP_MS = 1_500L
    private const val MAX_DURATION_MS = 20_000L
    private const val MAX_CUES = 8
    private const val MAX_TEXT_LENGTH = 600
    private val leadingFormatting = Regex("^(?:\\s|<[^>]*>|\\{[^}]*\\})*")
    private val closingPunctuation = setOf('"', '\'', '”', '’', ')', ']', '»')
    private val whitespace = Regex("\\s+")

    fun group(rawCues: List<SubtitleCue>): List<SubtitleCue> {
        val result = mutableListOf<SubtitleCue>()
        var first: SubtitleCue? = null
        var endMs = 0L
        var count = 0
        val text = StringBuilder()

        fun flush() {
            val start = first ?: return
            result.add(SubtitleCue(result.size + 1, start.startMs, endMs, text.toString()))
            first = null
            text.clear()
            count = 0
        }

        for (cue in rawCues) {
            val normalized = PolishTextNormalizer.normalize(cue.text)
            if (normalized.isBlank()) {
                flush()
                continue
            }
            val start = first
            val nextIsDialogue = cue.text.replaceFirst(leadingFormatting, "")
                .startsWithAny("-", "–", "—", "•")
            val canJoin = start != null &&
                !endsSentence(text.toString()) &&
                !nextIsDialogue &&
                cue.startMs >= start.startMs &&
                cue.startMs - endMs <= MAX_GAP_MS &&
                cue.endMs - start.startMs <= MAX_DURATION_MS &&
                count < MAX_CUES &&
                text.length + 1 + normalized.length <= MAX_TEXT_LENGTH
            if (!canJoin) flush()
            if (first == null) first = cue
            if (text.isNotEmpty()) text.append(' ')
            text.append(normalized.replace(whitespace, " "))
            endMs = if (canJoin) maxOf(endMs, cue.endMs) else cue.endMs
            count++
        }
        flush()
        return result
    }

    private fun endsSentence(text: String): Boolean {
        val last = text.trimEnd().trimEnd { it in closingPunctuation }.trimEnd()
        if (last.endsWith("...") || last.endsWith("…")) return false
        return last.lastOrNull() in setOf('.', '!', '?')
    }

    private fun String.startsWithAny(vararg prefixes: String) = prefixes.any { startsWith(it) }
}
