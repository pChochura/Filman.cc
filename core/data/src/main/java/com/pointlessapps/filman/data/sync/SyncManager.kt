package com.pointlessapps.filman.data.sync

import android.util.Log
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.MediaRef
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.sync.api.SyncApiClient
import com.pointlessapps.filman.data.sync.model.SyncMediaEntry
import com.pointlessapps.filman.data.sync.model.SyncProgressEntry
import com.pointlessapps.filman.data.sync.model.SyncPushRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
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
    favoritesManager: FavoritesManager,
    private val settingsManager: SettingsManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    @Volatile
    private var isSyncing = false
    private var debounceJob: Job? = null

    private val favoritesManager = favoritesManager

    init {
        scope.launch {
            combine(
                progressManager.progressItemsFlow,
                watchlistManager.watchlistFlow,
                watchlistManager.deletedUrlsFlow,
                this@SyncManager.favoritesManager.favoritesFlow,
                this@SyncManager.favoritesManager.deletedUrlsFlow,
            ) { _ -> System.currentTimeMillis() }
                .drop(1)
                .collect {
                    if (!sessionManager.getAuthToken().isNullOrBlank() && !isSyncing) {
                        triggerDebouncedSync()
                    }
                }
        }
    }

    fun triggerDebouncedSync(delayMs: Long = 3000L) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(delayMs)
            syncNow()
        }
    }

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

        isSyncing = true
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

            // 2. Merge pulled progress into local storage (dropping legacy non-TMDB entries)
            if (pullResponse.progress.isNotEmpty()) {
                val pulledProgress = pullResponse.progress.mapNotNull { p ->
                    val ref = p.resolveMediaRef() ?: return@mapNotNull null
                    val canonicalUrl = ref.canonicalKey
                    val canonicalParent = if (ref.isTvShow) ref.parentRef.canonicalKey else canonicalUrl
                    if (p.isWatched) {
                        ProgressItem.Watched(
                            url = canonicalUrl,
                            parentUrl = canonicalParent,
                            posterUrl = p.posterUrl,
                            titlePl = p.titlePl,
                            season = ref.season ?: p.season,
                            episode = ref.episode ?: p.episode,
                            seriesTitle = p.seriesTitle,
                            episodeTitle = p.episodeTitle,
                            hasNextEpisode = p.hasNextEpisode,
                            progressMs = p.progressMs,
                            timestamp = p.updatedAt,
                        )
                    } else {
                        ProgressItem.InProgress(
                            progressPercentage = p.progressPercentage,
                            url = canonicalUrl,
                            parentUrl = canonicalParent,
                            progressMs = p.progressMs,
                            posterUrl = p.posterUrl,
                            titlePl = p.titlePl,
                            season = ref.season ?: p.season,
                            episode = ref.episode ?: p.episode,
                            seriesTitle = p.seriesTitle,
                            episodeTitle = p.episodeTitle,
                            hasNextEpisode = p.hasNextEpisode,
                            timestamp = p.updatedAt,
                        )
                    }
                }
                progressManager.mergeSyncedProgress(pulledProgress)
            }

            // 3. Merge pulled watchlist
            if (pullResponse.watchlist.isNotEmpty()) {
                val activeItems = pullResponse.watchlist.filterNot { it.isDeleted }.mapNotNull { entry ->
                    val ref = entry.resolveMediaRef() ?: return@mapNotNull null
                    MovieItem(
                        url = ref.canonicalKey,
                        titlePl = entry.titlePl,
                        posterUrl = entry.posterUrl,
                        backgroundUrl = entry.backgroundUrl,
                        isTvShow = ref.isTvShow,
                        year = entry.year,
                        detailsRequest = DetailsRequest.Url(ref.canonicalKey, ref.isTvShow),
                    )
                }
                val deletedUrls = pullResponse.watchlist
                    .filter { it.isDeleted }
                    .mapNotNull { it.resolveMediaRef()?.canonicalKey }
                    .toSet()
                watchlistManager.mergeSyncedItems(activeItems, deletedUrls)
            }

            // 4. Merge pulled favorites
            if (pullResponse.favorites.isNotEmpty()) {
                val activeItems = pullResponse.favorites.filterNot { it.isDeleted }.mapNotNull { entry ->
                    val ref = entry.resolveMediaRef() ?: return@mapNotNull null
                    MovieItem(
                        url = ref.canonicalKey,
                        titlePl = entry.titlePl,
                        posterUrl = entry.posterUrl,
                        backgroundUrl = entry.backgroundUrl,
                        isTvShow = ref.isTvShow,
                        year = entry.year,
                        detailsRequest = DetailsRequest.Url(ref.canonicalKey, ref.isTvShow),
                    )
                }
                val deletedUrls = pullResponse.favorites
                    .filter { it.isDeleted }
                    .mapNotNull { it.resolveMediaRef()?.canonicalKey }
                    .toSet()
                favoritesManager.mergeSyncedItems(activeItems, deletedUrls)
            }

            // 5. Merge pulled preferences
            if (pullResponse.preferences.isNotEmpty()) {
                settingsManager.applyPreferencesSnapshot(pullResponse.preferences)
            }

            // 6. Push local changes to server
            val localProgressEntries = progressManager.progressItemsFlow.value.mapNotNull { item ->
                val ref = item.mediaRef ?: return@mapNotNull null
                val canonicalUrl = ref.canonicalKey
                val canonicalParent = if (ref.isTvShow) ref.parentRef.canonicalKey else canonicalUrl
                SyncProgressEntry(
                    url = canonicalUrl,
                    parentUrl = canonicalParent,
                    type = ref.type,
                    tmdbId = ref.tmdbId,
                    season = ref.season ?: item.season,
                    episode = ref.episode ?: item.episode,
                    hasNextEpisode = item.hasNextEpisode,
                    progressPercentage = item.progressPercentage,
                    progressMs = when (item) {
                        is ProgressItem.InProgress -> item.progressMs
                        is ProgressItem.Watched -> item.progressMs
                        else -> 0L
                    },
                    posterUrl = item.posterUrl,
                    titlePl = item.titlePl,
                    seriesTitle = item.seriesTitle,
                    episodeTitle = item.episodeTitle,
                    isWatched = item is ProgressItem.Watched,
                    updatedAt = item.timestamp,
                )
            }

            val activeWatchlistEntries = watchlistManager.watchlistFlow.value.mapNotNull { movie ->
                val ref = MediaRef.parse(movie.url, movie.isTvShow)?.parentRef ?: return@mapNotNull null
                SyncMediaEntry(
                    url = ref.canonicalKey,
                    type = ref.type,
                    tmdbId = ref.tmdbId,
                    titlePl = movie.titlePl,
                    posterUrl = movie.posterUrl,
                    backgroundUrl = movie.backgroundUrl,
                    isTvShow = ref.isTvShow,
                    year = movie.year,
                    isDeleted = false,
                )
            }
            val tombstoneWatchlistUrls = watchlistManager.getDeletedUrls()
            val tombstoneWatchlistEntries = tombstoneWatchlistUrls.mapNotNull { url ->
                val ref = MediaRef.parse(url)?.parentRef ?: return@mapNotNull null
                SyncMediaEntry(
                    url = ref.canonicalKey,
                    type = ref.type,
                    tmdbId = ref.tmdbId,
                    titlePl = "",
                    isTvShow = ref.isTvShow,
                    isDeleted = true,
                )
            }
            val localWatchlistEntries = activeWatchlistEntries + tombstoneWatchlistEntries

            val activeFavoritesEntries = favoritesManager.favoritesFlow.value.mapNotNull { movie ->
                val ref = MediaRef.parse(movie.url, movie.isTvShow)?.parentRef ?: return@mapNotNull null
                SyncMediaEntry(
                    url = ref.canonicalKey,
                    type = ref.type,
                    tmdbId = ref.tmdbId,
                    titlePl = movie.titlePl,
                    posterUrl = movie.posterUrl,
                    backgroundUrl = movie.backgroundUrl,
                    isTvShow = ref.isTvShow,
                    year = movie.year,
                    isDeleted = false,
                )
            }
            val tombstoneFavoritesUrls = favoritesManager.getDeletedUrls()
            val tombstoneFavoritesEntries = tombstoneFavoritesUrls.mapNotNull { url ->
                val ref = MediaRef.parse(url)?.parentRef ?: return@mapNotNull null
                SyncMediaEntry(
                    url = ref.canonicalKey,
                    type = ref.type,
                    tmdbId = ref.tmdbId,
                    titlePl = "",
                    isTvShow = ref.isTvShow,
                    isDeleted = true,
                )
            }
            val localFavoritesEntries = activeFavoritesEntries + tombstoneFavoritesEntries

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

            if (tombstoneWatchlistUrls.isNotEmpty()) {
                watchlistManager.clearDeletedUrls(tombstoneWatchlistUrls)
            }
            if (tombstoneFavoritesUrls.isNotEmpty()) {
                favoritesManager.clearDeletedUrls(tombstoneFavoritesUrls)
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
        } finally {
            isSyncing = false
        }
    }
}
