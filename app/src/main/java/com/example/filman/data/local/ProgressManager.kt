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
import kotlinx.serialization.json.Json

internal class ProgressManager(private val context: Context) {

    private val Context.progressDataStore by preferencesDataStore(
        name = "filman_progress",
        produceMigrations = { context ->
            listOf(SharedPreferencesMigration(context, "filman_progress"))
        },
    )

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
        if (
            item is ProgressItem.InProgress &&
            item.progressPercentage >= MARK_AS_WATCHED_PROGRESS_THRESHOLD
        ) {
            return saveProgress(
                ProgressItem.Watched(
                    url = item.url,
                    parentUrl = item.parentUrl,
                    posterUrl = item.posterUrl,
                    titlePl = item.titlePl,
                    season = item.season,
                    episode = item.episode,
                    seriesTitle = item.seriesTitle,
                    episodeTitle = item.episodeTitle,
                )
            )
        }

        val items = _progressItemsFlow.value.toMutableList()
        items.removeAll { it.url == item.url }

        val recentEpisode = items.firstOrNull {
            it.parentUrl == item.parentUrl
        }

        val isOlder = if (recentEpisode != null && item.season != null && recentEpisode.season != null) {
            val seasonDiff = item.season!!.compareTo(recentEpisode.season!!)
            seasonDiff < 0 || (seasonDiff == 0 && (item.episode ?: 0) < (recentEpisode.episode ?: 0))
        } else {
            false
        }

        if (isOlder && recentEpisode != null) {
            val index = items.indexOf(recentEpisode)
            items.add(index + 1, item)
        } else {
            items.add(0, item)
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

    fun markAsWatched(
        url: String,
        parentUrl: String = url,
        season: Int? = null,
        episode: Int? = null,
    ) {
        val existing = getProgressForUrl(url)
        saveProgress(
            ProgressItem.Watched(
                url = url,
                parentUrl = parentUrl,
                posterUrl = existing?.posterUrl ?: "",
                titlePl = existing?.titlePl ?: "",
                season = existing?.season ?: season,
                episode = existing?.episode ?: episode,
                seriesTitle = existing?.seriesTitle,
                episodeTitle = existing?.episodeTitle,
            )
        )
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

    private companion object {
        const val MARK_AS_WATCHED_PROGRESS_THRESHOLD = 0.95f
    }
}
