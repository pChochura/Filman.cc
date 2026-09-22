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

private val Context.favoritesDataStore by preferencesDataStore(
    name = "filman_favorites",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "filman_favorites"))
    },
)

class FavoritesManager(
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val favoritesKey = stringPreferencesKey("favorites_list")
    private val json = Json { ignoreUnknownKeys = true }

    private val _favoritesFlow = MutableStateFlow<List<MovieItem>>(emptyList())
    val favoritesFlow: StateFlow<List<MovieItem>> = _favoritesFlow.asStateFlow()

    private val saveChannel = Channel<List<MovieItem>>(Channel.CONFLATED)

    init {
        scope.launch {
            val prefs = context.favoritesDataStore.data.first()
            val jsonString = prefs[favoritesKey]
            if (jsonString != null) {
                val list =
                    runCatching {
                        json.decodeFromString<List<MovieItem>>(jsonString)
                    }.getOrDefault(emptyList())

                if (_favoritesFlow.value.isEmpty()) {
                    _favoritesFlow.value = list
                } else {
                    val merged = (_favoritesFlow.value + list).distinctBy { it.url }
                    _favoritesFlow.value = merged
                    saveChannel.trySend(merged)
                }
            }

            for (items in saveChannel) {
                val toSave = json.encodeToString(items)
                context.favoritesDataStore.edit { editPrefs ->
                    editPrefs[favoritesKey] = toSave
                }
            }
        }
    }

    fun getFavorites(): List<MovieItem> = _favoritesFlow.value

    fun addFavorite(movie: MovieItem) {
        _favoritesFlow.update { current ->
            if (current.none { it.url == movie.url }) {
                listOf(movie) + current
            } else {
                current
            }
        }
        if (_favoritesFlow.value.firstOrNull()?.url == movie.url) {
            saveChannel.trySend(_favoritesFlow.value)
        }
    }

    fun removeFavorite(url: String) {
        val sizeBefore = _favoritesFlow.value.size
        _favoritesFlow.update { current -> current.filter { it.url != url } }
        if (_favoritesFlow.value.size != sizeBefore) {
            saveChannel.trySend(_favoritesFlow.value)
        }
    }

    fun isFavorite(url: String): Boolean = _favoritesFlow.value.any { it.url == url }
}
