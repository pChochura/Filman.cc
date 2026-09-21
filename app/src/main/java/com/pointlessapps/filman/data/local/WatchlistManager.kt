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

private val Context.watchlistDataStore by preferencesDataStore(
    name = "filman_watchlist",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "filman_watchlist"))
    },
)

class WatchlistManager(
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val watchlistKey = stringPreferencesKey("watchlist_list")
    private val json = Json { ignoreUnknownKeys = true }

    private val _watchlistFlow = MutableStateFlow<List<MovieItem>>(emptyList())
    val watchlistFlow: StateFlow<List<MovieItem>> = _watchlistFlow.asStateFlow()

    private val saveChannel = Channel<List<MovieItem>>(Channel.CONFLATED)

    init {
        scope.launch {
            val prefs = context.watchlistDataStore.data.first()
            val jsonString = prefs[watchlistKey]
            if (jsonString != null) {
                val list =
                    runCatching {
                        json.decodeFromString<List<MovieItem>>(jsonString)
                    }.getOrDefault(emptyList())

                if (_watchlistFlow.value.isEmpty()) {
                    _watchlistFlow.value = list
                } else {
                    val merged = (_watchlistFlow.value + list).distinctBy { it.url }
                    _watchlistFlow.value = merged
                    saveChannel.trySend(merged)
                }
            }

            for (items in saveChannel) {
                val toSave = json.encodeToString(items)
                context.watchlistDataStore.edit { editPrefs ->
                    editPrefs[watchlistKey] = toSave
                }
            }
        }
    }

    fun getWatchlist(): List<MovieItem> = _watchlistFlow.value

    fun addToWatchlist(movie: MovieItem) {
        _watchlistFlow.update { current ->
            if (current.none { it.url == movie.url }) {
                listOf(movie) + current
            } else {
                current
            }
        }
        if (_watchlistFlow.value.firstOrNull()?.url == movie.url) {
            saveChannel.trySend(_watchlistFlow.value)
        }
    }

    fun removeFromWatchlist(url: String) {
        val sizeBefore = _watchlistFlow.value.size
        _watchlistFlow.update { current -> current.filter { it.url != url } }
        if (_watchlistFlow.value.size != sizeBefore) {
            saveChannel.trySend(_watchlistFlow.value)
        }
    }

    fun isInWatchlist(url: String): Boolean = _watchlistFlow.value.any { it.url == url }
}
