package com.pointlessapps.filman.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class MediaAssociation(
    val tmdbId: Int,
    val isTvShow: Boolean,
    /** Provider name (e.g. "filman", "zaluknij", "ekino") -> Provider movie/show URL */
    val providerUrls: Map<String, String> = emptyMap(),
    /** "season:episode" (e.g. "1:1") -> (Provider -> Episode URL) */
    val episodeUrls: Map<String, Map<String, String>> = emptyMap(),
    /** Providers searched that returned no match -> timestamp of search */
    val notFoundProviders: Map<String, Long> = emptyMap(),
    val updatedAt: Long = System.currentTimeMillis(),
)

private val Context.mediaAssociationDataStore by preferencesDataStore(name = "filman_media_associations")

class MediaAssociationManager internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    constructor(context: Context) : this(
        context.mediaAssociationDataStore,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val memoryCache = ConcurrentHashMap<String, MediaAssociation>()

    private fun cacheKey(tmdbId: Int, isTvShow: Boolean): String =
        "${if (isTvShow) "tv" else "movie"}_$tmdbId"

    init {
        scope.launch {
            try {
                mutex.withLock {
                        val prefs = dataStore.data.first()
                        prefs.asMap().forEach { (_, value) ->
                            val rawJson = value as? String ?: return@forEach
                            val assoc = runCatching { json.decodeFromString<MediaAssociation>(rawJson) }.getOrNull()
                            if (assoc != null) {
                                // Clear negative notFound cache on startup to prevent stale blocking
                                val cleaned = if (assoc.notFoundProviders.isNotEmpty()) {
                                    assoc.copy(notFoundProviders = emptyMap(), updatedAt = System.currentTimeMillis())
                                } else {
                                    assoc
                                }
                                memoryCache[cacheKey(assoc.tmdbId, assoc.isTvShow)] = cleaned
                                if (assoc.notFoundProviders.isNotEmpty()) {
                                    persist(cleaned)
                                }
                            }
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getAssociation(tmdbId: Int, isTvShow: Boolean): MediaAssociation? {
        val key = cacheKey(tmdbId, isTvShow)
        memoryCache[key]?.let { return it }
        return mutex.withLock {
            getAssociationLocked(tmdbId, isTvShow)
        }
    }

    private suspend fun getAssociationLocked(tmdbId: Int, isTvShow: Boolean): MediaAssociation? {
        val key = cacheKey(tmdbId, isTvShow)
        return memoryCache[key] ?: readFromDisk(tmdbId, isTvShow)?.also { memoryCache[key] = it }
    }

    suspend fun getProviderUrl(tmdbId: Int, isTvShow: Boolean, provider: String): String? {
        val assoc = getAssociation(tmdbId, isTvShow) ?: return null
        return assoc.providerUrls[provider.lowercase()]
    }

    suspend fun getEpisodeUrl(tmdbId: Int, season: Int, episode: Int, provider: String): String? {
        val assoc = getAssociation(tmdbId, isTvShow = true) ?: return null
        val epKey = "$season:$episode"
        return assoc.episodeUrls[epKey]?.get(provider.lowercase())
    }

    suspend fun saveProviderUrl(tmdbId: Int, isTvShow: Boolean, provider: String, url: String) {
        val provKey = provider.lowercase()
        mutex.withLock {
            val key = cacheKey(tmdbId, isTvShow)
            val existing = getAssociationLocked(tmdbId, isTvShow) ?: MediaAssociation(tmdbId = tmdbId, isTvShow = isTvShow)
            val updated = existing.copy(
                isTvShow = isTvShow,
                providerUrls = existing.providerUrls + (provKey to url),
                notFoundProviders = existing.notFoundProviders - provKey,
                updatedAt = System.currentTimeMillis()
            )
            memoryCache[key] = updated
            persist(updated)
        }
    }

    suspend fun saveEpisodeUrl(tmdbId: Int, season: Int, episode: Int, provider: String, url: String) {
        val provKey = provider.lowercase()
        val epKey = "$season:$episode"
        mutex.withLock {
            val key = cacheKey(tmdbId, isTvShow = true)
            val existing = getAssociationLocked(tmdbId, isTvShow = true) ?: MediaAssociation(tmdbId = tmdbId, isTvShow = true)
            val currentForEp = existing.episodeUrls[epKey] ?: emptyMap()
            val updatedEpUrls = existing.episodeUrls + (epKey to (currentForEp + (provKey to url)))
            val updated = existing.copy(
                isTvShow = true,
                episodeUrls = updatedEpUrls,
                updatedAt = System.currentTimeMillis()
            )
            memoryCache[key] = updated
            persist(updated)
        }
    }

    suspend fun markNotFound(tmdbId: Int, isTvShow: Boolean, provider: String) {
        val provKey = provider.lowercase()
        mutex.withLock {
            val key = cacheKey(tmdbId, isTvShow)
            val existing = getAssociationLocked(tmdbId, isTvShow) ?: MediaAssociation(tmdbId = tmdbId, isTvShow = isTvShow)
            val updated = existing.copy(
                notFoundProviders = existing.notFoundProviders + (provKey to System.currentTimeMillis()),
                updatedAt = System.currentTimeMillis()
            )
            memoryCache[key] = updated
            persist(updated)
        }
    }

    suspend fun isNotFound(tmdbId: Int, isTvShow: Boolean, provider: String, ttlMs: Long = 10 * 60 * 1000L): Boolean {
        val assoc = getAssociation(tmdbId, isTvShow) ?: return false
        val timestamp = assoc.notFoundProviders[provider.lowercase()] ?: return false
        return (System.currentTimeMillis() - timestamp) < ttlMs
    }

    suspend fun clearNotFound(tmdbId: Int? = null, isTvShow: Boolean? = null) {
        mutex.withLock {
            if (tmdbId != null && isTvShow != null) {
                val key = cacheKey(tmdbId, isTvShow)
                val existing = getAssociationLocked(tmdbId, isTvShow) ?: return
                val updated = existing.copy(
                    notFoundProviders = emptyMap(),
                    updatedAt = System.currentTimeMillis()
                )
                memoryCache[key] = updated
                persist(updated)
            } else {
                memoryCache.forEach { (key, assoc) ->
                    if (assoc.notFoundProviders.isNotEmpty()) {
                        val updated = assoc.copy(
                            notFoundProviders = emptyMap(),
                            updatedAt = System.currentTimeMillis()
                        )
                        memoryCache[key] = updated
                        persist(updated)
                    }
                }
            }
        }
    }

    suspend fun clearAssociation(tmdbId: Int, isTvShow: Boolean) {
        mutex.withLock {
            val key = cacheKey(tmdbId, isTvShow)
            memoryCache.remove(key)
            val prefKey = stringPreferencesKey("assoc_$key")
            dataStore.edit { prefs ->
                prefs.remove(prefKey)
            }
        }
    }

    private suspend fun readFromDisk(tmdbId: Int, isTvShow: Boolean): MediaAssociation? {
        val key = cacheKey(tmdbId, isTvShow)
        val prefKey = stringPreferencesKey("assoc_$key")
        val prefs = dataStore.data.first()
        val rawJson = prefs[prefKey] ?: run {
            val legacyPrefKey = stringPreferencesKey("assoc_$tmdbId")
            prefs[legacyPrefKey]?.takeIf {
                val legacyAssoc = runCatching { json.decodeFromString<MediaAssociation>(it) }.getOrNull()
                legacyAssoc?.isTvShow == isTvShow
            }
        } ?: return null
        return runCatching { json.decodeFromString<MediaAssociation>(rawJson) }.getOrNull()
    }

    private suspend fun persist(association: MediaAssociation) {
        val key = cacheKey(association.tmdbId, association.isTvShow)
        val prefKey = stringPreferencesKey("assoc_$key")
        val rawJson = json.encodeToString(MediaAssociation.serializer(), association)
        dataStore.edit { prefs ->
            prefs[prefKey] = rawJson
        }
    }
}
