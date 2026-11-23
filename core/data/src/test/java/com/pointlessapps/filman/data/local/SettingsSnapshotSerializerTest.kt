package com.pointlessapps.filman.data.local

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.emptyPreferences
import com.pointlessapps.filman.data.model.AudioPreferenceMode
import com.pointlessapps.filman.data.model.ContentCategory
import com.pointlessapps.filman.data.model.ContentRule
import com.pointlessapps.filman.data.model.ContentRulesPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SettingsSnapshotSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `snapshot map serializes and deserializes content_rules accurately`() {
        val customRules = mapOf(
            ContentCategory.KIDS_FAMILY.key to ContentRule(
                audioMode = AudioPreferenceMode.LEKTOR_PREFERRED.value,
                subtitlesEnabled = true,
                subtitleLanguage = "pl",
            ),
            ContentCategory.ANIME.key to ContentRule(
                audioMode = AudioPreferenceMode.ORIGINAL_SUBTITLES.value,
                subtitlesEnabled = true,
                subtitleLanguage = "ja",
            ),
        )

        val jsonString = json.encodeToString(customRules)
        val snapshotMap = mapOf(
            "preferred_quality" to "1080p",
            "content_rules" to jsonString,
        )

        val mutablePrefs = emptyPreferences().toMutablePreferences()
        mutablePrefs.applySettingsSnapshotMap(snapshotMap)

        val restoredJson = mutablePrefs[SettingsPreferenceKeys.contentRulesKey]
        assertNotNull(restoredJson)

        val decodedRules = json.decodeFromString<Map<String, ContentRule>>(restoredJson!!)
        assertEquals(
            AudioPreferenceMode.LEKTOR_PREFERRED.value,
            decodedRules[ContentCategory.KIDS_FAMILY.key]?.audioMode,
        )
        assertEquals(
            true,
            decodedRules[ContentCategory.KIDS_FAMILY.key]?.subtitlesEnabled,
        )
        assertEquals(
            AudioPreferenceMode.ORIGINAL_SUBTITLES.value,
            decodedRules[ContentCategory.ANIME.key]?.audioMode,
        )
        assertEquals(
            "ja",
            decodedRules[ContentCategory.ANIME.key]?.subtitleLanguage,
        )
    }

    @Test
    fun `snapshot map serializes and deserializes app_language accurately`() {
        val snapshotMap = mapOf(
            "app_language" to "pl",
        )
        val mutablePrefs = emptyPreferences().toMutablePreferences()
        mutablePrefs.applySettingsSnapshotMap(snapshotMap)
        assertEquals("pl", mutablePrefs[SettingsPreferenceKeys.appLanguageKey])

        val serialized = mutablePrefs.toSettingsSnapshotMap()
        assertEquals("pl", serialized["app_language"])

        // Disallowed languages are rejected
        val invalidMap = mapOf("app_language" to "de")
        val mutablePrefs2 = emptyPreferences().toMutablePreferences()
        mutablePrefs2.applySettingsSnapshotMap(invalidMap)
        assertEquals(null, mutablePrefs2[SettingsPreferenceKeys.appLanguageKey])
    }
}
