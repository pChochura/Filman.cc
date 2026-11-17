package com.pointlessapps.filman.player.lektor.controller

internal object Mp3DurationEstimator {
    private val BITRATES_MPEG2_L3 = intArrayOf(
        0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0,
    )
    private val BITRATES_MPEG1_L3 = intArrayOf(
        0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0,
    )
    private val SAMPLE_RATES_MPEG1 = intArrayOf(44100, 48000, 32000)
    private val SAMPLE_RATES_MPEG2 = intArrayOf(22050, 24000, 16000)
    private val SAMPLE_RATES_MPEG25 = intArrayOf(11025, 12000, 8000)

    /**
     * Determines duration in milliseconds from MP3 byte array by inspecting frame headers.
     * Falls back to standard Edge TTS 48kbps bitrate estimation if headers cannot be found.
     */
    fun getDurationMs(audio: ByteArray): Long {
        if (audio.size < 4) return 0L

        var offset = 0
        val maxOffset = minOf(audio.size - 4, 4096)
        while (offset <= maxOffset) {
            val b0 = audio[offset].toInt() and 0xFF
            val b1 = audio[offset + 1].toInt() and 0xFF
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                val mpegVersion = (b1 shr 3) and 0x03
                val layer = (b1 shr 1) and 0x03
                if (layer == 1 && mpegVersion != 1) {
                    val b2 = audio[offset + 2].toInt() and 0xFF
                    val bitrateIdx = (b2 shr 4) and 0x0F
                    val sampleRateIdx = (b2 shr 2) and 0x03
                    val bitrates = if (mpegVersion == 3) BITRATES_MPEG1_L3 else BITRATES_MPEG2_L3
                    val sampleRates = when (mpegVersion) {
                        3 -> SAMPLE_RATES_MPEG1
                        2 -> SAMPLE_RATES_MPEG2
                        else -> SAMPLE_RATES_MPEG25
                    }
                    if (bitrateIdx in 1..14 && sampleRateIdx in 0..2) {
                        val bitrateKbps = bitrates[bitrateIdx]
                        val sampleRate = sampleRates[sampleRateIdx]
                        if (bitrateKbps > 0 && sampleRate > 0) {
                            val bitrateBps = bitrateKbps * 1000L
                            return (audio.size.toLong() * 8000L) / bitrateBps
                        }
                    }
                }
            }
            offset++
        }
        // Fallback: 48kbps mono MP3 (6 bytes per ms)
        return (audio.size.toLong() * 1000L) / 6000L
    }
}
