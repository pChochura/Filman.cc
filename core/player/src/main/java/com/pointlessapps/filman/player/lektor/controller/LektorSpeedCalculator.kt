package com.pointlessapps.filman.player.lektor.controller

import com.pointlessapps.filman.player.lektor.model.SubtitleCue
import kotlin.math.roundToInt

internal object LektorSpeedCalculator {
    const val MIN_SPEED = 0.92f
    const val MAX_SPEED = 1.45f
    const val NORMAL_SPEED = 1.0f
    const val DEFAULT_PAUSE_MARGIN_MS = 180L
    const val DEFAULT_NATURAL_MS_PER_CHAR = 62.0
    const val MIN_RESIDUAL_EDGE_SPEED = 1.0f

    private const val NEURAL_MIN_RELAXED_RATE = -10
    private const val NEURAL_MAX_RATE = 45
    private const val NEURAL_MAX_SPEEDUP_DELTA = 25
    private const val NEURAL_MAX_RELAX_DELTA = 6

    /**
     * Calculates the optimal playback speed multiplier for speech audio so that it finishes
     * before the next cue begins (preventing cut-offs mid-sentence or cue dropping), and does
     * not rush through when ample time is available (preventing awkward waiting for the next one).
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
        val startDelayMs = (adjustedPosition - cue.startMs).coerceAtLeast(0L)
        val availableWindowMs = availableWindow(cue, nextCue, startDelayMs)

        if (availableWindowMs <= 0L) return MAX_SPEED

        return when {
            audioDurationMs > availableWindowMs -> {
                val requiredSpeed = audioDurationMs.toFloat() / availableWindowMs.toFloat()
                requiredSpeed.coerceIn(NORMAL_SPEED, MAX_SPEED)
            }
            availableWindowMs > (audioDurationMs * 1.4f).toLong() &&
                (cue.durationMs - startDelayMs) > (audioDurationMs * 1.2f).toLong() -> {
                val relaxedTarget = (cue.durationMs - startDelayMs).coerceAtLeast(audioDurationMs)
                val relaxedSpeed = audioDurationMs.toFloat() / relaxedTarget.toFloat()
                relaxedSpeed.coerceIn(MIN_SPEED, NORMAL_SPEED)
            }
            else -> NORMAL_SPEED
        }
    }

    /**
     * Calculates the native Edge neural `<prosody rate>` percentage before synthesis so pacing
     * adapts without introducing DSP time-stretching artifacts.
     */
    fun calculateNeuralRatePercent(
        textLength: Int,
        cue: SubtitleCue,
        nextCue: SubtitleCue?,
        baseRatePercent: Int,
        naturalMsPerChar: Double = DEFAULT_NATURAL_MS_PER_CHAR,
    ): Int {
        val baseMultiplier = (1.0 + baseRatePercent / 100.0).coerceAtLeast(0.5)
        val estimatedNaturalMs = (textLength.coerceAtLeast(1) * naturalMsPerChar).toLong()
        val estimatedBaseMs = (estimatedNaturalMs / baseMultiplier).toLong()

        val availableWindowMs = if (nextCue != null && nextCue.startMs > cue.startMs) {
            ((nextCue.startMs - cue.startMs) - DEFAULT_PAUSE_MARGIN_MS).coerceAtLeast(cue.durationMs)
        } else {
            (cue.durationMs + 500L).coerceAtLeast(1_000L)
        }

        if (availableWindowMs <= 0L) {
            return (baseRatePercent + NEURAL_MAX_SPEEDUP_DELTA).coerceIn(-25, NEURAL_MAX_RATE)
        }

        return when {
            estimatedBaseMs > availableWindowMs -> {
                val targetMultiplier = baseMultiplier * (estimatedBaseMs.toDouble() / availableWindowMs.toDouble())
                val rawPercent = ((targetMultiplier - 1.0) * 100.0).roundToInt()
                val maxAllowed = (baseRatePercent + NEURAL_MAX_SPEEDUP_DELTA).coerceAtMost(NEURAL_MAX_RATE)
                rawPercent.coerceIn(baseRatePercent, maxAllowed)
            }
            availableWindowMs > (estimatedBaseMs * 1.5).toLong() &&
                cue.durationMs > (estimatedBaseMs * 1.3).toLong() -> {
                val relaxedPercent = (((estimatedNaturalMs.toDouble() / cue.durationMs.toDouble()) - 1.0) * 100.0)
                    .roundToInt()
                val minAllowed = (baseRatePercent - NEURAL_MAX_RELAX_DELTA).coerceAtLeast(NEURAL_MIN_RELAXED_RATE)
                relaxedPercent.coerceIn(minAllowed, baseRatePercent)
            }
            else -> baseRatePercent
        }
    }

    /**
     * Residual ExoPlayer speed multiplier for already-synthesized Edge audio.
     * Never slows below `1.0f` via Sonic; only accelerates if audio still overflows the remaining window.
     */
    fun calculateResidualEdgeSpeed(
        audioDurationMs: Long,
        cue: SubtitleCue,
        nextCue: SubtitleCue?,
        currentPositionMs: Long,
        timingOffsetMs: Int,
    ): Float {
        if (audioDurationMs <= 0L) return NORMAL_SPEED
        val adjustedPosition = LektorCueTiming.position(currentPositionMs, timingOffsetMs)
        val startDelayMs = (adjustedPosition - cue.startMs).coerceAtLeast(0L)
        val availableWindowMs = availableWindow(cue, nextCue, startDelayMs)

        if (availableWindowMs <= 0L) return MAX_SPEED
        if (audioDurationMs <= availableWindowMs) return NORMAL_SPEED

        val requiredSpeed = audioDurationMs.toFloat() / availableWindowMs.toFloat()
        return requiredSpeed.coerceIn(MIN_RESIDUAL_EDGE_SPEED, MAX_SPEED)
    }

    private fun availableWindow(cue: SubtitleCue, nextCue: SubtitleCue?, startDelayMs: Long): Long =
        if (nextCue != null && nextCue.startMs > cue.startMs) {
            val totalSpanMs = nextCue.startMs - (cue.startMs + startDelayMs)
            (totalSpanMs - DEFAULT_PAUSE_MARGIN_MS).coerceAtLeast(cue.durationMs - startDelayMs)
        } else {
            (cue.durationMs - startDelayMs + 500L).coerceAtLeast(1_000L)
        }
}
