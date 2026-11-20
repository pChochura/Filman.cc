package com.pointlessapps.filman.data.local

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pointlessapps.filman.data.model.LektorDucking
import com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions
import com.pointlessapps.filman.data.model.LektorTiming

internal object SettingsPreferenceKeys {
    val preferredQualityKey = stringPreferencesKey("preferred_quality")
    val autoPlayNextKey = stringPreferencesKey("autoplay_next")
    val initialAppearanceTypeKey = stringPreferencesKey("initial_appearance_type")
    val initialAppearanceOffsetKey = stringPreferencesKey("initial_appearance_offset")
    val secondaryAppearanceTypeKey = stringPreferencesKey("secondary_appearance_type")
    val secondaryAppearanceOffsetKey = stringPreferencesKey("secondary_appearance_offset")
    val secondaryTimerAmountKey = stringPreferencesKey("secondary_timer_amount")
    val initialAppearancePercentageKey = stringPreferencesKey("initial_appearance_percentage")
    val secondaryAppearancePercentageKey = stringPreferencesKey("secondary_appearance_percentage")
    val subtitleStyleKey = stringPreferencesKey("subtitle_style")
    val screensaverEnabledKey = stringPreferencesKey("screensaver_enabled")
    val screensaverInactivityTimeKey = stringPreferencesKey("screensaver_inactivity_time")
    val screensaverSlideDurationKey = stringPreferencesKey("screensaver_slide_duration")
    val lektorEnabledKey = booleanPreferencesKey("lektor_enabled")
    val lektorHideSubtitlesKey = booleanPreferencesKey("lektor_hide_subtitles")
    val lektorVoiceKey = stringPreferencesKey("lektor_voice")
    val lektorDuckingVolumeKey = floatPreferencesKey("lektor_ducking_volume")
    val lektorTimingOffsetKey = intPreferencesKey("lektor_timing_offset_ms")
    val lektorEdgeRateKey = stringPreferencesKey("lektor_edge_rate")
    val lektorEdgePitchKey = stringPreferencesKey("lektor_edge_pitch")
    val lektorEdgeVolumeKey = stringPreferencesKey("lektor_edge_volume")
    val lektorEdgeCalmModeKey = booleanPreferencesKey("lektor_edge_calm_mode")
    val contentRulesKey = stringPreferencesKey("content_rules")
    val notificationsEnabledKey = stringPreferencesKey("notifications_enabled")
}

internal fun Preferences.toSettingsSnapshotMap(): Map<String, String> = buildMap {
    this@toSettingsSnapshotMap[SettingsPreferenceKeys.preferredQualityKey]?.let { put("preferred_quality", it) }
    this@toSettingsSnapshotMap[SettingsPreferenceKeys.autoPlayNextKey]?.let { put("autoplay_next", it) }
    this@toSettingsSnapshotMap[SettingsPreferenceKeys.subtitleStyleKey]?.let { put("subtitle_style", it) }
    this@toSettingsSnapshotMap[SettingsPreferenceKeys.contentRulesKey]?.let { put("content_rules", it) }
    this@toSettingsSnapshotMap[SettingsPreferenceKeys.screensaverEnabledKey]?.let { put("screensaver_enabled", it) }
    this@toSettingsSnapshotMap[SettingsPreferenceKeys.screensaverInactivityTimeKey]?.let {
        put("screensaver_inactivity_time", it)
    }
    this@toSettingsSnapshotMap[SettingsPreferenceKeys.screensaverSlideDurationKey]?.let {
        put("screensaver_slide_duration", it)
    }
    this@toSettingsSnapshotMap[SettingsPreferenceKeys.lektorEnabledKey]?.let { put("lektor_enabled", it.toString()) }
    this@toSettingsSnapshotMap[SettingsPreferenceKeys.lektorHideSubtitlesKey]?.let {
        put("lektor_hide_subtitles", it.toString())
    }
    this@toSettingsSnapshotMap[SettingsPreferenceKeys.lektorVoiceKey]?.let { put("lektor_voice", it) }
    this@toSettingsSnapshotMap[SettingsPreferenceKeys.lektorDuckingVolumeKey]?.let {
        put("lektor_ducking_volume", it.toString())
    }
    this@toSettingsSnapshotMap[SettingsPreferenceKeys.lektorTimingOffsetKey]?.let {
        put("lektor_timing_offset_ms", it.toString())
    }
    this@toSettingsSnapshotMap[SettingsPreferenceKeys.lektorEdgeRateKey]?.let { put("lektor_edge_rate", it) }
    this@toSettingsSnapshotMap[SettingsPreferenceKeys.lektorEdgePitchKey]?.let { put("lektor_edge_pitch", it) }
    this@toSettingsSnapshotMap[SettingsPreferenceKeys.lektorEdgeVolumeKey]?.let { put("lektor_edge_volume", it) }
    this@toSettingsSnapshotMap[SettingsPreferenceKeys.lektorEdgeCalmModeKey]?.let {
        put("lektor_edge_calm_mode", it.toString())
    }
}

internal fun MutablePreferences.applySettingsSnapshotMap(map: Map<String, String>) {
    map["preferred_quality"]?.let { this[SettingsPreferenceKeys.preferredQualityKey] = it }
    map["autoplay_next"]?.let { this[SettingsPreferenceKeys.autoPlayNextKey] = it }
    map["subtitle_style"]?.let { this[SettingsPreferenceKeys.subtitleStyleKey] = it }
    map["content_rules"]?.let { this[SettingsPreferenceKeys.contentRulesKey] = it }
    map["screensaver_enabled"]?.let { this[SettingsPreferenceKeys.screensaverEnabledKey] = it }
    map["screensaver_inactivity_time"]?.let { this[SettingsPreferenceKeys.screensaverInactivityTimeKey] = it }
    map["screensaver_slide_duration"]?.let { this[SettingsPreferenceKeys.screensaverSlideDurationKey] = it }
    map["lektor_enabled"]?.toBooleanStrictOrNull()?.let { this[SettingsPreferenceKeys.lektorEnabledKey] = it }
    map["lektor_hide_subtitles"]?.toBooleanStrictOrNull()?.let {
        this[SettingsPreferenceKeys.lektorHideSubtitlesKey] = it
    }
    map["lektor_voice"]?.let { this[SettingsPreferenceKeys.lektorVoiceKey] = it }
    map["lektor_ducking_volume"]?.toFloatOrNull()?.let {
        if (it in LektorDucking.levels) this[SettingsPreferenceKeys.lektorDuckingVolumeKey] = it
    }
    map["lektor_timing_offset_ms"]?.toIntOrNull()?.let {
        if (it in LektorTiming.offsetsMs) this[SettingsPreferenceKeys.lektorTimingOffsetKey] = it
    }
    map["lektor_edge_rate"]?.let {
        if (it in LektorEdgeVoiceOptions.rates) this[SettingsPreferenceKeys.lektorEdgeRateKey] = it
    }
    map["lektor_edge_pitch"]?.let {
        if (it in LektorEdgeVoiceOptions.pitches) this[SettingsPreferenceKeys.lektorEdgePitchKey] = it
    }
    map["lektor_edge_volume"]?.let {
        if (it in LektorEdgeVoiceOptions.volumes) this[SettingsPreferenceKeys.lektorEdgeVolumeKey] = it
    }
    map["lektor_edge_calm_mode"]?.toBooleanStrictOrNull()?.let {
        this[SettingsPreferenceKeys.lektorEdgeCalmModeKey] = it
    }
}
