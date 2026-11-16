package com.pointlessapps.filman.player.lektor.controller

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.util.LruCache
import com.pointlessapps.filman.player.lektor.engine.LektorModelManager
import com.pointlessapps.filman.player.lektor.engine.LektorTtsEngine
import com.pointlessapps.filman.player.lektor.engine.SystemLektorSpeech
import com.pointlessapps.filman.data.model.LektorTiming
import com.pointlessapps.filman.data.model.LektorDucking
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import com.pointlessapps.filman.player.lektor.model.SubtitleCue
import com.pointlessapps.filman.player.lektor.parser.LektorSentenceGrouper
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
import kotlinx.coroutines.withContext

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
    private val nativePosition: () -> Long? = { null },
    private val nativeIsPlaying: () -> Boolean? = { null },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engineCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val edgePlayback = EdgeLektorPlayback(context, ::duckVolume, ::restoreVolume, nativePosition)

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _selectedVoice = MutableStateFlow(LektorVoice.DEFAULT)
    val selectedVoice: StateFlow<LektorVoice> = _selectedVoice.asStateFlow()
    private var systemVoiceName: String? = null

    private val _duckingVolume = MutableStateFlow(LektorDucking.DEFAULT_VOLUME)
    val duckingVolume: StateFlow<Float> = _duckingVolume.asStateFlow()

    @Volatile
    private var ttsEngine: LektorTtsEngine? = null
    @Volatile
    private var systemSpeech: SystemLektorSpeech? = null
    private var audioTrack: AudioTrack? = null
    private var currentSampleRate: Int = 22050

    @Volatile
    private var cues: List<SubtitleCue> = emptyList()
    private val audioCache = LruCache<Int, AudioCueData>(25)

    @Volatile
    private var currentPositionMs: Long = 0L
    @Volatile
    private var timingOffsetMs = LektorTiming.DEFAULT_OFFSET_MS
    @Volatile
    private var isPlaying: Boolean = false

    private var lookaheadJob: Job? = null
    private var engineInitializationJob: Job? = null
    @Volatile
    private var engineReleaseJob: Job? = null
    private var playbackLoopJob: Job? = null
    private var subtitleParsingJob: Job? = null
    private var activeDuckingJob: Job? = null
    private var isDucking = false
    private var appliedVolume = 1.0f

    private val playLock = Mutex()
    private val engineInitializationMutex = Mutex()
    private val engineStateLock = Any()
    private var currentlyPlayingCueIndex: Int? = null
    private var lastSpokenCueIndex: Int = -1
    @Volatile
    private var naturalMsPerCharacter: Double? = null
    @Volatile
    private var synthesisMsPerCharacter: Double? = null

    init {
        startPlaybackLoop()
    }

    fun setEnabled(enabled: Boolean) {
        if (_isEnabled.value == enabled) return
        synchronized(engineStateLock) { _isEnabled.value = enabled }
        edgePlayback.setEnabled(enabled && _selectedVoice.value.isOnline)
        android.util.Log.i("Lektor", "setEnabled($enabled)")
        if (enabled) {
            initializeEngine()
            triggerLookahead()
        } else {
            engineInitializationJob?.cancel()
            engineInitializationJob = null
            lookaheadJob?.cancel()
            systemSpeech?.release()
            systemSpeech = null
            stopAudio()
            restoreVolume()
        }
    }

    fun setVoice(voice: LektorVoice, systemVoiceName: String? = null) {
        if (_selectedVoice.value == voice && this.systemVoiceName == systemVoiceName) return
        synchronized(engineStateLock) {
            _selectedVoice.value = voice
            this.systemVoiceName = systemVoiceName
        }
        if (voice.isOnline) edgePlayback.setVoice(voice)
        edgePlayback.setEnabled(_isEnabled.value && voice.isOnline)
        android.util.Log.i("Lektor", "setVoice(${voice.id})")
        lookaheadJob?.cancel()
        audioCache.evictAll()
        naturalMsPerCharacter = null
        synthesisMsPerCharacter = null
        if (_isEnabled.value) {
            stopAudio()
            restoreVolume()
            systemSpeech?.release()
            systemSpeech = null
            initializeEngine()
        }
    }

    fun setDuckingVolume(volume: Float) {
        require(volume in LektorDucking.levels)
        _duckingVolume.value = volume
        if (isDucking) duckVolume()
    }

    fun setTimingOffsetMs(value: Int) {
        require(value in LektorTiming.offsetsMs)
        timingOffsetMs = value
        edgePlayback.setTimingOffsetMs(value)
        triggerLookahead()
    }

    fun setSubtitles(rawSrt: String) {
        clearSubtitles()
        subtitleParsingJob = scope.launch(Dispatchers.Default) {
            val parsed = LektorSentenceGrouper.group(SrtParser.parse(rawSrt, normalizePolish = false))
            withContext(Dispatchers.Main) {
                cues = parsed
                android.util.Log.i("Lektor", "setSubtitles parsed ${parsed.size} cues")
                audioCache.evictAll()
                naturalMsPerCharacter = null
                lastSpokenCueIndex = -1
                edgePlayback.setCues(parsed)
                if (_isEnabled.value) triggerLookahead()
            }
        }
    }

    fun clearSubtitles() {
        subtitleParsingJob?.cancel()
        lookaheadJob?.cancel()
        cues = emptyList()
        audioCache.evictAll()
        stopAudio()
        restoreVolume()
        edgePlayback.setCues(emptyList())
    }

    fun updatePlaybackState(positionMs: Long, playing: Boolean, buffering: Boolean = false) {
        val effectivePosition = nativePosition() ?: positionMs
        edgePlayback.update(effectivePosition, nativeIsPlaying() ?: (playing && !buffering))
        val positionDiscontinuity = kotlin.math.abs(effectivePosition - currentPositionMs) > 2000L
        currentPositionMs = effectivePosition
        val wasPlaying = isPlaying
        isPlaying = playing

        if (positionDiscontinuity) {
            onSeek(effectivePosition)
        } else if (wasPlaying != playing) {
            if (playing) {
                audioTrack?.play()
            } else {
                audioTrack?.pause()
                lookaheadJob?.cancel()
                systemSpeech?.stop()
                restoreVolume()
            }
        }

        if (_isEnabled.value) {
            triggerLookahead()
        }
    }

    fun onSeek(newPositionMs: Long) {
        edgePlayback.seek(newPositionMs)
        lookaheadJob?.cancel()
        currentPositionMs = newPositionMs
        val adjustedPosition = LektorCueTiming.position(newPositionMs, timingOffsetMs)
        val priorCue = cues.lastOrNull { it.endMs < adjustedPosition }
        lastSpokenCueIndex = priorCue?.index ?: -1
        android.util.Log.i("Lektor", "onSeek($newPositionMs), lastSpokenCueIndex=$lastSpokenCueIndex")
        stopAudio()
        restoreVolume()
        if (_isEnabled.value) {
            triggerLookahead()
        }
    }

    private fun initializeEngine() {
        val voice = _selectedVoice.value
        if (!voice.requiresModel) {
            engineInitializationJob?.cancel()
            lookaheadJob?.cancel()
            val previousEngine = ttsEngine
            ttsEngine = null
            if (previousEngine != null) {
                val pendingRelease = engineReleaseJob
                engineReleaseJob = engineCleanupScope.launch {
                    pendingRelease?.join()
                    previousEngine.release()
                }
            }
            if (voice.isSystem && systemSpeech == null) {
                systemSpeech = SystemLektorSpeech(context, ::duckVolume, ::restoreVolume, systemVoiceName).also {
                    it.initialize()
                }
            }
            return
        }
        if (ttsEngine?.voice == voice) {
            triggerLookahead()
            return
        }

        engineInitializationJob?.cancel()
        lookaheadJob?.cancel()
        val previousEngine = ttsEngine
        ttsEngine = null
        if (previousEngine != null) {
            val pendingRelease = engineReleaseJob
            engineReleaseJob = engineCleanupScope.launch {
                pendingRelease?.join()
                previousEngine.release()
            }
        }
        val pendingRelease = engineReleaseJob
        engineInitializationJob = scope.launch(Dispatchers.IO) {
            engineInitializationMutex.withLock {
                pendingRelease?.join()
                if (!isActive || !_isEnabled.value || _selectedVoice.value != voice) return@withLock

                val modelCheckStartedAt = android.os.SystemClock.elapsedRealtime()
                val isReady = modelManager.ensureModelDownloaded(voice)
                android.util.Log.d(
                    "Lektor",
                    "Model readiness for ${voice.id} took " +
                        "${android.os.SystemClock.elapsedRealtime() - modelCheckStartedAt}ms",
                )
                if (!isReady) {
                    android.util.Log.w("Lektor", "Model for voice ${voice.id} is not ready yet")
                    return@withLock
                }

                if (!isActive || !_isEnabled.value || _selectedVoice.value != voice) return@withLock
                val paths = modelManager.getModelPaths(voice) ?: return@withLock
                val engine = LektorTtsEngine.create(paths, voice)
                if (engine == null) {
                    android.util.Log.e("Lektor", "Failed to create LektorTtsEngine for ${voice.id}")
                    return@withLock
                }

                val installed = synchronized(engineStateLock) {
                    if (isActive && _isEnabled.value && _selectedVoice.value == voice) {
                        ttsEngine = engine
                        true
                    } else {
                        false
                    }
                }
                if (!installed) {
                    engine.release()
                    return@withLock
                }

                android.util.Log.i("Lektor", "LektorTtsEngine initialized successfully, sampleRate=${engine.sampleRate}")
                setupAudioTrack(engine.sampleRate)
                triggerLookahead()
            }
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
        if (!_isEnabled.value || !isPlaying || cues.isEmpty() || ttsEngine == null) return
        if (lookaheadJob?.isActive == true) return

        lookaheadJob = scope.launch(Dispatchers.Default) {
            while (isActive && _isEnabled.value && isPlaying) {
                val cue = LektorLookahead.nextCue(
                    cues = cues,
                    positionMs = LektorCueTiming.position(currentPositionMs, timingOffsetMs),
                    millisecondsPerCharacter = synthesisMsPerCharacter,
                    isCached = { audioCache.get(it) != null },
                ) ?: break
                val engine = ttsEngine ?: break

                val nextCue = cues.find { it.index == cue.index + 1 }
                val windowMs = if (nextCue != null && nextCue.startMs > cue.startMs) {
                    (nextCue.startMs - cue.startMs).coerceAtLeast(cue.durationMs)
                } else {
                    cue.durationMs
                }
                val estimatedDurationMs = naturalMsPerCharacter
                    ?.let { (cue.text.length * it).toLong() }
                val speedFactor = if (estimatedDurationMs != null && windowMs > 500L) {
                    (estimatedDurationMs.toFloat() / windowMs).coerceIn(1.0f, 1.25f)
                } else {
                    1.0f
                }
                val startedAt = SystemClock.elapsedRealtime()
                val generated = engine.generate(cue.text, speed = speedFactor)
                val inferenceMs = SystemClock.elapsedRealtime() - startedAt
                if (!isActive || engine !== ttsEngine) break
                if (generated != null && generated.samples.isNotEmpty()) {
                    val observedSynthesisMsPerCharacter =
                        inferenceMs.toDouble() / cue.text.length.coerceAtLeast(1)
                    synthesisMsPerCharacter = synthesisMsPerCharacter
                        ?.let { it * 0.7 + observedSynthesisMsPerCharacter * 0.3 }
                        ?: observedSynthesisMsPerCharacter
                    val finalDurationMs = (generated.samples.size * 1000L) / generated.sampleRate
                    val observedNaturalMsPerCharacter =
                        finalDurationMs.toDouble() * speedFactor /
                            cue.text.length.coerceAtLeast(1).toDouble()
                    naturalMsPerCharacter = naturalMsPerCharacter
                        ?.let { it * 0.7 + observedNaturalMsPerCharacter * 0.3 }
                        ?: observedNaturalMsPerCharacter
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
                } else {
                    android.util.Log.w("Lektor", "Could not synthesize cue #${cue.index}")
                    break
                }
            }
        }
    }

    private fun startPlaybackLoop() {
        playbackLoopJob?.cancel()
        playbackLoopJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                if (_isEnabled.value && !_selectedVoice.value.isOnline && isPlaying && cues.isNotEmpty()) {
                    checkAndPlayCurrentCue()
                }
                delay(30)
            }
        }
    }

    private suspend fun checkAndPlayCurrentCue() {
        val now = LektorCueTiming.position(currentPositionMs, timingOffsetMs)
        val matchingCue = cues.firstOrNull { cue ->
            LektorCueTiming.isDue(cue, currentPositionMs, timingOffsetMs) && cue.index > lastSpokenCueIndex
        } ?: return

        playLock.withLock {
            if (currentlyPlayingCueIndex == matchingCue.index) return
            currentlyPlayingCueIndex = matchingCue.index

            if (_selectedVoice.value.isSystem) {
                if (systemSpeech?.speak(matchingCue.text) == true) {
                    lastSpokenCueIndex = matchingCue.index
                    android.util.Log.i("Lektor", "System voice playing cue #${matchingCue.index}")
                }
                currentlyPlayingCueIndex = null
                return
            }

            val audioData = audioCache.get(matchingCue.index)

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
        if (nextStart == null || nextStart - LektorCueTiming.position(currentPositionMs, timingOffsetMs) > 400L) {
            restoreVolume()
        }
    }

    private fun duckVolume() {
        activeDuckingJob?.cancel()
        activeDuckingJob = scope.launch(Dispatchers.Main.immediate) {
            isDucking = true
            changeVolume(_duckingVolume.value)
        }
    }

    private fun restoreVolume() {
        activeDuckingJob?.cancel()
        activeDuckingJob = scope.launch(Dispatchers.Main.immediate) {
            isDucking = false
            changeVolume(1.0f)
        }
    }

    private suspend fun changeVolume(target: Float) {
        if (appliedVolume == target) return
        appliedVolume = (appliedVolume + target) / 2
        onDuckingVolumeChange(appliedVolume)
        delay(30)
        appliedVolume = target
        onDuckingVolumeChange(target)
    }

    private fun stopAudio() {
        systemSpeech?.stop()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        subtitleParsingJob?.cancel()
        edgePlayback.release()
        synchronized(engineStateLock) { _isEnabled.value = false }
        engineInitializationJob?.cancel()
        playbackLoopJob?.cancel()
        lookaheadJob?.cancel()
        activeDuckingJob?.cancel()
        if (appliedVolume != 1.0f) {
            appliedVolume = 1.0f
            onDuckingVolumeChange(1.0f)
        }
        stopAudio()
        systemSpeech?.release()
        systemSpeech = null
        audioTrack?.release()
        audioTrack = null
        val engine = ttsEngine
        ttsEngine = null
        if (engine != null) {
            val pendingRelease = engineReleaseJob
            engineReleaseJob = engineCleanupScope.launch {
                pendingRelease?.join()
                engine.release()
            }
        }
        scope.cancel()
    }

    fun onNativePlaybackChanged(positionMs: Long, playing: Boolean) {
        if (_selectedVoice.value.isOnline) edgePlayback.update(positionMs, playing)
    }

    fun onNativeSeek(positionMs: Long) {
        if (_selectedVoice.value.isOnline) onSeek(positionMs)
    }
}
