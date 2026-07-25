package com.example.filman.data.local

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.filman.data.model.MovieItem
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

private val Context.favoritesDataStore by preferencesDataStore(
    name = "filman_favorites",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "filman_favorites"))
    },
)

class FavoritesManager(private val context: Context) {
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
                val list = runCatching {
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

    fun getFavorites(): List<MovieItem> {
        return _favoritesFlow.value
    }

    fun addFavorite(movie: MovieItem) {
        val favorites = _favoritesFlow.value.toMutableList()
        if (favorites.none { it.url == movie.url }) {
            favorites.add(0, movie)
            _favoritesFlow.value = favorites
            saveChannel.trySend(favorites)
        }
    }

    fun removeFavorite(url: String) {
        val favorites = _favoritesFlow.value.toMutableList()
        if (favorites.removeAll { it.url == url }) {
            _favoritesFlow.value = favorites
            saveChannel.trySend(favorites)
        }
    }

    fun isFavorite(url: String): Boolean {
        return _favoritesFlow.value.any { it.url == url }
    }
}

