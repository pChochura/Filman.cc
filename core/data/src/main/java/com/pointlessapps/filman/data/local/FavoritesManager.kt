package com.pointlessapps.filman.data.local

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pointlessapps.filman.data.model.MediaRef
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
    private val deletedFavoritesKey = stringPreferencesKey("deleted_favorites_urls")
    private val json = Json { ignoreUnknownKeys = true }

    private val _favoritesFlow = MutableStateFlow<List<MovieItem>>(emptyList())
    val favoritesFlow: StateFlow<List<MovieItem>> = _favoritesFlow.asStateFlow()

    private val _deletedUrlsFlow = MutableStateFlow<Set<String>>(emptySet())
    val deletedUrlsFlow: StateFlow<Set<String>> = _deletedUrlsFlow.asStateFlow()

    private val saveChannel = Channel<List<MovieItem>>(Channel.CONFLATED)
    private val saveDeletedChannel = Channel<Set<String>>(Channel.CONFLATED)

    init {
        scope.launch {
            val prefs = context.favoritesDataStore.data.first()
            val jsonString = prefs[favoritesKey]
            if (jsonString != null) {
                val rawList =
                    runCatching {
                        json.decodeFromString<List<MovieItem>>(jsonString)
                    }.getOrDefault(emptyList())
                val list = rawList.mapNotNull { it.sanitizeToTmdb() }.distinctBy { it.url }

                if (_favoritesFlow.value.isEmpty()) {
                    _favoritesFlow.value = list
                    if (list.size != rawList.size || list != rawList) {
                        saveChannel.trySend(list)
                    }
                } else {
                    val merged = (_favoritesFlow.value + list).distinctBy { it.url }
                    _favoritesFlow.value = merged
                    saveChannel.trySend(merged)
                }
            }

            prefs[deletedFavoritesKey]?.let { str ->
                runCatching { json.decodeFromString<Set<String>>(str) }
                    .getOrNull()
                    ?.let { _deletedUrlsFlow.value = it }
            }

            launch {
                for (items in saveChannel) {
                    val toSave = json.encodeToString(items)
                    context.favoritesDataStore.edit { editPrefs ->
                        editPrefs[favoritesKey] = toSave
                    }
                }
            }

            launch {
                for (deleted in saveDeletedChannel) {
                    val toSave = json.encodeToString(deleted)
                    context.favoritesDataStore.edit { editPrefs ->
                        editPrefs[deletedFavoritesKey] = toSave
                    }
                }
            }
        }
    }

    fun getFavorites(): List<MovieItem> = _favoritesFlow.value

    fun getDeletedUrls(): Set<String> = _deletedUrlsFlow.value

    fun clearDeletedUrls(urls: Set<String>) {
        if (urls.isEmpty()) return
        _deletedUrlsFlow.update { it - urls }
        saveDeletedChannel.trySend(_deletedUrlsFlow.value)
    }

    private fun normalizeKey(url: String): String =
        MediaRef.parse(url)?.parentRef?.canonicalKey ?: url

    fun addFavorite(movie: MovieItem) {
        val normKey = normalizeKey(movie.url)
        _deletedUrlsFlow.update { it - normKey }
        saveDeletedChannel.trySend(_deletedUrlsFlow.value)

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
        val normKey = normalizeKey(url)
        _deletedUrlsFlow.update { it + normKey }
        saveDeletedChannel.trySend(_deletedUrlsFlow.value)

        val sizeBefore = _favoritesFlow.value.size
        _favoritesFlow.update { current -> current.filter { it.url != url } }
        if (_favoritesFlow.value.size != sizeBefore) {
            saveChannel.trySend(_favoritesFlow.value)
        }
    }

    fun isFavorite(url: String): Boolean = _favoritesFlow.value.any { it.url == url }

    fun mergeSyncedItems(syncedItems: List<MovieItem>, deletedUrls: Set<String> = emptySet()) {
        val normDeleted = deletedUrls.map(::normalizeKey).toSet()
        if (normDeleted.isNotEmpty()) {
            _deletedUrlsFlow.update { it - normDeleted }
            saveDeletedChannel.trySend(_deletedUrlsFlow.value)
        }
        _favoritesFlow.update { current ->
            val remaining = current.filterNot { it.url in normDeleted }
            (syncedItems.filterNot { it.url in normDeleted } + remaining).distinctBy { it.url }
        }
        saveChannel.trySend(_favoritesFlow.value)
    }
}
