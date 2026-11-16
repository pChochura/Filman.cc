package com.pointlessapps.filman.player.lektor.controller

import com.pointlessapps.filman.player.lektor.model.SubtitleCue

internal object LektorLookahead {
    private const val HORIZON_MS = 120_000L
    private const val MAX_CACHED_CUES = 3
    private const val INITIAL_ESTIMATE_MS = 15_000L
    private const val SAFETY_MARGIN_MS = 1_000L

    fun nextCue(
        cues: List<SubtitleCue>,
        positionMs: Long,
        millisecondsPerCharacter: Double?,
        isCached: (Int) -> Boolean,
    ): SubtitleCue? {
        val upcoming = cues.filter {
            it.endMs >= positionMs && it.startMs <= positionMs + HORIZON_MS
        }
        if (upcoming.count { isCached(it.index) } >= MAX_CACHED_CUES) return null

        val uncached = upcoming.filterNot { isCached(it.index) }
        if (uncached.isEmpty()) return null

        return uncached.firstOrNull { cue ->
            val estimate = millisecondsPerCharacter
                ?.let { (cue.text.length * it).toLong() }
                ?: INITIAL_ESTIMATE_MS
            cue.startMs - positionMs >= estimate + SAFETY_MARGIN_MS
        } ?: uncached.last()
    }
}
