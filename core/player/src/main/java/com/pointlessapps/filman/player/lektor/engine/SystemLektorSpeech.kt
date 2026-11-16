package com.pointlessapps.filman.player.lektor.engine

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import com.pointlessapps.filman.core.player.R
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

internal class SystemLektorSpeech(
    private val context: Context,
    private val onStart: () -> Unit,
    private val onFinish: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val sequence = AtomicLong()
    @Volatile private var ready = false
    @Volatile private var released = false
    private var tts: TextToSpeech? = null

    fun initialize() {
        handler.post {
            if (released) return@post
            tts = TextToSpeech(context) { status ->
                if (released) return@TextToSpeech
                val engine = tts
                val language = if (status == TextToSpeech.SUCCESS) {
                    engine?.setLanguage(Locale("pl", "PL")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                } else {
                    TextToSpeech.LANG_NOT_SUPPORTED
                }
                val offlineVoice = if (language >= 0 && engine != null) {
                    val defaultVoice = engine.voice
                    if (defaultVoice != null &&
                        defaultVoice.locale.language == "pl" &&
                        !defaultVoice.isNetworkConnectionRequired &&
                        !defaultVoice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                    ) {
                        defaultVoice
                    } else {
                        engine.voices?.firstOrNull {
                            it.locale.language == "pl" &&
                                !it.isNetworkConnectionRequired &&
                                !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                        }
                    }
                } else {
                    null
                }
                if (engine == null || offlineVoice == null ||
                    engine.setVoice(offlineVoice) != TextToSpeech.SUCCESS
                ) {
                    reportError("Offline Polish system TTS is unavailable (status=$status, language=$language)")
                } else {
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) {
                            handler.post {
                                if (utteranceId == sequence.get().toString() && !released) onStart()
                            }
                        }

                        override fun onDone(utteranceId: String) {
                            handler.post {
                                if (utteranceId == sequence.get().toString() && !released) onFinish()
                            }
                        }

                        override fun onError(utteranceId: String) {
                            handler.post {
                                if (utteranceId == sequence.get().toString() && !released) {
                                    onFinish()
                                    reportError("System TTS failed to speak a subtitle")
                                }
                            }
                        }
                    })
                    ready = true
                    Log.i("Lektor", "Polish system TTS initialized")
                }
            }
        }
    }

    fun speak(text: String): Boolean {
        if (!ready || released) return false
        val id = sequence.incrementAndGet().toString()
        handler.post {
            if (released || !ready || id != sequence.get().toString()) return@post
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
            if (result != TextToSpeech.SUCCESS) {
                onFinish()
                reportError("System TTS rejected a subtitle (result=$result)")
            }
        }
        return true
    }

    fun stop() {
        sequence.incrementAndGet()
        handler.post { tts?.stop() }
    }

    fun release() {
        released = true
        ready = false
        sequence.incrementAndGet()
        handler.post {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }

    private fun reportError(message: String) {
        Log.e("Lektor", message)
        Toast.makeText(context, R.string.lektor_system_tts_unavailable, Toast.LENGTH_LONG).show()
    }
}
