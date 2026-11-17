package com.pointlessapps.filman.player.lektor.controller

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.pointlessapps.filman.core.player.R
import com.pointlessapps.filman.data.model.LektorTiming
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
    private val synthesize: suspend (String, LektorVoice) -> ByteArray = { text, voice ->
        client.synthesize(text, voice)
    },
    private val playerFactory: () -> ExoPlayer = { ExoPlayer.Builder(context.applicationContext).build() },
    private val prepareAudio: (ExoPlayer, ByteArray) -> Unit = { player, audio ->
        val dataSource = DataSource.Factory { ByteArrayDataSource(audio) }
        val mediaItem = MediaItem.Builder().setUri("memory:///lektor.mp3")
            .setMimeType(MimeTypes.AUDIO_MPEG).build()
        player.setMediaSource(ProgressiveMediaSource.Factory(dataSource).createMediaSource(mediaItem))
        player.prepare()
    },
) {
    private val context = context.applicationContext
    private val cache = mutableMapOf<Int, ByteArray>()
    private var cues = emptyList<SubtitleCue>()
    private var positionMs = 0L
    private var timingOffsetMs = LektorTiming.DEFAULT_OFFSET_MS
    private var playing = false
    private var enabled = false
    private var voice = LektorVoice.EDGE
    private var lastSpoken = -1
    private var pending: Job? = null
    private var pendingCue: Int? = null
    private var activeCue: Int? = null
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
        voice = value
    }

    fun setTimingOffsetMs(value: Int) {
        timingOffsetMs = value
    }

    fun setCues(value: List<SubtitleCue>) {
        cancelPlayback()
        cache.clear()
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
            if (activeCue != null) onSpeechEnd()
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
            .filter { it.endMs + 300 >= adjustedPosition && it.index > lastSpoken }
            .take(PREFETCH_CUES)
            .toList()
        val wanted = window.mapTo(mutableSetOf()) { it.index }
        cache.keys.retainAll(wanted)
        if (pendingCue != null && pendingCue !in wanted) {
            pending?.cancel()
            pending = null
            pendingCue = null
        }
        val due = window.firstOrNull { LektorCueTiming.isDue(it, positionMs, timingOffsetMs) }
        if (playing && due != null && activeCue == null) {
            val audio = cache.remove(due.index)
            if (audio != null) {
                lastSpoken = due.index
                play(due, audio)
            }
        }
        if (pending?.isActive != true) {
            val next = window.firstOrNull { it.index !in cache && it.index > lastSpoken }
            if (next != null) {
                pendingCue = next.index
                pending = scope.launch {
                    try {
                        val audio = synthesize(next.text, voice)
                        if (enabled && next.endMs + 300 >= LektorCueTiming.position(positionMs, timingOffsetMs)) cache[next.index] = audio
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        reportError("Edge voice synthesis failed", e)
                        delay(RETRY_DELAY_MS)
                    }
                }
            }
        }
    }

    private fun play(cue: SubtitleCue, audio: ByteArray) {
        val player = speechPlayer ?: playerFactory().also { created ->
            created.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (activeCue != null && isPlaying) onSpeechStart()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) finishSpeech()
                }

                override fun onPlayerError(error: PlaybackException) {
                    finishSpeech()
                    reportError("Edge voice playback failed", error)
                }
            })
            speechPlayer = created
        }
        player.stop()
        finishSpeech()
        activeCue = cue.index
        prepareAudio(player, audio)
        player.play()
    }

    private fun finishSpeech() {
        if (activeCue != null) {
            activeCue = null
            onSpeechEnd()
        }
    }

    private fun cancelPlayback() {
        pending?.cancel()
        pending = null
        pendingCue = null
        speechPlayer?.stop()
        speechPlayer?.clearMediaItems()
        finishSpeech()
    }

    private fun reportError(message: String, error: Exception) {
        Log.e("Lektor", message, error)
        val now = android.os.SystemClock.elapsedRealtime()
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
        private const val RETRY_DELAY_MS = 5_000L
    }
}
