package com.pointlessapps.filman.data.sync

import android.util.Log
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.sync.api.SyncApiClient
import com.pointlessapps.filman.data.sync.model.SyncMediaEntry
import com.pointlessapps.filman.data.sync.model.SyncProgressEntry
import com.pointlessapps.filman.data.sync.model.SyncPushRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Success(val timestamp: Long) : SyncState
    data class Error(val message: String) : SyncState
}

class SyncManager(
    private val syncApiClient: SyncApiClient,
    private val sessionManager: SessionManager,
    private val progressManager: ProgressManager,
    private val watchlistManager: WatchlistManager,
    private val favoritesManager: FavoritesManager,
    private val settingsManager: SettingsManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun triggerSync() {
        scope.launch {
            syncNow()
        }
    }

    suspend fun syncNow(): Result<Unit> = syncMutex.withLock {
        val token = sessionManager.getAuthToken()
        if (token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("User is not logged in"))
        }

        _syncState.value = SyncState.Syncing

        try {
            // 1. Pull server changes since last sync
            val lastSync = sessionManager.getLastSyncTimestamp()
            val pullResult = syncApiClient.pull(lastSync, token)
            
            pullResult.onFailure { error ->
                if (error is CancellationException) throw error
                val message = error.message ?: "Failed to pull updates from sync server"
                _syncState.value = SyncState.Error(message)
                return Result.failure(error)
            }

            val pullResponse = pullResult.getOrThrow()

            // 2. Merge pulled progress into local storage
            if (pullResponse.progress.isNotEmpty()) {
                val pulledProgress = pullResponse.progress.map { p ->
                    if (p.isWatched) {
                        ProgressItem.Watched(
                            url = p.url,
                            parentUrl = p.parentUrl,
                            posterUrl = p.posterUrl,
                            titlePl = p.titlePl,
                            season = p.season,
                            episode = p.episode,
                            seriesTitle = p.seriesTitle,
                            episodeTitle = p.episodeTitle,
                            progressMs = p.progressMs,
                            timestamp = p.updatedAt,
                        )
                    } else {
                        ProgressItem.InProgress(
                            progressPercentage = p.progressPercentage,
                            url = p.url,
                            parentUrl = p.parentUrl,
                            progressMs = p.progressMs,
                            posterUrl = p.posterUrl,
                            titlePl = p.titlePl,
                            season = p.season,
                            episode = p.episode,
                            seriesTitle = p.seriesTitle,
                            episodeTitle = p.episodeTitle,
                            timestamp = p.updatedAt,
                        )
                    }
                }
                progressManager.mergeSyncedProgress(pulledProgress)
            }

            // 3. Merge pulled watchlist
            if (pullResponse.watchlist.isNotEmpty()) {
                val activeItems = pullResponse.watchlist.filterNot { it.isDeleted }.map {
                    MovieItem(
                        url = it.url,
                        titlePl = it.titlePl,
                        posterUrl = it.posterUrl,
                        backgroundUrl = it.backgroundUrl,
                        isTvShow = it.isTvShow,
                        year = it.year,
                    )
                }
                val deletedUrls = pullResponse.watchlist.filter { it.isDeleted }.map { it.url }.toSet()
                watchlistManager.mergeSyncedItems(activeItems, deletedUrls)
            }

            // 4. Merge pulled favorites
            if (pullResponse.favorites.isNotEmpty()) {
                val activeItems = pullResponse.favorites.filterNot { it.isDeleted }.map {
                    MovieItem(
                        url = it.url,
                        titlePl = it.titlePl,
                        posterUrl = it.posterUrl,
                        backgroundUrl = it.backgroundUrl,
                        isTvShow = it.isTvShow,
                        year = it.year,
                    )
                }
                val deletedUrls = pullResponse.favorites.filter { it.isDeleted }.map { it.url }.toSet()
                favoritesManager.mergeSyncedItems(activeItems, deletedUrls)
            }

            // 5. Merge pulled preferences
            if (pullResponse.preferences.isNotEmpty()) {
                settingsManager.applyPreferencesSnapshot(pullResponse.preferences)
            }

            // 6. Push local changes to server
            val localProgressEntries = progressManager.progressItemsFlow.value.map { item ->
                SyncProgressEntry(
                    url = item.url,
                    parentUrl = item.parentUrl,
                    progressPercentage = item.progressPercentage,
                    progressMs = when (item) {
                        is ProgressItem.InProgress -> item.progressMs
                        is ProgressItem.Watched -> item.progressMs
                        else -> 0L
                    },
                    posterUrl = item.posterUrl,
                    titlePl = item.titlePl,
                    season = item.season,
                    episode = item.episode,
                    seriesTitle = item.seriesTitle,
                    episodeTitle = item.episodeTitle,
                    isWatched = item is ProgressItem.Watched,
                    updatedAt = item.timestamp,
                )
            }

            val localWatchlistEntries = watchlistManager.watchlistFlow.value.map { movie ->
                SyncMediaEntry(
                    url = movie.url,
                    titlePl = movie.titlePl,
                    posterUrl = movie.posterUrl,
                    backgroundUrl = movie.backgroundUrl,
                    isTvShow = movie.isTvShow,
                    year = movie.year,
                    isDeleted = false,
                )
            }

            val localFavoritesEntries = favoritesManager.favoritesFlow.value.map { movie ->
                SyncMediaEntry(
                    url = movie.url,
                    titlePl = movie.titlePl,
                    posterUrl = movie.posterUrl,
                    backgroundUrl = movie.backgroundUrl,
                    isTvShow = movie.isTvShow,
                    year = movie.year,
                    isDeleted = false,
                )
            }

            val localPreferences = settingsManager.getPreferencesSnapshot()

            val pushPayload = SyncPushRequest(
                clientTimestamp = System.currentTimeMillis(),
                progress = localProgressEntries,
                watchlist = localWatchlistEntries,
                favorites = localFavoritesEntries,
                preferences = localPreferences,
            )

            val pushResult = syncApiClient.push(pushPayload, token)
            pushResult.onFailure { error ->
                if (error is CancellationException) throw error
                val message = error.message ?: "Failed to push updates to sync server"
                _syncState.value = SyncState.Error(message)
                return Result.failure(error)
            }

            val serverTime = pushResult.getOrThrow().serverTimestamp.takeIf { it > 0 }
                ?: System.currentTimeMillis()

            sessionManager.updateLastSyncTimestamp(serverTime)
            _syncState.value = SyncState.Success(serverTime)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SyncManager", "Sync execution failed", e)
            _syncState.value = SyncState.Error(e.message ?: "Unknown sync error")
            Result.failure(e)
        }
    }
}
