package com.pointlessapps.filman.data.local
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.data.api.NetworkClient
import com.pointlessapps.filman.data.api.challengeVerificationRequest
import okhttp3.Request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

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

    private val cookieKey = stringPreferencesKey("session_cookie")
    private val cloudflareCookieKey = stringPreferencesKey("cf_cookie")
    private val userAgentKey = stringPreferencesKey("user_agent")
    private val usernameKey = stringPreferencesKey("saved_username")
    private val authTokenKey = stringPreferencesKey("auth_token")
    private val userIdKey = stringPreferencesKey("user_id")
    private val lastSyncTimestampKey = longPreferencesKey("last_sync_timestamp")

    private val defaultUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val _cookieFlow = MutableStateFlow<String?>(null)
    val cookieFlow: StateFlow<String?> = _cookieFlow.asStateFlow()

    private val _cfCookieFlow = MutableStateFlow<String?>(null)
    val cfCookieFlow: StateFlow<String?> = _cfCookieFlow.asStateFlow()

    private val _userAgentFlow = MutableStateFlow(defaultUserAgent)
    val userAgentFlow: StateFlow<String> = _userAgentFlow.asStateFlow()
    private val userAgentLock = Any()
    private var userAgentUpdated = false

    private val _usernameFlow = MutableStateFlow<String?>(null)
    val usernameFlow: StateFlow<String?> = _usernameFlow.asStateFlow()

    private val _authTokenFlow = MutableStateFlow<String?>(null)
    val authTokenFlow: StateFlow<String?> = _authTokenFlow.asStateFlow()

    private val _lastSyncTimestampFlow = MutableStateFlow<Long>(0L)
    val lastSyncTimestampFlow: StateFlow<Long> = _lastSyncTimestampFlow.asStateFlow()

    private val _challengeRequiredEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val challengeRequiredEvent: SharedFlow<Unit> = _challengeRequiredEvent.asSharedFlow()

    private val _isChallengeActive = MutableStateFlow(false)
    val isChallengeActive: StateFlow<Boolean> = _isChallengeActive.asStateFlow()

    private val _isStartupVerification = MutableStateFlow(false)
    val isStartupVerification: StateFlow<Boolean> = _isStartupVerification.asStateFlow()

    private val _challengeRequest = MutableStateFlow<Request?>(null)
    val challengeRequest: StateFlow<Request?> = _challengeRequest.asStateFlow()

    private val _isChallengeCancelled = MutableStateFlow(false)
    val isChallengeCancelled: StateFlow<Boolean> = _isChallengeCancelled.asStateFlow()

    private val _verificationGeneration = MutableStateFlow(0L)
    val verificationGeneration: StateFlow<Long> = _verificationGeneration.asStateFlow()

    init {
        scope.launch {
            val prefs = context.sessionDataStore.data.first()
            _cookieFlow.value = prefs[cookieKey]
            _cfCookieFlow.value = prefs[cloudflareCookieKey]
            synchronized(userAgentLock) {
                if (!userAgentUpdated) {
                    _userAgentFlow.value = prefs[userAgentKey] ?: defaultUserAgent
                }
            }
            _usernameFlow.value = prefs[usernameKey]
            _authTokenFlow.value = prefs[authTokenKey]
            _lastSyncTimestampFlow.value = prefs[lastSyncTimestampKey] ?: 0L

            val sess = prefs[cookieKey]?.let(::legacySessionWithoutClearance)
            if (!sess.isNullOrBlank()) {
                NetworkClient.setCloudflareCookie("filman.cc", sess)
            }
        }
    }

    fun saveCookie(cookie: String) {
        _cookieFlow.value = cookie
        _isChallengeCancelled.value = false
        NetworkClient.setCloudflareCookie("filman.cc", cookie)
        scope.launch {
            context.sessionDataStore.edit { it[cookieKey] = cookie }
        }
        if (cookie.contains("cf_clearance")) {
            saveCloudflareCookie(cookie)
        }
    }

    fun saveCloudflareCookie(cookie: String) {
        _cfCookieFlow.value = cookie
        scope.launch {
            context.sessionDataStore.edit { it[cloudflareCookieKey] = cookie }
        }
    }

    fun getCloudflareCookie(): String? = _cfCookieFlow.value

    fun clearCloudflareCookie() {
        _cfCookieFlow.value = null
        NetworkClient.setCloudflareCookie("filman.cc", "")
        scope.launch {
            context.sessionDataStore.edit { it.remove(cloudflareCookieKey) }
        }
    }

    @Synchronized
    fun requestChallenge(request: Request? = null) {
        if (_isChallengeCancelled.value || (_isChallengeActive.value && !_isStartupVerification.value)) return
        _challengeRequest.value = challengeVerificationRequest(request, FilmanConfig.DOMAIN)
        _isStartupVerification.value = false
        _isChallengeActive.value = true
        _challengeRequiredEvent.tryEmit(Unit)
    }

    @Synchronized
    fun startStartupVerification() {
        if (_isChallengeCancelled.value || _isChallengeActive.value) return
        _isStartupVerification.value = true
        _challengeRequest.value = null
        _isChallengeActive.value = true
    }

    fun onChallengeSolved() {
        _isChallengeCancelled.value = false
        _isChallengeActive.value = false
        _isStartupVerification.value = false
        _challengeRequest.value = null
        _verificationGeneration.value += 1
    }

    fun cancelChallenge() {
        _isChallengeCancelled.value = true
        _isChallengeActive.value = false
        _isStartupVerification.value = false
        _challengeRequest.value = null
    }

    fun resetChallengeState() {
        _isChallengeCancelled.value = false
        _isChallengeActive.value = false
        _isStartupVerification.value = false
        _challengeRequest.value = null
    }

    fun clearCookie() {
        _cookieFlow.value = null
        scope.launch {
            context.sessionDataStore.edit { it.remove(cookieKey) }
        }
    }

    fun getCookie(): String? = _cookieFlow.value

    fun hasCookie(): Boolean = !_cookieFlow.value.isNullOrBlank()

    suspend fun clearSession() {
        val oldCookie = _cookieFlow.value
        val oldUserAgent = _userAgentFlow.value

        _cookieFlow.value = null
        _authTokenFlow.value = null
        _lastSyncTimestampFlow.value = 0L

        withContext(Dispatchers.IO) {
            try {
                context.sessionDataStore.edit {
                    it.remove(cookieKey)
                    it.remove(authTokenKey)
                    it.remove(userIdKey)
                    it.remove(usernameKey)
                    it.remove(stringPreferencesKey("saved_password"))
                    it.remove(lastSyncTimestampKey)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (!oldCookie.isNullOrBlank()) {
                try {
                    Jsoup
                        .connect("${FilmanConfig.BASE_URL}/wyloguj")
                        .userAgent(oldUserAgent)
                        .header("Cookie", oldCookie)
                        .ignoreHttpErrors(true)
                        .followRedirects(false)
                        .timeout(3000)
                        .get()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        withContext(Dispatchers.Main) {
            try {
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies(null)
                cookieManager.removeSessionCookies(null)
                cookieManager.flush()
                WebStorage.getInstance().deleteAllData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveUserAgent(ua: String) {
        synchronized(userAgentLock) {
            userAgentUpdated = true
            _userAgentFlow.value = ua
        }
        scope.launch {
            context.sessionDataStore.edit { it[userAgentKey] = ua }
        }
    }

    fun getUserAgent(): String = _userAgentFlow.value

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

internal fun legacySessionWithoutClearance(cookie: String): String =
    cookie.split(";")
        .map { it.trim() }
        .filter { it.isNotBlank() && it.substringBefore("=") !in setOf("cf_clearance", "__cf_bm") }
        .joinToString("; ")
