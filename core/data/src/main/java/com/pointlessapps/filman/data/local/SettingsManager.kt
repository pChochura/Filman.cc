package com.pointlessapps.filman.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.SHOW_IN_OVERLAY
import com.pointlessapps.filman.data.model.SubtitleStylePreferences
import com.pointlessapps.filman.data.model.LektorTiming
import com.pointlessapps.filman.data.model.LektorDucking
import com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "filman_settings")

class SettingsManager(
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val preferredQualityKey = stringPreferencesKey("preferred_quality")
    private val autoPlayNextKey = stringPreferencesKey("autoplay_next")
    private val initialAppearanceTypeKey = stringPreferencesKey("initial_appearance_type")
    private val initialAppearanceOffsetKey = stringPreferencesKey("initial_appearance_offset")
    private val secondaryAppearanceTypeKey = stringPreferencesKey("secondary_appearance_type")
    private val secondaryAppearanceOffsetKey =
        stringPreferencesKey("secondary_appearance_offset")
    private val secondaryTimerAmountKey = stringPreferencesKey("secondary_timer_amount")
    private val initialAppearancePercentageKey =
        stringPreferencesKey("initial_appearance_percentage")
    private val secondaryAppearancePercentageKey =
        stringPreferencesKey("secondary_appearance_percentage")
    private val subtitleStyleKey = stringPreferencesKey("subtitle_style")
    private val screensaverEnabledKey = stringPreferencesKey("screensaver_enabled")
    private val screensaverInactivityTimeKey = stringPreferencesKey("screensaver_inactivity_time")
    private val screensaverSlideDurationKey = stringPreferencesKey("screensaver_slide_duration")
    private val lektorEnabledKey = booleanPreferencesKey("lektor_enabled")
    private val lektorHideSubtitlesKey = booleanPreferencesKey("lektor_hide_subtitles")
    private val lektorVoiceKey = stringPreferencesKey("lektor_voice")
    private val lektorDuckingVolumeKey = floatPreferencesKey("lektor_ducking_volume")
    private val lektorTimingOffsetKey = intPreferencesKey("lektor_timing_offset_ms")
    private val lektorEdgeRateKey = stringPreferencesKey("lektor_edge_rate")
    private val lektorEdgePitchKey = stringPreferencesKey("lektor_edge_pitch")
    private val lektorEdgeVolumeKey = stringPreferencesKey("lektor_edge_volume")

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    val isLektorEnabled: StateFlow<Boolean> =
        context.settingsDataStore.data
            .map { it[lektorEnabledKey] ?: false }
            .stateIn(scope, SharingStarted.Eagerly, false)

    val lektorHideSubtitles: StateFlow<Boolean> =
        context.settingsDataStore.data
            .map { it[lektorHideSubtitlesKey] ?: false }
            .stateIn(scope, SharingStarted.Eagerly, false)

    val lektorVoiceId: StateFlow<String> =
        context.settingsDataStore.data
            .map { it[lektorVoiceKey] ?: "meski" }
            .stateIn(scope, SharingStarted.Eagerly, "meski")

    val lektorDuckingVolume: StateFlow<Float> =
        context.settingsDataStore.data
            .map { LektorDucking.fromStored(it[lektorDuckingVolumeKey] ?: LektorDucking.DEFAULT_VOLUME) }
            .stateIn(scope, SharingStarted.Eagerly, LektorDucking.DEFAULT_VOLUME)

    val lektorTimingOffsetMs: StateFlow<Int> =
        context.settingsDataStore.data
            .map { it[lektorTimingOffsetKey] ?: LektorTiming.DEFAULT_OFFSET_MS }
            .stateIn(scope, SharingStarted.Eagerly, LektorTiming.DEFAULT_OFFSET_MS)

    val lektorEdgeRate: StateFlow<String> =
        context.settingsDataStore.data
            .map { LektorEdgeVoiceOptions.fromStoredRate(it[lektorEdgeRateKey]) }
            .stateIn(scope, SharingStarted.Eagerly, LektorEdgeVoiceOptions.DEFAULT_RATE)

    val lektorEdgePitch: StateFlow<String> =
        context.settingsDataStore.data
            .map { LektorEdgeVoiceOptions.fromStoredPitch(it[lektorEdgePitchKey]) }
            .stateIn(scope, SharingStarted.Eagerly, LektorEdgeVoiceOptions.DEFAULT_PITCH)

    val lektorEdgeVolume: StateFlow<String> =
        context.settingsDataStore.data
            .map { LektorEdgeVoiceOptions.fromStoredVolume(it[lektorEdgeVolumeKey]) }
            .stateIn(scope, SharingStarted.Eagerly, LektorEdgeVoiceOptions.DEFAULT_VOLUME)

    val isScreensaverEnabled: StateFlow<Boolean> =
        context.settingsDataStore.data
            .map { it[screensaverEnabledKey]?.toBooleanStrictOrNull() ?: true }
            .stateIn(scope, SharingStarted.Eagerly, true)

    val screensaverInactivityTime: StateFlow<Long> =
        context.settingsDataStore.data
            .map { it[screensaverInactivityTimeKey]?.toLongOrNull() ?: 120_000L }
            .stateIn(scope, SharingStarted.Eagerly, 120_000L)

    val screensaverSlideDuration: StateFlow<Long> =
        context.settingsDataStore.data
            .map { it[screensaverSlideDurationKey]?.toLongOrNull() ?: 10_000L }
            .stateIn(scope, SharingStarted.Eagerly, 10_000L)

    val subtitleStylePreferencesFlow: StateFlow<SubtitleStylePreferences> =
        context.settingsDataStore.data
            .map { prefs ->
                val jsonString = prefs[subtitleStyleKey]
                if (jsonString != null) {
                    runCatching {
                        json.decodeFromString<SubtitleStylePreferences>(jsonString)
                    }.getOrDefault(SubtitleStylePreferences())
                } else {
                    SubtitleStylePreferences()
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, SubtitleStylePreferences())

    val preferredQualityFlow: StateFlow<String> =
        context.settingsDataStore.data
            .map { prefs -> prefs[preferredQualityKey] ?: SettingsConstants.Quality.AUTO }
            .stateIn(scope, SharingStarted.Eagerly, SettingsConstants.Quality.AUTO)

    val autoPlayNextFlow: StateFlow<Boolean> =
        context.settingsDataStore.data
            .map { prefs -> prefs[autoPlayNextKey]?.toBoolean() ?: true }
            .stateIn(scope, SharingStarted.Eagerly, true)

    val initialAppearanceTypeFlow: StateFlow<NextEpisodeAppearance> =
        context.settingsDataStore.data
            .map { prefs ->
                runCatching { prefs[initialAppearanceTypeKey]?.let(NextEpisodeAppearance::valueOf) }
                    .getOrNull() ?: SHOW_IN_OVERLAY
            }.stateIn(scope, SharingStarted.Eagerly, SHOW_IN_OVERLAY)

    val initialAppearanceOffsetFlow: StateFlow<Long> =
        context.settingsDataStore.data
            .map { prefs -> prefs[initialAppearanceOffsetKey]?.toLong() ?: 100L }
            .stateIn(scope, SharingStarted.Eagerly, 100L)

    val secondaryAppearanceTypeFlow: StateFlow<NextEpisodeAppearance> =
        context.settingsDataStore.data
            .map { prefs ->
                runCatching { prefs[secondaryAppearanceTypeKey]?.let(NextEpisodeAppearance::valueOf) }
                    .getOrNull() ?: if (prefs[autoPlayNextKey]?.toBoolean() == false) {
                    NextEpisodeAppearance.SHOW
                } else {
                    NextEpisodeAppearance.SHOW_WITH_TIMER
                }
            }.stateIn(scope, SharingStarted.Eagerly, NextEpisodeAppearance.SHOW_WITH_TIMER)

    val secondaryAppearanceOffsetFlow: StateFlow<Long> =
        context.settingsDataStore.data
            .map { prefs -> prefs[secondaryAppearanceOffsetKey]?.toLong() ?: 30L }
            .stateIn(scope, SharingStarted.Eagerly, 30L)

    val secondaryTimerAmountFlow: StateFlow<Long> =
        context.settingsDataStore.data
            .map { prefs -> prefs[secondaryTimerAmountKey]?.toLong() ?: 10L }
            .stateIn(scope, SharingStarted.Eagerly, 10L)

    val initialAppearancePercentageFlow: StateFlow<Long> =
        context.settingsDataStore.data
            .map { prefs -> prefs[initialAppearancePercentageKey]?.toLong() ?: 5L }
            .stateIn(scope, SharingStarted.Eagerly, 5L)

    val secondaryAppearancePercentageFlow: StateFlow<Long> =
        context.settingsDataStore.data
            .map { prefs -> prefs[secondaryAppearancePercentageKey]?.toLong() ?: 2L }
            .stateIn(scope, SharingStarted.Eagerly, 2L)

    private val notificationsEnabledKey = stringPreferencesKey("notifications_enabled")
    val notificationsEnabledFlow: StateFlow<Boolean> =
        context.settingsDataStore.data
            .map { prefs -> prefs[notificationsEnabledKey]?.toBoolean() ?: true }
            .stateIn(scope, SharingStarted.Eagerly, true)

    fun setPreferredQuality(quality: String) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[preferredQualityKey] = quality
            }
        }
    }

    fun setAutoPlayNext(enabled: Boolean) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[autoPlayNextKey] = enabled.toString()
            }
        }
    }

    fun setInitialAppearanceType(type: NextEpisodeAppearance) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[initialAppearanceTypeKey] = type.name
            }
        }
    }

    fun setInitialAppearanceOffset(offset: Long) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[initialAppearanceOffsetKey] = offset.toString()
            }
        }
    }

    fun setSecondaryAppearanceType(type: NextEpisodeAppearance) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[secondaryAppearanceTypeKey] = type.name
            }
        }
    }

    fun setSecondaryAppearanceOffset(offset: Long) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[secondaryAppearanceOffsetKey] = offset.toString()
            }
        }
    }

    fun setSecondaryTimerAmount(amount: Long) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[secondaryTimerAmountKey] = amount.toString()
            }
        }
    }

    fun setInitialAppearancePercentage(percentage: Long) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[initialAppearancePercentageKey] = percentage.toString()
            }
        }
    }

    fun setSecondaryAppearancePercentage(percentage: Long) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[secondaryAppearancePercentageKey] = percentage.toString()
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[notificationsEnabledKey] = enabled.toString()
            }
        }
    }

    fun setSubtitleStylePreferences(preferences: SubtitleStylePreferences) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[subtitleStyleKey] = json.encodeToString(preferences)
            }
        }
    }

    fun setScreensaverEnabled(enabled: Boolean) {
        scope.launch {
            context.settingsDataStore.edit {
                it[screensaverEnabledKey] = enabled.toString()
            }
        }
    }

    fun setScreensaverInactivityTime(durationMs: Long) {
        scope.launch {
            context.settingsDataStore.edit {
                it[screensaverInactivityTimeKey] = durationMs.toString()
            }
        }
    }

    fun setScreensaverSlideDuration(durationMs: Long) {
        scope.launch {
            context.settingsDataStore.edit {
                it[screensaverSlideDurationKey] = durationMs.toString()
            }
        }
    }

    fun setLektorEnabled(enabled: Boolean) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[lektorEnabledKey] = enabled
            }
        }
    }

    fun setLektorHideSubtitles(hide: Boolean) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[lektorHideSubtitlesKey] = hide
            }
        }
    }

    fun setLektorVoiceId(voiceId: String) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[lektorVoiceKey] = voiceId
            }
        }
    }

    fun setLektorDuckingVolume(volume: Float) {
        require(volume in LektorDucking.levels)
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[lektorDuckingVolumeKey] = volume
            }
        }
    }

    fun setLektorTimingOffsetMs(offsetMs: Int) {
        require(offsetMs in LektorTiming.offsetsMs)
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[lektorTimingOffsetKey] = offsetMs
            }
        }
    }

    fun setLektorEdgeRate(rate: String) {
        require(rate in LektorEdgeVoiceOptions.rates)
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[lektorEdgeRateKey] = rate
            }
        }
    }

    fun setLektorEdgePitch(pitch: String) {
        require(pitch in LektorEdgeVoiceOptions.pitches)
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[lektorEdgePitchKey] = pitch
            }
        }
    }

    fun setLektorEdgeVolume(volume: String) {
        require(volume in LektorEdgeVoiceOptions.volumes)
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[lektorEdgeVolumeKey] = volume
            }
        }
    }

    suspend fun getPreferencesSnapshot(): Map<String, String> {
        val prefs = context.settingsDataStore.data.first()
        val result = mutableMapOf<String, String>()
        prefs[preferredQualityKey]?.let { result["preferred_quality"] = it }
        prefs[autoPlayNextKey]?.let { result["autoplay_next"] = it }
        prefs[subtitleStyleKey]?.let { result["subtitle_style"] = it }
        prefs[screensaverEnabledKey]?.let { result["screensaver_enabled"] = it }
        prefs[screensaverInactivityTimeKey]?.let { result["screensaver_inactivity_time"] = it }
        prefs[screensaverSlideDurationKey]?.let { result["screensaver_slide_duration"] = it }
        prefs[lektorEnabledKey]?.let { result["lektor_enabled"] = it.toString() }
        prefs[lektorHideSubtitlesKey]?.let { result["lektor_hide_subtitles"] = it.toString() }
        prefs[lektorVoiceKey]?.let { result["lektor_voice"] = it }
        prefs[lektorDuckingVolumeKey]?.let { result["lektor_ducking_volume"] = it.toString() }
        prefs[lektorTimingOffsetKey]?.let { result["lektor_timing_offset_ms"] = it.toString() }
        prefs[lektorEdgeRateKey]?.let { result["lektor_edge_rate"] = it }
        prefs[lektorEdgePitchKey]?.let { result["lektor_edge_pitch"] = it }
        prefs[lektorEdgeVolumeKey]?.let { result["lektor_edge_volume"] = it }
        return result
    }

    suspend fun applyPreferencesSnapshot(map: Map<String, String>) {
        if (map.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            map["preferred_quality"]?.let { prefs[preferredQualityKey] = it }
            map["autoplay_next"]?.let { prefs[autoPlayNextKey] = it }
            map["subtitle_style"]?.let { prefs[subtitleStyleKey] = it }
            map["screensaver_enabled"]?.let { prefs[screensaverEnabledKey] = it }
            map["screensaver_inactivity_time"]?.let { prefs[screensaverInactivityTimeKey] = it }
            map["screensaver_slide_duration"]?.let { prefs[screensaverSlideDurationKey] = it }
            map["lektor_enabled"]?.toBooleanStrictOrNull()?.let { prefs[lektorEnabledKey] = it }
            map["lektor_hide_subtitles"]?.toBooleanStrictOrNull()?.let { prefs[lektorHideSubtitlesKey] = it }
            map["lektor_voice"]?.let { prefs[lektorVoiceKey] = it }
            map["lektor_ducking_volume"]?.toFloatOrNull()?.let { if (it in LektorDucking.levels) prefs[lektorDuckingVolumeKey] = it }
            map["lektor_timing_offset_ms"]?.toIntOrNull()?.let { if (it in LektorTiming.offsetsMs) prefs[lektorTimingOffsetKey] = it }
            map["lektor_edge_rate"]?.let { if (it in LektorEdgeVoiceOptions.rates) prefs[lektorEdgeRateKey] = it }
            map["lektor_edge_pitch"]?.let { if (it in LektorEdgeVoiceOptions.pitches) prefs[lektorEdgePitchKey] = it }
            map["lektor_edge_volume"]?.let { if (it in LektorEdgeVoiceOptions.volumes) prefs[lektorEdgeVolumeKey] = it }
        }
    }
}
