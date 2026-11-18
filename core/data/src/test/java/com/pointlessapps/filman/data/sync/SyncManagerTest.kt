package com.pointlessapps.filman.data.sync

import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.sync.api.SyncApiClient
import com.pointlessapps.filman.data.sync.model.SyncProgressEntry
import com.pointlessapps.filman.data.sync.model.SyncPullResponse
import com.pointlessapps.filman.data.sync.model.SyncPushResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncManagerTest {

    private val apiClient: SyncApiClient = mockk(relaxed = true)
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val progressManager: ProgressManager = mockk(relaxed = true)
    private val watchlistManager: WatchlistManager = mockk(relaxed = true)
    private val favoritesManager: FavoritesManager = mockk(relaxed = true)
    private val settingsManager: SettingsManager = mockk(relaxed = true)

    private lateinit var syncManager: SyncManager

    @Before
    fun setUp() {
        every { progressManager.progressItemsFlow } returns MutableStateFlow(emptyList())
        every { watchlistManager.watchlistFlow } returns MutableStateFlow(emptyList())
        every { favoritesManager.favoritesFlow } returns MutableStateFlow(emptyList())
        coEvery { settingsManager.getPreferencesSnapshot() } returns emptyMap()

        syncManager = SyncManager(
            syncApiClient = apiClient,
            sessionManager = sessionManager,
            progressManager = progressManager,
            watchlistManager = watchlistManager,
            favoritesManager = favoritesManager,
            settingsManager = settingsManager,
        )
    }

    @Test
    fun `syncNow fails when user is not logged in`() = runBlocking {
        every { sessionManager.getAuthToken() } returns null

        val result = syncManager.syncNow()
        assertTrue(result.isFailure)
        assertEquals("User is not logged in", result.exceptionOrNull()?.message)
    }

    @Test
    fun `syncNow pulls remote items, applies them, pushes local state, and updates lastSync`() = runBlocking {
        every { sessionManager.getAuthToken() } returns "valid_token"
        every { sessionManager.getLastSyncTimestamp() } returns 1000L

        coEvery { apiClient.pull(1000L, "valid_token") } returns Result.success(
            SyncPullResponse(
                serverTimestamp = 2000L,
                progress = listOf(
                    SyncProgressEntry(
                        url = "movie_1",
                        titlePl = "Remote Movie",
                        progressPercentage = 0.8f,
                        isWatched = false,
                        updatedAt = 1500L,
                    ),
                ),
                watchlist = emptyList(),
                favorites = emptyList(),
                preferences = mapOf("preferred_quality" to "720p"),
            ),
        )

        coEvery { apiClient.push(any(), "valid_token") } returns Result.success(
            SyncPushResponse(serverTimestamp = 2050L, success = true),
        )

        val result = syncManager.syncNow()
        assertTrue(result.isSuccess)

        coVerify { progressManager.mergeSyncedProgress(match { it.size == 1 && it.first().url == "movie_1" }) }
        coVerify { settingsManager.applyPreferencesSnapshot(match { it["preferred_quality"] == "720p" }) }
        coVerify { apiClient.push(any(), "valid_token") }
        coVerify { sessionManager.updateLastSyncTimestamp(2050L) }

        assertTrue(syncManager.syncState.value is SyncState.Success)
    }
}
