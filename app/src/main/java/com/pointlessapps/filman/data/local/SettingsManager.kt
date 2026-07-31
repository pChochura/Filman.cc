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

    private val defaultExtractorsPriority = listOf(
        "doodstream", "embed", "streamtape", "vidoza", "voe", "generic",
    )

    private val _extractorsPriorityFlow = MutableStateFlow(defaultExtractorsPriority)
    val extractorsPriorityFlow: StateFlow<List<String>> = _extractorsPriorityFlow.asStateFlow()

    init {
        scope.launch {
            val prefs = context.settingsDataStore.data.first()
            val savedPriorityStr = prefs[extractorsPriorityKey]
            if (savedPriorityStr != null) {
                _extractorsPriorityFlow.value =
                    savedPriorityStr.split(",").filter { it.isNotBlank() }
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
}
