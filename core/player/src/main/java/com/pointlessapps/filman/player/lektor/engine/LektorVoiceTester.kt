package com.pointlessapps.filman.player.lektor.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
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
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LektorVoiceTester(
    context: Context,
    private val modelManager: LektorModelManager = LektorModelManager(context),
    private val systemVoiceCatalog: SystemVoiceCatalog? = null,
    private val edgeTtsClient: EdgeTtsClient = EdgeTtsClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val synthesizeOnline: suspend (String, LektorVoice, String, String, String) -> ByteArray = { text, voice, rate, pitch, volume ->
        edgeTtsClient.synthesize(text, voice, rate, pitch, volume)
    },
    private val playerFactory: () -> ExoPlayer = { createPreviewPlayer(context.applicationContext) },
    private val prepareAudio: (ExoPlayer, ByteArray) -> Unit = ::preparePreviewAudio,
    private val engineFactory: (LektorModelPaths, LektorVoice) -> LektorTtsEngine? = { paths, voice ->
        LektorTtsEngine.create(paths, voice)
    },
    private val audioTrackFactory: (Int) -> AudioTrack? = { sampleRate ->
        createAudioTrack(sampleRate)
    },
) {
    private val context = context.applicationContext

    private var activeJob: Job? = null
    private var activeExoPlayer: ExoPlayer? = null
    private var activeAudioTrack: AudioTrack? = null
    private var activeTtsEngine: LektorTtsEngine? = null

    fun testVoice(
        voiceId: String,
        edgeRate: String = LektorEdgeVoiceOptions.DEFAULT_RATE,
        edgePitch: String = LektorEdgeVoiceOptions.DEFAULT_PITCH,
        edgeVolume: String = LektorEdgeVoiceOptions.DEFAULT_VOLUME,
        sampleText: String? = null,
    ) {
        stop()

        val text = sampleText ?: context.getString(R.string.lektor_system_tts_preview)
        val voice = LektorVoice.fromId(voiceId)

        if (voice.isSystem || LektorVoice.systemVoiceName(voiceId) != null) {
            val systemName = LektorVoice.systemVoiceName(voiceId)
            systemVoiceCatalog?.preview(systemName)
            return
        }

        if (voice.isOnline) {
            activeJob = scope.launch {
                try {
                    val audio = withContext(ioDispatcher) {
                        synthesizeOnline(text, voice, edgeRate, edgePitch, edgeVolume)
                    }
                    if (!isActive) return@launch

                    val player = playerFactory()
                    activeExoPlayer = player
                    player.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) {
                                cleanupPlayer(player)
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            cleanupPlayer(player)
                            reportError("Edge preview playback failed", error, R.string.lektor_edge_error)
                        }
                    })

                    prepareAudio(player, audio)
                    player.play()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    reportError("Edge preview synthesis failed", e, R.string.lektor_edge_error)
                }
            }
            return
        }

        if (voice.requiresModel) {
            if (!modelManager.isModelReady(voice)) {
                reportError("Local voice model not ready", null, R.string.lektor_preview_model_not_ready)
                return
            }

            activeJob = scope.launch(ioDispatcher) {
                var engine: LektorTtsEngine? = null
                var track: AudioTrack? = null
                try {
                    val paths = modelManager.getModelPaths(voice) ?: return@launch
                    engine = engineFactory(paths, voice) ?: return@launch
                    activeTtsEngine = engine

                    val generated = engine.generate(text) ?: return@launch
                    if (!isActive) return@launch

                    track = audioTrackFactory(generated.sampleRate) ?: return@launch
                    activeAudioTrack = track

                    track.play()
                    val shortSamples = ShortArray(generated.samples.size)
                    for (i in generated.samples.indices) {
                        shortSamples[i] = (generated.samples[i].coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
                    }

                    val startHead = track.playbackHeadPosition.toLong() and 0xffffffffL
                    var written = 0
                    while (written < shortSamples.size && isActive) {
                        val count = track.write(shortSamples, written, shortSamples.size - written, AudioTrack.WRITE_NON_BLOCKING)
                        if (count < 0) {
                            safeLogE("LektorVoiceTester", "AudioTrack write failed: $count")
                            break
                        }
                        written += count
                        if (count == 0) delay(20)
                    }

                    val maxDurationMs = (written * 1000L / (track.sampleRate.takeIf { it > 0 } ?: 22050)) + 500L
                    val startTime = System.currentTimeMillis()
                    while (isActive &&
                        ((track.playbackHeadPosition.toLong() and 0xffffffffL) - startHead and 0xffffffffL) < written &&
                        System.currentTimeMillis() - startTime < maxDurationMs
                    ) {
                        delay(20)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    safeLogE("LektorVoiceTester", "Local model preview error", e)
                } finally {
                    track?.let { cleanupTrack(it) }
                    engine?.let { cleanupEngine(it) }
                }
            }
        }
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null

        activeExoPlayer?.let { player ->
            activeExoPlayer = null
            try {
                player.stop()
                player.release()
            } catch (e: Throwable) {
                safeLogW("LektorVoiceTester", "Error stopping ExoPlayer", e)
            }
        }

        activeAudioTrack?.let { track ->
            activeAudioTrack = null
            try {
                track.pause()
                track.flush()
                track.release()
            } catch (e: Throwable) {
                safeLogW("LektorVoiceTester", "Error stopping AudioTrack", e)
            }
        }

        activeTtsEngine?.let { engine ->
            activeTtsEngine = null
            try {
                engine.release()
            } catch (e: Throwable) {
                safeLogW("LektorVoiceTester", "Error releasing engine", e)
            }
        }

        systemVoiceCatalog?.stop()
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun cleanupPlayer(player: ExoPlayer) {
        if (activeExoPlayer === player) {
            activeExoPlayer = null
        }
        try {
            player.stop()
            player.release()
        } catch (e: Throwable) {
            safeLogW("LektorVoiceTester", "Error releasing player", e)
        }
    }

    private fun cleanupTrack(track: AudioTrack) {
        if (activeAudioTrack === track) {
            activeAudioTrack = null
        }
        try {
            track.pause()
            track.flush()
            track.release()
        } catch (e: Throwable) {
            safeLogW("LektorVoiceTester", "Error releasing track", e)
        }
    }

    private fun cleanupEngine(engine: LektorTtsEngine) {
        if (activeTtsEngine === engine) {
            activeTtsEngine = null
        }
        try {
            engine.release()
        } catch (e: Throwable) {
            safeLogW("LektorVoiceTester", "Error releasing engine", e)
        }
    }

    private fun reportError(message: String, error: Throwable?, stringResId: Int) {
        safeLogE("LektorVoiceTester", message, error)
        scope.launch {
            try {
                Toast.makeText(context, stringResId, Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
                safeLogW("LektorVoiceTester", "Toast could not be shown", e)
            }
        }
    }

    private fun safeLogE(tag: String, message: String, error: Throwable? = null) {
        try {
            Log.e(tag, message, error)
        } catch (_: Throwable) {
            // Ignored in unit tests
        }
    }

    private fun safeLogW(tag: String, message: String, error: Throwable? = null) {
        try {
            Log.w(tag, message, error)
        } catch (_: Throwable) {
            // Ignored in unit tests
        }
    }

    companion object {
        private fun createPreviewPlayer(context: Context): ExoPlayer =
            ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(
                    Media3AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    false,
                )
            }

        @OptIn(UnstableApi::class)
        private fun preparePreviewAudio(player: ExoPlayer, audio: ByteArray) {
            val dataSource = DataSource.Factory { ByteArrayDataSource(audio) }
            val mediaItem = MediaItem.Builder().setUri("memory:///lektor_preview.mp3")
                .setMimeType(MimeTypes.AUDIO_MPEG).build()
            player.setMediaSource(ProgressiveMediaSource.Factory(dataSource).createMediaSource(mediaItem))
            player.prepare()
        }

        fun createAudioTrack(sampleRate: Int): AudioTrack? = try {
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

            AudioTrack(
                attributes,
                format,
                minBufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
        } catch (e: Throwable) {
            try {
                Log.e("LektorVoiceTester", "Could not create AudioTrack", e)
            } catch (_: Throwable) {}
            null
        }
    }
}
