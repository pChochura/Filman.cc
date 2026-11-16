package com.pointlessapps.filman.player.lektor.engine

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
                try {
                    tts.generate(text = text, sid = voice.speakerId, speed = speed)
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
                LektorTtsEngine(tts, voice)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
