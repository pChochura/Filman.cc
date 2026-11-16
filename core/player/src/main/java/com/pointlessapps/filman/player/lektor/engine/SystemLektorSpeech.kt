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

internal class SystemLektorSpeech(
    private val context: Context,
    private val onStart: () -> Unit,
    private val onFinish: () -> Unit,
    private val voiceName: String? = null,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val utterances = SpeechUtteranceGate()
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
                    if (voiceName != null) {
                        engine.voices?.firstOrNull { it.name == voiceName && it.isInstalledPolishVoice() }
                    } else if (defaultVoice != null && defaultVoice.isInstalledPolishVoice()) {
                        defaultVoice
                    } else {
                        engine.voices?.firstOrNull { it.isInstalledPolishVoice() }
                    }
                } else {
                    null
                }
                if (engine == null || offlineVoice == null ||
                    engine.setVoice(offlineVoice) != TextToSpeech.SUCCESS
                ) {
                    reportError("Offline Polish system TTS is unavailable (voice=$voiceName, status=$status, language=$language)")
                } else {
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) {
                            handler.post {
                                val id = utteranceId.toLongOrNull() ?: return@post
                                if (utterances.isCurrent(id) && !released) onStart()
                            }
                        }

                        override fun onDone(utteranceId: String) {
                            handler.post {
                                val id = utteranceId.toLongOrNull() ?: return@post
                                if (utterances.finish(id) && !released) onFinish()
                            }
                        }

                        override fun onError(utteranceId: String) {
                            handler.post {
                                val id = utteranceId.toLongOrNull() ?: return@post
                                if (utterances.finish(id) && !released) {
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
        val id = utterances.tryStart() ?: return false
        handler.post {
            if (released || !ready || !utterances.isCurrent(id)) return@post
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id.toString())
            if (result != TextToSpeech.SUCCESS) {
                if (utterances.finish(id)) {
                    onFinish()
                    reportError("System TTS rejected a subtitle (result=$result)")
                }
            }
        }
        return true
    }

    fun stop() {
        utterances.cancel()
        handler.post { tts?.stop() }
    }

    fun release() {
        released = true
        ready = false
        utterances.cancel()
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
