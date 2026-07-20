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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        scope.launch {
            context.favoritesDataStore.data.collect { prefs ->
                val jsonString = prefs[favoritesKey]
                if (jsonString != null) {
                    val list = runCatching {
                        json.decodeFromString<List<MovieItem>>(jsonString)
                    }.getOrDefault(emptyList())
                    _favoritesFlow.value = list
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
            scope.launch { saveFavorites(favorites) }
        }
    }

    fun removeFavorite(url: String) {
        val favorites = _favoritesFlow.value.toMutableList()
        if (favorites.removeAll { it.url == url }) {
            _favoritesFlow.value = favorites
            scope.launch { saveFavorites(favorites) }
        }
    }

    fun isFavorite(url: String): Boolean {
        return _favoritesFlow.value.any { it.url == url }
    }

    private suspend fun saveFavorites(favorites: List<MovieItem>) {
        val jsonString = json.encodeToString(favorites)
        context.favoritesDataStore.edit { prefs ->
            prefs[favoritesKey] = jsonString
        }
    }
}
