package com.pointlessapps.filman.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pointlessapps.filman.data.scraper.NetworkClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

private val Context.zaluknijDataStore by preferencesDataStore(name = "zaluknij_session")

class ZaluknijSessionManager(
    private val context: Context,
) {
    private companion object {
        val COOKIE_KEY = stringPreferencesKey("cookie")
    }

    val cookieFlow: Flow<String?> =
        context.zaluknijDataStore.data.map { preferences ->
            preferences[COOKIE_KEY]
        }

    private val _challengeRequiredEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val challengeRequiredEvent = _challengeRequiredEvent.asSharedFlow()

    private val _isChallengeActive = MutableStateFlow(false)
    val isChallengeActive: StateFlow<Boolean> = _isChallengeActive.asStateFlow()

    private val _isChallengeCancelled = MutableStateFlow(false)
    val isChallengeCancelled: StateFlow<Boolean> = _isChallengeCancelled.asStateFlow()

    suspend fun saveCookie(cookie: String) {
        NetworkClient.setCloudflareCookie("zaluknij.cc", cookie)
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
        if (_isChallengeCancelled.value) return
        _isChallengeActive.value = true
        _challengeRequiredEvent.tryEmit(Unit)
    }

    fun onChallengeSolved() {
        _isChallengeCancelled.value = false
        _isChallengeActive.value = false
    }

    fun cancelChallenge() {
        _isChallengeCancelled.value = true
        _isChallengeActive.value = false
    }

    fun resetChallengeState() {
        _isChallengeCancelled.value = false
        _isChallengeActive.value = false
    }
}
