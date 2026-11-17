package com.pointlessapps.filman.player.lektor.engine

import android.content.Context
import android.media.AudioTrack
import androidx.media3.exoplayer.ExoPlayer
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.pointlessapps.filman.core.player.R
import com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LektorVoiceTesterTest {

    private val context = mockk<Context>(relaxed = true) {
        every { applicationContext } returns this
        every { getString(R.string.lektor_system_tts_preview) } returns "Dzień dobry, to jest próbka polskiego głosu."
    }
    private val modelManager = mockk<LektorModelManager>(relaxed = true)
    private val systemVoiceCatalog = mockk<SystemVoiceCatalog>(relaxed = true)

    @Test
    fun `testVoice with system voice delegates to SystemVoiceCatalog preview`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val tester = LektorVoiceTester(
            context = context,
            modelManager = modelManager,
            systemVoiceCatalog = systemVoiceCatalog,
            scope = testScope,
            ioDispatcher = testDispatcher,
        )

        tester.testVoice("system:pl-PL-voice-a")
        testScheduler.runCurrent()

        verify { systemVoiceCatalog.preview("pl-PL-voice-a") }
    }

    @Test
    fun `testVoice with online voice synthesizes and prepares player`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val synthesizedCalls = mutableListOf<String>()
        val mockPlayer = mockk<ExoPlayer>(relaxed = true)
        var preparedAudio: ByteArray? = null

        val tester = LektorVoiceTester(
            context = context,
            modelManager = modelManager,
            systemVoiceCatalog = systemVoiceCatalog,
            scope = testScope,
            ioDispatcher = testDispatcher,
            synthesizeOnline = { text, voice, rate, pitch, volume ->
                synthesizedCalls.add("${voice.id}:$rate:$pitch:$volume:$text")
                byteArrayOf(1, 2, 3, 4)
            },
            playerFactory = { mockPlayer },
            prepareAudio = { player, audio ->
                preparedAudio = audio
            },
        )

        tester.testVoice(
            voiceId = "edge_marek",
            edgeRate = "+25%",
            edgePitch = "-10Hz",
            edgeVolume = "+15%",
            sampleText = "Test online sample",
        )
        testScheduler.runCurrent()

        assertEquals(listOf("edge_marek:+25%:-10Hz:+15%:Test online sample"), synthesizedCalls)
        assertEquals(listOf<Byte>(1, 2, 3, 4), preparedAudio?.toList())
        verify { mockPlayer.play() }
    }

    @Test
    fun `testVoice with local model when not downloaded does not generate or play`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        every { modelManager.isModelReady(LektorVoice.MESKI) } returns false

        var engineCreated = false
        val tester = LektorVoiceTester(
            context = context,
            modelManager = modelManager,
            systemVoiceCatalog = systemVoiceCatalog,
            scope = testScope,
            ioDispatcher = testDispatcher,
            engineFactory = { _, _ ->
                engineCreated = true
                null
            },
        )

        tester.testVoice("meski")
        testScheduler.runCurrent()

        assertTrue(!engineCreated)
    }

    @Test
    fun `testVoice with local model when downloaded synthesizes and plays audio`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val paths = LektorModelPaths("model", "tokens", "data")
        every { modelManager.isModelReady(LektorVoice.DARKMAN) } returns true
        every { modelManager.getModelPaths(LektorVoice.DARKMAN) } returns paths

        val mockEngine = mockk<LektorTtsEngine>(relaxed = true)
        val mockAudio = mockk<GeneratedAudio>(relaxed = true) {
            every { samples } returns floatArrayOf(0.1f, -0.2f, 0.5f)
            every { sampleRate } returns 22050
        }
        coEvery { mockEngine.generate(any(), any()) } returns mockAudio

        var headPos = 0
        val mockTrack = mockk<AudioTrack>(relaxed = true) {
            every { playbackHeadPosition } answers { headPos }
            every { write(any<ShortArray>(), any(), any(), any()) } answers {
                val size = arg<Int>(2)
                headPos += size
                size
            }
        }

        val tester = LektorVoiceTester(
            context = context,
            modelManager = modelManager,
            systemVoiceCatalog = systemVoiceCatalog,
            scope = testScope,
            ioDispatcher = testDispatcher,
            engineFactory = { _, _ -> mockEngine },
            audioTrackFactory = { mockTrack },
        )

        tester.testVoice("darkman", sampleText = "Cześć")
        testScheduler.runCurrent()

        coVerify { mockEngine.generate("Cześć") }
        verify { mockTrack.play() }
        verify { mockTrack.write(any<ShortArray>(), 0, 3, AudioTrack.WRITE_NON_BLOCKING) }
    }

    @Test
    fun `stop stops system voice catalog and active players`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val mockPlayer = mockk<ExoPlayer>(relaxed = true)
        val tester = LektorVoiceTester(
            context = context,
            modelManager = modelManager,
            systemVoiceCatalog = systemVoiceCatalog,
            scope = testScope,
            ioDispatcher = testDispatcher,
            synthesizeOnline = { _, _, _, _, _ -> byteArrayOf(1, 2, 3) },
            playerFactory = { mockPlayer },
            prepareAudio = { _, _ -> },
        )

        tester.testVoice("edge_zofia")
        testScheduler.runCurrent()

        tester.stop()
        verify { mockPlayer.stop() }
        verify { mockPlayer.release() }
        verify { systemVoiceCatalog.stop() }
    }
}
