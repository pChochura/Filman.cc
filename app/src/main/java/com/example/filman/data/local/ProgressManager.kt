package com.example.filman.data.local

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.filman.data.model.ProgressItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.progressDataStore by preferencesDataStore(
    name = "filman_progress",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "filman_progress"))
    }
)

class ProgressManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val progressKey = stringPreferencesKey("progress_list")
    private val json = Json { ignoreUnknownKeys = true }

    private val _progressItemsFlow = MutableStateFlow<List<ProgressItem>>(emptyList())
    val progressItemsFlow: StateFlow<List<ProgressItem>> = _progressItemsFlow.asStateFlow()

    init {
        scope.launch {
            context.progressDataStore.data.collect { prefs ->
                val jsonString = prefs[progressKey]
                if (jsonString != null) {
                    val list = runCatching {
                        json.decodeFromString<List<ProgressItem>>(jsonString)
                    }.getOrDefault(emptyList())
                    _progressItemsFlow.value = list
                }
            }
        }
    }

    fun getProgressItems(): List<ProgressItem> {
        return _progressItemsFlow.value
    }

    fun saveProgress(item: ProgressItem) {
        val items = _progressItemsFlow.value.toMutableList()
        items.removeAll { it.url == item.url }

        if (item is ProgressItem.InProgress) {
            items.add(0, item)
        } else {
            items.add(item)
        }

        val trimmedItems = items.take(500)
        _progressItemsFlow.value = trimmedItems
        scope.launch { saveItems(trimmedItems) }
    }

    fun removeProgress(url: String) {
        val items = _progressItemsFlow.value.toMutableList()
        if (items.removeAll { it.url == url }) {
            _progressItemsFlow.value = items
            scope.launch { saveItems(items) }
        }
    }

    fun markAsWatched(url: String, parentUrl: String = url) {
        saveProgress(ProgressItem.Watched(url, parentUrl))
    }

    fun markAsNotWatched(url: String) {
        val items = _progressItemsFlow.value.toMutableList()
        if (items.removeAll { it.url == url && it is ProgressItem.Watched }) {
            _progressItemsFlow.value = items
            scope.launch { saveItems(items) }
        }
    }

    fun isWatched(url: String): Boolean {
        return _progressItemsFlow.value.any { it.url == url && it is ProgressItem.Watched }
    }

    fun getProgressForUrl(url: String): ProgressItem? {
        return _progressItemsFlow.value.find { it.url == url }
    }

    private suspend fun saveItems(items: List<ProgressItem>) {
        val jsonString = json.encodeToString(items)
        context.progressDataStore.edit { prefs ->
            prefs[progressKey] = jsonString
        }
    }
}
