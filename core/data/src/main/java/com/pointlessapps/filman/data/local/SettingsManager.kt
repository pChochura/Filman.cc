package com.pointlessapps.filman.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.SHOW_IN_OVERLAY
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.autoPlayNextKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.contentRulesKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.initialAppearanceOffsetKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.initialAppearancePercentageKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.initialAppearanceTypeKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.lektorDuckingVolumeKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.lektorEdgeCalmModeKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.lektorEdgePitchKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.lektorEdgeRateKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.lektorEdgeVolumeKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.lektorEnabledKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.lektorHideSubtitlesKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.lektorTimingOffsetKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.lektorVoiceKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.notificationsEnabledKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.preferredQualityKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.screensaverEnabledKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.screensaverInactivityTimeKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.screensaverSlideDurationKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.secondaryAppearanceOffsetKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.secondaryAppearancePercentageKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.secondaryAppearanceTypeKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.secondaryTimerAmountKey
import com.pointlessapps.filman.data.local.SettingsPreferenceKeys.subtitleStyleKey
import com.pointlessapps.filman.data.model.ContentCategory
import com.pointlessapps.filman.data.model.ContentRule
import com.pointlessapps.filman.data.model.ContentRulesPreferences
import com.pointlessapps.filman.data.model.LektorDucking
import com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions
import com.pointlessapps.filman.data.model.LektorTiming
import com.pointlessapps.filman.data.model.SubtitleStylePreferences
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
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    val isLektorEnabled: StateFlow<Boolean> = stateFlow(false) { it[lektorEnabledKey] ?: false }
    val lektorHideSubtitles: StateFlow<Boolean> = stateFlow(false) { it[lektorHideSubtitlesKey] ?: false }
    val lektorVoiceId: StateFlow<String> = stateFlow("edge_marek") { it[lektorVoiceKey] ?: "edge_marek" }
    val lektorDuckingVolume: StateFlow<Float> = stateFlow(LektorDucking.DEFAULT_VOLUME) {
        LektorDucking.fromStored(it[lektorDuckingVolumeKey] ?: LektorDucking.DEFAULT_VOLUME)
    }
    val lektorTimingOffsetMs: StateFlow<Int> = stateFlow(LektorTiming.DEFAULT_OFFSET_MS) {
        it[lektorTimingOffsetKey] ?: LektorTiming.DEFAULT_OFFSET_MS
    }
    val lektorEdgeRate: StateFlow<String> = stateFlow(LektorEdgeVoiceOptions.DEFAULT_RATE) {
        LektorEdgeVoiceOptions.fromStoredRate(it[lektorEdgeRateKey])
    }
    val lektorEdgePitch: StateFlow<String> = stateFlow(LektorEdgeVoiceOptions.DEFAULT_PITCH) {
        LektorEdgeVoiceOptions.fromStoredPitch(it[lektorEdgePitchKey])
    }
    val lektorEdgeVolume: StateFlow<String> = stateFlow(LektorEdgeVoiceOptions.DEFAULT_VOLUME) {
        LektorEdgeVoiceOptions.fromStoredVolume(it[lektorEdgeVolumeKey])
    }
    val lektorEdgeCalmMode: StateFlow<Boolean> = stateFlow(LektorEdgeVoiceOptions.DEFAULT_CALM_MODE) {
        it[lektorEdgeCalmModeKey] ?: LektorEdgeVoiceOptions.DEFAULT_CALM_MODE
    }
    val isScreensaverEnabled: StateFlow<Boolean> = stateFlow(true) {
        it[screensaverEnabledKey]?.toBooleanStrictOrNull() ?: true
    }
    val screensaverInactivityTime: StateFlow<Long> = stateFlow(120_000L) {
        it[screensaverInactivityTimeKey]?.toLongOrNull() ?: 120_000L
    }
    val screensaverSlideDuration: StateFlow<Long> = stateFlow(10_000L) {
        it[screensaverSlideDurationKey]?.toLongOrNull() ?: 10_000L
    }
    val subtitleStylePreferencesFlow: StateFlow<SubtitleStylePreferences> = stateFlow(SubtitleStylePreferences()) { prefs ->
        val jsonString = prefs[subtitleStyleKey] ?: return@stateFlow SubtitleStylePreferences()
        runCatching { json.decodeFromString<SubtitleStylePreferences>(jsonString) }
            .getOrDefault(SubtitleStylePreferences())
    }
    val contentRulesFlow: StateFlow<ContentRulesPreferences> = stateFlow(ContentRulesPreferences()) { prefs ->
        val jsonString = prefs[contentRulesKey] ?: return@stateFlow ContentRulesPreferences()
        runCatching {
            val rulesMap = json.decodeFromString<Map<String, ContentRule>>(jsonString)
            ContentRulesPreferences(rules = ContentRulesPreferences.DEFAULT_CONTENT_RULES + rulesMap)
        }.getOrDefault(ContentRulesPreferences())
    }
    val preferredQualityFlow: StateFlow<String> = stateFlow(SettingsConstants.Quality.AUTO) {
        it[preferredQualityKey] ?: SettingsConstants.Quality.AUTO
    }
    val autoPlayNextFlow: StateFlow<Boolean> = stateFlow(true) {
        it[autoPlayNextKey]?.toBoolean() ?: true
    }
    val initialAppearanceTypeFlow: StateFlow<NextEpisodeAppearance> = stateFlow(SHOW_IN_OVERLAY) { prefs ->
        runCatching { prefs[initialAppearanceTypeKey]?.let(NextEpisodeAppearance::valueOf) }
            .getOrNull() ?: SHOW_IN_OVERLAY
    }
    val initialAppearanceOffsetFlow: StateFlow<Long> = stateFlow(100L) {
        it[initialAppearanceOffsetKey]?.toLong() ?: 100L
    }
    val secondaryAppearanceTypeFlow: StateFlow<NextEpisodeAppearance> = stateFlow(NextEpisodeAppearance.SHOW_WITH_TIMER) { prefs ->
        runCatching { prefs[secondaryAppearanceTypeKey]?.let(NextEpisodeAppearance::valueOf) }
            .getOrNull() ?: if (prefs[autoPlayNextKey]?.toBoolean() == false) {
            NextEpisodeAppearance.SHOW
        } else {
            NextEpisodeAppearance.SHOW_WITH_TIMER
        }
    }
    val secondaryAppearanceOffsetFlow: StateFlow<Long> = stateFlow(30L) {
        it[secondaryAppearanceOffsetKey]?.toLong() ?: 30L
    }
    val secondaryTimerAmountFlow: StateFlow<Long> = stateFlow(10L) {
        it[secondaryTimerAmountKey]?.toLong() ?: 10L
    }
    val initialAppearancePercentageFlow: StateFlow<Long> = stateFlow(5L) {
        it[initialAppearancePercentageKey]?.toLong() ?: 5L
    }
    val secondaryAppearancePercentageFlow: StateFlow<Long> = stateFlow(2L) {
        it[secondaryAppearancePercentageKey]?.toLong() ?: 2L
    }
    val notificationsEnabledFlow: StateFlow<Boolean> = stateFlow(true) {
        it[notificationsEnabledKey]?.toBoolean() ?: true
    }

    private fun <T> stateFlow(initialValue: T, mapper: (Preferences) -> T): StateFlow<T> =
        context.settingsDataStore.data
            .map(mapper)
            .stateIn(scope, SharingStarted.Eagerly, initialValue)

    private fun <T> setPref(key: Preferences.Key<T>, value: T) {
        scope.launch {
            context.settingsDataStore.edit { prefs -> prefs[key] = value }
        }
    }

    fun setPreferredQuality(quality: String) = setPref(preferredQualityKey, quality)
    fun setAutoPlayNext(enabled: Boolean) = setPref(autoPlayNextKey, enabled.toString())
    fun setInitialAppearanceType(type: NextEpisodeAppearance) = setPref(initialAppearanceTypeKey, type.name)
    fun setInitialAppearanceOffset(offset: Long) = setPref(initialAppearanceOffsetKey, offset.toString())
    fun setSecondaryAppearanceType(type: NextEpisodeAppearance) = setPref(secondaryAppearanceTypeKey, type.name)
    fun setSecondaryAppearanceOffset(offset: Long) = setPref(secondaryAppearanceOffsetKey, offset.toString())
    fun setSecondaryTimerAmount(amount: Long) = setPref(secondaryTimerAmountKey, amount.toString())
    fun setInitialAppearancePercentage(percentage: Long) = setPref(initialAppearancePercentageKey, percentage.toString())
    fun setSecondaryAppearancePercentage(percentage: Long) = setPref(secondaryAppearancePercentageKey, percentage.toString())
    fun setNotificationsEnabled(enabled: Boolean) = setPref(notificationsEnabledKey, enabled.toString())
    fun setSubtitleStylePreferences(preferences: SubtitleStylePreferences) =
        setPref(subtitleStyleKey, json.encodeToString(preferences))
    fun setScreensaverEnabled(enabled: Boolean) = setPref(screensaverEnabledKey, enabled.toString())
    fun setScreensaverInactivityTime(durationMs: Long) = setPref(screensaverInactivityTimeKey, durationMs.toString())
    fun setScreensaverSlideDuration(durationMs: Long) = setPref(screensaverSlideDurationKey, durationMs.toString())
    fun setLektorEnabled(enabled: Boolean) = setPref(lektorEnabledKey, enabled)
    fun setLektorHideSubtitles(hide: Boolean) = setPref(lektorHideSubtitlesKey, hide)
    fun setLektorVoiceId(voiceId: String) = setPref(lektorVoiceKey, voiceId)

    fun setLektorDuckingVolume(volume: Float) {
        require(volume in LektorDucking.levels)
        setPref(lektorDuckingVolumeKey, volume)
    }

    fun setLektorTimingOffsetMs(offsetMs: Int) {
        require(offsetMs in LektorTiming.offsetsMs)
        setPref(lektorTimingOffsetKey, offsetMs)
    }

    fun setLektorEdgeRate(rate: String) {
        require(rate in LektorEdgeVoiceOptions.rates)
        setPref(lektorEdgeRateKey, rate)
    }

    fun setLektorEdgePitch(pitch: String) {
        require(pitch in LektorEdgeVoiceOptions.pitches)
        setPref(lektorEdgePitchKey, pitch)
    }

    fun setLektorEdgeVolume(volume: String) {
        require(volume in LektorEdgeVoiceOptions.volumes)
        setPref(lektorEdgeVolumeKey, volume)
    }

    fun setLektorEdgeCalmMode(enabled: Boolean) = setPref(lektorEdgeCalmModeKey, enabled)

    fun setContentRules(preferences: ContentRulesPreferences) =
        setPref(contentRulesKey, json.encodeToString(preferences.rules))

    fun setContentRule(category: ContentCategory, rule: ContentRule) {
        val current = contentRulesFlow.value.rules.toMutableMap()
        current[category.key] = rule
        setContentRules(ContentRulesPreferences(current))
    }

    suspend fun getPreferencesSnapshot(): Map<String, String> =
        context.settingsDataStore.data.first().toSettingsSnapshotMap()

    suspend fun applyPreferencesSnapshot(map: Map<String, String>) {
        if (map.isEmpty()) return
        context.settingsDataStore.edit { prefs -> prefs.applySettingsSnapshotMap(map) }
    }
}
