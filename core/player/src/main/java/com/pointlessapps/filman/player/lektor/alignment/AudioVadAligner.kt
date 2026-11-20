package com.pointlessapps.filman.player.lektor.alignment

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeechSegment
import com.k2fsa.sherpa.onnx.TenVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.pointlessapps.filman.player.lektor.model.SubtitleCue

data class SpeechInterval(
    val startMs: Long,
    val endMs: Long,
)

object AudioVadAligner {

    fun createVad(
        modelPath: String,
        sampleRate: Int = 16000,
        threshold: Float = 0.5f,
        minSpeechDuration: Float = 0.25f,
        minSilenceDuration: Float = 0.5f,
        assetManager: AssetManager? = null,
    ): Vad? {
        return try {
            val sileroConfig = SileroVadModelConfig(
                model = modelPath,
                threshold = threshold,
                minSilenceDuration = minSilenceDuration,
                minSpeechDuration = minSpeechDuration,
                windowSize = 512,
                maxSpeechDuration = 30.0f,
            )
            val config = VadModelConfig(
                sileroVadModelConfig = sileroConfig,
                tenVadModelConfig = TenVadModelConfig(),
                sampleRate = sampleRate,
                numThreads = 1,
                provider = "cpu",
                debug = false,
            )
            Vad(assetManager = assetManager, config = config)
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Extracts speech intervals from audio samples (16kHz mono float) using Sherpa ONNX VAD.
     */
    fun extractSpeechIntervals(
        vad: Vad,
        samples: FloatArray,
        sampleRate: Int = 16000,
    ): List<SpeechInterval> {
        val intervals = mutableListOf<SpeechInterval>()
        vad.reset()
        vad.acceptWaveform(samples)
        vad.flush()
        while (!vad.empty()) {
            val segment: SpeechSegment = vad.front()
            val startMs = (segment.start.toDouble() / sampleRate * 1000.0).toLong()
            val durationMs = (segment.samples.size.toDouble() / sampleRate * 1000.0).toLong()
            intervals.add(SpeechInterval(startMs = startMs, endMs = startMs + durationMs))
            vad.pop()
        }
        return intervals
    }

    /**
     * Aligns target subtitle cues against speech intervals detected from audio.
     */
    fun alignWithSpeech(
        speechIntervals: List<SpeechInterval>,
        targetCues: List<SubtitleCue>,
        maxLagMs: Int = 45_000,
    ): AlignmentResult? {
        val speechCues = speechIntervals.mapIndexed { index, interval ->
            SubtitleCue(
                index = index,
                startMs = interval.startMs,
                endMs = interval.endMs,
                text = "",
            )
        }
        return SubtitleTrackAligner.align(
            referenceCues = speechCues,
            targetCues = targetCues,
            maxLagMs = maxLagMs,
        )
    }
}
