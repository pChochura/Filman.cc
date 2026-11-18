package com.pointlessapps.filman.player.lektor.controller

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.delay

internal class LektorPcmAudioPlayer {
    @Volatile
    private var audioTrack: AudioTrack? = null

    private var currentSampleRate: Int = 22050

    @Volatile
    private var audioGeneration: Int = 0

    fun isReady(): Boolean = audioTrack != null

    fun setup(sampleRate: Int) {
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

    fun play() {
        audioTrack?.play()
    }

    fun pause() {
        audioTrack?.pause()
    }

    fun stop() {
        audioGeneration++
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun playSamples(
        samples: FloatArray,
        isPlaying: () -> Boolean,
    ): Boolean {
        val track = audioTrack ?: return false
        val generation = audioGeneration
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            try {
                track.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val shortSamples = floatTo16BitPcm(samples)
        val startHead = track.playbackHeadPosition.toLong() and 0xffffffffL
        var written = 0
        while (written < shortSamples.size && generation == audioGeneration) {
            if (!isPlaying()) {
                delay(20)
                continue
            }
            val count = track.write(shortSamples, written, shortSamples.size - written, AudioTrack.WRITE_NON_BLOCKING)
            if (count < 0) {
                android.util.Log.e("Lektor", "AudioTrack write failed: $count")
                break
            }
            written += count
            if (count == 0) delay(20)
        }

        while (generation == audioGeneration &&
            ((track.playbackHeadPosition.toLong() and 0xffffffffL) - startHead and 0xffffffffL) < written
        ) {
            delay(20)
        }
        return generation == audioGeneration
    }

    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
    }

    private fun floatTo16BitPcm(floatSamples: FloatArray): ShortArray {
        val shorts = ShortArray(floatSamples.size)
        for (i in floatSamples.indices) {
            val s = (floatSamples[i].coerceIn(-1.0f, 1.0f) * 32767f).toInt()
            shorts[i] = s.toShort()
        }
        return shorts
    }
}
