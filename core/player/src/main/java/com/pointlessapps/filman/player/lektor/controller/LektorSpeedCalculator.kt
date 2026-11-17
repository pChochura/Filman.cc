package com.pointlessapps.filman.player.lektor.controller

import com.pointlessapps.filman.player.lektor.model.SubtitleCue

internal object LektorSpeedCalculator {
    const val MIN_SPEED = 0.92f
    const val MAX_SPEED = 1.45f
    const val NORMAL_SPEED = 1.0f
    const val DEFAULT_PAUSE_MARGIN_MS = 180L

    /**
     * Calculates the optimal playback speed multiplier for speech audio so that it finishes
     * before the next cue begins (preventing cut-offs mid-sentence or cue dropping), and does
     * not rush through when ample time is available (preventing awkward waiting for the next one).
     *
     * @param audioDurationMs The duration of the speech audio in milliseconds.
     * @param cue The current subtitle cue being played.
     * @param nextCue The upcoming subtitle cue, or null if this is the final cue.
     * @param currentPositionMs Current media playback position in milliseconds.
     * @param timingOffsetMs Current timing offset in milliseconds (e.g. -500ms).
     * @return The speed multiplier to apply (between MIN_SPEED and MAX_SPEED).
     */
    fun calculateSpeed(
        audioDurationMs: Long,
        cue: SubtitleCue,
        nextCue: SubtitleCue?,
        currentPositionMs: Long,
        timingOffsetMs: Int,
    ): Float {
        if (audioDurationMs <= 0L) return NORMAL_SPEED

        val adjustedPosition = LektorCueTiming.position(currentPositionMs, timingOffsetMs)
        // Latency: how many ms past cue.startMs are we already starting?
        val startDelayMs = (adjustedPosition - cue.startMs).coerceAtLeast(0L)

        // Determine the available window in milliseconds before the next cue or dialogue boundary
        val availableWindowMs: Long = if (nextCue != null && nextCue.startMs > cue.startMs) {
            val totalSpanMs = nextCue.startMs - (cue.startMs + startDelayMs)
            (totalSpanMs - DEFAULT_PAUSE_MARGIN_MS).coerceAtLeast(cue.durationMs - startDelayMs)
        } else {
            // No next cue: use on-screen duration plus a natural conversational grace buffer
            (cue.durationMs - startDelayMs + 500L).coerceAtLeast(1_000L)
        }

        if (availableWindowMs <= 0L) return MAX_SPEED

        return when {
            // Audio is longer than the available window: speed up so it finishes before the deadline
            audioDurationMs > availableWindowMs -> {
                val requiredSpeed = audioDurationMs.toFloat() / availableWindowMs.toFloat()
                requiredSpeed.coerceIn(NORMAL_SPEED, MAX_SPEED)
            }
            // Audio is significantly shorter than the available dialogue window:
            // Relax the pace slightly towards MIN_SPEED so the speech matches the scene's cadence
            // instead of rushing through and leaving the user waiting.
            availableWindowMs > (audioDurationMs * 1.4f).toLong() &&
                (cue.durationMs - startDelayMs) > (audioDurationMs * 1.2f).toLong() -> {
                val relaxedTarget = (cue.durationMs - startDelayMs).coerceAtLeast(audioDurationMs)
                val relaxedSpeed = audioDurationMs.toFloat() / relaxedTarget.toFloat()
                relaxedSpeed.coerceIn(MIN_SPEED, NORMAL_SPEED)
            }
            else -> NORMAL_SPEED
        }
    }
}
