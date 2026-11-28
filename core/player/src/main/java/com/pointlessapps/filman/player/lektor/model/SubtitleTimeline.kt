package com.pointlessapps.filman.player.lektor.model

/**
 * Manages transformed subtitle cues taking into account timing shift (offsetMs) and FPS scaling.
 *
 * Effective cue display window:
 * [startMs * fpsScale + offsetMs, endMs * fpsScale + offsetMs]
 */
data class SubtitleTimeline(
    val cues: List<SubtitleCue> = emptyList(),
    val offsetMs: Int = 0,
    val fpsScale: Double = 1.0,
) {
    /**
     * Finds active cues at the given playback position in milliseconds.
     */
    fun activeCuesAt(positionMs: Long): List<SubtitleCue> {
        if (cues.isEmpty()) return emptyList()
        // Reverse transform positionMs to original cue time domain:
        // cueTime = (positionMs - offsetMs) / fpsScale
        val targetCueTime = ((positionMs - offsetMs).toDouble() / fpsScale.coerceAtLeast(0.001)).toLong()
        return cues.filter { targetCueTime in it.startMs..it.endMs }
    }

    /**
     * Converts a cue's display start time in video domain.
     */
    fun displayStartMs(cue: SubtitleCue): Long {
        return (cue.startMs * fpsScale).toLong() + offsetMs
    }

    /**
     * Converts a cue's display end time in video domain.
     */
    fun displayEndMs(cue: SubtitleCue): Long {
        return (cue.endMs * fpsScale).toLong() + offsetMs
    }

    fun isDialogueCue(cue: SubtitleCue): Boolean {
        val clean = cue.text
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\{[^}]+\\}"), "")
            .replace(Regex("[\\[(][^\\])]*[\\])]"), "")
            .replace(Regex("[♪♫#*]"), "")
            .trim()
        if (clean.isBlank()) return false
        return !METADATA_OR_CREDITS_REGEX.containsMatchIn(cue.text)
    }

    /**
     * Estimates when dialogue finishes and end credits start.
     * Returns null if dialogue end cannot be reliably determined.
     */
    fun detectDialogueEndMs(durationMs: Long): Long? {
        if (cues.isEmpty() || durationMs <= 30_000L) return null

        val lastCue = cues.asReversed().firstOrNull { isDialogueCue(it) } ?: return null
        val effectiveEndMs = displayEndMs(lastCue)

        val minWindow = (durationMs * 0.70).toLong()
        val maxWindow = (durationMs * 0.995).toLong()
        if (effectiveEndMs !in minWindow..maxWindow) {
            return null
        }

        return (effectiveEndMs + 2_000L).coerceAtMost(durationMs - 1_000L)
    }

    /**
     * Checks if spoken dialogue is actively occurring at the given position.
     */
    fun hasSpokenDialogueAt(positionMs: Long): Boolean {
        return activeCuesAt(positionMs).any { isDialogueCue(it) }
    }

    companion object {
        private val METADATA_OR_CREDITS_REGEX = Regex(
            "(?i)(?:napisy|tłumaczenie|tlumaczenie|synchro|korekta|redakcja|wersja\\s+polska|dystrybucja|transkrypcja|dialogi|opensubtitles|subtitles\\s+by|translated\\s+by|sync(?:ed)?\\s+by|ripped\\s+by|encoded\\s+by|created\\s+by|addic7ed|podnapisi|yify|hatak|grupahatak|animesub|tvseries|dvdrip|web-dl|bluray|x264|x265|1080p|720p|https?://|www\\.|\\.com|\\.org|\\.pl|\\.net)",
        )
    }

    /**
     * Returns an updated timeline with a modified time shift in milliseconds.
     */
    fun withOffset(newOffsetMs: Int): SubtitleTimeline = copy(offsetMs = newOffsetMs)

    /**
     * Returns an updated timeline with a modified FPS scaling ratio (e.g. 25.0 / 23.976).
     */
    fun withFpsScale(newFpsScale: Double): SubtitleTimeline = copy(fpsScale = newFpsScale)
}
