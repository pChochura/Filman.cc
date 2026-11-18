package com.pointlessapps.filman.data.local

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.sessionDataStore by preferencesDataStore(
    name = "filman_session",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "filman_session"))
    },
)

class SessionManager(
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val usernameKey = stringPreferencesKey("saved_username")
    private val authTokenKey = stringPreferencesKey("auth_token")
    private val userIdKey = stringPreferencesKey("user_id")
    private val lastSyncTimestampKey = longPreferencesKey("last_sync_timestamp")

    private val _usernameFlow = MutableStateFlow<String?>(null)
    val usernameFlow: StateFlow<String?> = _usernameFlow.asStateFlow()

    private val _authTokenFlow = MutableStateFlow<String?>(null)
    val authTokenFlow: StateFlow<String?> = _authTokenFlow.asStateFlow()

    private val _lastSyncTimestampFlow = MutableStateFlow(0L)
    val lastSyncTimestampFlow: StateFlow<Long> = _lastSyncTimestampFlow.asStateFlow()

    init {
        scope.launch {
            val prefs = context.sessionDataStore.data.first()
            _usernameFlow.value = prefs[usernameKey]
            _authTokenFlow.value = prefs[authTokenKey]
            _lastSyncTimestampFlow.value = prefs[lastSyncTimestampKey] ?: 0L
        }
    }

    suspend fun clearSession() {
        _authTokenFlow.value = null
        _usernameFlow.value = null
        _lastSyncTimestampFlow.value = 0L

        withContext(Dispatchers.IO) {
            try {
                context.sessionDataStore.edit {
                    it.remove(authTokenKey)
                    it.remove(userIdKey)
                    it.remove(usernameKey)
                    it.remove(lastSyncTimestampKey)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveAuthToken(
        token: String,
        userId: String? = null,
        username: String? = null,
    ) {
        _authTokenFlow.value = token
        if (username != null) {
            _usernameFlow.value = username
        }
        scope.launch {
            context.sessionDataStore.edit {
                it[authTokenKey] = token
                if (userId != null) it[userIdKey] = userId
                if (username != null) it[usernameKey] = username
            }
        }
    }

    fun updateLastSyncTimestamp(timestamp: Long) {
        _lastSyncTimestampFlow.value = timestamp
        scope.launch {
            context.sessionDataStore.edit {
                it[lastSyncTimestampKey] = timestamp
            }
        }
    }

    fun getAuthToken(): String? = _authTokenFlow.value

    fun getLastSyncTimestamp(): Long = _lastSyncTimestampFlow.value

    fun isLoggedIn(): Boolean = !_authTokenFlow.value.isNullOrBlank()
}
