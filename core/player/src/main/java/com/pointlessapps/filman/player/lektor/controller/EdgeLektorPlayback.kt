package com.pointlessapps.filman.player.lektor.controller

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.pointlessapps.filman.core.player.R
import com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions
import com.pointlessapps.filman.data.model.LektorTiming
import com.pointlessapps.filman.player.lektor.engine.EdgeSynthesisResult
import com.pointlessapps.filman.player.lektor.engine.EdgeTtsClient
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import com.pointlessapps.filman.player.lektor.model.SubtitleCue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

internal class EdgeLektorPlayback(
    context: Context,
    private val onSpeechStart: () -> Unit,
    private val onSpeechEnd: () -> Unit,
    private val nativePosition: () -> Long?,
    private val client: EdgeTtsClient = EdgeTtsClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val synthesize: suspend (String, LektorVoice, String, String, String) -> EdgeSynthesisResult =
        { text, voice, rate, pitch, volume ->
            client.synthesizeWithMetadata(text, voice, rate, pitch, volume)
        },
    private val playerFactory: () -> ExoPlayer = { createSpeechPlayer(context.applicationContext) },
    private val prepareAudio: (ExoPlayer, EdgeSynthesisResult) -> Unit = ::prepareEdgeAudio,
) {
    constructor(
        context: Context,
        onSpeechStart: () -> Unit,
        onSpeechEnd: () -> Unit,
        nativePosition: () -> Long?,
        scope: CoroutineScope,
        synthesize: suspend (String, LektorVoice) -> ByteArray,
        playerFactory: () -> ExoPlayer,
        prepareAudio: (ExoPlayer, ByteArray) -> Unit,
    ) : this(
        context = context,
        onSpeechStart = onSpeechStart,
        onSpeechEnd = onSpeechEnd,
        nativePosition = nativePosition,
        client = EdgeTtsClient(),
        scope = scope,
        synthesize = { text, voice, _, _, _ -> EdgeSynthesisResult(audio = synthesize(text, voice)) },
        playerFactory = playerFactory,
        prepareAudio = { player, result -> prepareAudio(player, result.audio) },
    )

    private val context = context.applicationContext
    private val cache = mutableMapOf<Int, EdgeSynthesisResult>()
    private val pendingJobs = mutableMapOf<Int, Job>()
    private var cues = emptyList<SubtitleCue>()
    private var positionMs = 0L
    private var timingOffsetMs = LektorTiming.DEFAULT_OFFSET_MS
    private var playing = false
    private var enabled = false
    private var voice = LektorVoice.EDGE
    private var rate = LektorEdgeVoiceOptions.DEFAULT_RATE
    private var pitch = LektorEdgeVoiceOptions.DEFAULT_PITCH
    private var volume = LektorEdgeVoiceOptions.DEFAULT_VOLUME
    private var lastSpoken = -1
    private var activeCue: Int? = null
    private var isSpeechDucked = false
    private var naturalMsPerChar: Double? = null
    private var retryAfterElapsedMs = 0L
    private var lastErrorAt = 0L
    private var speechPlayer: ExoPlayer? = null

    init {
        scope.launch {
            while (isActive) {
                if (enabled) {
                    if (playing) positionMs = nativePosition() ?: positionMs
                    tick()
                }
                delay(30)
            }
        }
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) {
            cancelPlayback()
            cache.clear()
        }
    }

    fun setVoice(value: LektorVoice) {
        require(value.isOnline)
        if (voice == value) return
        cancelPlayback()
        cache.clear()
        naturalMsPerChar = null
        voice = value
    }

    fun setVoiceOptions(rate: String, pitch: String, volume: String) {
        if (this.rate == rate && this.pitch == pitch && this.volume == volume) return
        cancelPlayback()
        cache.clear()
        this.rate = rate
        this.pitch = pitch
        this.volume = volume
    }

    fun setTimingOffsetMs(value: Int) {
        timingOffsetMs = value
    }

    fun setCues(value: List<SubtitleCue>) {
        cancelPlayback()
        cache.clear()
        naturalMsPerChar = null
        cues = value
        lastSpoken = -1
    }

    fun update(position: Long, isPlaying: Boolean) {
        positionMs = position
        if (playing == isPlaying) return
        playing = isPlaying
        if (playing) {
            if (activeCue != null) speechPlayer?.play()
            if (enabled) tick()
        } else {
            speechPlayer?.pause()
            if (activeCue != null || isSpeechDucked) {
                isSpeechDucked = false
                onSpeechEnd()
            }
        }
    }

    fun seek(position: Long) {
        cancelPlayback()
        cache.clear()
        positionMs = position
        val adjustedPosition = LektorCueTiming.position(position, timingOffsetMs)
        lastSpoken = cues.lastOrNull { it.endMs < adjustedPosition }?.index ?: -1
    }

    private fun tick() {
        val adjustedPosition = LektorCueTiming.position(positionMs, timingOffsetMs)
        val window = cues.asSequence()
            .filter { it.index > lastSpoken && (it.endMs + 300 >= adjustedPosition || it.index in pendingJobs) }
            .take(PREFETCH_CUES)
            .toList()
        val wanted = window.mapTo(mutableSetOf()) { it.index }
        cache.keys.retainAll(wanted)
        pruneStalePendingJobs(wanted)

        val due = findDueCue(window, adjustedPosition)
        if (playing && due != null && activeCue == null) {
            val result = cache.remove(due.index)
            if (result != null) {
                lastSpoken = due.index
                play(due, result)
            }
        }

        restoreDuckingIfIdle(adjustedPosition)
        schedulePrefetch(window, adjustedPosition)
    }

    private fun pruneStalePendingJobs(wanted: Set<Int>) {
        val staleCues = mutableListOf<Int>()
        for ((cueIndex, job) in pendingJobs) {
            if (!job.isActive || cueIndex !in wanted || cueIndex <= lastSpoken) {
                staleCues.add(cueIndex)
            }
        }
        if (staleCues.isEmpty()) return

        val jobsToCancel = mutableListOf<Job>()
        for (cueIndex in staleCues) {
            val job = pendingJobs.remove(cueIndex)
            if (job != null && job.isActive) {
                jobsToCancel.add(job)
            }
        }
        jobsToCancel.forEach { it.cancel() }
    }

    private fun findDueCue(window: List<SubtitleCue>, adjustedPosition: Long): SubtitleCue? =
        window.firstOrNull { cue ->
            val nextCueStart = cues.find { it.index == cue.index + 1 }?.startMs
            val deadline = if (nextCueStart != null && nextCueStart > cue.startMs) {
                minOf(nextCueStart, cue.endMs + 800L)
            } else {
                cue.endMs + 800L
            }
            adjustedPosition >= cue.startMs && adjustedPosition <= deadline
        }

    private fun restoreDuckingIfIdle(adjustedPosition: Long) {
        if (activeCue != null || !isSpeechDucked) return
        val nextCue = cues.firstOrNull { it.index > lastSpoken }
        if (nextCue == null || nextCue.startMs - adjustedPosition > DUCK_HYSTERESIS_MS) {
            isSpeechDucked = false
            onSpeechEnd()
        }
    }

    private fun schedulePrefetch(window: List<SubtitleCue>, adjustedPosition: Long) {
        if (SystemClock.elapsedRealtime() < retryAfterElapsedMs) return

        while (pendingJobs.size < MAX_CONCURRENT_PREFETCH) {
            val next = window.firstOrNull {
                it.index !in cache && it.index !in pendingJobs && it.index > lastSpoken
            } ?: break
            if (pendingJobs.isNotEmpty() && next.startMs - adjustedPosition > IMMINENT_PREFETCH_WINDOW_MS) {
                break
            }
            launchPrefetchJob(next)
        }
    }

    private fun launchPrefetchJob(cue: SubtitleCue) {
        val nextCueAfter = cues.find { it.index == cue.index + 1 }
        val baseRatePercent = LektorEdgeVoiceOptions.parseRatePercent(rate)
        val targetRatePercent = LektorSpeedCalculator.calculateNeuralRatePercent(
            textLength = cue.text.length,
            cue = cue,
            nextCue = nextCueAfter,
            baseRatePercent = baseRatePercent,
            naturalMsPerChar = naturalMsPerChar ?: LektorSpeedCalculator.DEFAULT_NATURAL_MS_PER_CHAR,
        )
        val targetRate = LektorEdgeVoiceOptions.formatRatePercent(targetRatePercent)
        val generationVoice = voice

        pendingJobs[cue.index] = scope.launch {
            try {
                val result = synthesize(cue.text, generationVoice, targetRate, pitch, volume)
                if (enabled && voice == generationVoice && cue.index > lastSpoken) {
                    cache[cue.index] = result
                    updateNaturalRateEstimate(cue.text.length, result, targetRatePercent)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                reportError("Edge voice synthesis failed", e)
                retryAfterElapsedMs = SystemClock.elapsedRealtime() + RETRY_DELAY_MS
            } finally {
                pendingJobs.remove(cue.index)
            }
        }
    }

    private fun updateNaturalRateEstimate(
        textLength: Int,
        result: EdgeSynthesisResult,
        synthesizedRatePercent: Int,
    ) {
        if (textLength < MIN_CHARS_FOR_EMA) return
        val durationMs = result.speechDurationMs ?: Mp3DurationEstimator.getDurationMs(result.audio)
        if (durationMs <= 0L) return

        val multiplier = (1.0 + synthesizedRatePercent / 100.0).coerceAtLeast(0.5)
        val observedNaturalMsPerChar = ((durationMs * multiplier) / textLength.toDouble())
            .coerceIn(MIN_MS_PER_CHAR, MAX_MS_PER_CHAR)
        naturalMsPerChar = naturalMsPerChar
            ?.let { it * (1.0 - EMA_ALPHA) + observedNaturalMsPerChar * EMA_ALPHA }
            ?: observedNaturalMsPerChar
    }

    private fun play(cue: SubtitleCue, result: EdgeSynthesisResult) {
        val nextCue = cues.find { it.index == cue.index + 1 }
        val effectiveDurationMs = result.speechDurationMs
            ?.let { it + LEADING_TRIM_MARGIN_MS + TRAILING_TRIM_MARGIN_MS }
            ?: Mp3DurationEstimator.getDurationMs(result.audio)
        val speed = LektorSpeedCalculator.calculateResidualEdgeSpeed(
            audioDurationMs = effectiveDurationMs,
            cue = cue,
            nextCue = nextCue,
            currentPositionMs = positionMs,
            timingOffsetMs = timingOffsetMs,
        )

        val player = speechPlayer ?: playerFactory().also { created ->
            created.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (activeCue != null && isPlaying && !isSpeechDucked) {
                        isSpeechDucked = true
                        onSpeechStart()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        finishSpeech(keepDuckedIfImminent = true)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    finishSpeech(keepDuckedIfImminent = false)
                    reportError("Edge voice playback failed", error)
                }
            })
            speechPlayer = created
        }
        player.stop()
        activeCue = cue.index
        player.setPlaybackSpeed(speed)
        prepareAudio(player, result)
        player.play()
    }

    private fun finishSpeech(keepDuckedIfImminent: Boolean) {
        if (activeCue == null && !isSpeechDucked) return
        activeCue = null
        if (keepDuckedIfImminent && playing && isNextCueImminent()) {
            return
        }
        isSpeechDucked = false
        onSpeechEnd()
    }

    private fun isNextCueImminent(): Boolean {
        val nextCue = cues.firstOrNull { it.index > lastSpoken } ?: return false
        val currentPos = nativePosition() ?: positionMs
        val adjustedPosition = LektorCueTiming.position(currentPos, timingOffsetMs)
        return nextCue.startMs - adjustedPosition <= DUCK_HYSTERESIS_MS
    }

    private fun cancelPlayback() {
        val jobsToCancel = pendingJobs.values.toList()
        pendingJobs.clear()
        jobsToCancel.forEach { it.cancel() }
        speechPlayer?.stop()
        speechPlayer?.clearMediaItems()
        finishSpeech(keepDuckedIfImminent = false)
    }

    private fun reportError(message: String, error: Exception) {
        Log.e("Lektor", message, error)
        val now = SystemClock.elapsedRealtime()
        if (lastErrorAt == 0L || now - lastErrorAt >= 20_000L) {
            lastErrorAt = now
            Toast.makeText(context, R.string.lektor_edge_error, Toast.LENGTH_LONG).show()
        }
    }

    fun release() {
        cancelPlayback()
        speechPlayer?.release()
        speechPlayer = null
        scope.cancel()
    }

    companion object {
        private const val PREFETCH_CUES = 8
        private const val MAX_CONCURRENT_PREFETCH = 2
        private const val IMMINENT_PREFETCH_WINDOW_MS = 6_000L
        private const val DUCK_HYSTERESIS_MS = 400L
        private const val RETRY_DELAY_MS = 5_000L
        private const val MIN_CHARS_FOR_EMA = 12
        private const val MIN_MS_PER_CHAR = 30.0
        private const val MAX_MS_PER_CHAR = 120.0
        private const val EMA_ALPHA = 0.25
        internal const val LEADING_TRIM_MARGIN_MS = 20L
        internal const val TRAILING_TRIM_MARGIN_MS = 50L
        private const val MIN_LEADING_CLIP_MS = 25L
        private const val MIN_CLIP_DURATION_MS = 50L

        private fun createSpeechPlayer(context: Context): ExoPlayer =
            ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    false,
                )
            }

        @OptIn(UnstableApi::class)
        private fun prepareEdgeAudio(player: ExoPlayer, result: EdgeSynthesisResult) {
            val dataSource = DataSource.Factory { ByteArrayDataSource(result.audio) }
            val mediaItemBuilder = MediaItem.Builder()
                .setUri("memory:///lektor.mp3")
                .setMimeType(MimeTypes.AUDIO_MPEG)

            val startMs = result.speechStartMs
            val endMs = result.speechEndMs
            if (startMs != null && endMs != null && endMs > startMs) {
                val clipStart = (startMs - LEADING_TRIM_MARGIN_MS).coerceAtLeast(0L)
                val clipEnd = endMs + TRAILING_TRIM_MARGIN_MS
                val clipConfig = MediaItem.ClippingConfiguration.Builder().apply {
                    if (clipStart >= MIN_LEADING_CLIP_MS) setStartPositionMs(clipStart)
                    if (clipEnd > clipStart + MIN_CLIP_DURATION_MS) setEndPositionMs(clipEnd)
                }.build()
                mediaItemBuilder.setClippingConfiguration(clipConfig)
            }

            player.setMediaSource(
                ProgressiveMediaSource.Factory(dataSource).createMediaSource(mediaItemBuilder.build()),
            )
            player.prepare()
        }
    }
}
