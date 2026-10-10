package com.pointlessapps.filman.lite

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerSettingsTest {
    @Test
    fun normalizeAddsSchemeAndStripsTrailingSlash() {
        assertEquals("http://1.2.3.4:8080", ServerSettings.normalize(" 1.2.3.4:8080/ "))
        assertEquals("https://x.example.com", ServerSettings.normalize("https://x.example.com//"))
        assertEquals("", ServerSettings.normalize("  "))
    }
}
