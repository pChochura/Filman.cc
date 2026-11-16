package com.pointlessapps.filman.player.lektor.engine

import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LektorTtsEngine private constructor(
    private val tts: OfflineTts,
    val voice: LektorVoice,
) {
    private val nativeLock = Any()
    private var isReleased = false

    val sampleRate: Int = tts.sampleRate()

    suspend fun generate(text: String, speed: Float = 1.0f): GeneratedAudio? =
        withContext(Dispatchers.Default) {
            synchronized(nativeLock) {
                if (isReleased || text.isBlank()) return@withContext null
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    tts.generate(text = text, sid = voice.speakerId, speed = speed)
                        .also { audio ->
                            val inferenceMs = SystemClock.elapsedRealtime() - startedAt
                            val audioMs = audio.samples.size * 1000L / audio.sampleRate
                            val realTimeFactor = inferenceMs.toFloat() / audioMs.coerceAtLeast(1L)
                            Log.d(
                                "Lektor",
                                "TTS generated ${text.length} chars in ${inferenceMs}ms " +
                                    "(audio=${audioMs}ms, RTF=${"%.2f".format(realTimeFactor)})",
                            )
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }

    fun release() {
        synchronized(nativeLock) {
            if (!isReleased) {
                isReleased = true
                try {
                    tts.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    companion object {
        fun create(paths: LektorModelPaths, voice: LektorVoice): LektorTtsEngine? {
            return try {
                val startedAt = SystemClock.elapsedRealtime()
                val vitsConfig = OfflineTtsVitsModelConfig().apply {
                    model = paths.modelPath
                    tokens = paths.tokensPath
                    dataDir = paths.dataDirPath
                    noiseScale = voice.noiseScale
                    noiseScaleW = voice.noiseScaleW
                    lengthScale = voice.lengthScale
                }

                val modelConfig = OfflineTtsModelConfig().apply {
                    vits = vitsConfig
                    numThreads = 2
                    debug = false
                    provider = "cpu"
                }

                val ttsConfig = OfflineTtsConfig().apply {
                    model = modelConfig
                    maxNumSentences = 1
                }

                val tts = OfflineTts(null, ttsConfig)
                Log.d(
                    "Lektor",
                    "TTS engine for ${voice.id} created in ${SystemClock.elapsedRealtime() - startedAt}ms",
                )
                LektorTtsEngine(tts, voice)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
