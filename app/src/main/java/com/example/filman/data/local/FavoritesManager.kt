package com.example.filman.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.filman.data.model.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class FavoritesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("filman_favorites", Context.MODE_PRIVATE)

    private val _favoritesFlow = MutableStateFlow<List<Movie>>(getFavorites())
    val favoritesFlow: StateFlow<List<Movie>> = _favoritesFlow.asStateFlow()


    fun getFavorites(): List<Movie> {
        val jsonString = prefs.getString("favorites_list", "[]") ?: "[]"
        val list = mutableListOf<Movie>()
        runCatching {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val movie = Movie(
                    url = obj.getString("url"),
                    title = obj.getString("title"),
                    posterUrl = obj.getString("posterUrl"),
                )
                list.add(movie)
            }
        }
        return list
    }

    fun addFavorite(movie: Movie) {
        val favorites = getFavorites().toMutableList()
        if (favorites.none { it.url == movie.url }) {
            favorites.add(0, movie) // Add to top
            saveFavorites(favorites)
            _favoritesFlow.value = favorites
        }
    }

    fun removeFavorite(url: String) {
        val favorites = getFavorites().toMutableList()
        val iterator = favorites.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().url == url) {
                iterator.remove()
                saveFavorites(favorites)
                _favoritesFlow.value = favorites
                break
            }
        }
    }

    fun isFavorite(url: String): Boolean {
        return getFavorites().any { it.url == url }
    }

    private fun saveFavorites(favorites: List<Movie>) {
        val jsonArray = JSONArray()
        for (movie in favorites) {
            val obj = JSONObject()
            obj.put("url", movie.url)
            obj.put("title", movie.title)
            obj.put("posterUrl", movie.posterUrl)
            jsonArray.put(obj)
        }
        prefs.edit { putString("favorites_list", jsonArray.toString()) }
    }
}
