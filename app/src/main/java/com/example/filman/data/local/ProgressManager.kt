package com.example.filman.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.filman.data.model.ProgressItem
import org.json.JSONArray
import org.json.JSONObject

class ProgressManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("filman_progress", Context.MODE_PRIVATE)

    fun getProgressItems(): List<ProgressItem> {
        val jsonString = prefs.getString("progress_list", "[]") ?: "[]"
        val list = mutableListOf<ProgressItem>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val item = ProgressItem(
                    url = obj.getString("url"),
                    title = obj.getString("title"),
                    posterUrl = obj.getString("posterUrl"),
                    progressMs = obj.getLong("progressMs"),
                    durationMs = obj.getLong("durationMs"),
                    seriesTitle = if (obj.has("seriesTitle")) obj.getString("seriesTitle") else null
                )
                list.add(item)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveProgress(item: ProgressItem) {
        val items = getProgressItems().toMutableList()
        // Remove existing entry if it exists (exact URL)
        items.removeAll { it.url == item.url }
        
        // Also remove any older episodes from the same series!
        if (item.seriesTitle != null && item.seriesTitle.isNotBlank()) {
            items.removeAll { it.seriesTitle == item.seriesTitle }
        }
        
        if (item.durationMs > 0) {
            items.add(0, item) // Add to top (most recently watched)
        }
        
        // Keep only top 200 items to avoid bloating
        val trimmedItems = items.take(200)
        
        saveItems(trimmedItems)
    }

    fun getProgressForUrl(url: String): ProgressItem? {
        return getProgressItems().find { it.url == url }
    }

    private fun saveItems(items: List<ProgressItem>) {
        val jsonArray = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("url", item.url)
            obj.put("title", item.title)
            obj.put("posterUrl", item.posterUrl)
            obj.put("progressMs", item.progressMs)
            obj.put("durationMs", item.durationMs)
            if (item.seriesTitle != null) {
                obj.put("seriesTitle", item.seriesTitle)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("progress_list", jsonArray.toString()).apply()
    }
}
