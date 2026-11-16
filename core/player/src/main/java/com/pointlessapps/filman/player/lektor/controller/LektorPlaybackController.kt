package com.pointlessapps.filman.player.lektor.controller

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.LruCache
import com.pointlessapps.filman.player.lektor.engine.LektorModelManager
import com.pointlessapps.filman.player.lektor.engine.LektorTtsEngine
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import com.pointlessapps.filman.player.lektor.model.SubtitleCue
import com.pointlessapps.filman.player.lektor.parser.SrtParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AudioCueData(
    val cue: SubtitleCue,
    val samples: FloatArray,
    val sampleRate: Int,
    val durationMs: Long,
)

class LektorPlaybackController(
    private val context: Context,
    val modelManager: LektorModelManager = LektorModelManager(context),
    private val onDuckingVolumeChange: (Float) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _selectedVoice = MutableStateFlow(LektorVoice.DEFAULT)
    val selectedVoice: StateFlow<LektorVoice> = _selectedVoice.asStateFlow()

    private val _duckingVolume = MutableStateFlow(0.22f)
    val duckingVolume: StateFlow<Float> = _duckingVolume.asStateFlow()

    private var ttsEngine: LektorTtsEngine? = null
    private var audioTrack: AudioTrack? = null
    private var currentSampleRate: Int = 22050

    private var cues: List<SubtitleCue> = emptyList()
    private val audioCache = LruCache<Int, AudioCueData>(25)

    private var currentPositionMs: Long = 0L
    private var isPlaying: Boolean = false

    private var lookaheadJob: Job? = null
    private var playbackLoopJob: Job? = null
    private var activeDuckingJob: Job? = null

    private val playLock = Mutex()
    private var currentlyPlayingCueIndex: Int? = null
    private var lastSpokenCueIndex: Int = -1

    init {
        startPlaybackLoop()
    }

    fun setEnabled(enabled: Boolean) {
        if (_isEnabled.value == enabled) return
        _isEnabled.value = enabled
        android.util.Log.i("Lektor", "setEnabled($enabled)")
        if (enabled) {
            initializeEngine()
            triggerLookahead()
        } else {
            stopAudio()
            restoreVolume()
        }
    }

    fun setVoice(voice: LektorVoice) {
        if (_selectedVoice.value == voice) return
        _selectedVoice.value = voice
        android.util.Log.i("Lektor", "setVoice(${voice.id})")
        audioCache.evictAll()
        if (_isEnabled.value) {
            initializeEngine()
            triggerLookahead()
        }
    }

    fun setDuckingVolume(volume: Float) {
        _duckingVolume.value = volume.coerceIn(0.05f, 0.8f)
    }

    fun setSubtitles(rawSrt: String) {
        scope.launch(Dispatchers.Default) {
            val parsed = SrtParser.parse(rawSrt)
            cues = parsed
            android.util.Log.i("Lektor", "setSubtitles parsed ${parsed.size} cues")
            audioCache.evictAll()
            lastSpokenCueIndex = -1
            if (_isEnabled.value) {
                triggerLookahead()
            }
        }
    }

    fun updatePlaybackState(positionMs: Long, playing: Boolean) {
        val positionDiscontinuity = kotlin.math.abs(positionMs - currentPositionMs) > 2000L
        currentPositionMs = positionMs
        val wasPlaying = isPlaying
        isPlaying = playing

        if (positionDiscontinuity) {
            onSeek(positionMs)
        } else if (wasPlaying != playing) {
            if (playing) {
                audioTrack?.play()
            } else {
                audioTrack?.pause()
            }
        }

        if (_isEnabled.value) {
            triggerLookahead()
        }
    }

    fun onSeek(newPositionMs: Long) {
        lookaheadJob?.cancel()
        currentPositionMs = newPositionMs
        val priorCue = cues.lastOrNull { it.endMs < newPositionMs }
        lastSpokenCueIndex = priorCue?.index ?: -1
        android.util.Log.i("Lektor", "onSeek($newPositionMs), lastSpokenCueIndex=$lastSpokenCueIndex")
        stopAudio()
        restoreVolume()
        if (_isEnabled.value) {
            triggerLookahead()
        }
    }

    private fun initializeEngine() {
        scope.launch(Dispatchers.IO) {
            val voice = _selectedVoice.value
            val isReady = modelManager.ensureModelDownloaded(voice)
            if (!isReady) {
                android.util.Log.w("Lektor", "Model for voice ${voice.id} is not ready yet")
                return@launch
            }

            val paths = modelManager.getModelPaths(voice) ?: return@launch
            val engine = LektorTtsEngine.create(paths, voice)
            if (engine == null) {
                android.util.Log.e("Lektor", "Failed to create LektorTtsEngine for ${voice.id}")
                return@launch
            }

            ttsEngine?.release()
            ttsEngine = engine
            android.util.Log.i("Lektor", "LektorTtsEngine initialized successfully, sampleRate=${engine.sampleRate}")
            setupAudioTrack(engine.sampleRate)
            triggerLookahead()
        }
    }

    private fun setupAudioTrack(sampleRate: Int) {
        if (audioTrack != null && currentSampleRate == sampleRate) return

        try {
            audioTrack?.release()
            currentSampleRate = sampleRate

            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(sampleRate * 2)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .build()

            val track = AudioTrack(
                attributes,
                format,
                minBufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
            track.play()
            audioTrack = track
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun floatTo16BitPcm(floatSamples: FloatArray): ShortArray {
        val shorts = ShortArray(floatSamples.size)
        for (i in floatSamples.indices) {
            val s = (floatSamples[i].coerceIn(-1.0f, 1.0f) * 32767f).toInt()
            shorts[i] = s.toShort()
        }
        return shorts
    }

    private fun triggerLookahead() {
        if (!_isEnabled.value || cues.isEmpty() || ttsEngine == null) return
        if (lookaheadJob?.isActive == true) return

        lookaheadJob = scope.launch(Dispatchers.Default) {
            val now = currentPositionMs
            val upcomingCues = cues.filter { it.startMs in (now - 200L)..(now + 15_000L) }
                .take(6)

            for (cue in upcomingCues) {
                if (!isActive) break
                if (audioCache.get(cue.index) != null) continue

                val engine = ttsEngine ?: break

                // Adaptive speed calculation
                val nextCue = cues.find { it.index == cue.index + 1 }
                val windowMs = if (nextCue != null && nextCue.startMs > cue.startMs) {
                    (nextCue.startMs - cue.startMs).coerceAtLeast(cue.durationMs)
                } else {
                    cue.durationMs
                }

                // Initial synthesis
                var generated = engine.generate(cue.text, speed = 1.0f)
                if (generated != null && generated.samples.isNotEmpty()) {
                    val spokenDurationMs = (generated.samples.size * 1000L) / generated.sampleRate
                    if (spokenDurationMs > windowMs && windowMs > 500L) {
                        val speedFactor = (spokenDurationMs.toFloat() / windowMs).coerceIn(1.05f, 1.25f)
                        val adapted = engine.generate(cue.text, speed = speedFactor)
                        if (adapted != null) {
                            generated = adapted
                        }
                    }

                    val finalDurationMs = (generated.samples.size * 1000L) / generated.sampleRate
                    audioCache.put(
                        cue.index,
                        AudioCueData(
                            cue = cue,
                            samples = generated.samples,
                            sampleRate = generated.sampleRate,
                            durationMs = finalDurationMs,
                        )
                    )
                    android.util.Log.d("Lektor", "Pre-synthesized cue #${cue.index} (${finalDurationMs}ms)")
                }
            }
        }
    }

    private fun startPlaybackLoop() {
        playbackLoopJob?.cancel()
        playbackLoopJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                if (_isEnabled.value && isPlaying && cues.isNotEmpty()) {
                    checkAndPlayCurrentCue()
                }
                delay(30)
            }
        }
    }

    private suspend fun checkAndPlayCurrentCue() {
        val now = currentPositionMs
        val matchingCue = cues.firstOrNull { cue ->
            now in (cue.startMs - 50L)..(cue.endMs + 300L) && cue.index > lastSpokenCueIndex
        } ?: return

        playLock.withLock {
            if (currentlyPlayingCueIndex == matchingCue.index) return
            currentlyPlayingCueIndex = matchingCue.index

            var audioData = audioCache.get(matchingCue.index)
            if (audioData == null && ttsEngine != null) {
                val engine = ttsEngine
                if (engine != null) {
                    val gen = engine.generate(matchingCue.text, speed = 1.0f)
                    if (gen != null && gen.samples.isNotEmpty()) {
                        val durationMs = (gen.samples.size * 1000L) / gen.sampleRate
                        audioData = AudioCueData(
                            cue = matchingCue,
                            samples = gen.samples,
                            sampleRate = gen.sampleRate,
                            durationMs = durationMs,
                        )
                        audioCache.put(matchingCue.index, audioData)
                    }
                }
            }

            if (audioData != null) {
                lastSpokenCueIndex = matchingCue.index
                android.util.Log.i("Lektor", "Playing cue #${matchingCue.index}: \"${matchingCue.text}\" (${audioData.durationMs}ms)")
                playAudioCue(audioData)
            } else if (now > matchingCue.endMs + 800L) {
                // If the cue is already way past its window and couldn't be played, advance past it
                lastSpokenCueIndex = matchingCue.index
            }
            currentlyPlayingCueIndex = null
        }
    }

    private suspend fun playAudioCue(audioData: AudioCueData) {
        val track = audioTrack ?: return
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            try {
                track.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 1. Duck volume smoothly
        duckVolume()

        // 2. Play audio as 16-bit PCM
        val shortSamples = floatTo16BitPcm(audioData.samples)
        val startTime = System.currentTimeMillis()
        var written = 0
        while (written < shortSamples.size && isPlaying) {
            val count = track.write(shortSamples, written, shortSamples.size - written)
            if (count <= 0) break
            written += count
        }

        // 3. Wait for audio buffer to drain through speakers
        val elapsed = System.currentTimeMillis() - startTime
        val remainingAudioMs = (audioData.durationMs - elapsed).coerceAtLeast(0L)
        android.util.Log.d("Lektor", "Audio buffer written ($written samples), waiting ${remainingAudioMs}ms")
        delay(remainingAudioMs + 150L)

        // 4. Check if the next cue is about to play immediately
        val nextStart = cues.find { it.index == audioData.cue.index + 1 }?.startMs
        if (nextStart == null || nextStart - currentPositionMs > 400L) {
            restoreVolume()
        }
    }

    private fun duckVolume() {
        activeDuckingJob?.cancel()
        activeDuckingJob = scope.launch(Dispatchers.Main) {
            val target = _duckingVolume.value
            // Smooth ducking over 60ms
            onDuckingVolumeChange(0.6f)
            delay(30)
            onDuckingVolumeChange(target)
        }
    }

    private fun restoreVolume() {
        activeDuckingJob?.cancel()
        activeDuckingJob = scope.launch(Dispatchers.Main) {
            // Smooth restoration over 90ms
            onDuckingVolumeChange(0.6f)
            delay(45)
            onDuckingVolumeChange(1.0f)
        }
    }

    private fun stopAudio() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        playbackLoopJob?.cancel()
        lookaheadJob?.cancel()
        activeDuckingJob?.cancel()
        stopAudio()
        audioTrack?.release()
        audioTrack = null
        ttsEngine?.release()
        ttsEngine = null
        scope.cancel()
    }
}
