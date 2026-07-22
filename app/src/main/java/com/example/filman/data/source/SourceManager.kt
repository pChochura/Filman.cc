package com.example.filman.data.source

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
import kotlinx.coroutines.launch

internal enum class SourceType {
    FILMAN,
    OBEJRZYJ
}

private val Context.sourceDataStore by preferencesDataStore(name = "source_preferences")

internal class SourceManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sourceKey = stringPreferencesKey("active_source")

    private val _activeSourceFlow = MutableStateFlow(SourceType.OBEJRZYJ)
    val activeSourceFlow: StateFlow<SourceType> = _activeSourceFlow.asStateFlow()

    init {
        scope.launch {
            context.sourceDataStore.data.collect { prefs ->
                val sourceString = prefs[sourceKey] ?: SourceType.OBEJRZYJ.name
                _activeSourceFlow.value = runCatching {
                    SourceType.valueOf(sourceString)
                }.getOrDefault(SourceType.OBEJRZYJ)
            }
        }
    }

    val activeSource: SourceType
        get() = _activeSourceFlow.value

    fun setActiveSource(source: SourceType) {
        _activeSourceFlow.value = source
        scope.launch {
            context.sourceDataStore.edit { prefs ->
                prefs[sourceKey] = source.name
            }
        }
    }
}
