package com.pointlessapps.filman.data.local

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pointlessapps.filman.data.model.TvShowSourceSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val Context.tvShowSettingsDataStore by preferencesDataStore(
    name = "filman_tv_show_settings",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "filman_tv_show_settings"))
    },
)

class TvShowSettingsManager(
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settingsKey = stringPreferencesKey("tv_show_settings_map")
    private val json = Json { ignoreUnknownKeys = true }

    private val _settingsFlow = MutableStateFlow<Map<String, TvShowSourceSettings>>(emptyMap())
    val settingsFlow: StateFlow<Map<String, TvShowSourceSettings>> = _settingsFlow.asStateFlow()

    private val saveChannel = Channel<Map<String, TvShowSourceSettings>>(Channel.CONFLATED)
    private val isInitialized = CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                val prefs = context.tvShowSettingsDataStore.data.first()
                val jsonString = prefs[settingsKey]
                if (jsonString != null) {
                    val map =
                        runCatching {
                            json.decodeFromString<Map<String, TvShowSourceSettings>>(jsonString)
                        }.getOrDefault(emptyMap())

                    if (_settingsFlow.value.isEmpty()) {
                        _settingsFlow.value = map
                    } else {
                        val merged = map + _settingsFlow.value
                        _settingsFlow.value = merged
                        saveChannel.trySend(merged)
                    }
                }
            } finally {
                isInitialized.complete(Unit)
            }

            for (settingsMap in saveChannel) {
                val toSave = json.encodeToString(settingsMap)
                context.tvShowSettingsDataStore.edit { editPrefs ->
                    editPrefs[settingsKey] = toSave
                }
            }
        }
    }

    suspend fun getSettingsForTvShow(showKey: String): TvShowSourceSettings? {
        isInitialized.await()
        return _settingsFlow.value[showKey]
    }

    fun getSettingsForTvShowSync(showKey: String): TvShowSourceSettings? = _settingsFlow.value[showKey]

    fun saveSettingsForTvShow(
        showKey: String,
        settings: TvShowSourceSettings,
    ) {
        val current = _settingsFlow.value.toMutableMap()
        current[showKey] = settings
        _settingsFlow.value = current
        saveChannel.trySend(current)
    }

    fun removeSettingsForTvShow(showKey: String) {
        val current = _settingsFlow.value.toMutableMap()
        if (current.remove(showKey) != null) {
            _settingsFlow.value = current
            saveChannel.trySend(current)
        }
    }
}
