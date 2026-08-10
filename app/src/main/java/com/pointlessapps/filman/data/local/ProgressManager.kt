package com.pointlessapps.filman.data.local

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pointlessapps.filman.data.mapper.toInProgress
import com.pointlessapps.filman.data.mapper.toWatched
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.ProgressItem
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

    private val saveChannel = Channel<List<ProgressItem>>(Channel.CONFLATED)

    init {
        scope.launch {
            val prefs = context.progressDataStore.data.first()
            val jsonString = prefs[progressKey]
            if (jsonString != null) {
                val list = runCatching {
                    json.decodeFromString<List<ProgressItem>>(jsonString)
                }.getOrDefault(emptyList())

                if (_progressItemsFlow.value.isEmpty()) {
                    _progressItemsFlow.value = list
                } else {
                    val merged = (_progressItemsFlow.value + list)
                        .distinctBy { it.url.normalizeUrl() }
                    _progressItemsFlow.value = merged
                    saveChannel.trySend(merged)
                }
            }

            for (items in saveChannel) {
                val toSave = json.encodeToString(items)
                context.progressDataStore.edit { editPrefs ->
                    editPrefs[progressKey] = toSave
                }
            }
        }
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
                    hasNextEpisode = item.hasNextEpisode,
                ),
            )
        }

        val items = _progressItemsFlow.value.toMutableList()
        items.removeAll { it.url.normalizeUrl() == item.url.normalizeUrl() }

        val mostRecent = items.firstOrNull {
            it.parentUrl.normalizeUrl() == item.parentUrl.normalizeUrl()
        }

        val itemSeason = item.season ?: 0
        val itemEpisode = item.episode ?: 0
        val recentSeason = mostRecent?.season ?: 0
        val recentEpisode = mostRecent?.episode ?: 0

        val isOlder = if (mostRecent != null) {
            val seasonDiff = itemSeason.compareTo(recentSeason)
            seasonDiff < 0 || seasonDiff == 0 && itemEpisode < recentEpisode
        } else {
            false
        }

        if (isOlder && mostRecent != null) {
            val index = items.indexOf(mostRecent)
            items.add(index + 1, item)
        } else {
            items.add(0, item)
        }

        val trimmedItems = items.take(500)
        _progressItemsFlow.value = trimmedItems
        saveChannel.trySend(trimmedItems)
    }

    fun removeProgress(url: String) {
        val items = _progressItemsFlow.value.toMutableList()
        if (items.removeAll { it.url.normalizeUrl() == url.normalizeUrl() }) {
            _progressItemsFlow.value = items
            saveChannel.trySend(items)
        }
    }

    fun markAsWatched(movie: MovieItem) {
        saveProgress(movie.toWatched())
    }

    fun markAsWatched(movies: List<MovieItem>) {
        val items = _progressItemsFlow.value.toMutableList()
        val newItems = movies.map(MovieItem::toWatched)

        for (item in newItems) {
            items.removeAll { it.url.normalizeUrl() == item.url.normalizeUrl() }
            val mostRecent = items.firstOrNull {
                it.parentUrl.normalizeUrl() == item.parentUrl.normalizeUrl()
            }

            val itemSeason = item.season ?: 0
            val itemEpisode = item.episode ?: 0
            val recentSeason = mostRecent?.season ?: 0
            val recentEpisode = mostRecent?.episode ?: 0

            val isOlder = if (mostRecent != null) {
                val seasonDiff = itemSeason.compareTo(recentSeason)
                seasonDiff < 0 || seasonDiff == 0 && itemEpisode < recentEpisode
            } else {
                false
            }

            if (isOlder && mostRecent != null) {
                val index = items.indexOf(mostRecent)
                items.add(index + 1, item)
            } else {
                items.add(0, item)
            }
        }

        val trimmedItems = items.take(500)
        _progressItemsFlow.value = trimmedItems
        saveChannel.trySend(trimmedItems)
    }

    fun saveProgress(
        movie: MovieItem,
        progressMs: Long,
        durationMs: Long,
    ) {
        val progressPercentage = if (durationMs > 0) {
            progressMs.toFloat() / durationMs.toFloat()
        } else {
            val existingProgress = getProgressForUrl(movie.url)
            existingProgress?.progressPercentage ?: 0f
        }

        saveProgress(movie.toInProgress(progressPercentage, progressMs))
    }

    fun markAsNotWatched(url: String) {
        val items = _progressItemsFlow.value.toMutableList()
        if (
            items.removeAll {
                it.url.normalizeUrl() == url.normalizeUrl() && it is ProgressItem.Watched
            }
        ) {
            _progressItemsFlow.value = items
            saveChannel.trySend(items)
        }
    }

    fun getProgressForUrl(url: String): ProgressItem? {
        return _progressItemsFlow.value.find { it.url.normalizeUrl() == url.normalizeUrl() }
    }

    fun clearAll() {
        _progressItemsFlow.value = emptyList()
        saveChannel.trySend(emptyList())
    }


    private fun String?.normalizeUrl(): String? {
        if (this == null) return null
        return this.substringAfter("filman.cc")
            .substringAfter("ekino-tv.pl")
            .substringAfter("zaluknij.pl")
            .substringBefore("?")
            .substringBefore("#")
            .trimEnd('/')
    }

    companion object {
        const val MARK_AS_WATCHED_PROGRESS_THRESHOLD = 0.95f
    }
}
