package com.pointlessapps.filman.data.local

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pointlessapps.filman.data.model.MovieItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val Context.newEpisodesDataStore by preferencesDataStore(
    name = "filman_new_episodes",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "filman_new_episodes"))
    },
)

class NewEpisodesManager(
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val newEpisodesKey = stringPreferencesKey("new_episodes_list")
    private val json = Json { ignoreUnknownKeys = true }

    private val _newEpisodesFlow = MutableStateFlow<List<MovieItem>>(emptyList())
    val newEpisodesFlow: StateFlow<List<MovieItem>> = _newEpisodesFlow.asStateFlow()

    private val saveChannel = Channel<List<MovieItem>>(Channel.CONFLATED)

    init {
        scope.launch {
            val prefs = context.newEpisodesDataStore.data.first()
            val jsonString = prefs[newEpisodesKey]
            if (jsonString != null) {
                val list =
                    runCatching {
                        json.decodeFromString<List<MovieItem>>(jsonString)
                    }.getOrDefault(emptyList())

                if (_newEpisodesFlow.value.isEmpty()) {
                    _newEpisodesFlow.value = list
                } else {
                    val merged = (_newEpisodesFlow.value + list).distinctBy { it.url }
                    _newEpisodesFlow.value = merged
                    saveChannel.trySend(merged)
                }
            }

            for (items in saveChannel) {
                val toSave = json.encodeToString(items)
                context.newEpisodesDataStore.edit { editPrefs ->
                    editPrefs[newEpisodesKey] = toSave
                }
            }
        }
    }

    fun getNewEpisodes(): List<MovieItem> = _newEpisodesFlow.value

    fun addNewEpisode(episode: MovieItem) {
        _newEpisodesFlow.update { current ->
            if (current.none { it.url == episode.url }) {
                listOf(episode) + current
            } else {
                current
            }
        }
        saveChannel.trySend(_newEpisodesFlow.value)
    }

    fun removeNewEpisode(url: String) {
        val sizeBefore = _newEpisodesFlow.value.size
        _newEpisodesFlow.update { current -> current.filter { it.url != url } }
        if (_newEpisodesFlow.value.size != sizeBefore) {
            saveChannel.trySend(_newEpisodesFlow.value)
        }
    }

    fun clearNewEpisodes() {
        _newEpisodesFlow.value = emptyList()
        saveChannel.trySend(emptyList())
    }
}
