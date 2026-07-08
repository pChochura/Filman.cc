package com.example.filman.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("filman_session", Context.MODE_PRIVATE)

    fun saveCookie(cookie: String) {
        prefs.edit { putString("session_cookie", cookie) }
    }

    fun getCookie(): String? {
        return prefs.getString("session_cookie", null)
    }

    fun hasCookie(): Boolean {
        return getCookie() != null
    }

    fun clearCookie() {
        prefs.edit { remove("session_cookie") }
    }

    fun saveUserAgent(ua: String) {
        prefs.edit { putString("user_agent", ua) }
    }

    fun getUserAgent(): String {
        return prefs.getString(
            "user_agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        )!!
    }

    fun saveCredentials(username: String, pass: String) {
        prefs.edit { 
            putString("saved_username", username)
            putString("saved_password", pass)
        }
    }

    fun getSavedUsername(): String? {
        return prefs.getString("saved_username", null)
    }

    fun getSavedPassword(): String? {
        return prefs.getString("saved_password", null)
    }
}
