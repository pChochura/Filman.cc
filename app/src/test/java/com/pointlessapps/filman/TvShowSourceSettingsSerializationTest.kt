package com.pointlessapps.filman

import com.pointlessapps.filman.data.model.TvShowSourceSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TvShowSourceSettingsSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testSerializationAndDeserialization() {
        val settings =
            TvShowSourceSettings(
                serverName = "vidoza",
                version = "Dubbing",
                quality = "1080p",
                sourceWebsite = "filman.cc",
                subtitlesEnabled = true,
                subtitleLanguage = "pl",
                subtitleLabel = "Polski",
                playbackSpeed = 1.25f,
                aspectRatioMode = 1,
            )

        val encoded = json.encodeToString(settings)
        val decoded = json.decodeFromString<TvShowSourceSettings>(encoded)

        assertEquals("vidoza", decoded.serverName)
        assertEquals("Dubbing", decoded.version)
        assertEquals("1080p", decoded.quality)
        assertEquals("filman.cc", decoded.sourceWebsite)
        assertEquals(true, decoded.subtitlesEnabled)
        assertEquals("pl", decoded.subtitleLanguage)
        assertEquals("Polski", decoded.subtitleLabel)
        assertEquals(1.25f, decoded.playbackSpeed)
        assertEquals(1, decoded.aspectRatioMode)
    }

    @Test
    fun testMapSerializationAndIsolation() {
        val map =
            mapOf(
                "/serial-online/36/breaking-bad" to
                    TvShowSourceSettings(
                        serverName = "vidoza",
                        version = "Dubbing",
                        subtitlesEnabled = false,
                    ),
                "/serial-online/500/better-call-saul" to
                    TvShowSourceSettings(
                        serverName = "upstream",
                        version = "Lektor",
                        subtitlesEnabled = true,
                        subtitleLanguage = "pl",
                    ),
            )

        val encoded = json.encodeToString(map)
        val decoded = json.decodeFromString<Map<String, TvShowSourceSettings>>(encoded)

        assertEquals(2, decoded.size)

        val bbSettings = decoded["/serial-online/36/breaking-bad"]
        assertNotNull(bbSettings)
        assertEquals("vidoza", bbSettings?.serverName)
        assertEquals("Dubbing", bbSettings?.version)
        assertEquals(false, bbSettings?.subtitlesEnabled)

        val bcsSettings = decoded["/serial-online/500/better-call-saul"]
        assertNotNull(bcsSettings)
        assertEquals("upstream", bcsSettings?.serverName)
        assertEquals("Lektor", bcsSettings?.version)
        assertEquals(true, bcsSettings?.subtitlesEnabled)

        // Non-existent show returns null
        assertNull(decoded["/serial-online/999/unknown"])
    }
}
