package com.pointlessapps.filman.data.api

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class SubtitleTextDecoderTest {

    private val polishSample = "Zażółć gęślą jaźń — Świetnie, przyszłość!"

    @Test
    fun `decodes empty byte array to empty string`() {
        assertEquals("", SubtitleTextDecoder.decode(ByteArray(0)))
    }

    @Test
    fun `decodes UTF-8 without and with BOM`() {
        val utf8Bytes = polishSample.toByteArray(Charsets.UTF_8)
        assertEquals(polishSample, SubtitleTextDecoder.decode(utf8Bytes))

        val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + utf8Bytes
        assertEquals(polishSample, SubtitleTextDecoder.decode(bomBytes))
    }

    @Test
    fun `decodes UTF-16LE and UTF-16BE BOMs`() {
        val leBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + polishSample.toByteArray(Charsets.UTF_16LE)
        assertEquals(polishSample, SubtitleTextDecoder.decode(leBytes))

        val beBytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + polishSample.toByteArray(Charsets.UTF_16BE)
        assertEquals(polishSample, SubtitleTextDecoder.decode(beBytes))
    }

    @Test
    fun `decodes Windows-1250 Polish subtitles without charset header`() {
        val cp1250 = Charset.forName("windows-1250")
        val text = "Świetnie, przyszłość zależy od źródła! Źle się stało, że poszła."
        val bytes = text.toByteArray(cp1250)

        assertEquals(text, SubtitleTextDecoder.decode(bytes))
    }

    @Test
    fun `decodes ISO-8859-2 Polish subtitles without charset header`() {
        val iso88592 = Charset.forName("ISO-8859-2")
        val text = "Światło i źródło: Ąćęłńóśźż, ąćęłńóśźż."
        val bytes = text.toByteArray(iso88592)

        assertEquals(text, SubtitleTextDecoder.decode(bytes))
    }

    @Test
    fun `honors explicit declared charset when valid`() {
        val iso88592 = Charset.forName("ISO-8859-2")
        val text = "Polskie znaki: ąśćźżółę"
        val bytes = text.toByteArray(iso88592)

        assertEquals(text, SubtitleTextDecoder.decode(bytes, declaredCharset = iso88592))
    }
}
