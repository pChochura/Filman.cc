package com.example.filman.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.filman.data.model.ProgressItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class ProgressManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("filman_progress", Context.MODE_PRIVATE)

    private val _progressItemsFlow = MutableStateFlow(getProgressItems())
    val progressItemsFlow: StateFlow<List<ProgressItem>> = _progressItemsFlow.asStateFlow()

    fun getProgressItems() = prefs.getString("progress_list", null)?.let {
        Json.decodeFromString<List<ProgressItem>>(it)
    }.orEmpty()

    fun saveProgress(item: ProgressItem) {
        val items = getProgressItems().toMutableList()
        items.removeAll { it.url == item.url }

        if (item is ProgressItem.InProgress) {
            items.add(0, item)
        } else {
            items.add(item)
        }

        val trimmedItems = items.take(500)

        saveItems(trimmedItems)
        _progressItemsFlow.value = trimmedItems
    }

    fun removeProgress(url: String) {
        val items = getProgressItems().toMutableList()
        items.removeAll { it.url == url }
        saveItems(items)
        _progressItemsFlow.value = items
    }

    fun markAsWatched(url: String, parentUrl: String = url) {
        saveProgress(ProgressItem.Watched(url, parentUrl))
    }

    fun markAsNotWatched(url: String) {
        val items = getProgressItems().toMutableList()
        items.removeAll { it.url == url && it is ProgressItem.Watched }
        saveItems(items)
        _progressItemsFlow.value = items
    }

    fun isWatched(url: String): Boolean {
        return getProgressItems().any { it.url == url && it is ProgressItem.Watched }
    }

    fun getProgressForUrl(url: String): ProgressItem? {
        return getProgressItems().find { it.url == url }
    }

    private fun saveItems(items: List<ProgressItem>) {
        prefs.edit { putString("progress_list", Json.encodeToString(items)) }
    }
}
