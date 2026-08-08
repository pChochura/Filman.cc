package com.pointlessapps.filman.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "filman_settings")

internal class SettingsManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val extractorsPriorityKey = stringPreferencesKey("extractors_priority")
    private val preferredQualityKey = stringPreferencesKey("preferred_quality")
    private val autoPlayNextKey = stringPreferencesKey("autoplay_next")
    private val initialAppearanceTypeKey = stringPreferencesKey("initial_appearance_type")
    private val initialAppearanceOffsetKey = stringPreferencesKey("initial_appearance_offset")
    private val secondaryAppearanceTypeKey = stringPreferencesKey("secondary_appearance_type")
    private val secondaryAppearanceOffsetKey = stringPreferencesKey("secondary_appearance_offset")
    private val secondaryTimerAmountKey = stringPreferencesKey("secondary_timer_amount")
    private val initialAppearancePercentageKey =
        stringPreferencesKey("initial_appearance_percentage")
    private val secondaryAppearancePercentageKey =
        stringPreferencesKey("secondary_appearance_percentage")

    private val defaultExtractorsPriority = listOf(
        "doodstream", "embed", "streamtape", "vidoza", "voe", "player", "generic",
    )

    private val _extractorsPriorityFlow = MutableStateFlow(defaultExtractorsPriority)
    val extractorsPriorityFlow: StateFlow<List<String>> = _extractorsPriorityFlow.asStateFlow()

    private val _preferredQualityFlow = MutableStateFlow(SettingsConstants.Quality.AUTO)
    val preferredQualityFlow: StateFlow<String> = _preferredQualityFlow.asStateFlow()

    private val _autoPlayNextFlow = MutableStateFlow(true)
    val autoPlayNextFlow: StateFlow<Boolean> = _autoPlayNextFlow.asStateFlow()

    private val _initialAppearanceTypeFlow =
        MutableStateFlow(SettingsConstants.NextEpisodeInitialAppearance.SHOW_IN_OVERLAY)
    val initialAppearanceTypeFlow: StateFlow<String> = _initialAppearanceTypeFlow.asStateFlow()

    private val _initialAppearanceOffsetFlow = MutableStateFlow(120L)
    val initialAppearanceOffsetFlow: StateFlow<Long> = _initialAppearanceOffsetFlow.asStateFlow()

    private val _secondaryAppearanceTypeFlow =
        MutableStateFlow(SettingsConstants.NextEpisodeSecondaryAppearance.SHOW_WITH_TIMER)
    val secondaryAppearanceTypeFlow: StateFlow<String> = _secondaryAppearanceTypeFlow.asStateFlow()

    private val _secondaryAppearanceOffsetFlow = MutableStateFlow(60L)
    val secondaryAppearanceOffsetFlow: StateFlow<Long> =
        _secondaryAppearanceOffsetFlow.asStateFlow()

    private val _secondaryTimerAmountFlow = MutableStateFlow(10L)
    val secondaryTimerAmountFlow: StateFlow<Long> = _secondaryTimerAmountFlow.asStateFlow()

    private val _initialAppearancePercentageFlow = MutableStateFlow(5L)
    val initialAppearancePercentageFlow: StateFlow<Long> =
        _initialAppearancePercentageFlow.asStateFlow()

    private val _secondaryAppearancePercentageFlow = MutableStateFlow(3L)
    val secondaryAppearancePercentageFlow: StateFlow<Long> =
        _secondaryAppearancePercentageFlow.asStateFlow()

    init {
        scope.launch {
            val prefs = context.settingsDataStore.data.first()
            val savedPriorityStr = prefs[extractorsPriorityKey]
            if (savedPriorityStr != null) {
                val savedList = savedPriorityStr.split(",").filter { it.isNotBlank() }
                val missingItems = defaultExtractorsPriority.filter { it !in savedList }
                _extractorsPriorityFlow.value = savedList + missingItems
            }

            val savedQuality = prefs[preferredQualityKey]
            if (savedQuality != null) {
                _preferredQualityFlow.value = savedQuality
            }

            val savedAutoPlay = prefs[autoPlayNextKey]
            if (savedAutoPlay != null) {
                _autoPlayNextFlow.value = savedAutoPlay.toBoolean()
            }

            val savedInitialAppearanceType = prefs[initialAppearanceTypeKey]
            if (savedInitialAppearanceType != null) {
                _initialAppearanceTypeFlow.value = savedInitialAppearanceType
            }

            val savedInitialAppearanceOffset = prefs[initialAppearanceOffsetKey]
            if (savedInitialAppearanceOffset != null) {
                _initialAppearanceOffsetFlow.value = savedInitialAppearanceOffset.toLong()
            }

            val savedSecondaryAppearanceType = prefs[secondaryAppearanceTypeKey]
            if (savedSecondaryAppearanceType != null) {
                _secondaryAppearanceTypeFlow.value = savedSecondaryAppearanceType
            } else if (savedAutoPlay != null && !savedAutoPlay.toBoolean()) {
                _secondaryAppearanceTypeFlow.value =
                    SettingsConstants.NextEpisodeSecondaryAppearance.JUST_SHOW
            }

            val savedSecondaryAppearanceOffset = prefs[secondaryAppearanceOffsetKey]
            if (savedSecondaryAppearanceOffset != null) {
                _secondaryAppearanceOffsetFlow.value = savedSecondaryAppearanceOffset.toLong()
            }

            val savedSecondaryTimerAmount = prefs[secondaryTimerAmountKey]
            if (savedSecondaryTimerAmount != null) {
                _secondaryTimerAmountFlow.value = savedSecondaryTimerAmount.toLong()
            }

            val savedInitialAppearancePercentage = prefs[initialAppearancePercentageKey]
            if (savedInitialAppearancePercentage != null) {
                _initialAppearancePercentageFlow.value = savedInitialAppearancePercentage.toLong()
            }

            val savedSecondaryAppearancePercentage = prefs[secondaryAppearancePercentageKey]
            if (savedSecondaryAppearancePercentage != null) {
                _secondaryAppearancePercentageFlow.value =
                    savedSecondaryAppearancePercentage.toLong()
            }
        }
    }

    fun saveExtractorsPriority(priority: List<String>) {
        _extractorsPriorityFlow.value = priority
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[extractorsPriorityKey] = priority.joinToString(",")
            }
        }
    }

    fun setPreferredQuality(quality: String) {
        _preferredQualityFlow.value = quality
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[preferredQualityKey] = quality
            }
        }
    }

    fun setAutoPlayNext(enabled: Boolean) {
        _autoPlayNextFlow.value = enabled
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[autoPlayNextKey] = enabled.toString()
            }
        }
    }

    fun setInitialAppearanceType(type: String) {
        _initialAppearanceTypeFlow.value = type
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[initialAppearanceTypeKey] = type
            }
        }
    }

    fun setInitialAppearanceOffset(offset: Long) {
        _initialAppearanceOffsetFlow.value = offset
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[initialAppearanceOffsetKey] = offset.toString()
            }
        }
    }

    fun setSecondaryAppearanceType(type: String) {
        _secondaryAppearanceTypeFlow.value = type
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[secondaryAppearanceTypeKey] = type
            }
        }
    }

    fun setSecondaryAppearanceOffset(offset: Long) {
        _secondaryAppearanceOffsetFlow.value = offset
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[secondaryAppearanceOffsetKey] = offset.toString()
            }
        }
    }

    fun setSecondaryTimerAmount(amount: Long) {
        _secondaryTimerAmountFlow.value = amount
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[secondaryTimerAmountKey] = amount.toString()
            }
        }
    }

    fun setInitialAppearancePercentage(percentage: Long) {
        _initialAppearancePercentageFlow.value = percentage
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[initialAppearancePercentageKey] = percentage.toString()
            }
        }
    }

    fun setSecondaryAppearancePercentage(percentage: Long) {
        _secondaryAppearancePercentageFlow.value = percentage
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[secondaryAppearancePercentageKey] = percentage.toString()
            }
        }
    }
}
