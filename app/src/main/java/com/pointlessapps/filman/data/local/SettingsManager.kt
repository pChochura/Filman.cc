package com.pointlessapps.filman.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.SHOW_IN_OVERLAY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val secondaryAppearanceOffsetKey =
        stringPreferencesKey("secondary_appearance_offset")
    private val secondaryTimerAmountKey = stringPreferencesKey("secondary_timer_amount")
    private val initialAppearancePercentageKey =
        stringPreferencesKey("initial_appearance_percentage")
    private val secondaryAppearancePercentageKey =
        stringPreferencesKey("secondary_appearance_percentage")

    private val defaultExtractorsPriority = listOf(
        "doodstream", "embed", "streamtape", "vidoza", "voe", "player", "generic",
    )

    val extractorsPriorityFlow: StateFlow<List<String>> = context.settingsDataStore.data
        .map { prefs ->
            val savedPriorityStr = prefs[extractorsPriorityKey]
            if (savedPriorityStr != null) {
                val savedList = savedPriorityStr.split(",").filter { it.isNotBlank() }
                val missingItems = defaultExtractorsPriority.filter { it !in savedList }
                savedList + missingItems
            } else {
                defaultExtractorsPriority
            }
        }.stateIn(scope, SharingStarted.Eagerly, defaultExtractorsPriority)

    val preferredQualityFlow: StateFlow<String> = context.settingsDataStore.data
        .map { prefs -> prefs[preferredQualityKey] ?: SettingsConstants.Quality.AUTO }
        .stateIn(scope, SharingStarted.Eagerly, SettingsConstants.Quality.AUTO)

    val autoPlayNextFlow: StateFlow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[autoPlayNextKey]?.toBoolean() ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    val initialAppearanceTypeFlow: StateFlow<NextEpisodeAppearance> = context.settingsDataStore.data
        .map { prefs ->
            runCatching { prefs[initialAppearanceTypeKey]?.let(NextEpisodeAppearance::valueOf) }
                .getOrNull() ?: SHOW_IN_OVERLAY
        }.stateIn(scope, SharingStarted.Eagerly, SHOW_IN_OVERLAY)

    val initialAppearanceOffsetFlow: StateFlow<Long> = context.settingsDataStore.data
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

    val secondaryAppearanceOffsetFlow: StateFlow<Long> = context.settingsDataStore.data
        .map { prefs -> prefs[secondaryAppearanceOffsetKey]?.toLong() ?: 30L }
        .stateIn(scope, SharingStarted.Eagerly, 30L)

    val secondaryTimerAmountFlow: StateFlow<Long> = context.settingsDataStore.data
        .map { prefs -> prefs[secondaryTimerAmountKey]?.toLong() ?: 10L }
        .stateIn(scope, SharingStarted.Eagerly, 10L)

    val initialAppearancePercentageFlow: StateFlow<Long> = context.settingsDataStore.data
        .map { prefs -> prefs[initialAppearancePercentageKey]?.toLong() ?: 5L }
        .stateIn(scope, SharingStarted.Eagerly, 5L)

    val secondaryAppearancePercentageFlow: StateFlow<Long> = context.settingsDataStore.data
        .map { prefs -> prefs[secondaryAppearancePercentageKey]?.toLong() ?: 2L }
        .stateIn(scope, SharingStarted.Eagerly, 2L)

    fun saveExtractorsPriority(priority: List<String>) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[extractorsPriorityKey] = priority.joinToString(",")
            }
        }
    }

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
}
