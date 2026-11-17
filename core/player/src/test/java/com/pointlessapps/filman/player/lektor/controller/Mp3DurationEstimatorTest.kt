package com.pointlessapps.filman.player.lektor.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp3DurationEstimatorTest {

    @Test
    fun `empty or short byte arrays return 0ms safely`() {
        assertEquals(0L, Mp3DurationEstimator.getDurationMs(byteArrayOf()))
        assertEquals(0L, Mp3DurationEstimator.getDurationMs(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `fallback 48kbps estimation calculates 6 bytes per ms`() {
        // 6,000 bytes @ 48kbps = 1000ms
        val dummy6000 = ByteArray(6000)
        assertEquals(1000L, Mp3DurationEstimator.getDurationMs(dummy6000))

        // 18,000 bytes @ 48kbps = 3000ms
        val dummy18000 = ByteArray(18000)
        assertEquals(3000L, Mp3DurationEstimator.getDurationMs(dummy18000))
    }

    @Test
    fun `parses standard MPEG2 Layer III frame header with 48kbps bitrate`() {
        // Header:
        // Byte 0: 0xFF
        // Byte 1: 0xF2 (MPEG-2 Layer III: 1111 0010 -> sync=111, mpegVersion=2, layer=1, errorProtection=0)
        // Byte 2: 0x64 (bitrate index 6 = 48kbps in MPEG-2 L3, sample rate index 1 = 24000Hz)
        // Byte 3: 0x00
        val frame = ByteArray(12000)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xF2.toByte()
        frame[2] = 0x64.toByte()
        frame[3] = 0x00.toByte()

        val duration = Mp3DurationEstimator.getDurationMs(frame)
        // 12000 bytes * 8000 / 48000 = 2000ms
        assertEquals(2000L, duration)
    }

    @Test
    fun `parses MPEG1 Layer III frame header with 128kbps bitrate`() {
        // Byte 0: 0xFF
        // Byte 1: 0xFB (MPEG-1 Layer III: 1111 1011 -> sync=111, mpegVersion=3, layer=1, errorProtection=1)
        // Byte 2: 0x90 (bitrate index 9 = 128kbps, sample rate index 0 = 44100Hz)
        // Byte 3: 0x00
        val frame = ByteArray(16000)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFB.toByte()
        frame[2] = 0x90.toByte()
        frame[3] = 0x00.toByte()

        val duration = Mp3DurationEstimator.getDurationMs(frame)
        // 16000 bytes * 8000 / 128000 = 1000ms
        assertEquals(1000L, duration)
    }
}
