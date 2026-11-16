package com.pointlessapps.filman.player.lektor.engine

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import android.widget.Toast
import com.pointlessapps.filman.core.player.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class SystemVoiceList(
    val loading: Boolean = true,
    val names: List<String> = emptyList(),
    val failed: Boolean = false,
)

internal fun Voice.isInstalledPolishVoice(): Boolean =
    locale.language == "pl" && !isNetworkConnectionRequired &&
        !features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)

class SystemVoiceCatalog(context: Context) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val _voices = MutableStateFlow(SystemVoiceList())
    val voices = _voices.asStateFlow()
    private var engine: TextToSpeech? = null
    private var initializing = false
    private var defaultVoiceName: String? = null

    fun refresh() {
        handler.post {
            if (initializing) return@post
            engine?.stop()
            engine?.shutdown()
            engine = null
            _voices.value = SystemVoiceList()
            initializing = true
            engine = TextToSpeech(context) { status ->
                initializing = false
                val tts = engine
                if (tts == null || status != TextToSpeech.SUCCESS ||
                    tts.setLanguage(Locale("pl", "PL")) < 0
                ) {
                    Log.e("Lektor", "Could not enumerate Polish system voices (status=$status)")
                    tts?.shutdown()
                    engine = null
                    _voices.value = SystemVoiceList(loading = false, failed = true)
                    return@TextToSpeech
                }
                defaultVoiceName = tts.voice?.takeIf { it.isInstalledPolishVoice() }?.name
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) = Unit
                    override fun onDone(utteranceId: String) {
                        Log.i("Lektor", "System voice preview completed")
                    }
                    override fun onError(utteranceId: String) {
                        handler.post { reportPreviewError("System voice preview failed") }
                    }
                })
                publishVoices(tts)
            }
        }
    }

    fun preview(name: String?) {
        handler.post {
            val tts = engine
            val requested = name ?: defaultVoiceName
            val voice = tts?.voices?.firstOrNull {
                it.isInstalledPolishVoice() && (requested == null || it.name == requested)
            }
            if (tts == null || voice == null || tts.setVoice(voice) != TextToSpeech.SUCCESS ||
                tts.speak(context.getString(R.string.lektor_system_tts_preview), TextToSpeech.QUEUE_FLUSH, Bundle(), "preview") != TextToSpeech.SUCCESS
            ) {
                reportPreviewError("Could not preview system voice: $name")
            } else {
                Log.i("Lektor", "Previewing system voice ${voice.name}")
            }
        }
    }

    private fun publishVoices(tts: TextToSpeech) {
        val available = tts.voices
        if (available == null) {
            Log.e("Lektor", "System TTS did not return a voice list")
            _voices.value = SystemVoiceList(loading = false, failed = true)
            return
        }
        val names = available.filter { it.isInstalledPolishVoice() }
            .map { it.name }.distinct().sorted()
        _voices.value = SystemVoiceList(loading = false, names = names)
    }

    private fun reportPreviewError(message: String) {
        Log.e("Lektor", message)
        Toast.makeText(context, R.string.lektor_system_tts_unavailable, Toast.LENGTH_LONG).show()
    }
}
