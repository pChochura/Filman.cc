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

    private val defaultExtractorsPriority = listOf(
        "doodstream", "embed", "streamtape", "vidoza", "voe", "player", "generic",
    )

    private val _extractorsPriorityFlow = MutableStateFlow(defaultExtractorsPriority)
    val extractorsPriorityFlow: StateFlow<List<String>> = _extractorsPriorityFlow.asStateFlow()

    private val _preferredQualityFlow = MutableStateFlow(SettingsConstants.Quality.AUTO)
    val preferredQualityFlow: StateFlow<String> = _preferredQualityFlow.asStateFlow()

    private val _autoPlayNextFlow = MutableStateFlow(true)
    val autoPlayNextFlow: StateFlow<Boolean> = _autoPlayNextFlow.asStateFlow()

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
}
