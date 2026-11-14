package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.ui.core.isPlayerAdHost
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerAdPolicyTest {
    @Test
    fun `blocks known ad hosts and their subdomains`() {
        assertTrue(isPlayerAdHost("doubleclick.net"))
        assertTrue(isPlayerAdHost("ADS.DOUBLECLICK.NET."))
        assertTrue(isPlayerAdHost("amazon-adsystem.com"))
    }

    @Test
    fun `does not block host suffix lookalikes or player and challenge hosts`() {
        assertFalse(isPlayerAdHost("doubleclick.net.example.com"))
        assertFalse(isPlayerAdHost("notdoubleclick.net"))
        assertFalse(isPlayerAdHost("challenges.cloudflare.com"))
        assertFalse(isPlayerAdHost("cdn.example.com"))
        assertFalse(isPlayerAdHost(null))
    }
}
