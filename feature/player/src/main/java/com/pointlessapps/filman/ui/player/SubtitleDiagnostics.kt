package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.player.lektor.parser.SrtParser

internal enum class SubtitleWarning {
    UNREADABLE,
    POSSIBLY_INCOMPLETE,
}

private val POLISH_DIACRITICS = setOf(
    'ą', 'ć', 'ę', 'ł', 'ń', 'ó', 'ś', 'ź', 'ż',
    'Ą', 'Ć', 'Ę', 'Ł', 'Ń', 'Ó', 'Ś', 'Ź', 'Ż',
)

private const val MIN_LETTERS_FOR_DIACRITIC_CHECK = 200
private const val MIN_POLISH_DIACRITIC_RATIO = 0.008

internal fun diagnoseSubtitle(content: String, durationMs: Long): SubtitleWarning? {
    val cues = SrtParser.parse(content, normalizePolish = false).sortedBy { it.startMs }
    if (cues.isEmpty()) return SubtitleWarning.UNREADABLE

    val lastEnd = cues.maxOf { it.endMs }
    var coveredUntil = cues.first().endMs
    val hasLongGap = cues.drop(1).any { cue ->
        val gap = cue.startMs - coveredUntil
        coveredUntil = maxOf(coveredUntil, cue.endMs)
        gap >= 10 * 60_000L
    }
    val endsEarly = durationMs >= 10 * 60_000L &&
        lastEnd < durationMs * 0.7 &&
        durationMs - lastEnd >= 10 * 60_000L
    return if (cues.size < 10 || hasLongGap || endsEarly) SubtitleWarning.POSSIBLY_INCOMPLETE else null
}

internal fun isLowQualityPolishSubtitle(content: String): Boolean {
    if (content.contains('\uFFFD')) return true

    val cues = SrtParser.parse(content, normalizePolish = false)
    if (cues.isEmpty()) return false

    var letterCount = 0
    var diacriticCount = 0
    for (cue in cues) {
        for (ch in cue.text) {
            if (ch.isLetter()) {
                letterCount++
                if (ch in POLISH_DIACRITICS) diacriticCount++
            }
        }
    }
    return letterCount >= MIN_LETTERS_FOR_DIACRITIC_CHECK &&
        diacriticCount.toDouble() / letterCount.toDouble() < MIN_POLISH_DIACRITIC_RATIO
}
