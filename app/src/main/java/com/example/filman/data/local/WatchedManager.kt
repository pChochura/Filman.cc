package com.example.filman.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WatchedManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("filman_watched", Context.MODE_PRIVATE)

    private val _watchedUrlsFlow = MutableStateFlow<Set<String>>(getWatchedUrls())
    val watchedUrlsFlow: StateFlow<Set<String>> = _watchedUrlsFlow.asStateFlow()

    fun markAsWatched(url: String) {
        val newSet = getWatchedUrls() + url
        prefs.edit { putStringSet("watched_urls", newSet) }
        _watchedUrlsFlow.value = newSet
    }

    fun markAsNotWatched(url: String) {
        val newSet = getWatchedUrls() - url
        prefs.edit { putStringSet("watched_urls", newSet) }
        _watchedUrlsFlow.value = newSet
    }

    fun isWatched(url: String): Boolean {
        return getWatchedUrls().contains(url)
    }

    fun getWatchedUrls(): Set<String> {
        return prefs.getStringSet("watched_urls", emptySet()) ?: emptySet()
    }
}
