package com.pointlessapps.filman.player.lektor.alignment

import com.pointlessapps.filman.player.lektor.model.SubtitleCue
import kotlin.math.abs
import kotlin.math.roundToInt

data class AlignmentResult(
    val bestOffsetMs: Int,
    val bestFpsRatio: Double,
    val confidence: Double,
)

/**
 * Fast rasterized cross-correlation aligner between reference cues (e.g. embedded English track)
 * and target cues (e.g. external Polish track).
 */
object SubtitleTrackAligner {

    private const val BIN_MS = 100 // 100ms resolution
    private val CANDIDATE_RATIOS = listOf(
        1.0,
        25.0 / 23.976, // 1.042709
        23.976 / 25.0, // 0.95904
        24.0 / 23.976, // 1.001
        23.976 / 24.0, // 0.999
    )

    /**
     * Finds the optimal time shift (offsetMs) and FPS scale ratio to align target cues to reference cues.
     * Searches shifts within [-maxLagMs, +maxLagMs].
     */
    fun align(
        referenceCues: List<SubtitleCue>,
        targetCues: List<SubtitleCue>,
        maxLagMs: Int = 60_000,
    ): AlignmentResult? {
        if (referenceCues.size < 5 || targetCues.size < 5) return null

        val refMaxTime = referenceCues.maxOfOrNull { it.endMs } ?: return null
        val targetMaxTime = targetCues.maxOfOrNull { it.endMs } ?: return null
        val maxDurationMs = maxOf(refMaxTime, targetMaxTime) + maxLagMs
        val totalBins = (maxDurationMs / BIN_MS).toInt() + 10

        val refBitSet = BooleanArray(totalBins)
        rasterize(referenceCues, refBitSet, 1.0)
        val refActiveCount = refBitSet.count { it }
        if (refActiveCount == 0) return null

        var bestOffsetMs = 0
        var bestRatio = 1.0
        var maxScore = -1.0
        val maxLagBins = maxLagMs / BIN_MS

        val targetBitSet = BooleanArray(totalBins)
        val targetActiveIndices = IntArray(totalBins)

        for (ratio in CANDIDATE_RATIOS) {
            java.util.Arrays.fill(targetBitSet, false)
            rasterize(targetCues, targetBitSet, ratio)

            var targetActiveCount = 0
            for (i in 0 until totalBins) {
                if (targetBitSet[i]) {
                    targetActiveIndices[targetActiveCount++] = i
                }
            }
            if (targetActiveCount == 0) continue

            val denom = kotlin.math.sqrt((refActiveCount.toLong() * targetActiveCount).toDouble())
            if (denom <= 0.0) continue

            // Correlate only over active target indices across lags
            for (lag in -maxLagBins..maxLagBins) {
                var overlap = 0
                for (k in 0 until targetActiveCount) {
                    val refIdx = targetActiveIndices[k] + lag
                    if (refIdx in 0 until totalBins && refBitSet[refIdx]) {
                        overlap++
                    }
                }

                val score = overlap.toDouble() / denom
                if (score > maxScore) {
                    maxScore = score
                    bestOffsetMs = lag * BIN_MS
                    bestRatio = ratio
                }
            }
        }

        return AlignmentResult(
            bestOffsetMs = bestOffsetMs,
            bestFpsRatio = bestRatio,
            confidence = maxScore,
        )
    }

    private fun rasterize(cues: List<SubtitleCue>, bitSet: BooleanArray, ratio: Double) {
        val totalBins = bitSet.size
        for (cue in cues) {
            val startBin = ((cue.startMs * ratio) / BIN_MS).toInt().coerceIn(0, totalBins - 1)
            val endBin = ((cue.endMs * ratio) / BIN_MS).toInt().coerceIn(0, totalBins - 1)
            for (i in startBin..endBin) {
                bitSet[i] = true
            }
        }
    }
}
