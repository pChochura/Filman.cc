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

    /**
     * Returns an updated timeline with a modified time shift in milliseconds.
     */
    fun withOffset(newOffsetMs: Int): SubtitleTimeline = copy(offsetMs = newOffsetMs)

    /**
     * Returns an updated timeline with a modified FPS scaling ratio (e.g. 25.0 / 23.976).
     */
    fun withFpsScale(newFpsScale: Double): SubtitleTimeline = copy(fpsScale = newFpsScale)
}
