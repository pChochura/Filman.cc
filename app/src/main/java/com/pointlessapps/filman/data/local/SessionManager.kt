package com.pointlessapps.filman.data.local
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pointlessapps.filman.config.FilmanConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

internal class SessionManager(
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cookieKey = stringPreferencesKey("session_cookie")
    private val userAgentKey = stringPreferencesKey("user_agent")
    private val usernameKey = stringPreferencesKey("saved_username")
    private val passwordKey = stringPreferencesKey("saved_password")

    private val defaultUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val _cookieFlow = MutableStateFlow<String?>(null)
    val cookieFlow: StateFlow<String?> = _cookieFlow.asStateFlow()

    private val _userAgentFlow = MutableStateFlow(defaultUserAgent)
    val userAgentFlow: StateFlow<String> = _userAgentFlow.asStateFlow()

    private val _usernameFlow = MutableStateFlow<String?>(null)
    val usernameFlow: StateFlow<String?> = _usernameFlow.asStateFlow()

    private val _passwordFlow = MutableStateFlow<String?>(null)
    val passwordFlow: StateFlow<String?> = _passwordFlow.asStateFlow()

    init {
        scope.launch {
            val prefs = context.sessionDataStore.data.first()
            _cookieFlow.value = prefs[cookieKey]
            _userAgentFlow.value = prefs[userAgentKey] ?: defaultUserAgent
            _usernameFlow.value = prefs[usernameKey]
            _passwordFlow.value = prefs[passwordKey]
        }
    }

    fun saveCookie(cookie: String) {
        _cookieFlow.value = cookie
        scope.launch {
            context.sessionDataStore.edit { it[cookieKey] = cookie }
        }
    }

    fun getCookie(): String? = _cookieFlow.value

    fun hasCookie(): Boolean = !_cookieFlow.value.isNullOrBlank()

    suspend fun clearSession() {
        val oldCookie = _cookieFlow.value
        val oldUserAgent = _userAgentFlow.value

        _cookieFlow.value = null

        withContext(Dispatchers.IO) {
            try {
                context.sessionDataStore.edit {
                    it.remove(cookieKey)
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
        _userAgentFlow.value = ua
        scope.launch {
            context.sessionDataStore.edit { it[userAgentKey] = ua }
        }
    }

    fun getUserAgent(): String = _userAgentFlow.value

    fun saveCredentials(
        username: String,
        pass: String,
    ) {
        _usernameFlow.value = username
        _passwordFlow.value = pass
        scope.launch {
            context.sessionDataStore.edit {
                it[usernameKey] = username
                it[passwordKey] = pass
            }
        }
    }
}
