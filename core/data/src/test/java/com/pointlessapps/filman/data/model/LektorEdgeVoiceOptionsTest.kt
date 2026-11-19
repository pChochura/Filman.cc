package com.pointlessapps.filman.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LektorEdgeVoiceOptionsTest {

    @Test
    fun `defaults and levels are well formed`() {
        assertEquals("+10%", LektorEdgeVoiceOptions.DEFAULT_RATE)
        assertEquals("+0Hz", LektorEdgeVoiceOptions.DEFAULT_PITCH)
        assertEquals("+0%", LektorEdgeVoiceOptions.DEFAULT_VOLUME)
        assertTrue(LektorEdgeVoiceOptions.DEFAULT_CALM_MODE)

        assertTrue(LektorEdgeVoiceOptions.DEFAULT_RATE in LektorEdgeVoiceOptions.rates)
        assertTrue("+5%" in LektorEdgeVoiceOptions.rates)
        assertTrue("+10%" in LektorEdgeVoiceOptions.rates)

        assertTrue(LektorEdgeVoiceOptions.DEFAULT_PITCH in LektorEdgeVoiceOptions.pitches)
        assertTrue("-6Hz" in LektorEdgeVoiceOptions.pitches)
        assertTrue("-3Hz" in LektorEdgeVoiceOptions.pitches)
        assertTrue("+3Hz" in LektorEdgeVoiceOptions.pitches)
        assertTrue("+6Hz" in LektorEdgeVoiceOptions.pitches)

        assertTrue(LektorEdgeVoiceOptions.DEFAULT_VOLUME in LektorEdgeVoiceOptions.volumes)
    }

    @Test
    fun `fromStored falls back to defaults for invalid inputs`() {
        assertEquals("+10%", LektorEdgeVoiceOptions.fromStoredRate(null))
        assertEquals("+10%", LektorEdgeVoiceOptions.fromStoredRate("invalid"))
        assertEquals("-10%", LektorEdgeVoiceOptions.fromStoredRate("-10%"))
        assertEquals("+5%", LektorEdgeVoiceOptions.fromStoredRate("+5%"))

        assertEquals("+0Hz", LektorEdgeVoiceOptions.fromStoredPitch(null))
        assertEquals("+0Hz", LektorEdgeVoiceOptions.fromStoredPitch("xyz"))
        assertEquals("-20Hz", LektorEdgeVoiceOptions.fromStoredPitch("-20Hz"))
        assertEquals("-6Hz", LektorEdgeVoiceOptions.fromStoredPitch("-6Hz"))
        assertEquals("+3Hz", LektorEdgeVoiceOptions.fromStoredPitch("+3Hz"))

        assertEquals("+0%", LektorEdgeVoiceOptions.fromStoredVolume(null))
        assertEquals("+0%", LektorEdgeVoiceOptions.fromStoredVolume("loud"))
        assertEquals("+30%", LektorEdgeVoiceOptions.fromStoredVolume("+30%"))
        assertEquals("-15%", LektorEdgeVoiceOptions.fromStoredVolume("-15%"))
        assertEquals("-25%", LektorEdgeVoiceOptions.fromStoredVolume("-25%"))
        assertEquals("-35%", LektorEdgeVoiceOptions.fromStoredVolume("-35%"))
        assertEquals("-50%", LektorEdgeVoiceOptions.fromStoredVolume("-50%"))
    }

    @Test
    fun `rate parsing and formatting clamp and preserve sign`() {
        assertEquals(10, LektorEdgeVoiceOptions.parseRatePercent("+10%"))
        assertEquals(-20, LektorEdgeVoiceOptions.parseRatePercent("-20%"))
        assertEquals(0, LektorEdgeVoiceOptions.parseRatePercent("+0%"))
        assertEquals(10, LektorEdgeVoiceOptions.parseRatePercent("bad"))

        assertEquals("+10%", LektorEdgeVoiceOptions.formatRatePercent(10))
        assertEquals("+0%", LektorEdgeVoiceOptions.formatRatePercent(0))
        assertEquals("-5%", LektorEdgeVoiceOptions.formatRatePercent(-5))
        assertEquals("+45%", LektorEdgeVoiceOptions.formatRatePercent(90))
        assertEquals("-30%", LektorEdgeVoiceOptions.formatRatePercent(-50))
    }
}
