package com.pointlessapps.filman.player.lektor.controller

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.pointlessapps.filman.player.lektor.engine.EdgeSynthesisResult
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
import org.junit.Assert.assertTrue
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

    @Test
    fun `playback speed is adjusted dynamically on player to fit cue window and prevent cut-off`() = runTest {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        val player = mockk<ExoPlayer>(relaxed = true)
        val speedSlot = slot<Float>()
        every { player.setPlaybackSpeed(capture(speedSlot)) } returns Unit

        // 18,000 bytes @ 48kbps = 3,000ms audio duration
        // Cue window is 2,000ms (1,000 to 3,000). Next cue at 3,200 (gap 200ms -> window 2,020ms)
        // 3,000ms audio in 2,020ms window requires ~1.45x speed
        val audioData = ByteArray(18_000)
        val playback = EdgeLektorPlayback(
            context = context,
            onSpeechStart = {},
            onSpeechEnd = {},
            nativePosition = { null },
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            synthesize = { _, _ -> audioData },
            playerFactory = { player },
            prepareAudio = { _, _ -> },
        )
        playback.setCues(listOf(
            SubtitleCue(0, 1_000, 3_000, "tight cue"),
            SubtitleCue(1, 3_200, 5_000, "next cue"),
        ))
        playback.setEnabled(true)
        playback.update(1_000, true)
        runCurrent()
        advanceTimeBy(31)
        runCurrent()

        verify(atLeast = 1) { player.setPlaybackSpeed(any()) }
        assertTrue("Speed should be sped up to prevent cut-off: ${speedSlot.captured}", speedSlot.captured > 1.25f)
        playback.release()
    }

    @Test
    fun `native prosody rate adapts before synthesis and boundary metadata prevents unnecessary ExoPlayer speedup`() =
        runTest {
            val context = mockk<Context>()
            every { context.applicationContext } returns context
            val player = mockk<ExoPlayer>(relaxed = true)
            val speedSlot = slot<Float>()
            every { player.setPlaybackSpeed(capture(speedSlot)) } returns Unit
            val capturedRates = mutableListOf<String>()

            // Tight cue (~70 chars) -> native rate should speed up beyond default +10%
            // Inflated MP3 bytes (30,000 bytes = 5000ms) with actual WordBoundary speech duration = 1500ms
            val tightText = "To jest bardzo długie zdanie, które powinno przyspieszyć przed syntezą."
            val inflatedAudio = ByteArray(30_000)
            val playback = EdgeLektorPlayback(
                context = context,
                onSpeechStart = {},
                onSpeechEnd = {},
                nativePosition = { null },
                scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
                synthesize = { _, _, rate, _, _ ->
                    capturedRates.add(rate)
                    EdgeSynthesisResult(audio = inflatedAudio, speechStartMs = 150L, speechEndMs = 1_650L)
                },
                playerFactory = { player },
                prepareAudio = { _, _ -> },
            )
            try {
                playback.setTimingOffsetMs(0)
                playback.setCues(
                    listOf(
                        SubtitleCue(0, 1_000, 3_000, tightText),
                        SubtitleCue(1, 3_200, 5_000, "Krótko."),
                    ),
                )
                playback.setEnabled(true)
                playback.update(1_000, true)
                runCurrent()
                advanceTimeBy(31)
                runCurrent()

                assertTrue("Expected native rate > +10%, got $capturedRates", capturedRates.first() != "+10%")
                assertEquals(1.0f, speedSlot.captured)
            } finally {
                playback.release()
            }
        }

    @Test
    fun `ducking hysteresis retains ducking across rapid back-to-back cues and restores on longer gaps`() =
        runTest {
            val context = mockk<Context>()
            every { context.applicationContext } returns context
            val player = mockk<ExoPlayer>(relaxed = true)
            val listener = slot<Player.Listener>()
            every { player.addListener(capture(listener)) } returns Unit
            var duckCount = 0
            var restoreCount = 0
            var currentPos = 1_000L

            val playback = EdgeLektorPlayback(
                context = context,
                onSpeechStart = { duckCount++ },
                onSpeechEnd = { restoreCount++ },
                nativePosition = { currentPos },
                scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
                synthesize = { _, _, _, _, _ -> EdgeSynthesisResult(ByteArray(6_000), 50L, 950L) },
                playerFactory = { player },
                prepareAudio = { _, _ -> },
            )
            try {
                playback.setTimingOffsetMs(0)
                playback.setCues(
                    listOf(
                        SubtitleCue(0, 1_000, 2_000, "Pierwsze"),
                        SubtitleCue(1, 2_200, 3_200, "Drugie"),
                        SubtitleCue(2, 5_000, 6_000, "Trzecie"),
                    ),
                )
                playback.setEnabled(true)
                playback.update(1_000, true)
                runCurrent()
                advanceTimeBy(31)
                runCurrent()

                listener.captured.onIsPlayingChanged(true)
                assertEquals(1, duckCount)
                assertEquals(0, restoreCount)

                // Cue 0 ends at 2000ms; cue 1 starts at 2200ms (200ms gap <= 400ms hysteresis) -> stays ducked!
                currentPos = 2_000L
                listener.captured.onPlaybackStateChanged(Player.STATE_ENDED)
                assertEquals(0, restoreCount)

                // Cue 1 plays and ends at 3200ms; cue 2 starts at 5000ms (1800ms > 400ms) -> restores!
                currentPos = 2_200L
                advanceTimeBy(31)
                runCurrent()
                currentPos = 3_200L
                listener.captured.onPlaybackStateChanged(Player.STATE_ENDED)
                assertEquals(1, restoreCount)
            } finally {
                playback.release()
            }
        }
}
