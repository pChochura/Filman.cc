package com.pointlessapps.filman.player.lektor.engine

import com.pointlessapps.filman.player.lektor.model.LektorVoice
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class EdgeTtsClientTest {
    @Test
    fun `GEC v2 matches upstream vectors and five minute windows`() {
        assertEquals(
            "2CD23A2CA0B2595A-F3C846C370E05F9CC83ACC5F995C5F5D87A4D0F14CE8936A0F902E64099E38A2",
            EdgeTtsClient.gecToken(1_700_000_000L),
        )
        assertEquals(
            "0919602B5750900A-EC9DD394F23CB5635CF62A0E8E24136DCD32B26D10C543B10082E482D7516397",
            EdgeTtsClient.gecToken(1_700_000_100L),
        )
        assertEquals(
            "CF8CB0C21D74BFB9-B90BC2515A710BB13F37D3AA698CCB444A9F6F4AB07FF812E00FA908BF0C8DB1",
            EdgeTtsClient.gecToken(1_794_859_592L),
        )
        assertEquals(EdgeTtsClient.gecToken(1_700_000_000L), EdgeTtsClient.gecToken(1_700_000_099L))
        assertTrue(EdgeTtsClient.gecToken(1_700_000_000L) != EdgeTtsClient.gecToken(1_700_000_100L))
    }

    @Test
    fun `SSML escapes text and excludes invalid XML controls`() {
        val ssml = EdgeTtsClient.ssmlMessage("id", "timestamp", "A & <B> \"C\" 'D'\u0001")
        assertTrue(ssml.contains("Path:ssml\r\n\r\n"))
        assertTrue(ssml.contains("<voice name=\"pl-PL-MarekNeural\">"))
        assertTrue(ssml.contains("A &amp; &lt;B&gt; &quot;C&quot; &apos;D&apos; "))
        assertTrue(EdgeTtsClient.configMessage("t").contains("audio-24khz-48kbitrate-mono-mp3"))
    }

    @Test
    fun `selected neural voice is used in SSML`() {
        assertTrue(
            EdgeTtsClient.ssmlMessage("id", "timestamp", "Dzień dobry", LektorVoice.EDGE_ZOFIA)
                .contains("<voice name=\"pl-PL-ZofiaNeural\">"),
        )
        assertEquals(LektorVoice.EDGE, LektorVoice.fromId("edge_marek"))
        assertEquals(LektorVoice.EDGE_ZOFIA, LektorVoice.fromId("edge_zofia"))
        assertTrue(LektorVoice.EDGE_ZOFIA.isOnline)
        assertTrue(!LektorVoice.EDGE_ZOFIA.requiresModel)

        val onlineVoices = LektorVoice.entries.filter { it.isOnline }
        assertEquals(8, onlineVoices.size)
        onlineVoices.forEach { voice ->
            assertTrue(voice.edgeVoiceName!!.isNotBlank())
            val ssml = EdgeTtsClient.ssmlMessage("id", "timestamp", "Dzień dobry", voice)
            assertTrue(ssml.contains("<voice name=\"${voice.edgeVoiceName}\">"))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `offline voices cannot be sent to Edge`() {
        EdgeTtsClient.ssmlMessage("id", "timestamp", "test", LektorVoice.MESKI)
    }

    @Test
    fun `binary frame strips unsigned length and headers`() {
        val header = "Path:audio\r\nContent-Type:audio/mpeg\r\n".toByteArray()
        val payload = byteArrayOf(1, 2, 3)
        val frame = byteArrayOf((header.size shr 8).toByte(), header.size.toByte()) + header + payload
        assertArrayEquals(payload, EdgeTtsClient.parseAudioFrame(frame))
        val terminator = "Path:audio\r\n".toByteArray()
        assertArrayEquals(byteArrayOf(), EdgeTtsClient.parseAudioFrame(byteArrayOf(0, terminator.size.toByte()) + terminator))
    }

    @Test
    fun `binary header length is unsigned`() {
        val header = ("Path:audio\r\nContent-Type:audio/mpeg\r\nPadding:" + "x".repeat(32_768) + "\r\n").toByteArray()
        val frame = byteArrayOf((header.size shr 8).toByte(), header.size.toByte()) + header + byteArrayOf(42)
        assertArrayEquals(byteArrayOf(42), EdgeTtsClient.parseAudioFrame(frame))
    }

    @Test(expected = IOException::class)
    fun `truncated frame fails explicitly`() {
        EdgeTtsClient.parseAudioFrame(byteArrayOf(0, 20, 1))
    }

    @Test(expected = IOException::class)
    fun `audio without content type fails explicitly`() {
        val header = "Path:audio\r\n".toByteArray()
        EdgeTtsClient.parseAudioFrame(byteArrayOf(0, header.size.toByte()) + header + byteArrayOf(1))
    }

    @Test(expected = IOException::class)
    fun `unexpected binary path fails explicitly`() {
        val header = "Path:metadata\r\nContent-Type:audio/mpeg\r\n".toByteArray()
        EdgeTtsClient.parseAudioFrame(byteArrayOf(0, header.size.toByte()) + header + byteArrayOf(1))
    }
}
