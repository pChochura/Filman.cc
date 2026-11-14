package com.pointlessapps.filman.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAssociationManagerTest {
    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow<Preferences>(emptyPreferences())

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(data.value)
            data.value = updated
            return updated
        }
    }

    @Test
    fun `first writes and missing clears finish and survive a reload`() = runTest {
        val store = MemoryStore()
        val manager = MediaAssociationManager(store, backgroundScope)
        withTimeout(1_000) {
            manager.clearNotFound(99, false)
            manager.saveProviderUrl(1, false, "ekino", "https://example.com/movie")
            manager.saveEpisodeUrl(2, 1, 1, "zaluknij", "https://example.com/episode")
            manager.markNotFound(3, false, "cda_hd")
        }
        assertTrue(manager.isNotFound(3, false, "cda_hd"))
        manager.clearNotFound(3, false)
        assertFalse(manager.isNotFound(3, false, "cda_hd"))

        val reloaded = MediaAssociationManager(store, backgroundScope)
        assertEquals("https://example.com/movie", reloaded.getProviderUrl(1, false, "ekino"))
        assertEquals("https://example.com/episode", reloaded.getEpisodeUrl(2, 1, 1, "zaluknij"))
        assertNull(reloaded.getProviderUrl(99, false, "ekino"))
    }

    @Test
    fun `concurrent provider saves retain all associations`() = runTest {
        val manager = MediaAssociationManager(MemoryStore(), backgroundScope)
        withTimeout(1_000) {
            listOf("ekino", "zaluknij", "filman").map { provider ->
                async { manager.saveProviderUrl(1, false, provider, "https://example.com/$provider") }
            }.awaitAll()
        }
        for (provider in listOf("ekino", "zaluknij", "filman")) {
            assertEquals("https://example.com/$provider", manager.getProviderUrl(1, false, provider))
        }
    }
}
