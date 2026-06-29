package com.example.filman.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class WatchedManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("filman_watched", Context.MODE_PRIVATE)

    fun markAsWatched(url: String) {
        prefs.edit { putStringSet("watched_urls", getWatchedUrls() + url) }
    }

    fun isWatched(url: String): Boolean {
        return getWatchedUrls().contains(url)
    }

    private fun getWatchedUrls(): Set<String> {
        return prefs.getStringSet("watched_urls", emptySet()) ?: emptySet()
    }
}
