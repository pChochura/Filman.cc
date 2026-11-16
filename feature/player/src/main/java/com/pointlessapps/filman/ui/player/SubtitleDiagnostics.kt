package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.player.lektor.parser.SrtParser

internal enum class SubtitleWarning {
    UNREADABLE,
    POSSIBLY_INCOMPLETE,
}

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
