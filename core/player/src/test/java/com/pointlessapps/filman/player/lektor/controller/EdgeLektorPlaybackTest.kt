package com.pointlessapps.filman.player.lektor.controller

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.pointlessapps.filman.player.lektor.model.SubtitleCue
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EdgeLektorPlaybackTest {
    @Test
    fun `pause preserves active playback and pending synthesis and resumes immediately`() = runTest {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        val player = mockk<ExoPlayer>(relaxed = true)
        val listener = slot<Player.Listener>()
        every { player.addListener(capture(listener)) } returns Unit
        val requests = mutableListOf<String>()
        val secondAudio = CompletableDeferred<ByteArray>()
        val prepared = mutableListOf<ByteArray>()
        var ended = 0
        val playback = EdgeLektorPlayback(
            context = context,
            onSpeechStart = {},
            onSpeechEnd = { ended++ },
            nativePosition = { null },
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            synthesize = { text, _ ->
                requests.add(text)
                if (text == "second") secondAudio.await() else byteArrayOf(1)
            },
            playerFactory = { player },
            prepareAudio = { _, audio -> prepared.add(audio) },
        )
        playback.setCues(listOf(
            SubtitleCue(0, 1_000, 4_000, "first"),
            SubtitleCue(1, 5_000, 8_000, "second"),
        ))
        playback.setEnabled(true)
        playback.update(1_000, true)
        runCurrent()
        advanceTimeBy(31)
        runCurrent()
        assertEquals(1, prepared.size)
        assertEquals(listOf("first", "second"), requests)
        verify(exactly = 1) { player.play() }

        playback.update(1_000, false)
        verify(exactly = 1) { player.pause() }
        assertEquals(1, ended)
        secondAudio.complete(byteArrayOf(2))
        runCurrent()

        playback.update(1_000, true)
        verify(exactly = 2) { player.play() }
        assertEquals(1, prepared.size)
        assertEquals(listOf("first", "second"), requests)

        listener.captured.onPlaybackStateChanged(Player.STATE_ENDED)
        playback.update(5_000, true)
        advanceTimeBy(31)
        runCurrent()
        assertEquals(2, prepared.size)
        assertEquals(2, prepared[1][0].toInt())
        assertEquals(listOf("first", "second"), requests)

        playback.seek(1_000)
        verify(exactly = 3) { player.stop() }
        playback.release()
    }

    @Test
    fun `paused synthesis is retained for immediate playback on resume`() = runTest {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        val player = mockk<ExoPlayer>(relaxed = true)
        val audio = CompletableDeferred<ByteArray>()
        var prepared = 0
        val playback = EdgeLektorPlayback(
            context = context,
            onSpeechStart = {},
            onSpeechEnd = {},
            nativePosition = { null },
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            synthesize = { _, _ -> audio.await() },
            playerFactory = { player },
            prepareAudio = { _, _ -> prepared++ },
        )
        playback.setCues(listOf(SubtitleCue(0, 1_000, 4_000, "first")))
        playback.setEnabled(true)
        playback.update(1_000, true)
        runCurrent()
        playback.update(1_000, false)
        audio.complete(byteArrayOf(1))
        runCurrent()
        assertEquals(0, prepared)

        playback.update(1_000, true)
        assertEquals(1, prepared)
        verify(exactly = 1) { player.play() }
        playback.release()
    }

    @Test
    fun `seek while paused cancels the old request and prepares the new cue`() = runTest {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        val player = mockk<ExoPlayer>(relaxed = true)
        val oldAudio = CompletableDeferred<ByteArray>()
        val requests = mutableListOf<String>()
        val prepared = mutableListOf<Int>()
        val playback = EdgeLektorPlayback(
            context = context,
            onSpeechStart = {},
            onSpeechEnd = {},
            nativePosition = { null },
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            synthesize = { text, _ ->
                requests.add(text)
                if (text == "old") oldAudio.await() else byteArrayOf(2)
            },
            playerFactory = { player },
            prepareAudio = { _, audio -> prepared.add(audio[0].toInt()) },
        )
        playback.setCues(listOf(
            SubtitleCue(0, 1_000, 4_000, "old"),
            SubtitleCue(1, 10_000, 13_000, "new"),
        ))
        playback.setEnabled(true)
        playback.update(1_000, true)
        runCurrent()
        playback.update(1_000, false)
        playback.seek(10_000)
        oldAudio.complete(byteArrayOf(1))
        advanceTimeBy(31)
        runCurrent()
        assertEquals(listOf("old", "new"), requests)
        assertEquals(emptyList<Int>(), prepared)

        playback.update(10_000, true)
        assertEquals(listOf(2), prepared)
        playback.release()
    }
}
