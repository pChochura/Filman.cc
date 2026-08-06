package com.pointlessapps.filman.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

private val Context.zaluknijDataStore by preferencesDataStore(name = "zaluknij_session")

internal class ZaluknijSessionManager(
    private val context: Context,
) {
    private companion object {
        val COOKIE_KEY = stringPreferencesKey("cookie")
    }

    val cookieFlow: Flow<String?> = context.zaluknijDataStore.data.map { preferences ->
        preferences[COOKIE_KEY]
    }

    private val _challengeRequiredEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val challengeRequiredEvent = _challengeRequiredEvent.asSharedFlow()

    suspend fun saveCookie(cookie: String) {
        context.zaluknijDataStore.edit { preferences ->
            preferences[COOKIE_KEY] = cookie
        }
    }

    suspend fun clearCookie() {
        context.zaluknijDataStore.edit { preferences ->
            preferences.remove(COOKIE_KEY)
        }
    }

    fun requestChallenge() {
        _challengeRequiredEvent.tryEmit(Unit)
    }
}
