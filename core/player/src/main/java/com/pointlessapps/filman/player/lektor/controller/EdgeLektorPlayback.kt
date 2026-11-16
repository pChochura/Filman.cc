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
import com.pointlessapps.filman.player.lektor.engine.EdgeTtsClient
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
    private val setVolume: (Float) -> Unit,
    private val nativePosition: () -> Long?,
    private val client: EdgeTtsClient = EdgeTtsClient(),
) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = mutableMapOf<Int, ByteArray>()
    private var cues = emptyList<SubtitleCue>()
    private var positionMs = 0L
    private var playing = false
    private var enabled = false
    private var lastSpoken = -1
    private var pending: Job? = null
    private var pendingCue: Int? = null
    private var activeCue: Int? = null
    private var lastErrorAt = 0L
    private var speechPlayer: ExoPlayer? = null

    init {
        scope.launch {
            while (isActive) {
                if (enabled && playing) {
                    positionMs = nativePosition() ?: positionMs
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

    fun setCues(value: List<SubtitleCue>) {
        cancelPlayback()
        cache.clear()
        cues = value
        lastSpoken = -1
    }

    fun update(position: Long, isPlaying: Boolean) {
        positionMs = position
        if (playing && !isPlaying) cancelPlayback()
        playing = isPlaying
    }

    fun seek(position: Long) {
        cancelPlayback()
        cache.clear()
        positionMs = position
        lastSpoken = cues.lastOrNull { it.endMs < position }?.index ?: -1
    }

    private fun tick() {
        val window = cues.asSequence()
            .filter { it.endMs + 300 >= positionMs && it.index > lastSpoken }
            .take(PREFETCH_CUES)
            .toList()
        val wanted = window.mapTo(mutableSetOf()) { it.index }
        cache.keys.retainAll(wanted)
        if (pendingCue != null && pendingCue !in wanted) {
            pending?.cancel()
            pending = null
            pendingCue = null
        }
        val due = window.firstOrNull { positionMs >= it.startMs && positionMs <= it.endMs + 300 }
        if (due != null && activeCue == null) {
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
                        val audio = client.synthesize(next.text)
                        if (enabled && playing && next.endMs + 300 >= positionMs) cache[next.index] = audio
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
        val player = speechPlayer ?: ExoPlayer.Builder(context).build().also { created ->
            created.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (activeCue != null && isPlaying) setVolume(0.2f)
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
        val dataSource = DataSource.Factory { ByteArrayDataSource(audio) }
        val mediaItem = MediaItem.Builder().setUri("memory:///lektor.mp3")
            .setMimeType(MimeTypes.AUDIO_MPEG).build()
        player.setMediaSource(ProgressiveMediaSource.Factory(dataSource).createMediaSource(mediaItem))
        player.prepare()
        player.play()
    }

    private fun finishSpeech() {
        if (activeCue != null) {
            activeCue = null
            setVolume(1.0f)
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
