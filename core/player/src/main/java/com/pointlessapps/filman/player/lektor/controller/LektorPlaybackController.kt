package com.pointlessapps.filman.player.lektor.controller

import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import com.pointlessapps.filman.data.model.LektorTiming
import com.pointlessapps.filman.player.lektor.engine.LektorModelManager
import com.pointlessapps.filman.player.lektor.engine.LektorTtsEngine
import com.pointlessapps.filman.player.lektor.engine.SystemLektorSpeech
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
    onDuckingVolumeChange: (Float) -> Unit,
    private val nativePosition: () -> Long? = { null },
    private val nativeIsPlaying: () -> Boolean? = { null },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engineCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val volumeDucker = LektorVolumeDucker(scope, onDuckingVolumeChange)
    private val pcmAudioPlayer = LektorPcmAudioPlayer()
    private val edgePlayback = EdgeLektorPlayback(context, { volumeDucker.duck() }, { volumeDucker.restore() }, nativePosition)

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _selectedVoice = MutableStateFlow(LektorVoice.DEFAULT)
    val selectedVoice: StateFlow<LektorVoice> = _selectedVoice.asStateFlow()
    private var systemVoiceName: String? = null

    val duckingVolume: StateFlow<Float> = volumeDucker.duckingVolume

    @Volatile
    private var ttsEngine: LektorTtsEngine? = null

    @Volatile
    private var systemSpeech: SystemLektorSpeech? = null
    private val interruptedSystemSpeech = InterruptedSystemSpeech()

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

    private val playLock = Mutex()
    private val engineInitializationMutex = Mutex()
    private val engineStateLock = Any()

    @Volatile private var currentlyPlayingCueIndex: Int? = null
    @Volatile private var lastSpokenCueIndex: Int = -1
    @Volatile private var naturalMsPerCharacter: Double? = null
    @Volatile private var synthesisMsPerCharacter: Double? = null

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
            volumeDucker.restore()
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
            volumeDucker.restore()
            systemSpeech?.release()
            systemSpeech = null
            initializeEngine()
        }
    }

    fun setDuckingVolume(volume: Float) {
        volumeDucker.setDuckingVolume(volume)
    }

    fun setTimingOffsetMs(value: Int) {
        require(value in LektorTiming.offsetsMs)
        timingOffsetMs = value
        edgePlayback.setTimingOffsetMs(value)
        triggerLookahead()
    }

    fun setEdgeVoiceOptions(rate: String, pitch: String, volume: String) {
        edgePlayback.setVoiceOptions(rate, pitch, volume)
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
        volumeDucker.restore()
        edgePlayback.setCues(emptyList())
    }

    fun updatePlaybackState(positionMs: Long, playing: Boolean, buffering: Boolean = false) {
        val effectivePosition = nativePosition() ?: positionMs
        val effectivePlaying = nativeIsPlaying() ?: (playing && !buffering)
        edgePlayback.update(effectivePosition, effectivePlaying)
        val positionDiscontinuity = kotlin.math.abs(effectivePosition - currentPositionMs) > 2000L
        currentPositionMs = effectivePosition
        val wasPlaying = isPlaying
        isPlaying = effectivePlaying

        if (positionDiscontinuity) {
            onSeek(effectivePosition)
        } else if (wasPlaying != effectivePlaying) {
            if (effectivePlaying) {
                pcmAudioPlayer.play()
                if (currentlyPlayingCueIndex != null && _selectedVoice.value.requiresModel) {
                    volumeDucker.duck()
                }
            } else {
                pcmAudioPlayer.pause()
                interruptedSystemSpeech.pause { systemSpeech?.stop() }
                volumeDucker.restore()
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
        volumeDucker.restore()
        if (_isEnabled.value) {
            triggerLookahead()
        }
    }

    private fun initializeEngine() {
        val voice = _selectedVoice.value
        if (!voice.requiresModel) {
            cancelAndReleasePreviousEngine()
            if (voice.isSystem && systemSpeech == null) {
                systemSpeech = SystemLektorSpeech(
                    context,
                    { volumeDucker.duck() },
                    {
                        interruptedSystemSpeech.finish()
                        volumeDucker.restore()
                    },
                    systemVoiceName,
                ).also { it.initialize() }
            }
            return
        }
        if (ttsEngine?.voice == voice) {
            triggerLookahead()
            return
        }

        cancelAndReleasePreviousEngine()
        val pendingRelease = engineReleaseJob
        engineInitializationJob = scope.launch(Dispatchers.IO) {
            engineInitializationMutex.withLock {
                pendingRelease?.join()
                if (!isActive || !_isEnabled.value || _selectedVoice.value != voice) return@withLock

                val modelCheckStartedAt = SystemClock.elapsedRealtime()
                val isReady = modelManager.ensureModelDownloaded(voice)
                android.util.Log.d(
                    "Lektor",
                    "Model readiness for ${voice.id} took ${SystemClock.elapsedRealtime() - modelCheckStartedAt}ms",
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
                pcmAudioPlayer.setup(engine.sampleRate)
                triggerLookahead()
            }
        }
    }

    private fun cancelAndReleasePreviousEngine() {
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
    }

    private fun triggerLookahead() {
        if (!_isEnabled.value || cues.isEmpty() || ttsEngine == null) return
        if (lookaheadJob?.isActive == true) return

        lookaheadJob = scope.launch(Dispatchers.Default) {
            while (isActive && _isEnabled.value) {
                val cue = LektorLookahead.nextCue(
                    cues = cues,
                    positionMs = LektorCueTiming.position(currentPositionMs, timingOffsetMs),
                    millisecondsPerCharacter = synthesisMsPerCharacter,
                    prioritizeCurrent = !isPlaying,
                    isCached = { audioCache.get(it) != null },
                ) ?: break
                val engine = ttsEngine ?: break

                val nextCue = cues.find { it.index == cue.index + 1 }
                val estimatedDurationMs = naturalMsPerCharacter
                    ?.let { (cue.text.length * it).toLong() }
                    ?: (cue.text.length * 70L)
                val speedFactor = LektorSpeedCalculator.calculateSpeed(
                    audioDurationMs = estimatedDurationMs,
                    cue = cue,
                    nextCue = nextCue,
                    currentPositionMs = currentPositionMs,
                    timingOffsetMs = timingOffsetMs,
                )
                val startedAt = SystemClock.elapsedRealtime()
                val generated = engine.generate(cue.text, speed = speedFactor)
                val inferenceMs = SystemClock.elapsedRealtime() - startedAt
                if (!isActive || engine !== ttsEngine) break
                if (generated != null && generated.samples.isNotEmpty()) {
                    val observedSynthesisMsPerChar = inferenceMs.toDouble() / cue.text.length.coerceAtLeast(1)
                    synthesisMsPerCharacter = synthesisMsPerCharacter
                        ?.let { it * 0.7 + observedSynthesisMsPerChar * 0.3 }
                        ?: observedSynthesisMsPerChar
                    val finalDurationMs = (generated.samples.size * 1000L) / generated.sampleRate
                    val observedNaturalMsPerChar =
                        finalDurationMs.toDouble() * speedFactor / cue.text.length.coerceAtLeast(1).toDouble()
                    naturalMsPerCharacter = naturalMsPerCharacter
                        ?.let { it * 0.7 + observedNaturalMsPerChar * 0.3 }
                        ?: observedNaturalMsPerChar
                    audioCache.put(
                        cue.index,
                        AudioCueData(
                            cue = cue,
                            samples = generated.samples,
                            sampleRate = generated.sampleRate,
                            durationMs = finalDurationMs,
                        ),
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
        val interruptedIndex = interruptedSystemSpeech.interruptedIndex()
        val matchingCue = cues.firstOrNull { it.index == interruptedIndex && _selectedVoice.value.isSystem }
            ?: cues.firstOrNull { cue ->
                LektorCueTiming.isDue(cue, currentPositionMs, timingOffsetMs) && cue.index > lastSpokenCueIndex
            } ?: return

        playLock.withLock {
            if (!isPlaying || !_isEnabled.value) return
            if (currentlyPlayingCueIndex == matchingCue.index) return
            currentlyPlayingCueIndex = matchingCue.index

            if (_selectedVoice.value.isSystem) {
                val nextCue = cues.find { it.index == matchingCue.index + 1 }
                val estimatedDurationMs = matchingCue.text.length * 70L
                val speedFactor = LektorSpeedCalculator.calculateSpeed(
                    audioDurationMs = estimatedDurationMs,
                    cue = matchingCue,
                    nextCue = nextCue,
                    currentPositionMs = currentPositionMs,
                    timingOffsetMs = timingOffsetMs,
                )
                if (interruptedSystemSpeech.start(matchingCue.index) {
                        isPlaying && systemSpeech?.speak(matchingCue.text, speed = speedFactor) == true
                    }
                ) {
                    lastSpokenCueIndex = matchingCue.index
                    android.util.Log.i("Lektor", "System voice playing cue #${matchingCue.index} (speed=$speedFactor)")
                }
                currentlyPlayingCueIndex = null
                return
            }

            val audioData = audioCache.get(matchingCue.index)
            if (audioData != null && pcmAudioPlayer.isReady()) {
                lastSpokenCueIndex = matchingCue.index
                android.util.Log.i(
                    "Lektor",
                    "Playing cue #${matchingCue.index}: \"${matchingCue.text}\" (${audioData.durationMs}ms)",
                )
                playAudioCue(audioData)
            } else if (now > matchingCue.endMs + 800L) {
                lastSpokenCueIndex = matchingCue.index
            }
            currentlyPlayingCueIndex = null
        }
    }

    private suspend fun playAudioCue(audioData: AudioCueData) {
        if (!pcmAudioPlayer.isReady()) return
        volumeDucker.duck()
        val completed = pcmAudioPlayer.playSamples(audioData.samples) { isPlaying }
        if (!completed) return

        val nextStart = cues.find { it.index == audioData.cue.index + 1 }?.startMs
        if (nextStart == null || nextStart - LektorCueTiming.position(currentPositionMs, timingOffsetMs) > 400L) {
            volumeDucker.restore()
        }
    }

    private fun stopAudio() {
        interruptedSystemSpeech.clear()
        systemSpeech?.stop()
        pcmAudioPlayer.stop()
    }

    fun release() {
        subtitleParsingJob?.cancel()
        edgePlayback.release()
        synchronized(engineStateLock) { _isEnabled.value = false }
        engineInitializationJob?.cancel()
        playbackLoopJob?.cancel()
        lookaheadJob?.cancel()
        volumeDucker.releaseImmediate()
        interruptedSystemSpeech.clear()
        systemSpeech?.release()
        systemSpeech = null
        pcmAudioPlayer.release()
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
        updatePlaybackState(positionMs, playing)
    }

    fun onNativeSeek(positionMs: Long) {
        if (_selectedVoice.value.isOnline) onSeek(positionMs)
    }
}
