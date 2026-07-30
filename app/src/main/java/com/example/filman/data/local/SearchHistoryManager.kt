package com.example.filman.data.local

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

private val Context.searchHistoryDataStore by preferencesDataStore(
    name = "filman_search_history",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "filman_search_history"))
    },
)

class SearchHistoryManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val historyKey = stringPreferencesKey("search_history_list")
    private val json = Json { ignoreUnknownKeys = true }
    private val maxItems = 15

    private val _historyFlow = MutableStateFlow<List<String>>(emptyList())
    val historyFlow: StateFlow<List<String>> = _historyFlow.asStateFlow()

    private val saveChannel = Channel<List<String>>(Channel.CONFLATED)

    init {
        scope.launch {
            val prefs = context.searchHistoryDataStore.data.first()
            val jsonString = prefs[historyKey]
            if (jsonString != null) {
                val list = runCatching {
                    json.decodeFromString<List<String>>(jsonString)
                }.getOrDefault(emptyList())

                if (_historyFlow.value.isEmpty()) {
                    _historyFlow.value = list
                } else {
                    val merged = (list + _historyFlow.value).distinct().take(maxItems)
                    _historyFlow.value = merged
                    saveChannel.trySend(merged)
                }
            }

            for (items in saveChannel) {
                val toSave = json.encodeToString(items)
                context.searchHistoryDataStore.edit { editPrefs ->
                    editPrefs[historyKey] = toSave
                }
            }
        }
    }

    fun addSearchQuery(query: String) {
        if (query.isBlank()) return
        val current = _historyFlow.value.toMutableList()
        current.remove(query)
        current.add(0, query)
        val updated = current.take(maxItems)
        _historyFlow.value = updated
        saveChannel.trySend(updated)
    }

    fun removeSearchQuery(query: String) {
        val current = _historyFlow.value.toMutableList()
        if (current.remove(query)) {
            _historyFlow.value = current
            saveChannel.trySend(current)
        }
    }

    fun getHistory(): List<String> {
        return _historyFlow.value
    }
}
